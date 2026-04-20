# Best Practices

## ✅ Do

### 1. Always use try-with-resources

```java
// ✅ Correct
try (Scope scope = scopeFlow.open("operation", values)) {
    doWork();
}

// ❌ Wrong — scope may leak if exception occurs
Scope scope = scopeFlow.open("operation", values);
doWork();
scope.close();
```

### 2. Name scopes semantically

```java
// ✅ Good: describes the operation
scopeFlow.open("http.request", ...)
scopeFlow.open("order.create", ...)
scopeFlow.open("payment.process", ...)
scopeFlow.open("db.query", ...)

// ❌ Bad: generic or unclear
scopeFlow.open("scope1", ...)
scopeFlow.open("work", ...)
scopeFlow.open("s", ...)
```

### 3. Use allowList key policies in production

```java
// ✅ Production: explicit control over what goes to MDC/logs
MdcKeyPolicy.of(Set.of("request.id", "tenant.id", "user.id"));

// ⚠️ Development only: convenient but may leak sensitive data
MdcKeyPolicy.allowAll();
```

### 4. Wrap executors at creation time

```java
// ✅ Wrap once at configuration time
@Bean
public ExecutorService myExecutor(ScopeFlow scopeFlow) {
    return scopeFlow.wrapExecutorService(
        Executors.newVirtualThreadPerTaskExecutor());
}

// ❌ Don't wrap individual tasks if you can wrap the executor
executor.submit(scopeFlow.wrap(() -> doWork()));  // ← more verbose
```

### 5. Keep scope durations short

```java
// ✅ Scope matches the logical operation
try (Scope scope = scopeFlow.open("db.query",
        Map.of("query.table", "orders"))) {
    return jdbcTemplate.query("SELECT ...");
}

// ❌ Don't keep scopes open across long waits or user interactions
try (Scope scope = scopeFlow.open("session")) {
    waitForUserInput(); // Minutes/hours — scope too long
}
```

### 6. Use denyList for sensitive data

```java
// ✅ Block sensitive keys from appearing in context/MDC
ScopeFlowBuilder.create()
    .keyPolicy(ContextKeyPolicy.denyList(Set.of(
        "password", "token", "secret", "credit_card", "ssn")))
    .build();
```

---

## ❌ Don't

### 1. Don't share Scope objects across threads

```java
// ❌ NEVER do this
try (Scope scope = scopeFlow.open("request")) {
    executor.submit(() -> {
        scope.put("key", "value"); // RACE CONDITION!
    });
}

// ✅ Use wrap() for cross-thread propagation
executor.submit(scopeFlow.wrap(() -> {
    // Context is properly captured and restored
}));
```

### 2. Don't create overly large contexts

```java
// ❌ Too many keys — MDC, OTel, and logging overhead
try (Scope scope = scopeFlow.open("request",
        Map.of("k1", "v1", "k2", "v2", ... "k100", "v100"))) { }

// ✅ Keep to essential correlation data
try (Scope scope = scopeFlow.open("request",
        Map.of("request.id", id, "tenant.id", tenant))) { }
```

### 3. Don't store large objects as context values

```java
// ❌ Context values are captured in snapshots (serialized/cloned)
scope.put("request.body", hugeJsonString);  // Memory waste

// ✅ Store identifiers, not data
scope.put("request.id", requestId);
```

### 4. Don't rely on scope ordering without try-with-resources

```java
// ❌ If scope2 fails to close, scope1 state is corrupted
Scope scope1 = scopeFlow.open("outer");
Scope scope2 = scopeFlow.open("inner");
// ... if exception here, scopes leak
scope2.close();
scope1.close();

// ✅ try-with-resources guarantees correct LIFO close order
try (Scope scope1 = scopeFlow.open("outer")) {
    try (Scope scope2 = scopeFlow.open("inner")) {
        // ...
    } // scope2 always closes first
} // scope1 always closes second
```

---

## Performance Tips

### 1. Use virtual threads

```properties
spring.threads.virtual.enabled=true
```

ScopeFlow's ThreadLocal-based isolation is designed for virtual threads with no synchronization overhead.

### 2. Minimize propagator count

Each propagator's lifecycle hooks run on every scope open/close. Keep the propagator count minimal:
- MDC (if you need logs) ← almost always
- OTel (if you need distributed tracing) ← when using OTel
- Custom propagators ← only when truly needed

### 3. Use key policies to limit MDC/OTel work

```java
// Fewer keys = fewer MDC.put/MDC.remove calls = faster
MdcKeyPolicy.of(Set.of("request.id", "tenant.id"));
OtelBaggagePropagator.create(Set.of("request.id"));
```

### 4. Avoid scope churn in hot loops

```java
// ❌ Opening/closing scope per iteration is expensive
for (Item item : items) {
    try (Scope scope = scopeFlow.open("process", Map.of("item.id", item.id()))) {
        process(item);
    }
}

// ✅ Open scope once, enrich as needed
try (Scope scope = scopeFlow.open("batch.process")) {
    for (Item item : items) {
        scope.put("item.id", item.id());
        process(item);
    }
}
```

---

## Security Considerations

### 1. Deny-list sensitive keys

Always prevent sensitive data from reaching MDC/logs:

```java
ContextKeyPolicy.denyList(Set.of("password", "token", "secret", "api_key"));
```

### 2. Restrict MDC propagation

Don't use `MdcKeyPolicy.allowAll()` in production. Explicitly list keys:

```properties
# application-prod.properties
scopeflow.mdc.keys=request.id,tenant.id,user.id
```

### 3. Audit context in multi-tenant systems

Use propagators to validate tenant isolation:

```java
public class TenantIsolationPropagator implements Propagator {
    @Override
    public void onScopeOpened(String name, ScopeContext ctx) {
        ctx.get("tenant.id").ifPresent(tenant -> {
            if (!SecurityContext.getCurrentTenant().equals(tenant)) {
                throw new SecurityException("Tenant mismatch!");
            }
        });
    }
}
```

---

## Testing

### Unit tests with ScopeFlow

```java
@BeforeEach
void setUp() {
    scopeFlow = ScopeFlowBuilder.create()
        .propagator(new MdcPropagator(MdcKeyPolicy.allowAll()))
        .build();
}

@AfterEach
void tearDown() {
    MDC.clear(); // Always clean up MDC in tests
}

@Test
void testContextPropagation() {
    try (Scope scope = scopeFlow.open("test",
            Map.of("request.id", "test-123"))) {
        assertThat(MDC.get("request.id")).isEqualTo("test-123");
    }
    assertThat(MDC.get("request.id")).isNull();
}
```

### Integration tests with Spring Boot

```java
@SpringBootTest
class OrderIntegrationTest {

    @Autowired
    ScopeFlow scopeFlow;

    @Test
    void contextPropagatesInSpring() {
        try (Scope scope = scopeFlow.open("test",
                Map.of("request.id", "integration-test"))) {
            assertThat(scopeFlow.currentContext().get("request.id"))
                .hasValue("integration-test");
        }
    }
}
```
