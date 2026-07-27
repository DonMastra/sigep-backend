---
name: sigep-rest-api
description: Use for REST API design and contract changes in SiGEP frontend, backend, and integration services. Apply when creating or modifying endpoints, DTOs, OpenAPI/API documentation, pagination, filtering, validation errors, auth semantics, frontend-backend contracts, mock service contracts, or external integration boundaries such as billing and ARCA/AFIP mocks.
---

# SiGEP REST API

## Workflow

1. Start from consumers and use cases: which screen, backend flow, report, billing operation, or integration needs the contract.
2. Inspect existing endpoint naming, DTO shape, pagination, error format, auth requirements, and tests before designing a new surface.
3. Define the contract first: method, path, auth, request DTO, response DTO, status codes, validation errors, pagination/filtering, and idempotency behavior.
4. Prefer additive changes. Do not change or remove existing fields without a migration plan.
5. Implement server and client types together when both repos are in scope. Keep frontend models aligned with backend DTOs.
6. Add contract-level tests: success, validation failure, auth failure, not found, conflict, and relevant domain errors.
7. Update API docs or integration notes for non-obvious behavior.

## REST Conventions

- Use plural resource nouns, not verbs: `/api/students`, `/api/invoices`, `/api/payments`.
- Use subresources for ownership: `/api/students/{studentId}/invoices`.
- Use `GET` for reads, `POST` for creation/actions that create server state, `PATCH` for partial updates, `DELETE` for deletions or cancellations when semantically correct.
- Use query parameters for filtering, sorting, pagination, and date ranges.
- Paginate list endpoints from the start when result sets can grow.
- Keep field names consistent with project conventions. Do not mix casing or enum styles inside the same API family.
- Separate input DTOs from output DTOs. Output may include server-generated fields; input should include only client-provided data.

## Error Semantics

- Return a consistent structured error body across endpoints.
- Use status codes predictably: 400 malformed request, 401 unauthenticated, 403 forbidden, 404 missing resource, 409 conflict/idempotency/version issue, 422 valid JSON with invalid domain data, 500 unexpected server failure.
- Never expose stack traces, SQL details, secrets, or third-party raw credentials.
- Include machine-readable error codes that frontend can map to user-facing messages.
- For external integrations, distinguish unavailable service, rejected business request, invalid credentials, and unexpected response shape.

## SiGEP Domain Guidance

- Include role and authorization semantics when contracts differ for admin, staff, guardian, or student contexts.
- For billing, make money, currency, dates, due dates, periods, invoice numbers, and external authorization states explicit.
- Preserve idempotency for invoice creation and external authorization requests. Define the idempotency key or natural key in the contract.
- Keep mock-billing-service REST helpers separate from production ARCA-like SOAP contracts; REST helpers are for debug and tests.

## Verification Checklist

- Typed request and response DTOs exist.
- Validation happens at the boundary.
- Error shape is consistent with existing APIs.
- List endpoints paginate and document sorting/filtering.
- Auth and role behavior are covered by tests.
- Frontend service/client code and backend contract agree.
- External response payloads are validated before use.

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

- Endpoint names like `/createInvoice` or `/getStudent`.
- Returning different response shapes for success variants of the same endpoint.
- Frontend code depending on undocumented backend quirks.
- New endpoints without validation or authorization tests.
- Breaking field renames without compatibility handling.
