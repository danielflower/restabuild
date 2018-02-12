package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import com.jcraft.jsch.JSch;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.TimeUnit;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class ProjectManager {
    static {
        JSch.setConfig("StrictHostKeyChecking", "no");
    }
    private static final Logger log = LoggerFactory.getLogger(ProjectManager.class);

    public static ProjectManager create(String gitUrl, FileSandbox fileSandbox, Writer writer) {
        String repoId = DigestUtils.sha1Hex(gitUrl);
        File gitDir = fileSandbox.repoDir(repoId);
        File instanceDir = fileSandbox.tempDir(repoId + File.separator + "instances");

        Git git;
        try {
            try {
                git = Git.open(gitDir);
                log.info("Using existing git repo at " + dirPath(gitDir));
            } catch (RepositoryNotFoundException e) {
                log.info("Cloning " + gitUrl + " to " + dirPath(gitDir));
                git = Git.cloneRepository()
                    .setProgressMonitor(new TextProgressMonitor(writer))
                    .setURI(gitUrl)
                    .setBare(true)
                    .setDirectory(gitDir)
                    .call();
            }
        } catch (IOException | GitAPIException e) {
            throw new RestaBuildException("Could not open or create git repo at " + gitDir, e);
        }

        setRemoteOriginUrl(git.getRepository(), gitUrl);
        return new ProjectManager(git, instanceDir, gitUrl, gitDir);
    }

    private static void setRemoteOriginUrl(Repository repository, String originUrl) {
        StoredConfig config = repository.getConfig();
        config.setString("remote", "origin", "url", originUrl);
        try {
            config.save();
        } catch (IOException e) {
            throw new RestaBuildException("Error while setting remote on Git repo at " + repository, e);
        }
    }

    private final Git git;
    private final File instanceDir;
    private final String gitUrl;
    private final File repoDir;

    private ProjectManager(Git git, File instanceDir, String gitUrl, File repoDir) {
        this.git = git;
        this.instanceDir = instanceDir;
        this.gitUrl = gitUrl;
        this.repoDir = repoDir;
    }


    public BuildState build(Writer outputHandler) throws Exception {
        doubleLog(outputHandler, "Fetching latest changes from git...");
        File workDir = pullFromGitAndCopyWorkingCopyToNewDir(outputHandler);
        doubleLog(outputHandler, "Created new instance in " + dirPath(workDir));

        String buildFile = SystemUtils.IS_OS_WINDOWS ? "build.bat" : "build.sh";
        File f = new File(workDir, buildFile);
        BuildState result;
        if (!f.isFile()) {
            outputHandler.write("Please place a file called " + buildFile + " in the root of your repo");
            result = BuildState.FAILURE;
        } else {
            CommandLine command = new CommandLine(f);
            ProcessStarter processStarter = new ProcessStarter(outputHandler);
            result = processStarter.run(outputHandler, command, workDir, TimeUnit.MINUTES.toMillis(30));
        }
        FileUtils.deleteQuietly(workDir);

        return result;
    }


    private File pullFromGitAndCopyWorkingCopyToNewDir(Writer writer) throws GitAPIException, IOException {
        git.fetch().setRemote("origin").setProgressMonitor(new TextProgressMonitor(writer)).call();
        return copyToNewInstanceDir();
    }

    private File copyToNewInstanceDir() throws GitAPIException {
        File dest = new File(instanceDir, String.valueOf(System.currentTimeMillis()));
        if (!dest.mkdir()) {
            throw new RuntimeException("Could not create " + dirPath(dest));
        }
        Git copy = Git.cloneRepository()
            .setURI(repoDir.toURI().toString())
            .setBare(false)
            .setDirectory(dest)
            .call();
        setRemoteOriginUrl(copy.getRepository(), gitUrl);
        return dest;
    }


    private static void doubleLog(Writer writer, String message) {
        log.info(message);
        ProcessStarter.writeLine(writer, message);
    }
}
