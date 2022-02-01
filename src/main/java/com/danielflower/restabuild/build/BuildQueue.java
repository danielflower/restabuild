package com.danielflower.restabuild.build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ServiceUnavailableException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

public class BuildQueue {
    private static final Logger log = LoggerFactory.getLogger(BuildQueue.class);

    private final Queue<BuildResult> queue = new LinkedList<>();
    private final int numberOfConcurrentBuilds;
    private final int buildTimeout;
    private final DeletePolicy instanceDirDeletePolicy;
    private int inProgressBuilds = 0;
    private volatile boolean isRunning = true;

    public BuildQueue(int numberOfConcurrentBuilds, int buildTimeout, DeletePolicy instanceDirDeletePolicy) {
        this.numberOfConcurrentBuilds = numberOfConcurrentBuilds;
        this.buildTimeout = buildTimeout;
        this.instanceDirDeletePolicy = instanceDirDeletePolicy;
    }

    public int[] status() {
        synchronized (queue) {
            return new int[] {queue.size(), inProgressBuilds};
        }
    }

    public void stop() {
        isRunning = false;
    }

    public void enqueue(BuildResult buildResult) throws IOException {
        if (!isRunning) {
            throw new ServiceUnavailableException("The build server is shutting down");
        }
        synchronized (queue) {
            queue.add(buildResult);
            log.info("Queued " + buildResult.id + "; new queue size: " + queue.size() + "; in progress: " + inProgressBuilds + "; total concurrent allowed: " + numberOfConcurrentBuilds);
        }
        startIfCapacity();
    }

    public void startIfCapacity() throws IOException {
        synchronized (queue) {
            if (inProgressBuilds < numberOfConcurrentBuilds) {
                BuildResult build = queue.poll();
                if (build != null) {
                    build.run((buildProcess, oldStatus, newStatus) -> {
                        if (newStatus.endState()) {
                            synchronized (queue) {
                                inProgressBuilds--;
                                log.info("Build " + build.id + " completed with status " + newStatus + "; new queue size is " + inProgressBuilds);
                            }
                        }
                    }, buildTimeout, instanceDirDeletePolicy);
                    inProgressBuilds++;
                }
            }
        }
    }

    public void cancel(BuildResult buildResult) throws InterruptedException {
        synchronized (queue) {
            queue.remove(buildResult);
            buildResult.cancel();
        }
    }
}
