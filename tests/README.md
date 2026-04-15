# RemoteFlow Test Architecture

Layered strategy used in CI:

1. `unit` (fast domain and business rules)
2. `security` (JWT + RBAC authorization matrix)
3. `integration` (Flyway/JPA/Testcontainers + Alfresco HTTP integration)
4. `workflow` (real Zeebe BPMN lifecycle)
5. `e2e` (Playwright full business flow with real backend and IAM)
6. `performance` (JMeter baseline concurrency and latency)

## Maven commands

```bash
./mvnw -q -Ptest-unit test
./mvnw -q -Ptest-security test
./mvnw -q -Ptest-integration test
./mvnw -q -Ptest-workflow test
./mvnw -q -Ptest-all test
```
