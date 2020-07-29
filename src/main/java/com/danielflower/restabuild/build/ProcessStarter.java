package com.danielflower.restabuild.build;

import org.apache.commons.exec.*;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class ProcessStarter {
    private static final Logger log = LoggerFactory.getLogger(ProcessStarter.class);
    private final Writer outputHandler;

    ProcessStarter(Writer outputHandler) {
        this.outputHandler = outputHandler;
    }

    public BuildState run(Writer outputHandler, CommandLine command, File projectRoot, long timeout, String buildId) throws RestaBuildException, IOException {
        long startTime = logStartInfo(command);
        ExecuteWatchdog watchDog = new ExecuteWatchdog(timeout);
        Executor executor = createExecutor(this.outputHandler, command, projectRoot, watchDog);
        try {
            Map<String, String> environment = getEnrichedEnvironment(buildId);
            int exitValue = executor.execute(command, environment);
            if (executor.isFailure(exitValue)) {
                String message = watchDog.killedProcess()
                    ? "Timed out waiting for " + command
                    : "Exit code " + exitValue + " returned from " + command;
                outputHandler.append(message);
                return BuildState.FAILURE;
            } else {
                return BuildState.SUCCESS;
            }
        } catch (Exception e) {
            String message = "Error running: " + dirPath(projectRoot) + "> " + StringUtils.join(command.toStrings(), " ")
                + " - " + e.getMessage();
            log.info(message);
            outputHandler.append(message);
            return BuildState.FAILURE;
        } finally {
            logEndTime(command, startTime);
        }
    }

    private void doubleLog(String message) {
        log.info(message);
        writeLine(this.outputHandler, message);
    }

    public static void writeLine(Writer writer, String message) {
        try {
            writer.write(message + "\n");
            writer.flush();
        } catch (IOException e) {
            log.warn("Could not log to outputHandler", e);
        }
    }

    private long logStartInfo(CommandLine command) {
        doubleLog("Starting " + StringUtils.join(command.toStrings(), " "));
        return System.currentTimeMillis();
    }

    private void logEndTime(CommandLine command, long startTime) {
        doubleLog("Completed " + command.getExecutable() + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private static Executor createExecutor(Writer consoleLogHandler, CommandLine command, File projectRoot, ExecuteWatchdog watchDog) {
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(projectRoot);
        executor.setWatchdog(watchDog);
        executor.setStreamHandler(new PumpStreamHandler(new WriterOutputStream(consoleLogHandler, StandardCharsets.UTF_8, 1024, true)));
        writeLine(consoleLogHandler, dirPath(executor.getWorkingDirectory()) + "> " + String.join(" ", command.toStrings()) + "\n");
        return executor;
    }

    private Map<String, String> getEnrichedEnvironment(String buildId) {
        String requestUri = System.getProperty("RESTABUILD_REQUEST_URI");
        String logUrl = requestUri + "/" + buildId + "/log";
        Map<String, String> envMap = new HashMap<>(System.getenv());
        envMap.put("RESTABUILD_ID", buildId);
        envMap.put("RESTABUILD_LOG_URL", logUrl);
        return envMap;
    }

}
