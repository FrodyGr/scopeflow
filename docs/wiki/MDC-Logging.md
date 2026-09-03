# MDC Logging

## Overview

The `scopeflow-mdc` module bridges ScopeFlow context to SLF4J's **Mapped Diagnostic Context (MDC)**, enabling automatic correlated logging. Every scope change is reflected in MDC, so your logs automatically contain contextual information.

---

## Setup

### Spring Boot (automatic)

```xml
<dependency>
    <groupId>io.github.frodygr</groupId>
    <artifactId>scopeflow-spring-boot-starter</artifactId>
</dependency>
```

The `MdcPropagator` is auto-configured with `MdcKeyPolicy.allowAll()` by default.

### Manual setup

```java
MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "tenant.id", "user.id"));
MdcPropagator propagator = new MdcPropagator(policy);

ScopeFlow scopeFlow = ScopeFlowBuilder.create()
    .propagator(propagator)
    .build();
```

---

## MdcKeyPolicy

Controls which context keys are copied to MDC:

### Allow specific keys (recommended for production)

```java
MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id", "tenant.id", "user.id"));
```

Only `request.id`, `tenant.id`, and `user.id` will appear in MDC. All other context keys are ignored.

### Allow all keys

```java
MdcKeyPolicy policy = MdcKeyPolicy.allowAll();
```

Every context key is copied to MDC. Convenient for development but may leak sensitive data.

### With prefix

```java
MdcKeyPolicy policy = MdcKeyPolicy.of(Set.of("request.id"), "sf.");
// MDC key: "sf.request.id" instead of "request.id"

MdcKeyPolicy policy = MdcKeyPolicy.allowAll("ctx.");
// All keys prefixed: "ctx.request.id", "ctx.tenant.id", etc.
```

Useful to avoid conflicts with existing MDC keys in your application.

### Spring Boot properties

```properties
# Allow specific keys
scopeflow.mdc.keys=request.id,tenant.id,user.id

# Or allow all (default when keys is empty)
# scopeflow.mdc.keys=

# Optional prefix
scopeflow.mdc.prefix=sf.
```

---

## MDC Save/Restore Stack

`MdcPropagator` uses a **per-thread stack** to correctly handle nested scopes:

```java
MDC.put("request.id", "pre-existing");  // External value

try (Scope s1 = scopeFlow.open("outer",
        Map.of("request.id", "outer-id"))) {
    // MDC: request.id=outer-id (pre-existing saved in stack)

    try (Scope s2 = scopeFlow.open("inner",
            Map.of("request.id", "inner-id"))) {
        // MDC: request.id=inner-id (outer-id saved in stack)
    }
    // MDC: request.id=outer-id (restored from stack)
}
// MDC: request.id=pre-existing (restored from stack)
```

### Enrichment tracking

Keys added via `scope.put()` after scope open are also properly tracked:

```java
try (Scope scope = scopeFlow.open("request")) {
    // MDC: (empty)

    scope.put("request.id", "abc-123");
    // MDC: request.id=abc-123 (previous null value saved in stack)

    scope.put("user.id", "user-456");
    // MDC: request.id=abc-123, user.id=user-456
}
// MDC: (empty) — both keys properly cleaned up
```

---

## Logback Configuration

### Basic pattern

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{24} - %msg%n</pattern>
```

### With MDC context (recommended)

```xml
<pattern>%d{HH:mm:ss.SSS} [%thread] %-5level [req=%X{request.id:-}] [tenant=%X{tenant.id:-}] %logger{24} - %msg%n</pattern>
```

### JSON logging (for log aggregators)

```xml
<dependency>
    <groupId>ch.qos.logback.contrib</groupId>
    <artifactId>logback-json-classic</artifactId>
</dependency>
```

```xml
<appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
        <includeMdcKeyName>request.id</includeMdcKeyName>
        <includeMdcKeyName>tenant.id</includeMdcKeyName>
    </encoder>
</appender>
```

### Sample output

```
14:23:01.123 [virtual-1] INFO  [req=abc-123] [tenant=acme] OrderService - Creating order
14:23:01.145 [virtual-1] INFO  [req=abc-123] [tenant=acme] PaymentService - Processing payment
14:23:01.200 [virtual-2] INFO  [req=abc-123] [tenant=acme] NotificationService - Sending email
14:23:01.310 [virtual-1] INFO  [req=abc-123] [tenant=acme] OrderService - Order created
```

---

## Exception Safety

If an exception occurs, MDC is always cleaned up:

```java
try (Scope scope = scopeFlow.open("risky",
        Map.of("request.id", "abc"))) {
    // MDC: request.id=abc
    throw new RuntimeException("Boom!");
}
// MDC: (empty) — cleanup happened in finally block via try-with-resources
```

The `try-with-resources` pattern ensures `scope.close()` is always called, which triggers `MdcPropagator.onScopeClosed()` to restore previous MDC values.
