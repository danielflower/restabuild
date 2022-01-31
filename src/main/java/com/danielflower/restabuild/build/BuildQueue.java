package com.danielflower.restabuild.build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.ClientErrorException;
import java.io.InterruptedIOException;
import java.util.concurrent.*;

public class BuildQueue {
    private static final Logger log = LoggerFactory.getLogger(BuildQueue.class);

    private final ExecutorService executorService;

    private final BlockingQueue<BuildResult> queue = new LinkedBlockingQueue<>();
    private final int numberOfConcurrentBuilds;
    private final int buildTimeout;
    private volatile boolean isRunning = false;
    private final DeletePolicy instanceDirDeletePolicy;

    public BuildQueue(int numberOfConcurrentBuilds, int buildTimeout, DeletePolicy instanceDirDeletePolicy) {
        this.numberOfConcurrentBuilds = numberOfConcurrentBuilds;
        this.buildTimeout = buildTimeout;
        this.executorService = Executors.newFixedThreadPool(numberOfConcurrentBuilds + 1);
        this.instanceDirDeletePolicy = instanceDirDeletePolicy;
    }

    public void enqueue(BuildResult buildResult) {
        if (!isRunning) throw new ClientErrorException("Restabuild is not accepting builds at this time", 400);
        queue.add(buildResult);
    }

    public void start() {
        isRunning = true;
        executorService.submit(this::buildLoop);
    }

    private void buildLoop() {
        while (isRunning) {
            try {
                BuildResult build = queue.take();
                build.run(buildTimeout, instanceDirDeletePolicy);
            } catch (Throwable t) {
                if (t instanceof InterruptedException || t instanceof InterruptedIOException) {
                    log.info("Build loop stopping");
                    break;
                }
                if (isRunning) {
                    log.error("Error in the build loop", t);
                }
            }
        }
    }

    public void stop() throws InterruptedException {
        isRunning = false;
        executorService.shutdownNow();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
    }

}
