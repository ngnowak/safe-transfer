package com.nn.safetransfer.common.metrics;

import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransferMetricsTest {

    @Test
    void shouldRecordSuccessUsingOutcomeTagValue() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);
        var sample = transferMetrics.startTransfer();

        transferMetrics.recordTransferSuccess(sample);

        assertThat(meterRegistry.get("safetransfer.transfer.created")
                .tag("outcome", "success")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("safetransfer.transfer.duration")
                .tag("outcome", "success")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordFailureUsingEnumTagValue() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);
        var sample = transferMetrics.startTransfer();

        transferMetrics.recordTransferFailure(sample, TransferMetricOutcome.WALLET_NOT_FOUND);

        assertThat(meterRegistry.get("safetransfer.transfer.created")
                .tag("outcome", "wallet_not_found")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("safetransfer.transfer.duration")
                .tag("outcome", "wallet_not_found")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordOutboxPublishedUsingEnums() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);

        transferMetrics.recordOutboxPublished(EventType.TRANSFER_COMPLETED, 1_000L);

        assertThat(meterRegistry.get("safetransfer.outbox.publish.success")
                .tag("event_type", "TRANSFER_COMPLETED")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("safetransfer.outbox.publish.duration")
                .tag("event_type", "TRANSFER_COMPLETED")
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordFatalOutboxFailureUsingEnums() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);

        transferMetrics.recordOutboxFailed(EventType.TRANSFER_COMPLETED, OutboxStatus.FATAL, 1_000L);

        assertThat(meterRegistry.get("safetransfer.outbox.publish.failure")
                .tag("event_type", "TRANSFER_COMPLETED")
                .tag("status", "FATAL")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("safetransfer.outbox.fatal")
                .tag("event_type", "TRANSFER_COMPLETED")
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get("safetransfer.outbox.publish.duration")
                .tag("event_type", "TRANSFER_COMPLETED")
                .tag("status", "FATAL")
                .timer()
                .count()).isEqualTo(1L);
    }
}
