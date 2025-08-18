---
apply: always
---

# Modern Java 21 + Spring Boot 3.x Development Rules

## 0) Global Principles
- **Code Quality First**: Prioritize correctness, readability, and maintainability over cleverness
- **Fail Fast**: Validate early, use strict types, and provide clear error messages
- **Documentation**: Code should be self-documenting; use JavaDoc for public APIs
- **Incremental Changes**: Prefer small, focused changes over large refactoring
- **Modern Java**: Leverage Java 21 LTS features (records, pattern matching, virtual threads, etc.)
- **Spring Boot 3.x**: Use latest Spring Boot features and Jakarta EE namespace

## 1) Modern Java 21 Practices
- **Records**: Use for DTOs, value objects, and immutable data carriers
- **Pattern Matching**: Leverage switch expressions, instanceof patterns, and sealed classes
- **Text Blocks**: Use for multi-line strings, SQL queries, JSON/XML templates
- **Virtual Threads**: Consider for I/O-bound operations with `@Async` or explicit usage
- **Sealed Classes**: Use for closed hierarchies and domain modeling
- **Smart Casts**: Utilize pattern matching instead of explicit casting
- **Local Variable Type Inference**: Use `var` when type is obvious from right-hand side

## 2) Records Best Practices
- **DTOs and Value Objects**: Use records for all data transfer objects and immutable value objects
- **Factory Methods**: Add static factory methods for common creation patterns
- **Builder Pattern**: For complex records, consider static factory methods over builders
- **Validation**: Use bean validation annotations on record components
- **Null Safety**: Prefer `List.of()` over `null` for empty collections in records

## 3) Exception Handling
- **Custom Exceptions**: Create domain-specific runtime exceptions extending `RuntimeException`
- **Exception Hierarchy**: Organize exceptions by domain/module in dedicated exception packages
- **Fail Fast**: Validate inputs early and throw meaningful exceptions
- **Message Quality**: Provide clear, actionable error messages
- **Cause Preservation**: Always preserve original exception causes when wrapping

## 4) Configuration and Properties
- **Configuration Properties**: Use `@ConfigurationProperties` with records for type-safe configuration
- **Property Scanning**: Enable `@ConfigurationPropertiesScan` for automatic discovery
- **Validation**: Apply Jakarta validation annotations to configuration properties
- **Environment Profiles**: Use Spring profiles for environment-specific configurations
- **Constants Organization**: Group constants in interfaces by functional domain

## 5) Service Layer Architecture
- **Single Responsibility**: Each service should have one clear responsibility
- **Interface Segregation**: Define focused interfaces for service contracts
- **Factory Pattern**: Use factory classes for complex object creation (e.g., browser instances)
- **Result Objects**: Return result objects instead of throwing exceptions for business logic outcomes
- **Method Naming**: Use descriptive method names that clearly indicate intent and return type

## 6) Dependency Management
- **Spring Boot BOM**: Always use Spring Boot parent or BOM for dependency management
- **Version Properties**: Define version properties for external dependencies
- **Minimal Dependencies**: Only include necessary dependencies; avoid kitchen-sink starters
- **Test Dependencies**: Use appropriate test scopes and Spring Boot test slices
- **Plugin Management**: Keep build plugins up to date and well-configured

## 7) Testing Strategies
- **Test Slices**: Use Spring Boot test slices (`@WebMvcTest`, `@DataJpaTest`, etc.)
- **Integration Tests**: Write integration tests for critical business flows
- **Unit Tests**: Focus unit tests on business logic, not Spring wiring
- **Test Data**: Use test builders or factories for creating test data
- **Assertions**: Prefer AssertJ assertions over JUnit assertions for readability

## 8) Build and Code Quality
- **Code Formatting**: Use Spring Java Format plugin for consistent code style
- **Static Analysis**: Integrate OpenRewrite for automated code improvements
- **Dependency Updates**: Regularly update dependencies using versions-maven-plugin
- **POM Organization**: Keep POM files organized using sortpom-maven-plugin
- **Build Reproducibility**: Use Maven wrapper for consistent builds across environments

## 9) Spring Boot 3.x Specific Practices
- **Jakarta EE**: Use `jakarta.*` imports instead of `javax.*`
- **Native Image**: Consider GraalVM native image compilation for startup performance
- **Observability**: Leverage Spring Boot Actuator and Micrometer for monitoring
- **Configuration**: Use `application.yml` over `properties` for complex configurations
- **Security**: Apply Spring Security 6.x patterns with method-level security

## 10) Performance and Scalability
- **Lazy Loading**: Use `@Lazy` annotation judiciously to improve startup time
- **Connection Pooling**: Configure appropriate connection pool sizes for external services
- **Caching**: Implement caching strategies using Spring Cache abstraction
- **Async Processing**: Use `@Async` with virtual threads for I/O-bound operations
- **Resource Management**: Always properly close resources using try-with-resources

## 11) Logging and Monitoring
- **SLF4J**: Use SLF4J as logging facade with Logback as implementation
- **Structured Logging**: Use structured logging formats for better log analysis
- **Log Levels**: Use appropriate log levels (TRACE, DEBUG, INFO, WARN, ERROR)
- **MDC**: Use Mapped Diagnostic Context for request correlation
- **Health Checks**: Implement custom health indicators for external dependencies
