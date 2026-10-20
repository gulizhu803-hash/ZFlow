package com.zgl.zflow.domain.office.model;
/** Operational metric signal. Published transactionally; contains no user or resource identifiers. */
public record OfficeActivity(String eventType,String status,String stage,String errorKey,Long elapsedMillis) {}
