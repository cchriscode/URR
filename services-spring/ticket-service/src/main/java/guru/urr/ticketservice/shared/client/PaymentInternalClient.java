package guru.urr.ticketservice.shared.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class PaymentInternalClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentInternalClient.class);

    private final RestClient restClient;
    private final String internalToken;

    public PaymentInternalClient(
        @Value("${PAYMENT_SERVICE_URL:http://localhost:3003}") String paymentServiceUrl,
        @Value("${INTERNAL_API_TOKEN}") String internalToken
    ) {
        var requestFactory = ClientHttpRequestFactories.get(ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(Duration.ofSeconds(10)));
        this.restClient = RestClient.builder().baseUrl(paymentServiceUrl).requestFactory(requestFactory).build();
        this.internalToken = internalToken;
    }

    @CircuitBreaker(name = "internalService", fallbackMethod = "getPaymentByReservationFallback")
    @Retry(name = "internalService")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPaymentByReservation(UUID reservationId) {
        return restClient.get()
            .uri("/internal/payments/by-reservation/{reservationId}", reservationId)
            .header("Authorization", "Bearer " + internalToken)
            .retrieve()
            .body(Map.class);
    }

    @SuppressWarnings("unused")
    private Map<String, Object> getPaymentByReservationFallback(UUID reservationId, Throwable t) {
        log.warn("Circuit breaker: getPaymentByReservation failed for reservation {}: {}", reservationId, t.getMessage());
        return null;
    }
}
