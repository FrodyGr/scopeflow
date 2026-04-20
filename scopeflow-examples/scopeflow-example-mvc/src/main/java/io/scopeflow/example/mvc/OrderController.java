/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.example.mvc;

import io.scopeflow.core.Scope;
import io.scopeflow.core.ScopeFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Example REST controller demonstrating ScopeFlow context propagation.
 *
 * <p>Each endpoint opens domain-specific scopes that enrich the context
 * beyond the automatic HTTP request scope. Context values (including
 * request.id from the interceptor) flow to logs via MDC.</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final ScopeFlow scopeFlow;
    private final OrderService orderService;

    public OrderController(ScopeFlow scopeFlow, OrderService orderService) {
        this.scopeFlow = scopeFlow;
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> createOrder(
            @RequestParam String customerId,
            @RequestParam String product) {

        try (Scope scope = scopeFlow.open("order.create")
                .put("customer.id", customerId)
                .put("product", product)) {

            log.info("Creating order for customer={} product={}", customerId, product);

            String orderId = orderService.createOrder(customerId, product);

            // Also fire async processing
            orderService.notifyAsync(orderId);

            String requestId = scopeFlow.currentContext()
                    .get("request.id")
                    .orElse("N/A");

            log.info("Order created: orderId={}", orderId);

            return ResponseEntity.ok(Map.of(
                    "orderId", orderId,
                    "requestId", requestId,
                    "status", "CREATED"
            ));
        }
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> getOrderStatus(
            @PathVariable String orderId) {

        try (Scope scope = scopeFlow.open("order.status")
                .put("order.id", orderId)) {

            log.info("Checking status for orderId={}", orderId);

            return ResponseEntity.ok(Map.of(
                    "orderId", orderId,
                    "status", "PROCESSING",
                    "requestId", scopeFlow.currentContext()
                            .get("request.id")
                            .orElse("N/A")
            ));
        }
    }
}
