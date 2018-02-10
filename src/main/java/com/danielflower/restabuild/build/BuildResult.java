package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

public class BuildResult {

    public final String id = UUID.randomUUID().toString().replace("-", "");
    private final FileSandbox sandbox;
    private final File buildDir;
    private volatile BuildState state = BuildState.QUEUED;
    private final GitRepo gitRepo;
    private StringBuffer buildLog = new StringBuffer();
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
        StringBuffer inMem = buildLog;
        if (inMem != null) {
            return "Build in progress: " + inMem.toString();
        } else {
            return FileUtils.readFileToString(buildLogFile, StandardCharsets.UTF_8);
        }
    }

    public JSONObject toJson() {
        return new JSONObject()
            .put("id", id)
            .put("gitUrl", gitRepo.url)
            .put("status", state.name());
    }

    public void run() throws Exception {

        state = BuildState.IN_PROGRESS;
        try (FileWriter logFileWriter = new FileWriter(buildLogFile)) {
            Writer writer = new MultiWriter(logFileWriter);

            ProjectManager pm = ProjectManager.create(gitRepo.url, sandbox);
            state = pm.build(writer);

            FileUtils.write(new File(buildDir, "build.json"), toJson().toString(4), StandardCharsets.UTF_8);
        } finally {
            buildLog = null;
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
        private final Logger log = LoggerFactory.getLogger(MultiWriter.class);
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
