package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
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

    public BuildResult(FileSandbox sandbox, GitRepo gitRepo) {
        this.sandbox = sandbox;
        this.gitRepo = gitRepo;
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
        return new JSONObject()
            .put("id", id)
            .put("gitUrl", gitRepo.url)
            .put("status", state.name());
    }

    public void run() throws Exception {
        BuildState newState = state = BuildState.IN_PROGRESS;
        try (FileWriter logFileWriter = new FileWriter(buildLogFile);
             Writer writer = new MultiWriter(logFileWriter)) {
            ProjectManager pm = ProjectManager.create(gitRepo.url, sandbox, writer);
            newState = pm.build(writer);
        } catch (Exception ex) {
            newState = BuildState.FAILURE;
        } finally {
            FileUtils.write(new File(buildDir, "build.json"), toJson().toString(4), StandardCharsets.UTF_8);
            synchronized (lock) {
                state = newState;
                buildLog = null;
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
