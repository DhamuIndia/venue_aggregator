# GitHub Project Setup

Use GitHub Projects for day-to-day control of backend integration work.

## Project

Create one project:

```text
Venue Aggregator Backend Integration
```

Recommended views:

- Board by `Status`
- Table grouped by `Stream`
- Table filtered by `Assignee`
- Roadmap or table filtered by `Priority = P0`

## Fields

| Field | Type | Values |
| --- | --- | --- |
| Status | Single select | Backlog, Ready, In Progress, PR Raised, Code Review, Frontend Verified, Done, Blocked |
| Stream | Single select | Auth/Customer, Owner, Vendor/Admin |
| Priority | Single select | P0, P1, P2, P3 |
| Due Date | Date | Target completion date |
| Frontend Screen | Text | Example: `/owner?tab=bookings` |
| Backend Status | Single select | TODO, IN_PROGRESS, API_READY, BLOCKED |
| Blocker Reason | Text | Required when Status is Blocked |

## Labels

Create these labels in the repository:

```text
backend
frontend-verify
integration
stream:auth-customer
stream:owner
stream:vendor-admin
priority:P0
priority:P1
priority:P2
priority:P3
blocked
```

## Daily Management Rule

Check only these questions daily:

1. Which issues moved to `Done`?
2. Which issues are still `In Progress` for more than two days?
3. Which issues are `Blocked`, and what decision is needed?
4. Which pull requests are waiting for review?

## Developer Rule

Every developer must:

- Pick work only from `Ready`.
- Move the issue to `In Progress` before coding.
- Create a branch named from the issue, for example `backend/public-halls`.
- Open a PR and link it with `Closes #issueNumber`.
- Move the issue to `PR Raised`.
- Ask for frontend verification after merge or local backend run.

## Completion Rule

An issue is `Done` only when:

- Backend tests pass.
- The frontend screen is verified in API mode.
- The issue acceptance checklist is complete.
- The PR is merged.

