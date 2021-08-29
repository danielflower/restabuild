package com.danielflower.restabuild.build;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class ProcessTree {
    private static final Logger log = LoggerFactory.getLogger(ProcessTree.class);
    public final long pid;
    public final List<ProcessTree> children;
    public final ProcessHandle.Info info;
    public final ProcessHandle handle;

    public void destroy(long timeout, TimeUnit unit) throws InterruptedException {
        recursiveDestroy(pid, unit.toMillis(timeout), System.currentTimeMillis());
    }

    private void recursiveDestroy(long ancestorPid, long maxDuration, long startTime) throws InterruptedException {
        long newDuration = Math.max(0, maxDuration - (System.currentTimeMillis() - startTime));
        if (handle.isAlive()) {
            handle.destroy();
            log.info(prefix(ancestorPid) + pid + " graceful destroy invoked");

            try {
                handle.onExit().get(newDuration, TimeUnit.MILLISECONDS);
            } catch (ExecutionException e) {
                log.error(prefix(ancestorPid) + "Error running exit", e);
            } catch (TimeoutException e) {
                log.warn(prefix(ancestorPid) + "Timed out waiting for " + pid + " to exit so will forcibly destroy");
            }

            if (handle.isAlive()) {
                log.info(prefix(ancestorPid) + pid + " graceful destroy invoked");
                handle.destroyForcibly();
                try {
                    handle.onExit().get(100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    log.error(prefix(ancestorPid) + "Error foricbly destroying " + pid, e);
                }
            }
        } else {
            log.info(prefix(ancestorPid) + pid + " is already destroyed");
        }
        for (ProcessTree child : children) {
            child.recursiveDestroy(ancestorPid, newDuration, startTime);
        }
    }

    @NotNull
    private String prefix(long ancestorPid) {
        return "Killing process tree " + ancestorPid + "> ";
    }

    public static ProcessTree snapshot(Process process) {
        if (process == null) return null;
        return create(process.toHandle());
    }

    public boolean isAlive() {
        return handle.isAlive();
    }

    private static ProcessTree create(ProcessHandle parent) {
        List<ProcessTree> children = new ArrayList<>();
        parent.children().forEach(child -> children.add(create(child)));
        return new ProcessTree(parent.pid(), children, parent);
    }


    private ProcessTree(long pid, List<ProcessTree> children, ProcessHandle handle) {
        this.pid = pid;
        this.children = children;
        this.info = handle.info();
        this.handle = handle;
    }

    public JSONObject toJSON() {
        return new JSONObject()
            .put("pid", pid)
            .put("command", info.command().orElse(null))
            .put("commandArguments", info.arguments().orElse(null))
            .put("startTime", info.startInstant().map(Instant::toString).orElse(null))
            .put("cpuDuration", info.totalCpuDuration().map(Duration::toString).orElse(null))
            .put("cpuDurationMillis", info.totalCpuDuration().map(Duration::toMillis).orElse(null))
            .put("children", new JSONArray(children.stream().map(ProcessTree::toJSON).collect(Collectors.toList())))
            .put("isAlive", isAlive());
    }

}
