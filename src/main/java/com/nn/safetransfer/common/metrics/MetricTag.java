package com.nn.safetransfer.common.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetricTag {
    OUTCOME("outcome"),
    EVENT_TYPE("event_type"),
    STATUS("status");

    private final String value;
}
