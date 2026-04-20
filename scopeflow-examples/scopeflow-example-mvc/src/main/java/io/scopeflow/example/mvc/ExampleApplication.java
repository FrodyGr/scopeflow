/*
 * Copyright 2026 ScopeFlow Contributors
 */
package io.scopeflow.example.mvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Example Spring Boot MVC application demonstrating ScopeFlow.
 *
 * <p>Start the app and test context propagation with:</p>
 * <pre>{@code
 * curl -s http://localhost:8080/api/orders?customerId=cust-1&product=Widget | jq .
 * curl -s -H "X-Request-ID: my-custom-id" http://localhost:8080/api/orders?customerId=cust-2&product=Gadget | jq .
 * curl -s http://localhost:8080/api/orders/ORD-123/status
 * }</pre>
 */
@SpringBootApplication
@EnableAsync
public class ExampleApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExampleApplication.class, args);
    }
}
