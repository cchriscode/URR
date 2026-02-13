package com.tiketi.paymentservice.controller;

import com.tiketi.paymentservice.dto.CancelPaymentRequest;
import com.tiketi.paymentservice.dto.ConfirmPaymentRequest;
import com.tiketi.paymentservice.dto.PreparePaymentRequest;
import com.tiketi.paymentservice.dto.ProcessPaymentRequest;
import com.tiketi.paymentservice.security.AuthUser;
import com.tiketi.paymentservice.security.JwtTokenParser;
import com.tiketi.paymentservice.service.PaymentService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final JwtTokenParser jwtTokenParser;

    public PaymentController(PaymentService paymentService, JwtTokenParser jwtTokenParser) {
        this.paymentService = paymentService;
        this.jwtTokenParser = jwtTokenParser;
    }

    @PostMapping("/prepare")
    public Map<String, Object> prepare(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody PreparePaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return paymentService.prepare(user.userId(), request);
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return paymentService.confirm(user.userId(), request);
    }

    @GetMapping("/order/{orderId}")
    public Map<String, Object> order(
        @PathVariable String orderId,
        @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return paymentService.findByOrder(user.userId(), orderId);
    }

    @PostMapping("/{paymentKey}/cancel")
    public Map<String, Object> cancel(
        @PathVariable String paymentKey,
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestBody(required = false) CancelPaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return paymentService.cancel(user.userId(), paymentKey, request);
    }

    @GetMapping("/user/me")
    public Map<String, Object> myPayments(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return paymentService.myPayments(user.userId(), safeLimit, safeOffset);
    }

    @PostMapping("/process")
    public Map<String, Object> process(
        @RequestHeader(value = "Authorization", required = false) String authorization,
        @Valid @RequestBody ProcessPaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(authorization);
        return paymentService.process(user.userId(), request);
    }
}
