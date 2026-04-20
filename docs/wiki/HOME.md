# ScopeFlow Wiki

> **The simplest way to propagate context, logging and tracing in modern Java.**

Welcome to the ScopeFlow wiki! This documentation covers everything you need to know to use ScopeFlow effectively in your Java 21+ applications.

## Table of Contents

| Page | Description |
|------|-------------|
| [Getting Started](Getting-Started.md) | Installation, first scope, 5-minute quickstart |
| [Core Concepts](Core-Concepts.md) | Scope, ScopeContext, Propagator, Snapshot, lifecycle |
| [Spring Boot Integration](Spring-Boot-Integration.md) | Starter, auto-configuration, properties, MVC interceptor |
| [Context Propagation](Context-Propagation.md) | Wrappers, executors, virtual threads, async |
| [MDC Logging](MDC-Logging.md) | SLF4J MDC bridge, key policies, logback patterns |
| [Micrometer Integration](Micrometer-Integration.md) | ThreadLocalAccessor, Project Reactor support |
| [OpenTelemetry Integration](OpenTelemetry-Integration.md) | Baggage bridge, distributed tracing correlation |
| [ScopedValue & StructuredTaskScope](ScopedValue-Preview.md) | Java 23+ preview, ScopeFlowScoped API |
| [Configuration Reference](Configuration-Reference.md) | All properties, builder options, key policies |
| [Best Practices](Best-Practices.md) | Dos and don'ts, performance, security, patterns |

## Quick Links

- **Maven coordinates**: `io.scopeflow:scopeflow-spring-boot-starter`
- **License**: Apache License 2.0
- **Java baseline**: 21+
- **Spring Boot**: 3.2+

## Module Overview

```
scopeflow-core              → Core API: Scope, ScopeContext, ScopeFlow, Propagator
scopeflow-mdc               → SLF4J MDC bridge (correlated logging)
scopeflow-micrometer         → Micrometer Context Propagation bridge (reactive)
scopeflow-otel              → OpenTelemetry Baggage bridge (distributed tracing)
scopeflow-scoped            → ScopedValue + StructuredTaskScope (Java 23+ preview)
scopeflow-spring-boot-autoconfigure → Spring Boot auto-configuration
scopeflow-spring-boot-starter      → Single-dependency starter
scopeflow-bom               → Bill of Materials (version alignment)
```
