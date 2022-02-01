package com.danielflower.restabuild;

import com.danielflower.restabuild.build.BuildDatabase;
import com.danielflower.restabuild.build.BuildQueue;
import com.danielflower.restabuild.web.BuildResource;
import com.danielflower.restabuild.web.WebServer;
import io.muserver.Mutils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.danielflower.restabuild.Config.SERVER_PORT;
import static com.danielflower.restabuild.FileSandbox.dirPath;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);
    private final Config config;
    private WebServer webServer;
    public BuildQueue buildQueue;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public App(Config config) {
        this.config = config;
    }

    public URI uri() {
        return webServer.server.uri();
    }

    public void start() throws IOException {
        File dataDir = config.getOrCreateDir(Config.DATA_DIR);
        FileSandbox fileSandbox = new FileSandbox(dataDir);

        deleteOldTempFiles(fileSandbox.tempDir(""));

        int appRunnerPort = config.getInt(SERVER_PORT);

        BuildDatabase database = new BuildDatabase();
        int buildTimeoutMinutes = config.getInt(Config.TIMEOUT, 30);
        int numberOfConcurrentBuilds = config.getInt(Config.CONCURRENT_BUILDS);

        buildQueue = new BuildQueue(numberOfConcurrentBuilds, buildTimeoutMinutes, config.deletePolicy());

        BuildResource buildResource = new BuildResource(fileSandbox, database, buildQueue, executorService);
        String context = Mutils.trim(config.get(Config.CONTEXT, "restabuild"), "/");
        webServer = WebServer.start(appRunnerPort, context, buildResource, buildTimeoutMinutes);
    }

    private void deleteOldTempFiles(File tempDir) {
        log.info("Deleting contents of temporary folder at " + dirPath(tempDir));
        try {
            FileUtils.deleteDirectory(tempDir);
        } catch (IOException e) {
            log.warn("Failed to delete " + dirPath(tempDir), e);
        }
    }


    public void shutdown() {
        log.info("Shutdown invoked");
        try {
            log.info("Stopping builds.....");
            buildQueue.stop();
            executorService.shutdownNow();
            boolean allStopped = executorService.awaitTermination(2, TimeUnit.MINUTES);
            log.info("All stopped? " + allStopped);
        } catch (InterruptedException e) {
            log.info("Interrupted");
        }
        if (webServer != null) {
            log.info("Stopping web server on port " + webServer.server.uri().getPort());
            try {
                webServer.close();
            } catch (Exception e) {
                log.info("Error while stopping", e);
            }
            log.info("Shutdown complete");
            webServer = null;
        }
    }

    public static void main(String[] args) {
        try {
            App app = new App(Config.load(args));
            app.start();
            Runtime.getRuntime().addShutdownHook(new Thread(app::shutdown));
        } catch (Throwable t) {
            log.error("Error during startup", t);
            System.exit(1);
        }
    }
}
