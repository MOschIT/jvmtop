package com.jvmtop.tui;

import java.lang.management.MemoryUsage;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.Toolkit;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Row;

import com.jvmtop.monitor.VMInfo;
import com.jvmtop.monitor.VMInfoState;
import com.jvmtop.openjdk.tools.LocalVirtualMachine;
import com.jvmtop.profiler.CPUSampler;
import com.jvmtop.profiler.MethodStats;

import static dev.tamboui.toolkit.Toolkit.*;

public class JvmTopStateManager {

    private final ConcurrentHashMap<Integer, VMInfo> vmMap = new ConcurrentHashMap<>();
    private final AtomicReference<List<VMOverviewRow>> overviewRows = new AtomicReference<>(Collections.emptyList());
    private final ConcurrentHashMap<Integer, CPUSampler> cpuSamplers = new ConcurrentHashMap<>();

    private int scanCounter = 0;
    private int selectedRowIndex = 0;
    private VMSortMode sortMode = VMSortMode.CPU;

    public void update() {
        scanCounter++;
        if (scanCounter % 5 == 0) {
            scanForNewVMs();
        }
        updateAllVMs();
        snapshotOverview();
    }

    private void scanForNewVMs() {
        var allVms = LocalVirtualMachine.getAllVirtualMachines();
        for (Map.Entry<Integer, LocalVirtualMachine> entry : allVms.entrySet()) {
            int pid = entry.getKey();
            if (!vmMap.containsKey(pid)) {
                try {
                    VMInfo vmInfo = VMInfo.processNewVM(entry.getValue(), pid);
                    vmMap.put(pid, vmInfo);
                } catch (Exception e) {
                    // ignore
                }
            }
        }
        vmMap.entrySet().removeIf(entry -> {
            try {
                var state = entry.getValue().getState();
                return state == VMInfoState.DETACHED || state == VMInfoState.UNKNOWN_ERROR
                        || state == VMInfoState.ERROR_DURING_ATTACH || state == VMInfoState.CONNECTION_REFUSED;
            } catch (Exception e) {
                return true;
            }
        });
    }

