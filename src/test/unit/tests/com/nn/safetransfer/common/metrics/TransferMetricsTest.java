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

        assertThat(meterRegistry.get(MetricName.TRANSFER_CREATED.getValue())
                .tag(MetricTag.OUTCOME.getValue(), TransferMetricOutcome.SUCCESS.getTagValue())
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get(MetricName.TRANSFER_DURATION.getValue())
                .tag(MetricTag.OUTCOME.getValue(), TransferMetricOutcome.SUCCESS.getTagValue())
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordFailureUsingEnumTagValue() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);
        var sample = transferMetrics.startTransfer();

        transferMetrics.recordTransferFailure(sample, TransferMetricOutcome.WALLET_NOT_FOUND);

        assertThat(meterRegistry.get(MetricName.TRANSFER_CREATED.getValue())
                .tag(MetricTag.OUTCOME.getValue(), TransferMetricOutcome.WALLET_NOT_FOUND.getTagValue())
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get(MetricName.TRANSFER_DURATION.getValue())
                .tag(MetricTag.OUTCOME.getValue(), TransferMetricOutcome.WALLET_NOT_FOUND.getTagValue())
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordOutboxPublishedUsingEnums() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);

        transferMetrics.recordOutboxPublished(EventType.TRANSFER_COMPLETED, 1_000L);

        assertThat(meterRegistry.get(MetricName.OUTBOX_PUBLISH_SUCCESS.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), EventType.TRANSFER_COMPLETED.name())
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get(MetricName.OUTBOX_PUBLISH_DURATION.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), EventType.TRANSFER_COMPLETED.name())
                .timer()
                .count()).isEqualTo(1L);
    }

    @Test
    void shouldRecordFatalOutboxFailureUsingEnums() {
        var meterRegistry = new SimpleMeterRegistry();
        var transferMetrics = new TransferMetrics(meterRegistry);

        transferMetrics.recordOutboxFailed(EventType.TRANSFER_COMPLETED, OutboxStatus.FATAL, 1_000L);

        assertThat(meterRegistry.get(MetricName.OUTBOX_PUBLISH_FAILURE.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), EventType.TRANSFER_COMPLETED.name())
                .tag(MetricTag.STATUS.getValue(), OutboxStatus.FATAL.name())
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get(MetricName.OUTBOX_FATAL.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), EventType.TRANSFER_COMPLETED.name())
                .counter()
                .count()).isEqualTo(1.0d);
        assertThat(meterRegistry.get(MetricName.OUTBOX_PUBLISH_DURATION.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), EventType.TRANSFER_COMPLETED.name())
                .tag(MetricTag.STATUS.getValue(), OutboxStatus.FATAL.name())
                .timer()
                .count()).isEqualTo(1L);
    }
}
