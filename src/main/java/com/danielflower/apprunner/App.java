package com.danielflower.apprunner;

import com.danielflower.apprunner.runners.*;
import com.danielflower.apprunner.web.WebServer;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static com.danielflower.apprunner.Config.SERVER_PORT;
import static com.danielflower.apprunner.FileSandbox.dirPath;
import static java.util.Arrays.asList;

public class App {
    public static final Logger log = LoggerFactory.getLogger(App.class);
    private final Config config;
    private WebServer webServer;

    public App(Config config) {
        this.config = config;
    }

    public void start() throws Exception {
        File dataDir = config.getOrCreateDir(Config.DATA_DIR);
        FileSandbox fileSandbox = new FileSandbox(dataDir);

        deleteOldTempFiles(fileSandbox.tempDir(""));

        int appRunnerPort = config.getInt(SERVER_PORT);

        webServer = new WebServer(appRunnerPort, fileSandbox);
        webServer.start();
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
        if (webServer != null) {
            log.info("Stopping web server");
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
