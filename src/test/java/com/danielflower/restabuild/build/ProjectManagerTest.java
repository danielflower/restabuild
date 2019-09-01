package com.danielflower.restabuild.build;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.StringBuilderWriter;
import org.eclipse.jgit.api.errors.GitAPIException;
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

public class ProjectManagerTest {

    private AppRepo appRepo = AppRepo.create("maven");

    @Test
    public void canBuildProjectsAndPickUpChangesFromMasterBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, "master", null);
        assertThat(buildLog.toString(), extendedBuildState.buildState, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
        assertThat(extendedBuildState.tagsAdded, contains("my-maven-app-1.0.0"));

        breakTheProject(appRepo, "master");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        BuildState result2 = runner.build(badBuildLog, "master", null).buildState;
        assertThat(buildLog.toString(), result2, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));

    }

    @Test
    public void tagsCanBePickedUpEvenForBuildsWithCommitsInThem() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        AppRepo appRepo = AppRepo.create("tagger");
        appRepo.origin.tag().setName("pre-existing-tag").setMessage("This should not be returned as it exists before the build").call();
        String commitIDAtStart = appRepo.origin.getRepository().exactRef("HEAD").getObjectId().name();

        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        ProjectManager.ExtendedBuildState extendedBuildState = runner.build(buildLog, "master", null);

        String log = buildLog.toString();
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

        BuildState result = runner.build(buildLog, "branch-1", null).buildState;
        assertThat(buildLog.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        BuildState result2 = runner.build(badBuildLog, "branch-1", null).buildState;
        assertThat(buildLog.toString(), result2, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));

    }


    @Test
    public void canBuildProjectsAndSwitchFromBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        BuildState result = runner.build(buildLog, "master", null).buildState;
        assertThat(buildLog.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));


        StringBuilderWriter buildLogBranch1 = new StringBuilderWriter();
        result = runner.build(buildLogBranch1, "branch-1", null).buildState;
        assertThat(buildLogBranch1.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLogBranch1.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter buildLogMasterAgain = new StringBuilderWriter();
        BuildState result2 = runner.build(buildLogMasterAgain, "master", null).buildState;
        assertThat(buildLogMasterAgain.toString(), result2, is(BuildState.SUCCESS));
        assertThat(buildLogMasterAgain.toString(),  containsString("BUILD SUCCESS"));


        StringBuilderWriter buildLogBranch1Again = new StringBuilderWriter();
        BuildState branch1AgainResult = runner.build(buildLogBranch1Again, "branch-1", null).buildState;
        assertThat(buildLogBranch1Again.toString(), branch1AgainResult, is(BuildState.FAILURE));
        assertThat(buildLogBranch1Again.toString(),  containsString("The build could not read 1 project"));
    }

    @Test
    public void canFailBuildIfBranchDoesnotExist() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        try {
            runner.build(buildLog, "a-non-exist-branch", null);
            fail("It should throw exception when switching a non-exist-branch.");
        } catch (RuntimeException e) {
            assertThat(e.getMessage(), containsString("Failed to switch to branch a-non-exist-branch"));
        }

    }

    @Test
    public void canBuildProjectsWithParameter() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        BuildState result = runner.build(buildLog, "master", "Test Parameter").buildState;
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
