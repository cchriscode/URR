package guru.urr.statsservice.shared.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class BusinessMetrics {

    private final Counter eventsConsumed;
    private final Counter statsQueriesTotal;
    private final Counter statsQueryErrors;

    public BusinessMetrics(MeterRegistry registry) {
        this.eventsConsumed = Counter.builder("urr.stats.events.consumed")
                .description("Total events consumed from Kafka")
                .register(registry);
        this.statsQueriesTotal = Counter.builder("urr.stats.queries.total")
                .description("Total statistics queries")
                .register(registry);
        this.statsQueryErrors = Counter.builder("urr.stats.queries.errors")
                .description("Total statistics query errors")
                .register(registry);
    }

    public void recordEventConsumed() { eventsConsumed.increment(); }
    public void recordStatsQuery() { statsQueriesTotal.increment(); }
    public void recordStatsQueryError() { statsQueryErrors.increment(); }
}
