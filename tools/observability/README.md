# Observability tooling (runs on your laptop, not on the host)

- `prometheus.yml` — scrape config pointing at the SSH tunnel to the server's loopback
  management port (see README.md, "Production runbook", step 10).
- `grafana-flashchores.json` — a Grafana dashboard: capacity (heap, threads, UI state),
  users, speed, errors, database. Import it in Grafana under Dashboards → New → Import,
  paste or upload the file, and pick your Prometheus datasource when asked.

The error *details* (which button, which exception, which line) are not in Prometheus; read
them from `http://localhost:8090/actuator/vaadin/observability` through the same tunnel.
