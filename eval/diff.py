#!/usr/bin/env python3
"""
Diff two regression-eval runs.

Compares results-A.json against results-B.json on objectively-observable
fields (tool chain, references keys, answered, unanswered reason, summary
similarity). Token counts are reported as drift, not regression — they
vary turn-to-turn and chasing 0% drift is a false signal.

What counts as a regression vs informational:
  * tool-set lost  → regression  (a tool we used to call no longer fires)
  * tool-set gained → informational (could be legit improvement)
  * references lost → regression  (a key we used to surface dropped)
  * answered T → F → regression  (self-grade dropped)
  * answered F → T → improvement  (self-grade flipped up)
  * summary text  → drift  (always changes; show diff for eyeball)
  * cost delta    → drift  (always changes; show as analytic)

Usage:
  ./eval/diff.py eval/results/A.json eval/results/B.json
  ./eval/diff.py --json A.json B.json     # machine-readable output
"""

from __future__ import annotations

import argparse
import difflib
import json
import sys
from pathlib import Path
from typing import Any


def load(path: str) -> dict:
    return json.loads(Path(path).read_text())


def index(run: dict, kind: str) -> dict[str, dict]:
    if kind == "single":
        return {r["label"]: r for r in run.get("single", [])}
    return {r["label"]: r for r in run.get("flow", [])}


def diff_set(name: str, a: list, b: list) -> list[str]:
    sa, sb = set(a or []), set(b or [])
    notes = []
    lost = sa - sb
    gained = sb - sa
    if lost:
        notes.append(f"  ✗ {name} lost: {sorted(lost)}")
    if gained:
        notes.append(f"  + {name} gained: {sorted(gained)}")
    return notes


def text_similarity(a: str, b: str) -> float:
    if not a and not b:
        return 1.0
    return difflib.SequenceMatcher(None, a or "", b or "").ratio()


def diff_turn(a: dict, b: dict, prefix: str = "") -> list[str]:
    """Diff a single turn record (single Q or one turn of a flow)."""
    out: list[str] = []
    if "httpError" in a or "httpError" in b:
        if a.get("httpError") != b.get("httpError"):
            out.append(f"{prefix}HTTP error changed: {a.get('httpError')!r} → {b.get('httpError')!r}")
            return out

    out += [prefix + s for s in diff_set("tools", a.get("tools"), b.get("tools"))]
    out += [prefix + s for s in diff_set("references", a.get("referenceKeys"), b.get("referenceKeys"))]

    if a.get("answered") != b.get("answered"):
        flip = f"{a.get('answered')} → {b.get('answered')}"
        marker = "✗" if (a.get("answered") and not b.get("answered")) else "+"
        out.append(f"{prefix}  {marker} answered: {flip}")

    if a.get("unansweredReason") != b.get("unansweredReason"):
        out.append(
            f"{prefix}  · unansweredReason: {a.get('unansweredReason')!r} → "
            f"{b.get('unansweredReason')!r}"
        )

    sim = text_similarity(a.get("summary", ""), b.get("summary", ""))
    if sim < 0.6:
        out.append(f"{prefix}  · summary diverged (similarity={sim:.2f})")
        out.append(f"{prefix}    A: {(a.get('summary') or '')[:160]}")
        out.append(f"{prefix}    B: {(b.get('summary') or '')[:160]}")

    return out


def summarise_run(run: dict) -> dict:
    singles = run.get("single", [])
    answered_count = sum(1 for r in singles if r.get("answered") is True)
    total_cost = sum((r.get("costUsd") or 0) for r in singles)
    flow_turns = sum(len(f.get("turns", [])) for f in run.get("flow", []))
    return {
        "runId": run.get("runId"),
        "singles": len(singles),
        "answeredTrue": answered_count,
        "flowCases": len(run.get("flow", [])),
        "flowTurns": flow_turns,
        "totalCostUsd": round(total_cost, 6),
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("a", help="Baseline run results JSON")
    parser.add_argument("b", help="Candidate run results JSON")
    parser.add_argument("--json", action="store_true",
                        help="Emit machine-readable diff to stdout")
    args = parser.parse_args()

    A = load(args.a)
    B = load(args.b)

    if args.json:
        diff = collect_diff(A, B)
        json.dump(diff, sys.stdout, indent=2)
        sys.stdout.write("\n")
        return

    print(f"=== A: {args.a} ===")
    print(json.dumps(summarise_run(A), indent=2))
    print(f"=== B: {args.b} ===")
    print(json.dumps(summarise_run(B), indent=2))
    print()
    print("=== single-turn diffs ===")
    sa, sb = index(A, "single"), index(B, "single")
    for label in sorted(set(sa) | set(sb)):
        ra = sa.get(label)
        rb = sb.get(label)
        if not ra:
            print(f"[{label}] only in B (added)")
            continue
        if not rb:
            print(f"[{label}] only in A (dropped)")
            continue
        notes = diff_turn(ra, rb)
        if notes:
            print(f"[{label}]")
            for n in notes:
                print(n)
        else:
            print(f"[{label}] ✓ unchanged")
    print()
    print("=== flow diffs ===")
    fa, fb = index(A, "flow"), index(B, "flow")
    for label in sorted(set(fa) | set(fb)):
        ra = fa.get(label)
        rb = fb.get(label)
        if not ra or not rb:
            print(f"[{label}] presence delta")
            continue
        ta, tb = ra.get("turns", []), rb.get("turns", [])
        if len(ta) != len(tb):
            print(f"[{label}] turn-count: {len(ta)} → {len(tb)}")
        for i in range(min(len(ta), len(tb))):
            notes = diff_turn(ta[i], tb[i], prefix=f"[{label}/T{i+1}] ")
            for n in notes:
                print(n)


def collect_diff(A: dict, B: dict) -> dict:
    """Build a JSON-friendly diff doc — for CI piping or future tooling."""
    sa, sb = index(A, "single"), index(B, "single")
    fa, fb = index(A, "flow"), index(B, "flow")
    return {
        "summary": {"a": summarise_run(A), "b": summarise_run(B)},
        "single": [
            {
                "label": label,
                "presence": (
                    "both" if label in sa and label in sb
                    else "a-only" if label in sa else "b-only"
                ),
                "diffs": diff_turn(sa.get(label, {}), sb.get(label, {})),
            }
            for label in sorted(set(sa) | set(sb))
        ],
        "flow": [
            {
                "label": label,
                "turnDiffs": [
                    diff_turn(
                        fa.get(label, {}).get("turns", [{}] * 10)[i] if i < len(fa.get(label, {}).get("turns", [])) else {},
                        fb.get(label, {}).get("turns", [{}] * 10)[i] if i < len(fb.get(label, {}).get("turns", [])) else {},
                        prefix=f"T{i+1} ",
                    )
                    for i in range(max(
                        len(fa.get(label, {}).get("turns", [])),
                        len(fb.get(label, {}).get("turns", [])),
                    ))
                ],
            }
            for label in sorted(set(fa) | set(fb))
        ],
    }


if __name__ == "__main__":
    main()