    private void updateAllVMs() {
        for (VMInfo vmInfo : vmMap.values()) {
            try {
                vmInfo.update();
            } catch (Exception e) {
                // ignore
            }
        }
        for (var entry : cpuSamplers.entrySet()) {
            try {
                entry.getValue().update();
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void snapshotOverview() {
        List<VMOverviewRow> rows = new ArrayList<>();
        for (VMInfo vmInfo : vmMap.values()) {
            try {
                if (vmInfo.getState() == VMInfoState.ATTACHED) {
                    rows.add(new VMOverviewRow(
                            vmInfo.getId(),
                            vmInfo.getDisplayName(),
                            vmInfo.getHeapUsed(),
                            vmInfo.getHeapMax(),
                            vmInfo.getNonHeapUsed(),
                            vmInfo.getNonHeapMax(),
                            vmInfo.getCpuLoad() * 100,
                            vmInfo.getGcLoad() * 100,
                            vmInfo.getVMVersion(),
                            vmInfo.getOSUser(),
                            vmInfo.getThreadCount(),
                            vmInfo.hasDeadlockThreads()
                    ));
                }
            } catch (Exception e) {
                // ignore
            }
        }
        switch (sortMode) {
            case CPU -> rows.sort(Comparator.comparingDouble(VMOverviewRow::cpuLoad).reversed());
            case HEAP -> rows.sort(Comparator.comparingLong(VMOverviewRow::heapUsed).reversed());
            case PID -> rows.sort(Comparator.comparingInt(VMOverviewRow::pid));
        }
        overviewRows.set(rows);
    }

    public Element renderOverview() {
        var rows = overviewRows.get();

        var header = row(
                text(String.format("%6s", "PID")).bold(),
                text("  "),
                text(String.format("%-20s", "MAIN-CLASS")).bold(),
                text("  "),
                text(String.format("%8s", "HEAP")).bold(),
                text("  "),
                text(String.format("%8s", "H.MAX")).bold(),
                text("  "),
                text(String.format("%8s", "NON-HEAP")).bold(),
                text("  "),
                text(String.format("%8s", "NH.MAX")).bold(),
                text("  "),
                text(String.format("%7s", "CPU%")).bold(),
                text("  "),
                text(String.format("%7s", "GC%")).bold(),
                text("  "),
                text(String.format("%6s", "VM")).bold(),
                text("  "),
                text(String.format("%8s", "USER")).bold(),
                text("  "),
                text(String.format("%5s", "#THR")).bold(),
                text("  "),
                text(String.format("%3s", "DL")).bold()
        );

        List<Element> rowElements = new ArrayList<>();
        rowElements.add(header);

        for (int i = 0; i < rows.size(); i++) {
            rowElements.add(renderOverviewRow(rows.get(i), i == selectedRowIndex));
        }

        if (rows.isEmpty()) {
            rowElements.add(text(" No JVMs found").dim().fg(Color.YELLOW));
        }

        return panel(" JVMs (" + rows.size() + ")",
                column(rowElements.toArray(Element[]::new))
        ).rounded().borderColor(Color.CYAN);
    }

    private Element renderOverviewRow(VMOverviewRow r, boolean selected) {
        String deadlock = r.hasDeadlock() ? "!D" : "  ";
        String heapStr = formatMemory(r.heapUsed());
        String heapMaxStr = formatMemory(r.heapMax());
        String nhStr = formatMemory(r.nonHeapUsed());
        String nhMaxStr = formatMemory(r.nonHeapMax());
        String name = truncate(r.displayName(), 20);

        Row rowElement = row(
                text(String.format("%6d", r.pid())),
                text("  "),
                text(name),
                text("  "),
                text(String.format("%8s", heapStr)),
                text("  "),
                text(String.format("%8s", heapMaxStr)),
                text("  "),
                text(String.format("%8s", nhStr)),
                text("  "),
                text(String.format("%8s", nhMaxStr)),
                text("  "),
                text(String.format("%6.1f%%", r.cpuLoad())).fg(cpuColor(r.cpuLoad())),
                text("  "),
                text(String.format("%6.1f%%", r.gcLoad())).fg(r.gcLoad() > 30 ? Color.RED : Color.GREEN),
                text("  "),
                text(truncate(r.vmVersion(), 6)),
                text("  "),
                text(truncate(r.osUser(), 8)),
                text("  "),
                text(String.format("%5d", r.threadCount())),
                text("  "),
                text(deadlock).fg(Color.RED).bold()
        );

        return selected ? rowElement.reversed() : rowElement;
    }

    public Element renderDetail(Integer pid) {
        if (pid == null) {
            return panel(" Detail", text(" Select a PID from Overview first").fg(Color.YELLOW).dim());
        }

        VMInfo vmInfo = vmMap.get(pid);
        if (vmInfo == null) {
            return panel(" Detail (PID: " + pid + ")", text(" VM not found").fg(Color.YELLOW).dim());
        }

        try {
            if (vmInfo.getState() != VMInfoState.ATTACHED) {
                return panel(" Detail (PID: " + pid + ")", text(" VM state: " + vmInfo.getState()).fg(Color.YELLOW).dim());
            }

            var props = vmInfo.getSystemProperties();
            String command = props.get("sun.java.command");
            String vmName = props.getOrDefault("java.vm.name", "unknown");
            String javaVersion = props.getOrDefault("java.version", "unknown");
            String vendor = props.getOrDefault("java.vendor", "unknown");

            String appName = "unknown";
            String appArgs = "";
            if (command != null && !command.isEmpty()) {
                var parts = command.split(" ", 2);
                appName = parts[0];
                appArgs = parts.length > 1 ? parts[1] : "";
            }

            var heapUsage = vmInfo.getMemoryMXBean().getHeapMemoryUsage();
            var nonHeapUsage = vmInfo.getMemoryMXBean().getNonHeapMemoryUsage();
            double heapRatio = heapUsage.getMax() > 0 ? (double) heapUsage.getUsed() / heapUsage.getMax() : 0;

            var threadInfos = vmInfo.getThreadMXBean().dumpAllThreads(true, true);
            List<ThreadRow> threadRows = new ArrayList<>();
            for (var ti : threadInfos) {
                threadRows.add(new ThreadRow(
                        ti.getThreadId(),
                        ti.getThreadName(),
                        ti.getThreadState().toString(),
                        ti.getLockOwnerId() >= 0 ? String.valueOf(ti.getLockOwnerId()) : ""
                ));
            }
            threadRows.sort(Comparator.comparing(ThreadRow::state));

            return renderDetailPanel(pid, appName, appArgs, vmName, javaVersion, vendor,
                    vmInfo.getRuntimeMXBean().getUptime(),
                    vmInfo.getThreadCount(),
                    vmInfo.getThreadMXBean().getPeakThreadCount(),
                    vmInfo.getThreadMXBean().getTotalStartedThreadCount(),
                    vmInfo.getOSUser(),
                    vmInfo.getGcTime(),
                    vmInfo.getGcCount(),
                    vmInfo.getTotalLoadedClassCount(),
                    vmInfo.getCpuLoad() * 100,
                    vmInfo.getGcLoad() * 100,
                    heapUsage, nonHeapUsage,
                    threadRows,
                    vmInfo.hasDeadlockThreads());

        } catch (Exception e) {
            return panel(" Detail (PID: " + pid + ")", text(" Error: " + e.getMessage()).fg(Color.RED).dim());
        }
    }

    private Element renderDetailPanel(int pid, String appName, String appArgs, String vmName,
                                      String javaVersion, String vendor, long uptime,
                                      long threadCount, long peakThreads, long totalThreadsCreated,
                                      String osUser, long gcTime, long gcCount, long totalLoadedClasses,
                                      double cpuLoad, double gcLoad,
                                      MemoryUsage heapUsage, MemoryUsage nonHeapUsage,
                                      List<ThreadRow> threadRows, boolean hasDeadlock) {

        String heapUsed = formatMemory(heapUsage.getUsed());
        String heapMax = formatMemory(heapUsage.getMax());
        String nonHeapUsed = formatMemory(nonHeapUsage.getUsed());
        String nonHeapMax = formatMemory(nonHeapUsage.getMax());
        String uptimeStr = formatUptime(uptime);
        String gcTimeStr = formatUptime(gcTime);
        double heapRatio = heapUsage.getMax() > 0 ? (double) heapUsage.getUsed() / heapUsage.getMax() : 0;

        Element infoSection = column(
                text(" PID: " + pid + "  " + appName).bold().fg(Color.CYAN),
                appArgs.isEmpty() ? spacer() : text(" Args: " + truncate(appArgs, 70)).dim(),
                text(" VM: " + vmName + " " + javaVersion + " (" + vendor + ")").dim(),
                text(" Uptime: " + uptimeStr + "  Threads: " + threadCount +
                        " (peak: " + peakThreads + ", created: " + totalThreadsCreated + ")").dim(),
                text(" GC Time: " + gcTimeStr + "  GC Runs: " + gcCount +
                        "  Classes: " + totalLoadedClasses).dim(),
                text(" CPU: " + String.format("%.1f%%", cpuLoad) +
                        "  GC: " + String.format("%.1f%%", gcLoad)).fg(cpuColor(cpuLoad)),
                text(" Heap: " + heapUsed + " / " + heapMax +
                        "  Non-Heap: " + nonHeapUsed + " / " + nonHeapMax).dim(),
                hasDeadlock ? text(" *** DEADLOCK DETECTED ***").bold().fg(Color.RED) : spacer()
        );

        Element gaugeSection = row(
                gauge(heapRatio)
                        .label(heapUsed + " / " + heapMax)
                        .gaugeColor(heapRatio > 0.9 ? Color.RED : heapRatio > 0.7 ? Color.YELLOW : Color.GREEN)
                        .title("Heap " + String.format("%.0f%%", heapRatio * 100))
                        .rounded()
        );

        var tbl = table()
                .header("TID", "NAME", "STATE", "BLOCKED BY")
                .widths(Constraint.percentage(10),
                        Constraint.percentage(35),
                        Constraint.percentage(25),
                        Constraint.fill())
                .title("Threads (" + threadRows.size() + ")")
                .rounded();

        int limit = Math.min(20, threadRows.size());
        for (int i = 0; i < limit; i++) {
            var tr = threadRows.get(i);
            tbl.row(String.valueOf(tr.tid()),
                    truncate(tr.name(), 30),
                    tr.state(),
                    tr.blockedBy());
        }

        return column(
                panel(" VM Info", infoSection).rounded().borderColor(Color.CYAN),
                gaugeSection,
                panel(" Threads", tbl).rounded().borderColor(Color.MAGENTA)
        );
    }

    public Element renderProfile(Integer pid) {
        if (pid == null) {
            return panel(" Profile", text(" Select a PID from Overview first").fg(Color.YELLOW).dim());
        }

        VMInfo vmInfo = vmMap.get(pid);
        if (vmInfo == null) {
            return panel(" Profile (PID: " + pid + ")", text(" VM not found").fg(Color.YELLOW).dim());
        }

        cpuSamplers.computeIfAbsent(pid, p -> {
            try {
                return new CPUSampler(vmInfo);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        var sampler = cpuSamplers.get(pid);

        var tbl = table()
                .header("CPU%", "TIME(s)", "METHOD")
                .widths(Constraint.percentage(8),
                        Constraint.percentage(10),
                        Constraint.fill())
                .title("CPU Profile (samples: " + sampler.getUpdateCount() + ")")
                .rounded();

        try {
            var topMethods = sampler.getTop(30);
            long total = sampler.getTotal();
            for (var ms : topMethods) {
                double pct = total > 0 ? (double) ms.getHits().get() / total * 100 : 0;
                double secs = ms.getHits().get() / 1_000_000_000.0;
                String method = ms.getClassName() + "." + ms.getMethodName();
                tbl.row(
                        String.format("%.1f%%", pct),
                        String.format("%.2f", secs),
                        method
                );
            }
        } catch (Exception e) {
            // ignore
        }

        return panel(" CPU Profile (PID: " + pid + ")",
                column(
                        text(" Sampling active.").dim().fg(Color.CYAN),
                        tbl
                )
        ).rounded().borderColor(Color.YELLOW);
    }

    public List<VMOverviewRow> getOverviewRows() {
        return overviewRows.get();
    }

    public int getSelectedRowIndex() {
        return selectedRowIndex;
    }

    public void setSelectedRowIndex(int index) {
        selectedRowIndex = index;
    }

    public void toggleSortMode() {
        sortMode = sortMode.next();
    }

    public VMSortMode getSortMode() {
        return sortMode;
    }

    private String formatMemory(long bytes) {
        if (bytes < 0) return "n/a";
        if (bytes >= 1024 * 1024 * 1024) {
            return String.format("%.1fG", bytes / 1024.0 / 1024.0 / 1024.0);
        } else if (bytes >= 1024 * 1024) {
            return String.format("%dm", bytes / 1024 / 1024);
        } else if (bytes >= 1024) {
            return String.format("%dk", bytes / 1024);
        }
        return String.valueOf(bytes);
    }

    private String formatUptime(long millis) {
        long hours = millis / 3600000;
        long minutes = (millis % 3600000) / 60000;
        long seconds = (millis % 60000) / 1000;
        if (hours > 0) return String.format("%dh %dm %ds", hours, minutes, seconds);
        if (minutes > 0) return String.format("%dm %ds", minutes, seconds);
        return String.format("%ds", seconds);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private Color cpuColor(double cpuLoad) {
        if (cpuLoad > 80) return Color.RED;
        if (cpuLoad > 50) return Color.YELLOW;
        return Color.GREEN;
    }

    public enum VMSortMode {
        CPU, HEAP, PID;

        public VMSortMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public record VMOverviewRow(
            int pid, String displayName, long heapUsed, long heapMax,
            long nonHeapUsed, long nonHeapMax, double cpuLoad, double gcLoad,
            String vmVersion, String osUser, long threadCount, boolean hasDeadlock
    ) {}

    public record ThreadRow(long tid, String name, String state, String blockedBy) {}
}
