---
name: sigep-angular-frontend
description: Use for frontend development in SiGEP Angular applications, especially sigep. Apply when creating or modifying Angular pages, standalone components, services, guards, interceptors, forms, dashboards, routes, SCSS layouts, accessibility behavior, responsive UI, API-backed data flows, or mock/real data switching for academic, billing, payments, reports, communications, schedules, students, guardians, and staff domains.
---

# SiGEP Angular Frontend

## Workflow

1. Inspect the local Angular structure before editing: routes, feature folders, services, models, environment files, and sibling screens with similar behavior.
2. Preserve the modular domain architecture. Keep auth, courses, exams, payments, reports, communications, schedules, students, staff, and billing concerns in their feature boundaries.
3. Separate presentation, state composition, and data access. Components render and coordinate UI; services/facades compose HTTP or mock data; typed models describe contracts.
4. Prefer existing design tokens, layout conventions, shared components, and SCSS patterns over new visual systems.
5. Implement real data flow when an endpoint or service exists. Use mock data only through the established provider or environment switch, never by hardcoding feature UI.
6. Add loading, empty, error, and permission states for user-facing views.
7. Verify with the narrowest useful tests and, for visual work, browser/runtime checks at desktop and mobile widths.

## Angular Practices

- Prefer standalone components and local imports when the repo already uses them.
- Keep templates readable; move non-trivial derivation into typed component methods, computed state, services, or facades.
- Use strongly typed DTOs and UI models. Avoid `any` unless adapting unknown external input at a boundary.
- Keep forms explicit: validators in the form definition, user-facing errors in the template, submission side effects in the component or facade.
- Use guards and interceptors for cross-cutting auth, authorization, tracing, and error behavior.
- Avoid direct mock branching in components. Select mock vs HTTP behind a service/provider according to environment configuration.
- If the app uses zoneless change detection, avoid fixes that depend on classic Zone.js test helpers or timing assumptions.

## UI Quality

- Build production UI, not placeholder screens. Use realistic academic and billing content while respecting privacy.
- Match sibling screens for headers, spacing, tables, filters, cards, responsive breakpoints, and action placement.
- Keep cards for repeated records, dialogs, and framed tools. Do not wrap whole pages in nested cards.
- Ensure keyboard access, focus visibility, semantic buttons/links, labels for controls, and non-color-only status indicators.
- Use stable dimensions for tables, toolbars, grids, badges, counters, and action buttons so dynamic text does not shift layout.
- Avoid generic AI aesthetics: excessive purple gradients, oversized hero layouts, arbitrary shadows, and decorative blobs.

## Testing And Verification

- Unit test services, facades, guards, interceptors, and non-trivial component behavior.
- For bug fixes, add or update a test that reproduces the broken behavior before the fix when practical.
- Test mock and HTTP provider branches when changing data-source logic.
- Run targeted Angular tests for the touched area before broad suites when the repo has known unrelated failures.
- For browser verification, check console errors, network payload shape, responsive layout, focus navigation, and loading/error/empty states.

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

- Static dashboard or table data when connected services already exist.
- Component-level endpoint URLs or auth headers.
- UI states that assume only admin users.
- New colors, radii, spacing, or typography that do not match existing screens.
- API response shapes duplicated ad hoc instead of using shared typed contracts.
