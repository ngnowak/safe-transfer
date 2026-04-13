package com.nn.safetransfer.common.metrics;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MetricName {
    TRANSFER_CREATED("safetransfer.transfer.created"),
    TRANSFER_DURATION("safetransfer.transfer.duration"),
    OUTBOX_PUBLISH_SUCCESS("safetransfer.outbox.publish.success"),
    OUTBOX_PUBLISH_FAILURE("safetransfer.outbox.publish.failure"),
    OUTBOX_FATAL("safetransfer.outbox.fatal"),
    OUTBOX_PUBLISH_DURATION("safetransfer.outbox.publish.duration");

    private final String value;
}
