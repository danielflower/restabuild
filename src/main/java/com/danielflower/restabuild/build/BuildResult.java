package com.danielflower.restabuild.build;

import com.danielflower.restabuild.Config;
import com.danielflower.restabuild.FileSandbox;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class BuildResult {
    private static final Logger log = LoggerFactory.getLogger(BuildResult.class);
    public static String buildFile = Config.isWindows() ? "build.bat" : "build.sh";

    private final Object lock = new Object();
    public final String id;
    private final FileSandbox sandbox;
    private final File buildDir;
    private volatile BuildStatus status = BuildStatus.QUEUED;
    private final RepoBranch repoBranch;
    private final StringBuffer buildLog = new StringBuffer();
    private final File buildLogFile;
    public final long queueStart = System.currentTimeMillis();
    private long buildStart = -1;
    private long buildComplete = -1;
    private String commitIDBeforeBuild;
    private String commitIDAfterBuild;
    private List<String> createdTags;
    private final String buildParam;
    private final ExecutorService executorService;
    private final Map<String, String> environment;
    private final List<BuildResult.StringListener> logListeners = new CopyOnWriteArrayList<>();
    private volatile BuildProcess buildProcess;


    public BuildResult(FileSandbox sandbox, RepoBranch repoBranch, String buildParam, String id, Map<String, String> environment, ExecutorService executorService) {
        this.sandbox = sandbox;
        this.repoBranch = repoBranch;
        this.buildParam = buildParam;
        this.executorService = executorService;
        this.buildDir = sandbox.buildDir(id);
        this.buildLogFile = new File(buildDir, "build.log");
        this.id = id;
        this.environment = environment;
    }

    public boolean hasFinished() {
        synchronized (lock) {
            return status.endState();
        }
    }

    public boolean isCancellable() {
        synchronized (lock) {
            return status.isCancellable();
        }
    }

    public String log() throws IOException {
        if (status == BuildStatus.QUEUED) {
            return "Build not started.";
        }

        synchronized (lock) {
            if (!hasFinished()) {
                return "Build in progress: " + buildLog;
            }
        }
        return FileUtils.readFileToString(buildLogFile, StandardCharsets.UTF_8);
    }

    public JSONObject toJson() {
        long queueDuration = buildStart < 0 ? (System.currentTimeMillis() - queueStart) : (buildStart - queueStart);
        JSONObject build = new JSONObject()
            .put("id", id)
            .put("gitUrl", repoBranch.url)
            .put("gitBranch", repoBranch.branch)
            .put("buildParam", buildParam == null ? "" : buildParam)
            .put("status", status.name())
            .put("queuedAt", Instant.ofEpochMilli(queueStart).toString())
            .put("queueDurationMillis", queueDuration)
            .put("commitIDBeforeBuild", this.commitIDBeforeBuild)
            .put("commitIDAfterBuild", this.commitIDAfterBuild)
            .put("tagsCreated", createdTags == null ? new JSONArray() : new JSONArray(createdTags));
        if (buildStart > 0) {
            long buildDuration = buildComplete < 0 ? (System.currentTimeMillis() - buildStart) : (buildComplete - buildStart);
            build.put("buildDurationMillis", buildDuration);
        }
        BuildProcess bp = this.buildProcess;
        if (bp != null) {
            var tree = bp.currentProcessTree();
            if (tree != null) {
                build.put("processTree", tree.toJSON());
            }
        }
        return build;
    }

    public void run(@NotNull BuildProcessListener buildProcessListener, int buildTimeoutMins, DeletePolicy instanceDirDeletePolicy) throws IOException {
        long timeoutMillis = TimeUnit.MINUTES.toMillis(buildTimeoutMins);
        MultiWriter logWriter = new MultiWriter();
        BuildProcess bp = new BuildProcess((buildProcess, oldStatus, newStatus) -> {
            synchronized (lock) {
                if (oldStatus == BuildStatus.QUEUED) {
                    buildStart = System.currentTimeMillis();
                }

                try {
                    buildComplete = System.currentTimeMillis();
                    status = newStatus;
                    commitIDBeforeBuild = commitName(buildProcess.commitIDBeforeBuild());
                    commitIDAfterBuild = commitName(buildProcess.commitIDAfterBuild());
                    if (newStatus.endState()) {
                        createdTags = buildProcess.createdTags();
                        FileUtils.write(new File(buildDir, "build.json"), toJson().toString(4), StandardCharsets.UTF_8);
                        buildLog.setLength(0);
                        this.buildProcess = null;
                    }
                } finally {
                    if (newStatus.endState()) {
                        log.info("Closing log file writer");
                        logWriter.close();
                    }
                    buildProcessListener.onStatusChanged(buildProcess, oldStatus, newStatus);
                }
            }
        }, logWriter, executorService,timeoutMillis, environment, buildParam, repoBranch, sandbox, instanceDirDeletePolicy);
        this.buildProcess = bp;
        bp.start();
    }

    private static String commitName(ObjectId objectId) {
        return objectId == null ? null : objectId.name();
    }

    public void streamLog(StringListener writer) throws IOException {
        writer.onString(log());
        logListeners.add(writer);
    }

    public void stopListening(StringListener writer) {
        logListeners.remove(writer);
    }

    public void cancel() throws InterruptedException {
        BuildProcess bp = this.buildProcess;
        if (bp != null) {
            bp.cancel(BuildStatus.CANCELLED);
        } else {
            status = BuildStatus.CANCELLED;
        }
    }

    public interface StringListener {
        void onString(String value);
    }

    public class MultiWriter extends Writer {
        private final FileWriter logFileWriter;

        MultiWriter() throws IOException {
            this.logFileWriter = new FileWriter(buildLogFile, StandardCharsets.UTF_8);
        }

        public void write(char @NotNull [] cbuf, int off, int len) throws IOException {
            buildLog.append(cbuf, off, len);
            logFileWriter.write(cbuf, off, len);
            if (!logListeners.isEmpty()) {
                String m = new String(cbuf, off, len);
                for (BuildResult.StringListener logListener : logListeners) {
                    logListener.onString(m);
                }
            }
        }

        public void flush() throws IOException {
            logFileWriter.flush();
        }

        public void close() throws IOException {
            logFileWriter.close();
            logListeners.clear();
        }
    }
}
