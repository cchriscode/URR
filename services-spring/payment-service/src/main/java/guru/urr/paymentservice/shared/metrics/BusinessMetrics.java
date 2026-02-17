package guru.urr.paymentservice.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter paymentProcessed;
    private final Counter paymentFailed;
    private final Counter paymentRefunded;
    private final Timer paymentProcessingDuration;

    public BusinessMetrics(MeterRegistry registry) {
        this.paymentProcessed = Counter.builder("urr.payment.processed")
                .description("Total payments processed successfully")
                .register(registry);
        this.paymentFailed = Counter.builder("urr.payment.failed")
                .description("Total payments failed")
                .register(registry);
        this.paymentRefunded = Counter.builder("urr.payment.refunded")
                .description("Total payments refunded")
                .register(registry);
        this.paymentProcessingDuration = Timer.builder("urr.payment.processing.duration")
                .description("Payment processing duration")
                .register(registry);
    }

    public void recordPaymentProcessed() { paymentProcessed.increment(); }
    public void recordPaymentFailed() { paymentFailed.increment(); }
    public void recordPaymentRefunded() { paymentRefunded.increment(); }
    public Timer.Sample startPaymentTimer() { return Timer.start(); }
    public void stopPaymentTimer(Timer.Sample sample) { sample.stop(paymentProcessingDuration); }
}
