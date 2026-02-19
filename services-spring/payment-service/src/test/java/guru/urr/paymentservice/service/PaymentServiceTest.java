package guru.urr.paymentservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import guru.urr.paymentservice.client.TicketInternalClient;
import guru.urr.paymentservice.dto.ConfirmPaymentRequest;
import guru.urr.paymentservice.dto.PreparePaymentRequest;
import guru.urr.paymentservice.dto.ProcessPaymentRequest;
import guru.urr.paymentservice.messaging.PaymentEventProducer;
import guru.urr.paymentservice.messaging.event.PaymentRefundedEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @Mock private TicketInternalClient ticketInternalClient;
    @Mock private PaymentEventProducer paymentEventProducer;
    @Mock private PaymentTypeDispatcher paymentTypeDispatcher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(jdbcTemplate, ticketInternalClient, paymentEventProducer, paymentTypeDispatcher, "test_ck_dummy");
    }

    @Test
    void prepare_reservation_success() {
        UUID reservationId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        when(ticketInternalClient.validateReservation(reservationId, userId))
            .thenReturn(Map.of("total_amount", 50000, "event_id", UUID.randomUUID().toString()));
        when(jdbcTemplate.queryForList(contains("SELECT id, order_id, status"), any(Object[].class)))
            .thenReturn(Collections.emptyList());
        when(jdbcTemplate.update(contains("INSERT INTO payments"), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(1);

        PreparePaymentRequest request = new PreparePaymentRequest(reservationId, 50000, null, null);
        Map<String, Object> result = paymentService.prepare(userId, request);

        assertNotNull(result.get("orderId"));
        assertEquals(50000, result.get("amount"));
        assertEquals("test_ck_dummy", result.get("clientKey"));
    }

    @Test
    void prepare_amountMismatch_throws() {
        UUID reservationId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();

        when(ticketInternalClient.validateReservation(reservationId, userId))
            .thenReturn(Map.of("total_amount", 50000, "event_id", UUID.randomUUID().toString()));

        PreparePaymentRequest request = new PreparePaymentRequest(reservationId, 30000, null, null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> paymentService.prepare(userId, request));
        assertTrue(ex.getReason().contains("Amount mismatch"));
    }

    @Test
    void confirm_success() {
        String userId = UUID.randomUUID().toString();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Map<String, Object> payment = new HashMap<>();
        payment.put("id", paymentId);
        payment.put("reservation_id", reservationId);
        payment.put("user_id", userId);
        payment.put("amount", 50000);
        payment.put("status", "pending");
        payment.put("payment_type", "reservation");
        payment.put("reference_id", null);
        payment.put("order_id", "ORD_123");

        when(jdbcTemplate.queryForList(contains("SELECT id, reservation_id"), any(Object[].class)))
            .thenReturn(List.of(payment));
        when(ticketInternalClient.validateReservation(reservationId, userId))
            .thenReturn(Map.of("total_amount", 50000));
        when(jdbcTemplate.update(contains("UPDATE payments"), any(), any(), any())).thenReturn(1);

        ConfirmPaymentRequest request = new ConfirmPaymentRequest("pk_test_123", "ORD_123", 50000);
        Map<String, Object> result = paymentService.confirm(userId, request);

        assertTrue((Boolean) result.get("success"));
        verify(paymentTypeDispatcher).completeByType(eq("reservation"), any(), eq(userId), eq("toss"));
    }

    @Test
    void confirm_alreadyConfirmed_throws() {
        String userId = UUID.randomUUID().toString();

        Map<String, Object> payment = new HashMap<>();
        payment.put("id", UUID.randomUUID());
        payment.put("user_id", userId);
        payment.put("amount", 50000);
        payment.put("status", "confirmed");
        payment.put("payment_type", "reservation");

        when(jdbcTemplate.queryForList(contains("SELECT id, reservation_id"), any(Object[].class)))
            .thenReturn(List.of(payment));

        ConfirmPaymentRequest request = new ConfirmPaymentRequest("pk_test", "ORD_123", 50000);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
            () -> paymentService.confirm(userId, request));
        assertTrue(ex.getReason().contains("already confirmed"));
    }

    @Test
    void cancel_success() {
        String userId = UUID.randomUUID().toString();
        UUID paymentId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Map<String, Object> payment = new HashMap<>();
        payment.put("id", paymentId);
        payment.put("reservation_id", reservationId);
        payment.put("user_id", userId);
        payment.put("amount", 50000);
        payment.put("status", "confirmed");

        when(jdbcTemplate.queryForList(contains("SELECT id, reservation_id"), any(Object[].class)))
            .thenReturn(List.of(payment));
        when(jdbcTemplate.update(contains("UPDATE payments"), any(String.class), any())).thenReturn(1);

        Map<String, Object> result = paymentService.cancel(userId, "pk_test_123", null);

        assertTrue((Boolean) result.get("success"));
        verify(paymentEventProducer).publishRefund(any(PaymentRefundedEvent.class));
    }

    @Test
    void process_transfer_success() {
        UUID referenceId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();
        UUID paymentId = UUID.randomUUID();

        when(ticketInternalClient.validateTransfer(referenceId, userId))
            .thenReturn(Map.of("total_amount", 110000));
        when(jdbcTemplate.queryForObject(contains("INSERT INTO payments"), eq(UUID.class),
            any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(paymentId);

        ProcessPaymentRequest request = new ProcessPaymentRequest(null, "card", "transfer", referenceId);
        Map<String, Object> result = paymentService.process(userId, request);

        assertTrue((Boolean) result.get("success"));
        verify(paymentTypeDispatcher).completeByType(eq("transfer"), any(), eq(userId), eq("card"));
    }
}
