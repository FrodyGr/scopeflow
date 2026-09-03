# Getting Started

## Installation

### Spring Boot Applications (Recommended)

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.frodygr</groupId>
    <artifactId>scopeflow-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

This single dependency includes:
- `scopeflow-core` — Core API
- `scopeflow-mdc` — SLF4J MDC integration
- `scopeflow-spring-boot-autoconfigure` — Auto-configuration
- Spring Boot starter

### Non-Spring Applications

Use individual modules:

```xml
<dependency>
    <groupId>io.github.frodygr</groupId>
    <artifactId>scopeflow-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>

<!-- Optional: MDC integration -->
<dependency>
    <groupId>io.github.frodygr</groupId>
    <artifactId>scopeflow-mdc</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### BOM (Bill of Materials)

To align all ScopeFlow versions:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.frodygr</groupId>
            <artifactId>scopeflow-bom</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

---

## 5-Minute Quickstart

### 1. Create a ScopeFlow instance

```java
// Spring Boot: auto-injected, just @Autowire it
@Autowired
private ScopeFlow scopeFlow;

// Non-Spring: build manually
ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .propagator(new MdcPropagator(MdcKeyPolicy.allowAll()))
    .build();
```

### 2. Open a scope

```java
try (Scope scope = scopeFlow.open("order.process",
        Map.of("order.id", "ORD-123", "customer.id", "CUST-456"))) {

    log.info("Processing order");
    // MDC automatically contains: order.id=ORD-123, customer.id=CUST-456

    // Enrich the scope dynamically
    scope.put("status", "VALIDATED");

    processPayment();
    shipOrder();
}
// Scope closed — MDC cleaned up automatically
```

### 3. Access context anywhere

```java
public void processPayment() {
    // Access the current context from anywhere in the call stack
    String orderId = scopeFlow.currentContext()
        .get("order.id")
        .orElse("unknown");

    log.info("Processing payment for order {}", orderId);
    // Log output: [order.id=ORD-123] Processing payment for order ORD-123
}
```

### 4. Propagate to async threads

```java
// Wrap a Runnable — context propagates to the new thread
executor.submit(scopeFlow.wrap(() -> {
    log.info("Async work");
    // MDC still has order.id=ORD-123!
}));

// Or wrap the entire executor
ExecutorService wrapped = scopeFlow.wrapExecutor(existingExecutor);
wrapped.submit(() -> {
    // Context automatically available here too
});
```

### 5. Nested scopes

```java
try (Scope httpScope = scopeFlow.open("http.request",
        Map.of("request.id", "abc-123"))) {

    try (Scope dbScope = scopeFlow.open("db.query",
            Map.of("query.table", "orders"))) {

        // Both request.id and query.table are in context and MDC
        log.info("Executing query");
    }
    // query.table is gone, request.id still here

    log.info("Continuing request");
}
// Everything cleaned up
```

---

## Spring Boot: Zero Configuration

With the starter, ScopeFlow works out of the box:

```java
@RestController
public class OrderController {

    @Autowired
    private ScopeFlow scopeFlow;

    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request) {
        // The MVC interceptor already opened a scope with:
        //   request.id=<UUID or X-Request-ID header>
        //   http.method=POST
        //   http.path=/orders

        try (Scope scope = scopeFlow.open("order.create")
                .put("customer.id", request.customerId())) {

            log.info("Creating order");
            // Log: [req=abc-123] [cust=CUST-1] Creating order

            return orderService.create(request);
        }
    }
}
```

### Logback pattern to show MDC context

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [req=%X{request.id:-}] [cust=%X{customer.id:-}] %logger{24} - %msg%n</pattern>
```

### Sample log output

```
14:23:01.123 [virtual-1] INFO  [req=abc-123] [cust=CUST-1] OrderController - Creating order
14:23:01.145 [virtual-1] INFO  [req=abc-123] [cust=CUST-1] PaymentService - Processing payment
14:23:01.200 [virtual-2] INFO  [req=abc-123] [cust=CUST-1] NotificationService - [ASYNC] Sending notification
```

Notice how `request.id` and `customer.id` appear in all log lines, even on different threads!
