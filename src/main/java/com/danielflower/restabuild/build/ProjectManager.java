package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
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
    private static final Logger log = LoggerFactory.getLogger(ProjectManager.class);

    public static ProjectManager create(String gitUrl, FileSandbox fileSandbox) {
        String buildId = DigestUtils.sha1Hex(gitUrl);
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
            throw new RestaBuildException("Could not open or create git repo at " + gitDir, e);
        }
        StoredConfig config = git.getRepository().getConfig();
        config.setString("remote", "origin", "url", gitUrl);
        try {
            config.save();
        } catch (IOException e) {
            throw new RestaBuildException("Error while setting remote on Git repo at " + dirPath(gitDir), e);
        }
        return new ProjectManager(git, instanceDir);
    }

    private final Git git;
    private final File instanceDir;

    private ProjectManager(Git git, File instanceDir) {
        this.git = git;
        this.instanceDir = instanceDir;
    }


    public BuildResult build(Writer outputHandler) throws Exception {
        doubleLog(outputHandler, "Fetching latest changes from git...");
        File id = pullFromGitAndCopyWorkingCopyToNewDir(outputHandler);
        doubleLog(outputHandler, "Created new instance in " + dirPath(id));

        CommandLine command = new CommandLine(buildCommand(id));
        ProcessStarter processStarter = new ProcessStarter(outputHandler);
        return processStarter.run(command, id, TimeUnit.MINUTES.toMillis(30));
    }

    private File buildCommand(File projectRoot) {
        String buildFile = SystemUtils.IS_OS_WINDOWS ? "build.bat" : "build.sh";
        File f = new File(projectRoot, buildFile);
        if (!f.isFile()) {
            throw new RestaBuildException("Please place a file called " + buildFile + " in the root of your repo");
        }
        return f;
    }

    public File pullFromGitAndCopyWorkingCopyToNewDir(Writer writer) throws GitAPIException, IOException {
        git.fetch().setRemote("origin").setProgressMonitor(new TextProgressMonitor(writer)).call();
        git.reset().setMode(ResetCommand.ResetType.HARD).setRef("origin/master").call();
        return copyToNewInstanceDir();
    }

    private File copyToNewInstanceDir() throws IOException {
        File dest = new File(instanceDir, String.valueOf(System.currentTimeMillis()));
        if (!dest.mkdir()) {
            throw new RuntimeException("Could not create " + dirPath(dest));
        }
        FileUtils.copyDirectory(git.getRepository().getWorkTree(), dest, pathname -> !pathname.getName().equals(".git"));
        return dest;
    }


    private static void doubleLog(Writer writer, String message) {
        log.info(message);
        ProcessStarter.writeLine(writer, message);
    }
}
