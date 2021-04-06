package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import com.jcraft.jsch.JSch;
import io.muserver.Mutils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.io.file.DeleteOption;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.file.StandardDeleteOption;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.FetchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class ProjectManager implements AutoCloseable {
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
        public final File workDir;
        public final String branch;
        ExtendedBuildState(BuildState buildState, String commitIDBeforeBuild, String commitIDAfterBuild, List<String> tagsAdded, File workDir, String branch) {
            this.buildState = buildState;
            this.commitIDBeforeBuild = commitIDBeforeBuild;
            this.commitIDAfterBuild = commitIDAfterBuild;
            this.tagsAdded = tagsAdded;
            this.workDir = workDir;
            this.branch = branch;
        }
    }

    public ExtendedBuildState build(Writer outputHandler, String branch, String buildParam, int buildTimeout, Map<String, String> environment) throws Exception {
        doubleLog(outputHandler, "Fetching latest changes from git...");
        final File workDir;
        final ExtendedBuildState extendedBuildState;
        try (Git git = pullFromGitAndCopyWorkingCopyToNewDir(outputHandler, branch)) {
            branch = git.getRepository().getBranch();
            workDir = git.getRepository().getWorkTree();
            doubleLog(outputHandler, "Created new instance in " + dirPath(workDir));

            Ref headBefore = git.getRepository().exactRef(Constants.HEAD);
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
                if (StringUtils.isNoneBlank(buildParam)) {
                    command.addArguments(buildParam);
                }
                ProcessStarter processStarter = new ProcessStarter(outputHandler);
                result = processStarter.run(outputHandler, command, workDir, TimeUnit.MINUTES.toMillis(buildTimeout), environment);

                headAfter = git.getRepository().exactRef(Constants.HEAD);

                try (RevWalk walk = new RevWalk(git.getRepository())) {
                    walk.markStart(walk.parseCommit(headAfter.getObjectId()));
                    walk.markUninteresting(walk.parseCommit(headBefore.getObjectId()));
                    for (RevCommit commit : walk) {
                        getTagsAt(git, commit.getId()).forEach(s -> newTags.add(0, s));
                    }
                }
                getTagsAt(git, beforeCommitID).forEach(s -> newTags.add(0, s));
            }

            newTags.removeAll(tagsBefore);

            extendedBuildState = new ExtendedBuildState(result, beforeCommitID.name(), headAfter.getObjectId().name(), Collections.unmodifiableList(newTags), workDir, branch);
        }

        deleteDirectoryQuietly(workDir, StandardDeleteOption.OVERRIDE_READ_ONLY);

        return extendedBuildState;
    }

    public void close() {
        if (git != null) {
            git.close();
        }
    }

    private Git pullFromGitAndCopyWorkingCopyToNewDir(Writer writer, String branch) throws GitAPIException, IOException {
        FetchResult fetchResult = git.fetch().setRemote("origin").setProgressMonitor(new TextProgressMonitor(writer)).call();
        if (branch == null) {
            Ref ref = findBranchToBuild(fetchResult);
            if (ref == null) {
                throw new RuntimeException("Could not determine default branch to build");
            } else {
                branch = StringUtils.removeStart(ref.getName(), Constants.R_HEADS);
            }
            doubleLog(writer, "Building default branch: "+branch);
        } else {
            doubleLog(writer, "Building branch: "+branch);
        }
        return copyToNewInstanceDirAndSwitchBranch(branch);
    }

    private Ref findBranchToBuild(FetchResult result) {
        final Ref headRef = result.getAdvertisedRef(Constants.HEAD);
        if (headRef == null) {
            return null;
        }

        if (headRef.isSymbolic()) {
            return headRef.getTarget();
        }

        final ObjectId headObjectId = headRef.getObjectId();
        if (headObjectId == null) {
            return null;
        }

        final Ref masterRef = result.getAdvertisedRef(Constants.R_HEADS + Constants.MASTER);
        if (masterRef != null && headObjectId.equals(masterRef.getObjectId())) {
            return masterRef;
        }

        for (Ref adRef : result.getAdvertisedRefs()) {
            if (!adRef.getName().startsWith(Constants.R_HEADS)) {
                continue;
            }
            if (headObjectId.equals(adRef.getObjectId())) {
                return adRef;
            }
        }

        return null;
    }

    private Git copyToNewInstanceDirAndSwitchBranch(String branch) throws GitAPIException, IOException {
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
        return copy;
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

    private static void deleteDirectoryQuietly(File workDir, DeleteOption... options) {
        try {
            PathUtils.deleteDirectory(workDir.toPath(), options);
        } catch (IOException e) {
            log.debug("Failed to delete {}", workDir, e);
        }
    }
}
