package com.hari.paymentservice.metrics;

import com.hari.paymentservice.payment.PaymentRepository;
import com.hari.paymentservice.payment.PaymentStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentMetricsConfig {

    @Bean
    MeterBinder unresolvedRefundMetrics(PaymentRepository paymentRepository,
                                        @Value("${payment.refund.max-attempts}") int maxAttempts) {
        return registry -> Gauge.builder("payment.refunds.unresolved", paymentRepository,
                        repository -> repository.countByStatusAndRefundAttemptCountGreaterThanEqual(
                                PaymentStatus.REFUND_FAILED, maxAttempts))
                .description("Refunds that exhausted their retries and need manual reconciliation")
                .register(registry);
    }
}