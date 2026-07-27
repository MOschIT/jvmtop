# jvmtop Profiling Improvement Suggestions

## Current State

jvmtop is a pure-JMX monitoring tool with no native JVMTI agent. All data is collected remotely via JMX by attaching to the target JVM's management agent.

### What Exists Today

- **CPU Profiling**: Sampling-based, 100ms interval. Attributes CPU delta to the top-most non-filtered stack frame per RUNNABLE thread. Only inclusive time, no self-time.
- **Memory Monitoring**: Aggregate heap/non-heap usage totals. GC total time and count.
- **Dead Code**: `MemoryPoolStat` class is defined but never instantiated or queried.
- **Thread Monitoring**: Per-thread CPU times, blocking detection, deadlock detection.

---

## Memory Profiling Suggestions

### 1. Per-Pool Memory Breakdown

**Description**: Query `MemoryPoolMXBean` to display individual memory pools (Eden Space, Survivor Space, Old Gen, Metaspace, Code Cache, etc.) instead of only aggregate heap/non-heap totals.

**How**: The `MemoryPoolStat` data model already exists in `MemoryPoolStat.java` and tracks pool name, `MemoryUsage`, thresholds, and GC-related fields. Wire it up by iterating `ManagementFactory.getMemoryPoolMXBeans()` during the `VMInfo.update()` cycle and storing per-pool stats.

**Display**: Add a per-pool table in `VMDetailView` showing pool name, used, max, and utilization percentage. In `VMOverviewView`, consider showing Old Gen and Metaspace usage as additional columns.

**Effort**: Low — the data model and MXBean APIs are already in place.

---

### 2. Allocation Rate Tracking

**Description**: Compute and display the rate at which memory is being allocated (bytes/sec or objects/sec).

**How**: Two approaches:
- **GC-based**: Track `MemoryPoolMXBean.getCollectionUsage()` for young-gen pools. The difference in used bytes between GC cycles, divided by elapsed time, gives allocation rate.
- **Counter-based**: Some JVMs expose allocation counters via `com.sun.management.ThreadMXBean` or non-standard MXBeans. Sample the delta over each update cycle.

**Display**: Add an `ALLOc/S` column to the overview and a running allocation rate chart in the detail view.

**Effort**: Medium — requires tracking GC cycle boundaries and correlating with memory pool deltas.

---

### 3. GC Pause Time Analysis

**Description**: Track individual GC events with their type, duration, and heap impact, rather than only showing total GC time.

**How**: On each update cycle, compare `GarbageCollectorMXBean.getCollectionTime()` and `getCollectionCount()` deltas. If count increased, record the time delta as a pause event. Cross-reference with `MemoryPoolMXBean` to determine which pools were collected.

**Display**: In `VMDetailView`, show a rolling log of the last N GC events (e.g., 10), each with timestamp, type (young/old/mixed), duration, and heap before/after. In the overview, show "recent GC pause" as a column.

**Effort**: Low-Medium — delta tracking on existing MXBeans.

---

### 4. Memory Trend Detection and Leak Heuristics

**Description**: Detect potential memory leaks by analyzing heap usage trends over time.

**How**: Maintain a rolling window of heap usage samples (e.g., last 60 readings). Apply a simple heuristic:
- Compute a moving average of heap usage
- If usage is monotonically increasing over the window and GC reclamation is decreasing (i.e., post-GC heap is trending upward), flag a potential leak
- Optionally use a simple linear regression on the window to compute the growth rate (bytes/sec)

**Display**: Add a `MEM TREND` indicator to the overview view (e.g., arrow up/down/stable) and a growth rate in the detail view.

**Effort**: Medium — requires a circular buffer of historical samples and trend computation.

---

### 5. Memory Profiling Mode

**Description**: Add a `--memory-profile` flag analogous to `--profile`, which attributes allocation activity to methods.

