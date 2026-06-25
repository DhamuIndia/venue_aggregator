## Summary

- 

## Issue

Closes #

## Backend Checklist

- [ ] Routes match `docs/api/frontend-backend-contract-v1.md`
- [ ] Controllers use request/response DTOs, not entities
- [ ] Protected routes derive identity from JWT
- [ ] Services own validation and business rules
- [ ] Empty states return valid JSON shapes
- [ ] Problem details returned for validation/auth errors
- [ ] Tests added or updated

## Frontend Verification

- [ ] Tested the linked frontend screen in API mode
- [ ] Confirmed loading, empty, success, and error states
- [ ] No contract changes needed

## Test Commands

```bash
cd apps/backend
mvn test
```

```bash
cd apps/frontend
npm run typecheck
```

