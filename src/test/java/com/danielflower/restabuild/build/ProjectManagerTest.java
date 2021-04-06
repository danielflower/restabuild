package com.danielflower.restabuild.build;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scaffolding.AppRepo;
import scaffolding.TestConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;

public class ProjectManagerTest {

    private AppRepo appRepo;
    private final int defaultTimeout = 30;

    @Before
    public void setUp() {
        appRepo = AppRepo.create("maven");
    }

    @After
    public void tearDown() {
        appRepo.close();
    }

    @Test
    public void canBuildProjectsAndPickUpChangesFromMasterBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, "master", null, defaultTimeout, System.getenv());
        assertThat(buildLog.toString(), extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState.branch, equalTo("master"));
        assertThat(extendedBuildState.tagsAdded, contains("my-maven-app-1.0.0"));
        assertThat("workDir does not still exist", extendedBuildState.workDir.exists(), is(false));

        breakTheProject(appRepo, "master");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState badExtendedBuildState = runner.build(badBuildLog, "master", null, defaultTimeout, System.getenv());
        assertThat(badBuildLog.toString(), badExtendedBuildState.buildState, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));
        assertThat(badExtendedBuildState.branch, equalTo("master"));
        assertThat(badExtendedBuildState.tagsAdded, hasSize(0));
        assertThat("workDir does not still exist", badExtendedBuildState.workDir.exists(), is(false));

    }

    @Test
    public void canBuildProjectsUsingDefaultBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);
        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, null, null, defaultTimeout, System.getenv());
        assertThat(buildLog.toString(), extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState.branch, equalTo("master"));
        assertThat(extendedBuildState.tagsAdded, contains("my-maven-app-1.0.0"));
        assertThat("workDir does not still exist", extendedBuildState.workDir.exists(), is(false));

        breakTheProject(appRepo, "master");
        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState badExtendedBuildState = runner.build(badBuildLog, null, null, defaultTimeout, System.getenv());
        assertThat(badBuildLog.toString(), badExtendedBuildState.buildState, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));
        assertThat(badExtendedBuildState.branch, equalTo("master"));
        assertThat(badExtendedBuildState.tagsAdded, hasSize(0));
        assertThat("workDir does not still exist", badExtendedBuildState.workDir.exists(), is(false));
    }

    @Test
    public void canBuildProjectsWithADefaultBranchNotCalledMaster() throws Exception {
        appRepo.origin.checkout().setName("branch-1").call();

        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);
        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, null, null, defaultTimeout, System.getenv());
        assertThat(buildLog.toString(), extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState.branch, equalTo("branch-1"));
        assertThat(extendedBuildState.tagsAdded, contains("my-maven-app-1.0.0"));
        assertThat("workDir does not still exist", extendedBuildState.workDir.exists(), is(false));

        breakTheProject(appRepo, "branch-1");
        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState badExtendedBuildState = runner.build(badBuildLog, null, null, defaultTimeout, System.getenv());
        assertThat(badBuildLog.toString(), badExtendedBuildState.buildState, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));
        assertThat(badExtendedBuildState.branch, equalTo("branch-1"));
        assertThat(badExtendedBuildState.tagsAdded, hasSize(0));
        assertThat("workDir does not still exist", badExtendedBuildState.workDir.exists(), is(false));
    }

    @Test
    public void canBuildProjectsUsingChangingDefaultBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);
        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, null, null, defaultTimeout, System.getenv());
        assertThat(buildLog.toString(), extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState.branch, equalTo("master"));
        assertThat(extendedBuildState.tagsAdded, contains("my-maven-app-1.0.0"));
        assertThat("workDir does not still exist", extendedBuildState.workDir.exists(), is(false));


        appRepo.origin.checkout().setName("branch-1").call();
        StringBuilderWriter buildLog2 = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState extendedBuildState2 = runner.build(buildLog2, null, null, defaultTimeout, System.getenv());
        assertThat(buildLog2.toString(), extendedBuildState2.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog2.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState2.branch, equalTo("branch-1"));
        assertThat(extendedBuildState2.tagsAdded, contains("my-maven-app-1.0.1"));
        assertThat("workDir does not still exist", extendedBuildState2.workDir.exists(), is(false));
    }

    @Test
    public void tagsCanBePickedUpEvenForBuildsWithCommitsInThem() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        AppRepo appRepo = AppRepo.create("tagger");
        appRepo.origin.tag().setName("pre-existing-tag").setMessage("This should not be returned as it exists before the build").call();
        String commitIDAtStart = appRepo.origin.getRepository().exactRef("HEAD").getObjectId().name();

        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, "master", null, defaultTimeout, System.getenv());

        String log = buildLog.toString();
        assertThat(log, extendedBuildState.branch, equalTo("master"));
        assertThat(log, extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(log, extendedBuildState.commitIDBeforeBuild, is(commitIDAtStart));
        assertThat(log, extendedBuildState.commitIDAfterBuild, not(equalTo(commitIDAtStart)));
        assertThat("Actual: " + extendedBuildState.tagsAdded, extendedBuildState.tagsAdded,
            contains("lightweight1", "annnotated1", "lightweight2", "annnotated2", "lightweight3", "annnotated3"));

    }


    @Test
    public void canBuildProjectsAndPickUpChangesFromAnyExistingBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, "branch-1", null, defaultTimeout, System.getenv());
        assertThat(buildLog.toString(), extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState.branch, equalTo("branch-1"));

        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState badExtendedBuildState = runner.build(badBuildLog, "branch-1", null, defaultTimeout, System.getenv());
        assertThat(badBuildLog.toString(), badExtendedBuildState.buildState, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));
        assertThat(badExtendedBuildState.branch, equalTo("branch-1"));
    }


    @Test
    public void canBuildProjectsAndSwitchFromBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        ProjectManager.ExtendedBuildState masterExtendedBuildState = runner.build(buildLog, "master", null, defaultTimeout, System.getenv());
        assertThat(buildLog.toString(), masterExtendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(buildLog.toString(), masterExtendedBuildState.branch, equalTo("master"));


        StringBuilderWriter buildLogBranch1 = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState branch1ExtendedBuildState = runner.build(buildLogBranch1, "branch-1", null, defaultTimeout, System.getenv());
        assertThat(buildLogBranch1.toString(), branch1ExtendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLogBranch1.toString(), containsString("BUILD SUCCESS"));
        assertThat(buildLogBranch1.toString(), branch1ExtendedBuildState.branch, equalTo("branch-1"));

        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter buildLogMasterAgain = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState masterExtendedBuildState2 = runner.build(buildLogMasterAgain, "master", null, defaultTimeout, System.getenv());
        assertThat(buildLogMasterAgain.toString(), masterExtendedBuildState2.buildState, is(BuildState.SUCCESS));
        assertThat(buildLogMasterAgain.toString(),  containsString("BUILD SUCCESS"));
        assertThat(buildLogMasterAgain.toString(), masterExtendedBuildState2.branch, equalTo("master"));


        StringBuilderWriter buildLogBranch1Again = new StringBuilderWriter();
        ProjectManager.ExtendedBuildState branch1ExtendedBuildState2 = runner.build(buildLogBranch1Again, "branch-1", null, defaultTimeout, System.getenv());
        assertThat(buildLogBranch1Again.toString(), branch1ExtendedBuildState2.buildState, is(BuildState.FAILURE));
        assertThat(buildLogBranch1Again.toString(),  containsString("The build could not read 1 project"));
        assertThat(buildLogBranch1Again.toString(), branch1ExtendedBuildState2.branch, equalTo("branch-1"));
    }

    @Test
    public void canFailBuildIfBranchDoesnotExist() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        try {
            runner.build(buildLog, "a-non-exist-branch", null, defaultTimeout, System.getenv());
            fail("It should throw exception when switching a non-exist-branch.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Failed to switch to branch a-non-exist-branch"));
        }

    }

    @Test
    public void canBuildProjectsWithParameter() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        BuildState result = runner.build(buildLog, "master", "Test Parameter", defaultTimeout, System.getenv()).buildState;
        assertThat(buildLog.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("build parameter: Test Parameter"));
    }

    private static void breakTheProject(AppRepo appRepo, String branch) throws IOException, GitAPIException {
        File pom = new File(appRepo.originDir, "pom.xml");
        FileUtils.write(pom, "I am a corrupt pom", StandardCharsets.UTF_8);
        appRepo.origin.checkout().setCreateBranch(false).setName(branch).call();
        appRepo.origin.add().addFilepattern(".").call();
        appRepo.origin.commit().setMessage("Breaking the build").call();
    }
}
