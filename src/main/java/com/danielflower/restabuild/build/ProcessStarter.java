package com.danielflower.restabuild.build;

import org.apache.commons.exec.*;
import org.apache.commons.io.output.WriterOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class ProcessStarter {
    private static final Logger log = LoggerFactory.getLogger(ProcessStarter.class);
    private final Writer outputHandler;

    public ProcessStarter(Writer outputHandler) {
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
        executor.setStreamHandler(new PumpStreamHandler(new WriterOutputStream(consoleLogHandler)));
        writeLine(consoleLogHandler, dirPath(executor.getWorkingDirectory()) + "> " + String.join(" ", command.toStrings()) + "\n");
        return executor;
    }

}
