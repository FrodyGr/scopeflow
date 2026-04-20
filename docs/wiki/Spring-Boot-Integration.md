# Spring Boot Integration

## Auto-Configuration

The `scopeflow-spring-boot-starter` provides **zero-configuration** setup:

```xml
<dependency>
    <groupId>io.scopeflow</groupId>
    <artifactId>scopeflow-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### What gets auto-configured

| Bean | Type | Condition |
|------|------|-----------|
| `scopeFlow` | `ScopeFlow` | Always (unless `scopeflow.enabled=false`) |
| `mdcPropagator` | `MdcPropagator` | `scopeflow-mdc` on classpath |
| `scopeFlowMvcInterceptor` | `ScopeFlowMvcInterceptor` | Spring MVC (Servlet web app) |
| `scopeFlowTaskDecorator` | `ScopeFlowTaskDecorator` | `TaskDecorator` on classpath |

### How it works

```
Application Startup
    │
    ├── ScopeFlowAutoConfiguration
    │   ├── Creates MdcPropagator bean (if scopeflow-mdc on classpath)
    │   └── Creates ScopeFlow bean (wires all Propagator beans)
    │
    └── ScopeFlowWebMvcAutoConfiguration
        ├── Creates ScopeFlowMvcInterceptor
        │   └── Registers as HandlerInterceptor for all paths
        ├── Creates ScopeFlowTaskDecorator
        │   └── Applied to all ThreadPoolTaskExecutor/SimpleAsyncTaskExecutor beans
        └── Done

HTTP Request Lifecycle:
    │
    ├── preHandle: Opens "http.request" scope
    │   ├── Generates/extracts X-Request-ID
    │   ├── Adds: request.id, http.method, http.path
    │   └── MdcPropagator: copies to MDC
    │
    ├── Controller + Services run
    │   ├── MDC has request.id for all log lines
    │   ├── @Async methods propagate context via TaskDecorator
    │   └── Additional scopes can be opened
    │
    └── afterCompletion: Closes "http.request" scope
        ├── MdcPropagator: restores previous MDC
        └── Scope stack cleaned up
```

---

## MVC Interceptor

The interceptor automatically creates a scope for every HTTP request:

### Automatic context values

| Key | Value | Example |
|-----|-------|---------|
| `request.id` | UUID or from `X-Request-ID` header | `f47ac10b-58cc-4372-a567-0e02b2c3d479` |
| `http.method` | HTTP method | `GET`, `POST`, `PUT`, `DELETE` |
| `http.path` | Request URI | `/api/orders/123` |

### Request ID behavior

1. If client sends `X-Request-ID` header → uses that value
2. If no header → generates a UUID
3. Always echoes the request ID back in the response `X-Request-ID` header
4. Useful for end-to-end request tracing across microservices

### Example: Tracing across services

```java
// Service A makes a call to Service B
WebClient.builder().build()
    .get()
    .uri("http://service-b/api/data")
    .header("X-Request-ID", scopeFlow.currentContext()
        .get("request.id").orElse(""))
    .retrieve()
    .bodyToMono(String.class);

// Service B's interceptor picks up the same request.id
// → correlated logs across both services!
```

---

## @Async Support

### How it works

The `ScopeFlowTaskDecorator` extends `TaskDecorator` and wraps every `@Async` task with a snapshot of the current context:

```java
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async
    public void sendEmail(String orderId) {
        // Context is automatically propagated!
        // request.id is in MDC even though we're on a different thread
        log.info("Sending email for order {}", orderId);
    }
}
```

### With virtual threads

```properties
# application.properties
spring.threads.virtual.enabled=true
```

ScopeFlow works with both `ThreadPoolTaskExecutor` and `SimpleAsyncTaskExecutor` (used for virtual threads). The task decorator is automatically applied to both.

---

## Injecting ScopeFlow

### Constructor injection (recommended)

```java
@Service
public class OrderService {
    private final ScopeFlow scopeFlow;

    public OrderService(ScopeFlow scopeFlow) {
        this.scopeFlow = scopeFlow;
    }
}
```

### Custom propagators

Register additional propagators as Spring beans:

```java
@Configuration
public class ScopeFlowConfig {

    @Bean
    public Propagator auditPropagator() {
        return new Propagator() {
            @Override
            public String name() { return "audit"; }

            @Override
            public void onScopeOpened(String name, ScopeContext ctx) {
                AuditContext.set(ctx.get("user.id").orElse("anonymous"));
            }

            @Override
            public void onScopeClosed(String name, ScopeContext ctx) {
                AuditContext.clear();
            }
        };
    }
}
```

The ScopeFlow bean automatically picks up all `Propagator` beans from the Spring context.

### Custom ScopeFlow bean

Override the auto-configured ScopeFlow:

```java
@Bean
public ScopeFlow scopeFlow() {
    return ScopeFlowBuilder.create()
        .propagator(new MdcPropagator(MdcKeyPolicy.of(Set.of("request.id"))))
        .keyPolicy(ContextKeyPolicy.denyList(Set.of("password", "token")))
        .build();
}
```
