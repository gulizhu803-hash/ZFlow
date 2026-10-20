package com.zgl.zflow.domain.office.model;
public enum OfficeStatus {
    QUEUED, RUNNING, CANCEL_REQUESTED, SUCCEEDED, FAILED, CANCELLED;
    public boolean terminal() { return this == SUCCEEDED || this == FAILED || this == CANCELLED; }
}
