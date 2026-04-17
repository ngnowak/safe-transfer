package com.nn.safetransfer.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import com.nn.safetransfer.outbox.domain.EventType;
import com.nn.safetransfer.outbox.domain.OutboxStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class TransferMetrics {
    private static final String TRANSFER_DURATION_DESCRIPTION = "Time spent processing transfer commands";
    private static final String OUTBOX_DURATION_DESCRIPTION = "Time spent publishing outbox events";

    private final MeterRegistry meterRegistry;

    public TransferMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startTransfer() {
        return Timer.start(meterRegistry);
    }

    public void recordTransferSuccess(Timer.Sample sample) {
        recordTransfer(sample, TransferMetricOutcome.SUCCESS);
    }

    public void recordTransferFailure(Timer.Sample sample, TransferMetricOutcome outcome) {
        recordTransfer(sample, outcome);
    }

    private void recordTransfer(Timer.Sample sample, TransferMetricOutcome outcome) {
        Counter.builder(MetricName.TRANSFER_CREATED.getValue())
                .tag(MetricTag.OUTCOME.getValue(), outcome.getTagValue())
                .register(meterRegistry)
                .increment();

        sample.stop(Timer.builder(MetricName.TRANSFER_DURATION.getValue())
                .description(TRANSFER_DURATION_DESCRIPTION)
                .tag(MetricTag.OUTCOME.getValue(), outcome.getTagValue())
                .publishPercentileHistogram()
                .register(meterRegistry));
    }

    public void recordOutboxPublished(EventType eventType, long durationNanos) {
        Counter.builder(MetricName.OUTBOX_PUBLISH_SUCCESS.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), eventType.name())
                .register(meterRegistry)
                .increment();

        Timer.builder(MetricName.OUTBOX_PUBLISH_DURATION.getValue())
                .description(OUTBOX_DURATION_DESCRIPTION)
                .tag(MetricTag.EVENT_TYPE.getValue(), eventType.name())
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    public void recordOutboxFailed(EventType eventType, OutboxStatus status, long durationNanos) {
        Counter.builder(MetricName.OUTBOX_PUBLISH_FAILURE.getValue())
                .tag(MetricTag.EVENT_TYPE.getValue(), eventType.name())
                .tag(MetricTag.STATUS.getValue(), status.name())
                .register(meterRegistry)
                .increment();

        if (status == OutboxStatus.FATAL) {
            Counter.builder(MetricName.OUTBOX_FATAL.getValue())
                    .tag(MetricTag.EVENT_TYPE.getValue(), eventType.name())
                    .register(meterRegistry)
                    .increment();
        }

        Timer.builder(MetricName.OUTBOX_PUBLISH_DURATION.getValue())
                .description(OUTBOX_DURATION_DESCRIPTION)
                .tag(MetricTag.EVENT_TYPE.getValue(), eventType.name())
                .tag(MetricTag.STATUS.getValue(), status.name())
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
