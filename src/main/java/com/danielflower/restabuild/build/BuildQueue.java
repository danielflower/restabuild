package com.danielflower.restabuild.build;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

public class BuildQueue {
    private static final Logger log = LoggerFactory.getLogger(BuildQueue.class);

    private final ExecutorService executorService;

    private final BlockingQueue<BuildResult> queue = new LinkedBlockingQueue<>();
    private final int numberOfConcurrentBuilds;
    private volatile boolean isRunning = false;

    public BuildQueue(int numberOfConcurrentBuilds) {
        this.numberOfConcurrentBuilds = numberOfConcurrentBuilds;
        this.executorService = Executors.newFixedThreadPool(numberOfConcurrentBuilds);
    }

    public void enqueue(BuildResult buildResult) {
        queue.add(buildResult);
    }

    public void start() {
        isRunning = true;
        for (int i = 0; i < numberOfConcurrentBuilds; i++) {
            executorService.submit(this::buildLoop);
        }
    }

    private void buildLoop() {
        while (isRunning) {
            try {
                BuildResult build = queue.take();
                build.run();
            } catch (Throwable t) {
                if (isRunning) {
                    log.error("Error in the build loop", t);
                } else {
                    break;
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
