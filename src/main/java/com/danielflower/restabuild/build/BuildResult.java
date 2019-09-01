package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import io.muserver.Mutils;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class BuildResult {
    private final Object lock = new Object();
    public final String id = UUID.randomUUID().toString().replace("-", "");
    private final FileSandbox sandbox;
    private final File buildDir;
    private volatile BuildState state = BuildState.QUEUED;
    private final GitRepo gitRepo;
    private volatile StringBuffer buildLog = new StringBuffer();
    private File buildLogFile;
    private final List<StringListener> logListeners = new CopyOnWriteArrayList<>();
    public final long queueStart = System.currentTimeMillis();
    private long buildStart = -1;
    private long buildComplete = -1;
    private String commitIDBeforeBuild;
    private String commitIDAfterBuild;
    private List<String> createdTags;
    private String buildParam;

    public BuildResult(FileSandbox sandbox, GitRepo gitRepo, String buildParam) {
        this.sandbox = sandbox;
        this.gitRepo = gitRepo;
        this.buildParam = buildParam;
        this.buildDir = sandbox.buildDir(id);
        buildLogFile = new File(buildDir, "build.log");
    }

    public boolean hasFinished() {
        return state == BuildState.SUCCESS || state == BuildState.FAILURE;
    }

    public String log() throws IOException {
        if (state == BuildState.QUEUED) {
            return "Build not started.";
        }

        synchronized (lock) {
            if (!hasFinished()) {
                return "Build in progress: " + buildLog.toString();
            }
        }
        return FileUtils.readFileToString(buildLogFile, StandardCharsets.UTF_8);
    }

    public JSONObject toJson() {
        long queueDuration = buildStart < 0 ? (System.currentTimeMillis() - queueStart) : (buildStart - queueStart);
        JSONObject build = new JSONObject()
            .put("id", id)
            .put("gitUrl", gitRepo.url)
            .put("gitBranch", gitRepo.branch)
            .put("buildParam", buildParam == null ? "" : buildParam)
            .put("status", state.name())
            .put("queuedAt", Instant.ofEpochMilli(queueStart).toString())
            .put("queueDurationMillis", queueDuration)
            .put("commitIDBeforeBuild", this.commitIDBeforeBuild)
            .put("commitIDAfterBuild", this.commitIDAfterBuild)
            .put("tagsCreated", new JSONArray(Mutils.coalesce(createdTags, Collections.<String>emptyList())));
        if (buildStart > 0) {
            long buildDuration = buildComplete < 0 ? (System.currentTimeMillis() - buildStart) : (buildComplete - buildStart);
            build.put("buildDurationMillis", buildDuration);
        }
        return build;
    }

    public void run() throws Exception {
        BuildState newState = state = BuildState.IN_PROGRESS;
        ProjectManager.ExtendedBuildState extendedBuildState = null;
        buildStart = System.currentTimeMillis();
        try (FileWriter logFileWriter = new FileWriter(buildLogFile);
             Writer writer = new MultiWriter(logFileWriter)) {
            try {
                ProjectManager pm = ProjectManager.create(gitRepo.url, sandbox, writer);
                extendedBuildState = pm.build(writer, gitRepo.branch, buildParam);
                newState = extendedBuildState.buildState;
            } catch (Exception ex) {
                writer.write("\n\nERROR: " + ex.getMessage());
                ex.printStackTrace(new PrintWriter(writer));
                newState = BuildState.FAILURE;
            }
        } finally {
            buildComplete = System.currentTimeMillis();
            FileUtils.write(new File(buildDir, "build.json"), toJson().toString(4), StandardCharsets.UTF_8);
            synchronized (lock) {
                state = newState;
                buildLog = null;
                if (extendedBuildState != null) {
                    this.commitIDBeforeBuild = extendedBuildState.commitIDBeforeBuild;
                    this.commitIDAfterBuild = extendedBuildState.commitIDAfterBuild;
                    this.createdTags = extendedBuildState.tagsAdded;
                }
            }
        }
    }

    public void streamLog(StringListener writer) throws IOException {
        writer.onString(log());
        logListeners.add(writer);
    }

    public void stopListening(StringListener writer) {
        logListeners.remove(writer);
    }


    private class MultiWriter extends Writer {
        private final FileWriter logFileWriter;

        MultiWriter(FileWriter logFileWriter) {
            this.logFileWriter = logFileWriter;
        }

        public void write(char[] cbuf, int off, int len) throws IOException {
            buildLog.append(cbuf, off, len);
            logFileWriter.write(cbuf, off, len);
            if (!logListeners.isEmpty()) {
                String m = new String(cbuf, off, len);
                for (StringListener logListener : logListeners) {
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

    public interface StringListener {
        void onString(String value);
    }
}
