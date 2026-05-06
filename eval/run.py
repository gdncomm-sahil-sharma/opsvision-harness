#!/usr/bin/env python3
"""
Regression-diff harness runner.

Fires each question fixture against /api/chat with a fresh userId, then
pulls the per-turn audit trail straight from Postgres (tool_execution +
conversation rows) so we capture what the harness actually DID rather
than parsing the HTTP response. Result is written as JSON to
eval/results/<run-id>.json. Compare two runs with diff.py.

Why DB rather than just the HTTP response: tool_execution rows include
the full chain (cache hits, retries, augmented results); the conversation
row carries token counts and references that the API response sometimes
omits.

No expected-answer scoring. The diff tool compares two runs on
objectively-observable fields only. Regressions are deltas, not failures
against a fixed truth.

Usage:
  ./eval/run.py                                          # default fixture
  ./eval/run.py --fixture eval/fixtures/wcs-phantom-close.yaml
  ./eval/run.py --label baseline                         # tag the run
"""

from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import subprocess
import sys
import time
import uuid
from pathlib import Path
from typing import Any

import urllib.request
import urllib.error
import yaml  # PyYAML — install via `pip install pyyaml` if missing

API = os.environ.get("HARNESS_URL", "http://localhost:8080")
DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_USER = os.environ.get("DB_USER", "opsvision")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "opsvision")
DB_NAME = os.environ.get("DB_NAME", "opsvision")
TIMEOUT_SEC = int(os.environ.get("EVAL_TIMEOUT", "180"))


def post_chat(user_id: str, message: str, chat_id: str | None = None) -> dict:
    body = {"userId": user_id, "message": message}
    if chat_id:
        body["chatId"] = chat_id
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        f"{API}/api/chat",
        data=data,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SEC) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"error": f"HTTP {e.code}", "body": e.read().decode("utf-8", errors="ignore")}
    except urllib.error.URLError as e:
        return {"error": f"URLError {e.reason}"}


def psql(query: str) -> str:
    """Run a quick read-only psql query and return raw stdout."""
    cmd = [
        "psql", "-h", DB_HOST, "-U", DB_USER, "-d", DB_NAME,
        "-t", "-A", "-c", query,
    ]
    env = os.environ.copy()
    env["PGPASSWORD"] = DB_PASSWORD
    out = subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=30)
    if out.returncode != 0:
        raise RuntimeError(f"psql failed: {out.stderr}")
    return out.stdout.strip()


def capture_turn(chat_id: str) -> dict[str, Any]:
    """Pull the audit-side facts for a chat's most recent turn.

    Returns the canonical eval record: tool chain (in invocation order),
    cache-hit count, references keys, answered, summary, token counts.
    """
    # Latest conversation row in this chat.
    row = psql(
        "SELECT id, response, context_data::text, answered, unanswered_reason, "
        "       prompt_tokens, completion_tokens, cost_usd, model, created_at "
        f"FROM conversation WHERE session_id='{chat_id}' "
        "ORDER BY sequence_number DESC LIMIT 1"
    )
    if not row:
        return {"error": "no conversation row"}
    parts = row.split("|")
    conv_id = parts[0]
    response = parts[1] if len(parts) > 1 else ""
    context_data_raw = parts[2] if len(parts) > 2 else ""
    answered = parts[3] if len(parts) > 3 else ""
    unanswered_reason = parts[4] if len(parts) > 4 else ""
    prompt_tokens = int(parts[5]) if len(parts) > 5 and parts[5] else None
    completion_tokens = int(parts[6]) if len(parts) > 6 and parts[6] else None
    cost_usd = float(parts[7]) if len(parts) > 7 and parts[7] else None
    model = parts[8] if len(parts) > 8 and parts[8] else None

    refs: list[str] = []
    if context_data_raw:
        try:
            refs = sorted(json.loads(context_data_raw).keys())
        except json.JSONDecodeError:
            pass

    # Tool chain in invocation order. We log a row per call; cache hits
    # are tagged via error_message='cache-hit'.
    tools_raw = psql(
        "SELECT tool_name, COALESCE(error_message,''), execution_time_ms "
        f"FROM tool_execution WHERE conversation_id='{conv_id}' "
        "ORDER BY created_at ASC"
    )
    tools = []
    cache_hits = 0
    failures = 0
    for line in tools_raw.splitlines():
        if not line:
            continue
        bits = line.split("|")
        name = bits[0]
        err = bits[1] if len(bits) > 1 else ""
        latency = int(bits[2]) if len(bits) > 2 and bits[2] else 0
        is_cache = err == "cache-hit"
        if is_cache:
            cache_hits += 1
        if err and not is_cache:
            failures += 1
        tools.append({"name": name, "cacheHit": is_cache, "latencyMs": latency})

    return {
        "conversationId": conv_id,
        "summary": response,
        "answered": answered.lower() == "t" if answered else None,
        "unansweredReason": unanswered_reason or None,
        "referenceKeys": refs,
        "tools": [t["name"] for t in tools],
        "toolCacheHits": cache_hits,
        "toolFailures": failures,
        "toolDetail": tools,
        "promptTokens": prompt_tokens,
        "completionTokens": completion_tokens,
        "costUsd": cost_usd,
        "model": model,
    }


