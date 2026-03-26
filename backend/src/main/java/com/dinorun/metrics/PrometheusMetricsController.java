package com.dinorun.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

/**
 * Prometheus가 스크랩하는 표준 경로 {@code /metrics}.
 * (액추에이터 기본은 {@code /actuator/prometheus})
 */
@RestController
@ConditionalOnBean(PrometheusMeterRegistry.class)
public class PrometheusMetricsController {

    private static final String PROMETHEUS_TEXT_004 = "text/plain;version=0.0.4;charset=utf-8";

    private final PrometheusMeterRegistry registry;

    public PrometheusMetricsController(PrometheusMeterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/metrics", produces = PROMETHEUS_TEXT_004)
    public String metrics() {
        return registry.scrape();
    }
}
