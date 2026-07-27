---
name: sigep-spring-backend
description: Use for backend development in SiGEP Spring Boot services, especially sigep-backend and related mock services. Apply when implementing controllers, services, repositories, security, validation, transactions, integrations, billing flows, ARCA/AFIP mock interactions, scheduled jobs, error handling, tests, or backend architecture for academic management and billing domains.
---

# SiGEP Spring Backend

## Workflow

1. Inspect package structure, existing domain boundaries, controllers, services, repositories, DTOs, security config, migrations, and tests before editing.
2. Keep layers clear: controllers adapt HTTP, services own business rules, repositories own persistence, DTOs own API shape, entities model database state.
3. Preserve domain boundaries for academic management and billing. Avoid leaking billing-specific mock behavior into core academic flows unless the feature explicitly needs it.
4. Define or update typed request/response contracts before implementation. Keep validation at boundaries.
5. Implement thin vertical slices: contract, validation, service behavior, persistence, tests, then docs or API notes when useful.
6. Use transactions around business operations that must be atomic. Keep read-only operations read-only when the framework supports it.
7. Verify with targeted unit or integration tests before broad suites.

## Spring Practices

- Prefer constructor injection and immutable dependencies.
- Keep controllers small. They should delegate to services and map results/errors consistently.
- Use Bean Validation for request DTOs and explicit domain validation for business rules.
- Centralize exception mapping so REST errors are predictable and do not expose internal details.
- Keep authorization rules in security config, method guards, or policies rather than scattered inline checks.
- Avoid returning JPA entities directly from public APIs. Map to DTOs.
- Avoid lazy-loading surprises in serialization. Fetch intentionally in queries or map inside transaction boundaries.
- Use repositories for persistence queries, not business logic.

## Billing And Integration Context

- Treat ARCA/AFIP and mock-billing-service as external integrations from the main backend perspective.
- Validate every external response before trusting it, including mock service payloads.
- Preserve idempotency for payment, invoice, receipt, and external authorization flows.
- Model failure states explicitly: pending, authorized, rejected, unavailable, retriable, and cancelled when the domain needs them.
- Do not couple production backend code to development-only mocks. Select mock vs real integration through configuration or adapter implementations.

## Testing And Verification

- Unit test service rules with clear arrange-act-assert scenarios.
- Use integration tests for controller validation, security, repository queries, transactions, and external adapter boundaries.
- Prefer real framework slices or fakes over interaction-heavy mocks when they give stronger confidence.
- For bug fixes, first reproduce the behavior with a failing test when practical.
- Test authorization failures, validation errors, not-found cases, conflict/idempotency cases, and external service failures.

## Cross-Cutting Engineering Process

- Use spec-driven development for new features, ambiguous requirements, architectural decisions, or changes that cross modules. Capture goal, scope, acceptance criteria, non-goals, risks, and open questions before implementation.
- Use planning and task breakdown when the work is larger than a focused edit. Order tasks by dependency graph: database/schema, contracts, backend behavior, frontend client, UI, tests, then documentation.
- Prefer vertical slices over horizontal rewrites. Each slice should leave SiGEP runnable and verifiable.
- Size tasks so each has clear acceptance criteria, likely files touched, dependencies, and a verification command or manual check.
- Apply code simplification only after understanding the existing reason for the code. Preserve behavior exactly, follow local conventions, and keep simplification scoped to touched code unless the user asks for a broader refactor.
- Separate refactoring from feature work when the diff would become hard to review. Do not weaken validation, error handling, authorization, or tests in the name of simplicity.
- Review every completed change across correctness, readability, architecture, security, and performance. Verify tests first, then implementation, then the verification story.
- For review findings, lead with concrete bugs and risks. Mark blockers clearly, distinguish optional suggestions, and avoid style-only churn when the code follows project conventions.
- Check for dead code after refactors, but ask before deleting uncertain or potentially user-owned code.

## Red Flags

- Controllers containing billing or academic business rules.
- Public APIs returning entities directly.
- Inconsistent error response shapes.
- Database writes outside an explicit transactional boundary.
- Tests that only verify mocks were called and do not assert observable behavior.
- Hardcoded mock URLs or credentials in source code.
