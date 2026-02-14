package com.tiketi.queueservice.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class QueueMetrics {

    private final Counter queueJoined;
    private final Counter queueAdmitted;
    private final Counter queueLeft;

    public QueueMetrics(MeterRegistry registry) {
        this.queueJoined = Counter.builder("business.queue.joined.total")
            .description("Total users who joined the queue")
            .register(registry);
        this.queueAdmitted = Counter.builder("business.queue.admitted.total")
            .description("Total users admitted from queue")
            .register(registry);
        this.queueLeft = Counter.builder("business.queue.left.total")
            .description("Total users who left the queue")
            .register(registry);
    }

    public void recordQueueJoined() { queueJoined.increment(); }
    public void recordQueueAdmitted() { queueAdmitted.increment(); }
    public void recordQueueLeft() { queueLeft.increment(); }
}
