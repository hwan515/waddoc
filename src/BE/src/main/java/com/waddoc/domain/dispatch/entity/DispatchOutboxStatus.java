package com.waddoc.domain.dispatch.entity;

public enum DispatchOutboxStatus {
    PENDING,
    PUBLISHED,
    RETRY_PENDING,
    COMPLETED
}