**How**: During periods of high allocation rate (detected via suggestion #2), sample thread stack traces via `ThreadMXBean.dumpAllThreads()`. Attribute allocation activity to the top non-filtered frame, similar to how `CPUSampler` attributes CPU time. Accumulate per-method allocation counts in a `MemoryMethodStats` class parallel to `MethodStats`.

**Display**: A new view mode showing top methods by attributed allocation volume, sorted descending.

**Effort**: Medium — mirrors the existing CPU profiler architecture but correlates with allocation events instead of CPU deltas.

---

## CPU Profiling Enhancements

### 6. Self-Time vs. Inclusive Time

**Description**: Currently, all CPU time for a thread sample is attributed to the single top-most non-filtered frame. Walk the full stack and attribute time to each qualifying frame to separate self-time (time spent in a method excluding callees) from inclusive time (total time including callees).

**How**: In `CPUSampler.update()`, instead of breaking after the first non-filtered frame, walk the entire stack trace. For each non-filtered frame, add `deltaCpuTime` to that method's inclusive time. Then, after processing all frames, compute self-time as `inclusiveTime - sum(child inclusiveTimes)`.

**Display**: Show both self-time % and inclusive time % in the profile view. This is critical for identifying whether a method is slow itself or slow because of what it calls.

**Effort**: Medium — requires restructuring the stack walk and adding a child-tracking mechanism in `MethodStats`.

---

### 7. Call Tree and Flame Graph Export

**Description**: Track parent-child relationships between methods and export profiling data in a format suitable for visualization.

**How**: During stack trace sampling, record the full call path (e.g., `main->run->process->compute`). Accumulate counts per unique path. Export in folded-stack format compatible with flame graph tools:
```
main;run;process;compute 42
main;run;process;validate 18
...
```

**Display**: Add a `--export-folded` flag that writes the folded stack output to a file on exit or on a key press. The file can be piped to `flamegraph.pl` for visual analysis.

**Effort**: Medium — requires path tracking and a data structure to accumulate per-path counts (e.g., a Trie or HashMap of path strings).

---

### 8. Configurable Sampling Rate

**Description**: Make the 100ms sampling interval configurable via CLI.

**How**: Add a `--sample-interval <ms>` argument to `JvmTop`. Pass the value to `VMProfileView` and `CPUSampler`. Validate that the value is within a reasonable range (e.g., 10ms–2000ms).

**Display**: Show the active sampling rate in the profile view header.

**Effort**: Low — plumbing a CLI argument through to the sampler.

---

### 9. Native/JNI Time Tracking

**Description**: Attribute time spent in native code separately from Java code.

**How**: When walking stack traces, detect frames with `<native method>` or frames from known native libraries. Maintain a separate accumulator for native time. Also use `ThreadInfo.getBlockedTime()` and `ThreadInfo.getWaitedTime()` for lock contention time.

**Display**: Show a `NATIVE %` column in the profile view and a breakdown in the detail view.

**Effort**: Low-Medium — stack frame detection is straightforward; the MXBean APIs for blocked/waited time already exist.

---

## General Enhancements

### 10. Lock Contention Profiling

**Description**: Beyond deadlock detection, measure which threads are spending time blocked or waiting on locks, and which locks are the most contended.

**How**: Sample `ThreadInfo.getBlockedTime()`, `ThreadInfo.getWaitedTime()`, `ThreadInfo.getBlockedMonitor()`, and `ThreadInfo.getLockedSynchronizers()` on each cycle. Aggregate contention time per lock object.

**Display**: Add a "top contended locks" section to the detail view, showing lock address, wait time, and which threads are blocked.

**Effort**: Medium — requires tracking lock identity across samples and aggregating.

---

### 11. JIT Compilation Tracking

**Description**: Use `CompilationMXBean` to track which methods the JVM is actively compiling, indicating hot spots detected by the JVM itself.

**How**: Query `ManagementFactory.getCompilationMXBean()` for ` getTotalCompilationTime()` and total compilations. Some implementations expose per-method compilation data.

**Display**: Show compilation rate and total compilation time in the detail view. Optionally flag methods that are being compiled repeatedly.

**Effort**: Low — the MXBean exists; availability varies by JVM implementation.

---

### 12. Historical Data and CSV Export

**Description**: Add the ability to record metrics over time for post-analysis.

**How**: Add a `--export-csv <file>` flag. On each update cycle, append a CSV row with timestamp and all collected metrics (CPU%, GC%, heap used, thread count, etc.).

**Display**: Not applicable — output goes to file. The data can be loaded into spreadsheets, Grafana, or other analysis tools.

**Effort**: Low — straightforward CSV writing on each cycle.

---

### 13. Wall-Clock vs. CPU Time Distinction

**Description**: The profiler currently labels CPU delta as "wall time" in the profile view output. Fix the labeling and optionally track both CPU time and wall-clock time separately.

**How**: `ThreadMXBean.getThreadCpuTime()` gives CPU time. Wall-clock time is derived from the sampling interval. Make the distinction clear in the display.

**Display**: Label columns accurately as `CPU%` and `WALL%`. For I/O-bound threads, wall time will exceed CPU time, which is useful diagnostic information.

**Effort**: Low — primarily a labeling fix, with optional wall-clock tracking.

---

## Prioritized Implementation Plan

| Priority | Feature | Effort | Impact | Notes |
|----------|---------|--------|--------|-------|
| P0 | Per-pool memory breakdown | Low | High | Reuse existing `MemoryPoolStat` |
| P0 | Self-time vs inclusive time | Medium | High | Biggest CPU profiling improvement |
| P0 | GC pause analysis | Low-Med | High | High diagnostic value |
| P1 | Allocation rate tracking | Medium | Medium | Foundation for memory profiling |
| P1 | Memory trend / leak detection | Medium | Medium | Prevents production incidents |
| P1 | Flame graph export | Medium | Medium | Enables external visualization |
| P2 | Configurable sample rate | Low | Med-Low | Quick win |
| P2 | Lock contention profiling | Medium | Medium | Complements deadlock detection |
| P2 | CSV export | Low | Med-Low | Enables post-analysis |
| P3 | Memory profiling mode | Medium | Medium | Builds on allocation tracking |
| P3 | Native/JNI tracking | Low-Med | Low-Med | Niche but useful |
| P3 | JIT compilation tracking | Low | Low | JVM-dependent availability |
| P3 | Wall vs CPU labeling fix | Low | Low | Correctness fix |

---

## Architecture Considerations

### Keeping It Pure JMX

All suggestions above are designed to work within the existing pure-JMX architecture. None require a native JVMTI agent. The trade-off is that some advanced features (precise allocation site tracking, object retention analysis, heap dump integration) would require either a native agent or integration with external tools like JProfiler or YourKit.

### Extending the Update Cycle

The `VMInfo.update()` method is the central data collection point. New metrics should be added there, with care to:
- Keep JMX calls within the existing snapshot cache (`SnapshotMBeanServerConnection`)
- Not increase the update cycle duration significantly
- Handle missing MXBeans gracefully (not all JVMs expose all beans)

### New Views vs. Extended Views

New profiling modes (memory profile, lock profile) should follow the existing `ConsoleView` interface pattern. Reuse `AbstractConsoleView` for formatting and the `CPUSampler` pattern for per-method accumulation.

### Data Retention

For trend analysis and historical features, consider a circular buffer (`ArrayDeque` with fixed max size) to avoid unbounded memory growth. A 60-sample window at 1-second intervals gives 60 seconds of history, which is sufficient for short-term trend detection.
