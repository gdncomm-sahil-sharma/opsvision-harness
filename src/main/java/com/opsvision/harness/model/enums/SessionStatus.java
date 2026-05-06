package com.opsvision.harness.model.enums;

public enum SessionStatus {
    ACTIVE,
    COMPLETED,
    FAILED,
    EXPIRED,
    /** User-driven soft delete. Read-only; chats in this state cannot
     *  accept new messages until they are unarchived back to ACTIVE. */
    ARCHIVED
}