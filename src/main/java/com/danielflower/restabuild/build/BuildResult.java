package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import org.apache.commons.io.FileUtils;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BuildResult {

    public final String id = UUID.randomUUID().toString().replace("-", "");
    private final FileSandbox sandbox;
    private final File buildDir;
    private volatile BuildState state = BuildState.QUEUED;
    private final GitRepo gitRepo;
    private StringBuffer buildLog = new StringBuffer();
    private File buildLogFile;

    public BuildResult(FileSandbox sandbox, GitRepo gitRepo) {
        this.sandbox = sandbox;
        this.gitRepo = gitRepo;
        this.buildDir = sandbox.buildDir(id);
        buildLogFile = new File(buildDir, "build.log");

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

        } finally {
            buildLog = null;
        }
    }

    private class MultiWriter extends Writer {
        private final FileWriter logFileWriter;

        MultiWriter(FileWriter logFileWriter) {
            this.logFileWriter = logFileWriter;
        }

        public void write(char[] cbuf, int off, int len) throws IOException {
            buildLog.append(cbuf, off, len);
            logFileWriter.write(cbuf, off, len);
        }

        public void flush() throws IOException {
            logFileWriter.flush();
        }

        public void close() throws IOException {
            logFileWriter.close();
        }
    }
}
