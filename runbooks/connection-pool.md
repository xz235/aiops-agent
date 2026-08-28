# Database connection pool exhaustion runbook

For connection_pool alerts, compare active and maximum pool connections. Search for
connection acquisition timeouts and slow queries. Mitigate by stopping the traffic
source or applying an approved temporary pool adjustment; do not blindly restart.

