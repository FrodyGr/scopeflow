/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.example.mvc;

import io.scopeflow.core.ScopeFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Example service demonstrating context propagation in synchronous and async methods.
 *
 * <p>The {@code @Async} method receives context automatically via the
 * {@code ScopeFlowTaskDecorator} — no manual wrapping needed.</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final ScopeFlow scopeFlow;

    public OrderService(ScopeFlow scopeFlow) {
        this.scopeFlow = scopeFlow;
    }

    /**
     * Simulates order creation. The MDC will contain request.id, customer.id,
     * and product from the calling scope.
     */
    public String createOrder(String customerId, String product) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        log.info("Processing order {} for customer {} — product: {}",
                orderId, customerId, product);

        // Simulate some processing time
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Order {} saved to database", orderId);
        return orderId;
    }

    /**
     * Async method that demonstrates context propagation to @Async threads.
     * The TaskDecorator ensures MDC context flows to this thread automatically.
     */
    @Async
    public void notifyAsync(String orderId) {
        log.info("[ASYNC] Sending notification for order {}", orderId);

        // Verify that context propagated
        String requestId = scopeFlow.currentContext()
                .get("request.id")
                .orElse("NOT_PROPAGATED");

        log.info("[ASYNC] request.id in async thread: {}", requestId);

        // Simulate notification delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[ASYNC] Notification sent for order {}", orderId);
    }
}
