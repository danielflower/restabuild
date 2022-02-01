package com.danielflower.restabuild.build;

import com.danielflower.restabuild.Config;
import com.danielflower.restabuild.FileSandbox;
import org.apache.commons.io.file.StandardDeleteOption;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class BuildProcess {

    private volatile BuildStatus status = BuildStatus.QUEUED;
    private final Object lock = new Object();

    private static final Logger log = LoggerFactory.getLogger(BuildProcess.class);
    private final BuildProcessListener buildProcessListener;
    private final Writer logWriter;
    private final ExecutorService executor;
    private final long timeoutMillis;
    private final Map<String, String> environment;
    private final String buildParam;
    private final RepoBranch repoBranch;
    private final FileSandbox sandbox;
    private ObjectId commitIDBeforeBuild;
    private ObjectId commitIDAfterBuild;
    private List<String> tagsBefore;
    private List<String> createdTags;
    private File workDir;
    private volatile Process process;
    private final DeletePolicy instanceDirDeletePolicy;

    public File workDir() {
        return workDir;
    }

    public ObjectId commitIDBeforeBuild() {
        return commitIDBeforeBuild;
    }

    public ObjectId commitIDAfterBuild() {
        return commitIDAfterBuild;
    }

    public List<String> createdTags() {
        return createdTags;
    }

    BuildProcess(@NotNull BuildProcessListener buildProcessListener, @NotNull Writer logWriter, ExecutorService executor, long timeoutMillis, Map<String, String> environment, String buildParam, RepoBranch repoBranch, FileSandbox sandbox, DeletePolicy instanceDirDeletePolicy) {
        this.buildProcessListener = Objects.requireNonNull(buildProcessListener, "buildProcessListener");
        this.logWriter = logWriter;
        this.executor = executor;
        this.timeoutMillis = timeoutMillis;
        this.environment = environment;
        this.buildParam = buildParam;
        this.repoBranch = repoBranch;
        this.sandbox = sandbox;
        this.instanceDirDeletePolicy = Objects.requireNonNull(instanceDirDeletePolicy, "instanceDirDeletePolicy");
    }

    private void changeStatus(BuildStatus newStatus, Git git) {
        synchronized (lock) {
            BuildStatus oldStatus = this.status;
            this.status = newStatus;
            if (newStatus.endState()) {
                try {
                    if (git != null) {
                        List<String> newTags = new ArrayList<>();

                        Ref headAfter = git.getRepository().exactRef("HEAD");

                        RemoteGitRepo.getTagsAt(git, commitIDBeforeBuild).forEach(s -> newTags.add(0, s));
                        try (RevWalk walk = new RevWalk(git.getRepository())) {
                            walk.markStart(walk.parseCommit(headAfter.getObjectId()));
                            walk.markUninteresting(walk.parseCommit(commitIDBeforeBuild));
                            for (RevCommit commit : walk) {
                                RemoteGitRepo.getTagsAt(git, commit.getId()).forEach(s -> newTags.add(0, s));
                            }
                        }
                        newTags.removeAll(tagsBefore);

                        commitIDAfterBuild = headAfter.getObjectId();
                        createdTags = Collections.unmodifiableList(newTags);
                    }
                } catch (Exception e) {
                    log.error("Error while processing build completion", e);
                    this.status = newStatus = BuildStatus.FAILURE;
                }

            }
            try {
                buildProcessListener.onStatusChanged(this, oldStatus, newStatus);
            } catch (Exception e) {
                log.error("Error while executing build listener callback from " + oldStatus + " to " + newStatus);
                this.status = BuildStatus.FAILURE;
            }
        }
    }

    private boolean buildCancelled() {
        return status == BuildStatus.CANCELLED || status == BuildStatus.CANCELLING;
    }

    public void start() {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                changeStatus(BuildStatus.IN_PROGRESS, null);

                try {
                    RemoteGitRepo pm = RemoteGitRepo.create(repoBranch.url, sandbox);
                    doubleLog(logWriter, "Fetching latest changes from git...");
                    TextProgressMonitor gitProgressMonitor = new TextProgressMonitor(logWriter) {
                        public boolean isCancelled() {
                            return buildCancelled();
                        }
                    };
                    try (Git git = pm.pullFromGitAndCopyWorkingCopyToNewDir(repoBranch.branch, timeoutMillis, gitProgressMonitor)) {
                        log.info("Current status is " + status);

                        workDir = git.getRepository().getWorkTree();
                        doubleLog(logWriter, "Created new instance in " + dirPath(workDir));

                        Ref headBefore = git.getRepository().exactRef("HEAD");
                        commitIDBeforeBuild = headBefore.getObjectId();
                        tagsBefore = RemoteGitRepo.getTagsAt(git, commitIDBeforeBuild);

                        File f = new File(workDir, BuildResult.buildFile);
                        if (!f.isFile()) {
                            logWriter.write("Please place a file called " + BuildResult.buildFile + " in the root of your repo");
                            changeStatus(BuildStatus.FAILURE, git);
                        } else {

                            List<String> commands = new ArrayList<>();
                            if (Config.isWindows()) {
                                commands.add(f.getCanonicalPath());
                            } else {
                                commands.add("bash");
                                commands.add("-x");
                                commands.add(f.getName());
                            }
                            if (buildParam != null) {
                                // TODO: add support for quoted parameter values
                                commands.addAll(Stream.of(buildParam.split("\\s")).filter(s -> !s.isEmpty()).collect(Collectors.toList()));
                            }


                            long buildStartMillis = System.currentTimeMillis();
                            doubleLog(logWriter, "Starting " + String.join(" ", commands));
                            synchronized (lock) {
                                if (buildCancelled()) {
                                    return;
                                } else {
                                    ProcessBuilder processBuilder = new ProcessBuilder()
                                        .command(commands).directory(workDir)
                                        .redirectOutput(ProcessBuilder.Redirect.PIPE)
                                        .redirectErrorStream(true);
                                    processBuilder.environment().putAll(environment);
                                    process = processBuilder.start();
                                }
                            }

                            Future<?> outputListener = executor.submit(() -> {
                                try (InputStreamReader out = new InputStreamReader(process.getInputStream())) {
                                    char[] buffer = new char[512];
                                    int read;
                                    while ((read = out.read(buffer)) > -1) {
                                        if (read > 0) {
                                            String text = new String(buffer, 0, read);
                                            logWriter.write(text);
                                            logWriter.flush();
                                        }
                                    }
                                } catch (Exception e) {
                                    if (!buildCancelled()) {
                                        log.error("Error while reading output of command", e);
                                    }
                                }
                            });

                            boolean timedOut = !process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                            try {
                                outputListener.get(timeoutMillis, TimeUnit.MILLISECONDS);
                            } catch (TimeoutException e) {
                                timedOut = true;
                            }

                            if (timedOut) {
                                changeStatus(BuildStatus.TIMED_OUT, git);
                                boolean destroyed = process.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                                log.info("Timed out build " + this + " " + (destroyed ? "and destroyed process" : "but could not destroy process"));
                            } else {
                                if (!buildCancelled()) {
                                    if (process.exitValue() == 0) {
                                        doubleLog(logWriter, "Completed " + f.getName() + " in " + (System.currentTimeMillis() - buildStartMillis) + "ms");
                                        changeStatus(BuildStatus.SUCCESS, git);
                                    } else {
                                        String message = "Exit code " + process.exitValue() + " returned from " + f.getName();
                                        doubleLog(logWriter, message);
                                        changeStatus(BuildStatus.FAILURE, git);
                                    }
                                }
                            }

                        }
                    } finally {
                        var wd = workDir;
                        if (instanceDirDeletePolicy.shouldDelete(status) && wd != null) {
                            RemoteGitRepo.deleteDirectoryQuietly(wd, StandardDeleteOption.OVERRIDE_READ_ONLY);
                        }
                    }


                } catch (Exception ex) {
                    BuildStatus finalStatue;
                    if (buildCancelled()) {
                        doubleLogIgnoreException(logWriter, "Build cancelled");
                        finalStatue = BuildStatus.CANCELLED;
                    } else if (ex instanceof InterruptedException || ex instanceof InterruptedIOException) {
                        log.info("Stopping due to shut down of server");
                        doubleLogIgnoreException(logWriter, "Restabuild server shutting down so build stopped");
                        finalStatue = BuildStatus.CANCELLED;
                    } else if (ex instanceof GitAPIException) {
                        Throwable cause = Objects.requireNonNullElse(ex.getCause(), ex);
                        doubleLogIgnoreException(logWriter, "Error while checking out repository: " + cause.getMessage());
                        finalStatue = BuildStatus.FAILURE;
                    } else {
                        Throwable cause = Objects.requireNonNullElse(ex.getCause(), ex);
                        doubleLogIgnoreException(logWriter, "Error while starting build: " + cause.getMessage());
                        log.error("Error stacktrace:", ex);
                        finalStatue = BuildStatus.FAILURE;
                    }
                    try {
                        cancel(finalStatue);
                    } catch (InterruptedException e) {
                        log.info("Interrupted while stopping");
                    }

                }
            }
        });
    }

    public void cancel(BuildStatus finalState) throws InterruptedException {
        if (!status.endState()) {
            if (finalState == BuildStatus.CANCELLED) {
                doubleLogIgnoreException(logWriter, "Build cancelled.");
            }
            changeStatus(BuildStatus.CANCELLING, null);
            ProcessTree processTree = ProcessTree.snapshot(this.process);
            if (processTree != null) {
                processTree.destroy(10, TimeUnit.SECONDS);
            }
            changeStatus(finalState, null);
        }
    }

    public ProcessTree currentProcessTree() {
        Process p = this.process;
        if (p != null) {
            return ProcessTree.snapshot(p);
        }
        return null;
    }

    private void doubleLogIgnoreException(Writer writer, String message) {
        try {
            doubleLog(writer, message);
        } catch (Exception e) {
            log.warn("Error while writing " + message + " to log: " + e.getMessage());
        }
    }
    private void doubleLog(Writer writer, String message) throws IOException {
        log.info(message);
        writer.write(message);
        writer.write('\n');
        writer.flush();
    }


}
