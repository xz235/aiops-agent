# CPU spike and GC pressure runbook

For a cpu_spike alert, compare CPU saturation, load average, request latency, and GC
messages. If CPU remains above 90 percent and logs show GC pressure, capture a thread
dump, reduce the offending batch workload, and review heap allocation before scaling.

