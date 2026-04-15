# RemoteFlow E2E (Playwright)

This suite validates real backend lifecycle flows:

1. Keycloak login and token retrieval
2. Employee request submission
3. Manager approval
4. HR approval
5. Final request status verification

## Run locally

```bash
cd tests/e2e/playwright
npm ci
npx playwright install --with-deps chromium
npm test
```

## Required environment variables

- `BACKEND_BASE_URL` (default: `http://localhost:8088`)
- `KEYCLOAK_URL`
- `KEYCLOAK_REALM`
- `KEYCLOAK_CLIENT_ID`
- `KEYCLOAK_CLIENT_SECRET` (optional)
- `EMPLOYEE_USERNAME`
- `EMPLOYEE_PASSWORD`
- `MANAGER_USERNAME`
- `MANAGER_PASSWORD`
- `HR_USERNAME`
- `HR_PASSWORD`