def run_single(label: str, message: str, run_id: str) -> dict:
    user_id = f"eval-{run_id}-{label}"
    t0 = time.time()
    resp = post_chat(user_id, message)
    elapsed_ms = int((time.time() - t0) * 1000)

    chat_id = resp.get("chatId") if isinstance(resp, dict) else None
    if not chat_id:
        return {
            "label": label,
            "message": message,
            "userId": user_id,
            "elapsedMs": elapsed_ms,
            "httpError": resp.get("error") or "no chatId in response",
            "raw": resp,
        }
    captured = capture_turn(chat_id)
    return {
        "label": label,
        "message": message,
        "userId": user_id,
        "chatId": chat_id,
        "elapsedMs": elapsed_ms,
        **captured,
    }


def run_flow(label: str, turns: list[str], run_id: str) -> dict:
    user_id = f"eval-{run_id}-{label}"
    out_turns: list[dict] = []
    chat_id: str | None = None
    for i, msg in enumerate(turns, start=1):
        t0 = time.time()
        resp = post_chat(user_id, msg, chat_id=chat_id)
        elapsed_ms = int((time.time() - t0) * 1000)
        if not chat_id:
            chat_id = resp.get("chatId") if isinstance(resp, dict) else None
        if not chat_id:
            out_turns.append({
                "turn": i,
                "message": msg,
                "elapsedMs": elapsed_ms,
                "httpError": resp.get("error") or "no chatId",
                "raw": resp,
            })
            break
        captured = capture_turn(chat_id)
        out_turns.append({"turn": i, "message": msg, "elapsedMs": elapsed_ms, **captured})
    return {"label": label, "userId": user_id, "chatId": chat_id, "turns": out_turns}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--fixture", default="eval/fixtures/wcs-phantom-close.yaml")
    parser.add_argument("--label", default=None,
                        help="Optional name suffix for the result file (e.g. 'baseline').")
    args = parser.parse_args()

    fixture_path = Path(args.fixture)
    fixture = yaml.safe_load(fixture_path.read_text())

    run_id = dt.datetime.now().strftime("%Y%m%dT%H%M%S")
    if args.label:
        run_id = f"{run_id}-{args.label}"

    print(f"[run] fixture={fixture_path} run_id={run_id}", file=sys.stderr)
    results = {
        "runId": run_id,
        "fixture": str(fixture_path),
        "startedAt": dt.datetime.now().isoformat(),
        "single": [],
        "flow": [],
    }

    for case in fixture.get("single", []):
        print(f"[run]   {case['label']}", file=sys.stderr)
        results["single"].append(run_single(case["label"], case["message"], run_id))

    for case in fixture.get("flow", []):
        print(f"[run]   {case['label']} ({len(case['turns'])} turns)", file=sys.stderr)
        results["flow"].append(run_flow(case["label"], case["turns"], run_id))

    out = Path(f"eval/results/{run_id}.json")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(results, indent=2))
    print(f"[run] wrote {out}")


if __name__ == "__main__":
    main()
