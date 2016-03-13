package com.danielflower.apprunner.build;

import com.danielflower.apprunner.FileSandbox;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.StoredConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.danielflower.apprunner.FileSandbox.dirPath;

public class ProjectManager {
    public static final Logger log = LoggerFactory.getLogger(ProjectManager.class);

    public static ProjectManager create(String gitUrl, FileSandbox fileSandbox) {
        String buildId = DigestUtils.shaHex(gitUrl);
        File gitDir = fileSandbox.repoDir(buildId);
        File instanceDir = fileSandbox.tempDir(buildId + File.separator + "instances");

        Git git;
        try {
            try {
                log.info("Using existing git repo at " + dirPath(gitDir));
                git = Git.open(gitDir);
            } catch (RepositoryNotFoundException e) {
                log.info("Cloning " + gitUrl + " to " + dirPath(gitDir));
                git = Git.cloneRepository()
                    .setURI(gitUrl)
                    .setBare(false)
                    .setDirectory(gitDir)
                    .call();
            }
        } catch (IOException | GitAPIException e) {
            throw new AppRunnerException("Could not open or create git repo at " + gitDir, e);
        }
        StoredConfig config = git.getRepository().getConfig();
        config.setString("remote", "origin", "url", gitUrl);
        try {
            config.save();
        } catch (IOException e) {
            throw new AppRunnerException("Error while setting remote on Git repo at " + dirPath(gitDir), e);
        }
        return new ProjectManager(git, instanceDir);
    }

    private final Git git;
    private final File instanceDir;

    private ProjectManager(Git git, File instanceDir) {
        this.git = git;
        this.instanceDir = instanceDir;
    }


    public void build(InvocationOutputHandler outputHandler) throws Exception {
        InvocationOutputHandler buildLogHandler = outputHandler::consumeLine;

        buildLogHandler.consumeLine("Fetching latest changes from git...");
        File id = pullFromGitAndCopyWorkingCopyToNewDir();
        buildLogHandler.consumeLine("Created new instance in " + dirPath(id));

        runBuild(id, buildLogHandler);
        buildLogHandler.consumeLine("Completed");
    }

    private void runBuild(File projectRoot, InvocationOutputHandler buildLogHandler) {
        CommandLine command = new CommandLine(buildCommand(projectRoot));
        buildLogHandler.consumeLine("Running " + StringUtils.join(command.toStrings(), " "));
        ProcessStarter.run(buildLogHandler, command, projectRoot, TimeUnit.MINUTES.toMillis(30));
        buildLogHandler.consumeLine("Build complete");
    }

    private File buildCommand(File projectRoot) {
        String buildFile = SystemUtils.IS_OS_WINDOWS ? "build.bat" : "build.sh";
        File f = new File(projectRoot, buildFile);
        if (!f.isFile()) {
            throw new AppRunnerException("Please place a file called " + buildFile + " in the root of your repo");
        }
        return f;
    }

    public File pullFromGitAndCopyWorkingCopyToNewDir() throws GitAPIException, IOException {
        git.fetch().setRemote("origin").call();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/master").call();
        return copyToNewInstanceDir();
    }

    private File copyToNewInstanceDir() throws IOException {
        File dest = new File(instanceDir, String.valueOf(System.currentTimeMillis()));
        if (!dest.mkdir()) {
            throw new AppRunnerException("Could not create " + dirPath(dest));
        }
        FileUtils.copyDirectory(git.getRepository().getWorkTree(), dest, pathname -> !pathname.getName().equals(".git"));
        return dest;
    }
}
