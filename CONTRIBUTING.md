# Contributing to ScopeFlow

Thank you for your interest in contributing to ScopeFlow! This document provides guidelines and information for contributors.

## How to Contribute

### Reporting Issues

- Use the GitHub issue tracker to report bugs
- Include a minimal reproducible example when possible
- Describe the expected vs actual behavior
- Include Java version, Spring Boot version, and ScopeFlow version

### Submitting Changes

1. Fork the repository
2. Create a feature branch from `main`
3. Write your changes with appropriate tests
4. Ensure all tests pass: `mvn clean verify`
5. Submit a pull request

### Code Style

- Follow standard Java conventions
- Use meaningful names for variables, methods, and classes
- Add Javadoc for all public APIs
- Keep methods focused and short
- Prefer immutability

### Testing

- Write unit tests for all new functionality
- Include edge cases and error conditions
- Concurrency tests should use virtual threads where applicable
- Run the full test suite before submitting

### Commit Messages

Use clear, descriptive commit messages:

```
feat(core): add nested scope support with parent context inheritance
fix(mdc): ensure MDC cleanup on exception in scope close
docs: update README with executor wrapper examples
test(core): add virtual thread isolation test with 10K threads
```

## Development Setup

### Prerequisites

- JDK 21 or later
- Maven 3.9+
- An IDE with Java 21 support (IntelliJ IDEA recommended)

### Building

```bash
mvn clean verify
```

### Running Tests

```bash
# All tests
mvn test

# Specific module
mvn test -pl scopeflow-core

# Specific test class
mvn test -pl scopeflow-core -Dtest=DefaultScopeFlowTest
```

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
