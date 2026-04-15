# RemoteFlow Performance Baseline (JMeter)

This load plan validates:

- employee quota endpoint under concurrent load
- manager pending validation inbox under concurrent load

## Run

```bash
jmeter -n \
  -t tests/performance/jmeter/telework-load-test.jmx \
  -l tests/performance/jmeter/results.jtl \
  -e -o tests/performance/jmeter/report
```

## Inputs

Before execution, set these variables in the JMX file (or via `-J` overrides):

- `BASE_HOST`
- `BASE_PORT`
- `EMPLOYEE_TOKEN`
- `MANAGER_TOKEN`

## Minimum acceptance targets

- API error rate: `< 1%`
- p95 latency for quota endpoint: `< 800 ms`
- p95 latency for validation inbox endpoint: `< 1000 ms`
