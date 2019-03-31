package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import com.jcraft.jsch.JSch;
import io.muserver.Mutils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class ProjectManager {
    static {
        JSch.setConfig("StrictHostKeyChecking", "no");
    }
    private static final Logger log = LoggerFactory.getLogger(ProjectManager.class);
    public static String buildFile = SystemUtils.IS_OS_WINDOWS ? "build.bat" : "build.sh";


    static synchronized ProjectManager create(String gitUrl, FileSandbox fileSandbox, Writer writer) {
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

    static class ExtendedBuildState {
        public final BuildState buildState;
        public final String commitIDBeforeBuild;
        public final String commitIDAfterBuild;
        public final List<String> tagsAdded;
        ExtendedBuildState(BuildState buildState, String commitIDBeforeBuild, String commitIDAfterBuild, List<String> tagsAdded) {
            this.buildState = buildState;
            this.commitIDBeforeBuild = commitIDBeforeBuild;
            this.commitIDAfterBuild = commitIDAfterBuild;
            this.tagsAdded = tagsAdded;
        }
    }

    public ExtendedBuildState build(Writer outputHandler, String branch) throws Exception {
        doubleLog(outputHandler, "Fetching latest changes from git...");
        File workDir = pullFromGitAndCopyWorkingCopyToNewDir(outputHandler, branch);
        doubleLog(outputHandler, "Created new instance in " + dirPath(workDir));

        Git git = Git.open(workDir);


        Ref headBefore = git.getRepository().exactRef("HEAD");
        Ref headAfter = headBefore;
        ObjectId beforeCommitID = headBefore.getObjectId();
        List<String> newTags = new ArrayList<>();
        List<String> tagsBefore = getTagsAt(git, beforeCommitID);

        File f = new File(workDir, buildFile);
        BuildState result;
        if (!f.isFile()) {
            outputHandler.write("Please place a file called " + buildFile + " in the root of your repo");
            result = BuildState.FAILURE;
        } else {

            CommandLine command;
            if (SystemUtils.IS_OS_WINDOWS) {
                command = new CommandLine(f);
            } else {
                command = new CommandLine("bash")
                .addArgument("-x")
                .addArgument(f.getName());
            }
            ProcessStarter processStarter = new ProcessStarter(outputHandler);
            result = processStarter.run(outputHandler, command, workDir, TimeUnit.MINUTES.toMillis(30));

            headAfter = git.getRepository().exactRef("HEAD");

            try (RevWalk walk = new RevWalk(git.getRepository())) {
                walk.markStart(walk.parseCommit(headAfter.getObjectId()));
                walk.markUninteresting(walk.parseCommit(headBefore.getObjectId()));
                for (RevCommit commit : walk) {
                    getTagsAt(git, commit.getId()).forEach(s -> newTags.add(0, s));
                }
            }
            getTagsAt(git, beforeCommitID).forEach(s -> newTags.add(0, s));
        }

        FileUtils.deleteQuietly(workDir);

        for (String existingTag : tagsBefore) {
            newTags.remove(existingTag);
        }

        return new ExtendedBuildState(result, beforeCommitID.name(), headAfter.getObjectId().name(), Collections.unmodifiableList(newTags));
    }


    private File pullFromGitAndCopyWorkingCopyToNewDir(Writer writer, String branch) throws GitAPIException, IOException {
        git.fetch().setRemote("origin").setProgressMonitor(new TextProgressMonitor(writer)).call();
        return copyToNewInstanceDirAndSwitchBranch(branch);
    }

    private File copyToNewInstanceDirAndSwitchBranch(String branch) throws GitAPIException, IOException {
        File dest = new File(instanceDir, String.valueOf(System.currentTimeMillis()));
        if (!dest.mkdir()) {
            throw new RuntimeException("Could not create " + dirPath(dest));
        }
        Git copy = Git.cloneRepository()
            .setBranch(branch)
            .setURI(repoDir.toURI().toString())
            .setBare(false)
            .setDirectory(dest)
            .call();


        String currentBranch = copy.getRepository().getBranch();
        if(!branch.equals(currentBranch)) {
            throw new RuntimeException("Failed to switch to branch " + branch + " the current branch is " + currentBranch);
        }

        setRemoteOriginUrl(copy.getRepository(), gitUrl);
        return dest;
    }

    private static List<String> getTagsAt(Git git, ObjectId commitID) throws GitAPIException {
        RefDatabase refDatabase = git.getRepository().getRefDatabase();
        return git.tagList().call()
            .stream()
            .map(tag -> {
                try {
                    return refDatabase.peel(tag);
                } catch (IOException e) {
                    return null;
                }
            })
            .filter(tag -> tag != null && Mutils.coalesce(tag.getPeeledObjectId(), tag.getObjectId()).equals(commitID))
            .map(tag -> {
                String name = tag.getName();
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash > -1) {
                    name = name.substring(lastSlash + 1);
                }
                return name;
            })
            .collect(Collectors.toList());
    }

    private static void doubleLog(Writer writer, String message) {
        log.info(message);
        ProcessStarter.writeLine(writer, message);
    }
}
