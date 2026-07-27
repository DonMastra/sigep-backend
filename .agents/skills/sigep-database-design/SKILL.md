---
name: sigep-database-design
description: Use for database, persistence, and migration work in SiGEP backend services and mock-billing-service. Apply when designing or changing schemas, JPA entities, repositories, indexes, constraints, H2/PostgreSQL behavior, Flyway/Liquibase migrations, seed data, idempotency records, audit fields, reporting queries, or billing and academic persistence models.
---

# SiGEP Database Design

## Workflow

1. Inspect existing entities, repositories, migrations, naming conventions, indexes, constraints, and test data before changing schema.
2. Model the domain first, then map it to tables. Keep academic, billing, auth, and integration persistence concerns clear.
3. Prefer explicit constraints and indexes over relying only on application code.
4. Plan migrations as forward-only, reviewable changes. Include rollback notes only when the repo convention uses them.
5. Update entities, DTO mappings, repositories, service rules, and tests together.
6. Verify with repository or integration tests that exercise real persistence behavior.

## Schema Principles

- Use stable primary keys and explicit unique constraints for natural idempotency keys.
- Make foreign keys express ownership and referential integrity where the database supports it.
- Use decimal-safe types for money; never floats for currency.
- Store timestamps and dates intentionally. Distinguish due date, service period, authorization timestamp, and audit timestamps.
- Use nullable fields only when the domain state genuinely allows absence. Otherwise enforce not-null at schema and validation layers.
- Add indexes for lookup paths used by APIs, reports, auth checks, and scheduled jobs.
- Avoid denormalization until a measured reporting or performance need exists.

## JPA And Repository Practices

- Keep entities focused on persistence state. Do not put HTTP or external API DTO concerns in entities.
- Avoid exposing entities directly through REST responses.
- Use explicit fetch plans, joins, or projections for read models that cross relationships.
- Keep repository methods named and scoped around use cases. Move business decisions to services.
- Test query methods that encode business filters, date ranges, role visibility, or billing state.

## Billing And Mock Service Guidance

- Preserve idempotency by natural key for invoice authorization flows, such as CUIT, point of sale, voucher type, and voucher number in mock-billing-service.
- Keep sequence tables or records transactionally safe when generating next numbers.
- Persist external authorization state and request identifiers needed for retries and reconciliation.
- Keep local persistent H2 data out of Git, commonly under `data/` for mock-billing-service.
- For SiGEP backend billing, model debt, payments, invoices, and reports so business state does not depend on mock-only tables.

## Testing And Verification

- Add migration tests or repository integration tests when schema behavior matters.
- Test unique constraints, FK behavior, nullable rules, sequence/idempotency behavior, and important indexes indirectly through query paths.
- Include sample data that represents realistic academic and billing states: paid, pending, overdue, authorized, rejected, and unavailable.
- Run targeted persistence tests before broad suites when unrelated tests are noisy.

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

- Schema changes without matching entity and repository updates.
- Application-only uniqueness for invoice, payment, or identity keys.
- Money stored as floating-point values.
- Queries that rely on lazy loading during JSON serialization.
- Reports implemented with unindexed scans over growing tables.
- Mock persistence leaking into production backend models.
