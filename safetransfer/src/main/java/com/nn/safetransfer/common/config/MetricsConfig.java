package com.nn.safetransfer.common.config;

import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class MetricsConfig {

    @Bean
    MeterFilter metricsCommonTags() {
        return MeterFilter.commonTags(List.of(Tag.of("application", "safetransfer")));
    }
}
