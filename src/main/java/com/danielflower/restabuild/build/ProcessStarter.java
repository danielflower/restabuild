package com.danielflower.restabuild.build;

import org.apache.commons.exec.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.eclipse.jetty.io.WriterOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class ProcessStarter {
    public static final Logger log = LoggerFactory.getLogger(ProcessStarter.class);
    private final InvocationOutputHandler outputHandler;

    public ProcessStarter(InvocationOutputHandler outputHandler) {
        this.outputHandler = outputHandler;
    }

    public BuildResult run(CommandLine command, File projectRoot, long timeout) throws RestaBuildException {
        long startTime = logStartInfo(command);
        ExecuteWatchdog watchDog = new ExecuteWatchdog(timeout);
        Executor executor = createExecutor(outputHandler, command, projectRoot, watchDog);
        try {
            int exitValue = executor.execute(command, System.getenv());
            if (executor.isFailure(exitValue)) {
                String message = watchDog.killedProcess()
                    ? "Timed out waiting for " + command
                    : "Exit code " + exitValue + " returned from " + command;
                return BuildResult.failure(message);
            } else {
                return BuildResult.success();
            }
        } catch (Exception e) {
            String message = "Error running: " + dirPath(projectRoot) + "> " + StringUtils.join(command.toStrings(), " ")
                + " - " + e.getMessage();
            log.info(message);
            return BuildResult.failure(message);
        } finally {
            logEndTime(command, startTime);
        }
    }

    private void doubleLog(String message) {
        log.info(message);
        outputHandler.consumeLine(message);
    }

    private long logStartInfo(CommandLine command) {
        doubleLog("Starting " + StringUtils.join(command.toStrings(), " "));
        return System.currentTimeMillis();
    }

    private void logEndTime(CommandLine command, long startTime) {
        doubleLog("Completed " + command.getExecutable() + " in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    private static Executor createExecutor(InvocationOutputHandler consoleLogHandler, CommandLine command, File projectRoot, ExecuteWatchdog watchDog) {
        Executor executor = new DefaultExecutor();
        executor.setWorkingDirectory(projectRoot);
        executor.setWatchdog(watchDog);
        executor.setStreamHandler(new PumpStreamHandler(new WriterOutputStream(new WriterToOutputBridge(consoleLogHandler))));
        consoleLogHandler.consumeLine(dirPath(executor.getWorkingDirectory()) + "> " + String.join(" ", command.toStrings()) + "\n");
        return executor;
    }

}
