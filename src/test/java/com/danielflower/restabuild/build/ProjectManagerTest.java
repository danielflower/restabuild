package com.danielflower.restabuild.build;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import scaffolding.AppRepo;
import scaffolding.AssertUtil;
import scaffolding.TestConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static junit.framework.TestCase.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static scaffolding.AssertUtil.assertEventually;

public class ProjectManagerTest {

    private final AppRepo appRepo = AppRepo.create("maven");
    private final int defaultTimeout = 30000;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final DeletePolicy instanceDirDeletePolicy = DeletePolicy.NEVER;
    private final AtomicReference<BuildStatus> endStatus = new AtomicReference<>(null);
    private final BuildProcessListener endStatusSetter = (buildProcess, oldStatus, newStatus) -> {
        endStatus.set(newStatus);
    };

    @After
    public void shutdown() {
        assertThat(executor.shutdownNow(), empty());
    }

    @Test
    public void canBuildProjectsAndPickUpChangesFromMasterBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        BuildProcess buildProcess = startProcess(buildLog, appRepo.toRepoBranch("master"));
        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(buildProcess.createdTags().toString(), buildProcess.createdTags(), contains("my-maven-app-1.0.0"));
        assertThat("workDir still exists", buildProcess.workDir().exists(), is(false));

        breakTheProject(appRepo, "master");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        BuildProcess secondBuildProcess = startProcess(badBuildLog, appRepo.toRepoBranch("master"));

        assertEventually(endStatus::get, equalTo(BuildStatus.FAILURE));

        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));
        assertThat("workDir does not still exist", secondBuildProcess.workDir().exists(), is(true));
    }

    @NotNull
    private BuildProcess startProcess(StringBuilderWriter buildLog, RepoBranch repoBranch) {
        BuildProcess buildProcess = new BuildProcess(endStatusSetter, buildLog, executor, defaultTimeout, System.getenv(), null, repoBranch, TestConfig.testSandbox(), instanceDirDeletePolicy);
        buildProcess.start();
        return buildProcess;
    }

    @Test
    public void tagsCanBePickedUpEvenForBuildsWithCommitsInThem() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        AppRepo appRepo = AppRepo.create("tagger");
        appRepo.origin.tag().setName("pre-existing-tag").setMessage("This should not be returned as it exists before the build").call();
        ObjectId commitIDAtStart = appRepo.origin.getRepository().exactRef("HEAD").getObjectId();

        BuildProcess build = startProcess(buildLog, appRepo.toRepoBranch("master"));
        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));


        String log = buildLog.toString();
        assertThat(log, build.commitIDBeforeBuild(), is(commitIDAtStart));
        assertThat(log, build.commitIDAfterBuild(), not(equalTo(commitIDAtStart)));
        assertThat("Actual: " + build.createdTags(), build.createdTags(),
            containsInAnyOrder("lightweight1", "annnotated1", "lightweight2", "annnotated2", "lightweight3", "annnotated3"));

    }


    @Test
    public void canBuildProjectsAndPickUpChangesFromAnyExistingBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();

        startProcess(buildLog, appRepo.toRepoBranch("master"));
        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));

        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();

        startProcess(badBuildLog, appRepo.toRepoBranch("branch-1"));
        assertEventually(endStatus::get, equalTo(BuildStatus.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));
    }


    @Test
    public void canBuildProjectsAndSwitchFromBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        startProcess(buildLog, appRepo.toRepoBranch("master"));
        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));

        endStatus.set(null);
        StringBuilderWriter buildLogBranch1 = new StringBuilderWriter();
        startProcess(buildLogBranch1, appRepo.toRepoBranch("branch-1"));
        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));
        assertThat(buildLogBranch1.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo, "branch-1");

        endStatus.set(null);
        StringBuilderWriter buildLogMasterAgain = new StringBuilderWriter();
        startProcess(buildLogMasterAgain, appRepo.toRepoBranch("master"));
        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));
        assertThat(buildLogMasterAgain.toString(),  containsString("BUILD SUCCESS"));

        endStatus.set(null);
        StringBuilderWriter buildLogBranch1Again = new StringBuilderWriter();
        startProcess(buildLogBranch1Again, appRepo.toRepoBranch("branch-1"));
        assertEventually(endStatus::get, equalTo(BuildStatus.FAILURE));
        assertThat(buildLogBranch1Again.toString(),  containsString("The build could not read 1 project"));
    }

    @Test
    public void canFailBuildIfBranchDoesnotExist() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        startProcess(buildLog, appRepo.toRepoBranch("a-non-exist-branch"));
        assertEventually(endStatus::get, equalTo(BuildStatus.FAILURE));
        assertThat(buildLog.toString(), containsString("Remote branch 'a-non-exist-branch' not found in upstream origin"));
    }

    @Test
    public void canBuildProjectsWithParameter() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        BuildProcess buildProcess = new BuildProcess(endStatusSetter, buildLog, executor, defaultTimeout, System.getenv(), "\tTest  Parameter\n", appRepo.toRepoBranch("master"), TestConfig.testSandbox(), instanceDirDeletePolicy);
        buildProcess.start();

        assertEventually(endStatus::get, equalTo(BuildStatus.SUCCESS));
        assertThat(buildLog.toString(), containsString("build parameter 1: --Test--"));
        assertThat(buildLog.toString(), containsString("build parameter 2: --Parameter--"));
    }

    private static void breakTheProject(AppRepo appRepo, String branch) throws IOException, GitAPIException {
        File pom = new File(appRepo.originDir, "pom.xml");
        FileUtils.write(pom, "I am a corrupt pom", StandardCharsets.UTF_8);
        appRepo.origin.checkout().setCreateBranch(false).setName(branch).call();
        appRepo.origin.add().addFilepattern(".").call();
        appRepo.origin.commit().setMessage("Breaking the build").call();
    }
}
