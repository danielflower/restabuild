package com.danielflower.restabuild.build;

import com.danielflower.restabuild.FileSandbox;
import com.jcraft.jsch.JSch;
import io.muserver.Mutils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.file.DeleteOption;
import org.apache.commons.io.file.PathUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.danielflower.restabuild.FileSandbox.dirPath;

public class RemoteGitRepo {
    static {
        JSch.setConfig("StrictHostKeyChecking", "no");
    }
    private static final Logger log = LoggerFactory.getLogger(RemoteGitRepo.class);


    static synchronized RemoteGitRepo create(URIish gitUrl, FileSandbox fileSandbox) {
        String repoId = DigestUtils.sha1Hex(gitUrl.toString());
        File gitDir = fileSandbox.repoDir(repoId);
        File instanceDir = fileSandbox.tempDir(repoId + File.separator + "instances");
        return new RemoteGitRepo(instanceDir, gitUrl, gitDir);
    }

    private Git getGit(ProgressMonitor progressMonitor) {
        Git git;
        try {
            try {
                git = Git.open(repoDir);
                log.info("Using existing git repo at " + dirPath(repoDir));
            } catch (RepositoryNotFoundException e) {
                log.info("Cloning " + gitUrl + " to " + dirPath(repoDir));
                git = Git.cloneRepository()
                    .setProgressMonitor(progressMonitor)
                    .setURI(gitUrl.toString())
                    .setBare(true)
                    .setDirectory(repoDir)
                    .call();
            }
            git.remoteSetUrl().setRemoteName("origin").setRemoteUri(gitUrl).call();
        } catch (IOException | GitAPIException e) {
            throw new RestaBuildException("Could not open or create git repo at " + repoDir, e);
        }
        return git;
    }


    private final File instanceDir;
    private final URIish gitUrl;
    private final File repoDir;

    private RemoteGitRepo(File instanceDir, URIish gitUrl, File repoDir) {
        this.instanceDir = instanceDir;
        this.gitUrl = gitUrl;
        this.repoDir = repoDir;
    }


    public Git pullFromGitAndCopyWorkingCopyToNewDir(String branch, long timeoutMillis, ProgressMonitor progressMonitor) throws GitAPIException, IOException {
        synchronized (repoDir.getCanonicalPath().intern()) {
            try (Git git = getGit(progressMonitor)) {
                git.fetch().setRemote("origin")
                    .setProgressMonitor(progressMonitor).setTimeout((int) TimeUnit.MILLISECONDS.toSeconds(timeoutMillis)).call();
            }
            return copyToNewInstanceDirAndSwitchBranch(branch);
        }
    }

    private Git copyToNewInstanceDirAndSwitchBranch(String branch) throws GitAPIException, IOException {
        File dest = new File(instanceDir, String.valueOf(System.currentTimeMillis()));
        if (!dest.mkdir()) {
            throw new RuntimeException("Could not create " + dirPath(dest));
        }
        // Clone from the bare repo on the local disk....
        Git copy = Git.cloneRepository()
            .setBranch(branch)
            .setURI(repoDir.toURI().toString())
            .setBare(false)
            .setDirectory(dest)
            .call();

        // ...but set the origin to the remote URL the user selected so that if their build pushes anything to origin it goes to the right place
        copy.remoteSetUrl().setRemoteName("origin").setRemoteUri(gitUrl).call();
        return copy;
    }

    public static List<String> getTagsAt(Git git, ObjectId commitID) throws GitAPIException {
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

    public static void deleteDirectoryQuietly(File workDir, DeleteOption... options) {
        try {
            PathUtils.deleteDirectory(workDir.toPath(), options);
        } catch (IOException e) {
            log.debug("Failed to delete {}", workDir, e);
        }
    }
}
