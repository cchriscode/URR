package guru.urr.paymentservice.controller;

import guru.urr.paymentservice.dto.CancelPaymentRequest;
import guru.urr.paymentservice.dto.ConfirmPaymentRequest;
import guru.urr.paymentservice.dto.PreparePaymentRequest;
import guru.urr.paymentservice.dto.ProcessPaymentRequest;
import guru.urr.paymentservice.security.AuthUser;
import guru.urr.paymentservice.security.JwtTokenParser;
import guru.urr.paymentservice.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
        HttpServletRequest httpRequest,
        @Valid @RequestBody PreparePaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return paymentService.prepare(user.userId(), request);
    }

    @PostMapping("/confirm")
    public Map<String, Object> confirm(
        HttpServletRequest httpRequest,
        @Valid @RequestBody ConfirmPaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return paymentService.confirm(user.userId(), request);
    }

    @GetMapping("/order/{orderId}")
    public Map<String, Object> order(
        @PathVariable String orderId,
        HttpServletRequest httpRequest
    ) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return paymentService.findByOrder(user.userId(), orderId);
    }

    @PostMapping("/{paymentKey}/cancel")
    public Map<String, Object> cancel(
        @PathVariable String paymentKey,
        HttpServletRequest httpRequest,
        @RequestBody(required = false) CancelPaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return paymentService.cancel(user.userId(), paymentKey, request);
    }

    @GetMapping("/user/me")
    public Map<String, Object> myPayments(
        HttpServletRequest httpRequest,
        @RequestParam(defaultValue = "10") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int safeOffset = Math.max(offset, 0);
        return paymentService.myPayments(user.userId(), safeLimit, safeOffset);
    }

    @PostMapping("/process")
    public Map<String, Object> process(
        HttpServletRequest httpRequest,
        @Valid @RequestBody ProcessPaymentRequest request
    ) {
        AuthUser user = jwtTokenParser.requireUser(httpRequest);
        return paymentService.process(user.userId(), request);
    }
}
