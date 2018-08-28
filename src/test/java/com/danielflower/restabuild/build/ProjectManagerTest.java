package com.danielflower.restabuild.build;

import junit.framework.TestCase;
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
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProjectManagerTest {

    private AppRepo appRepo = AppRepo.create("maven");

    @Test
    public void canBuildProjectsAndPickUpChangesFromMasterBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        BuildState result = runner.build(buildLog, "master");
        assertThat(buildLog.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo, "master");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        BuildState result2 = runner.build(badBuildLog, "master");
        assertThat(buildLog.toString(), result2, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));

    }


    @Test
    public void canBuildProjectsAndPickUpChangesFromAnyExisingBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        BuildState result = runner.build(buildLog, "branch-1");
        assertThat(buildLog.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        BuildState result2 = runner.build(badBuildLog, "branch-1");
        assertThat(buildLog.toString(), result2, is(BuildState.FAILURE));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));

    }


    @Test
    public void canBuildProjectsAndSwitchFromBranch() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        BuildState result = runner.build(buildLog, "master");
        assertThat(buildLog.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));


        StringBuilderWriter buildLogBranch1 = new StringBuilderWriter();
        result = runner.build(buildLogBranch1, "branch-1");
        assertThat(buildLogBranch1.toString(), result, is(BuildState.SUCCESS));
        assertThat(buildLogBranch1.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo, "branch-1");

        StringBuilderWriter buildLogMasterAgain = new StringBuilderWriter();
        BuildState result2 = runner.build(buildLogMasterAgain, "master");
        assertThat(buildLogMasterAgain.toString(), result2, is(BuildState.SUCCESS));
        assertThat(buildLogMasterAgain.toString(),  containsString("BUILD SUCCESS"));


        StringBuilderWriter buildLogBranch1Again = new StringBuilderWriter();
        BuildState branch1AgainResult = runner.build(buildLogBranch1Again, "branch-1");
        assertThat(buildLogBranch1Again.toString(), branch1AgainResult, is(BuildState.FAILURE));
        assertThat(buildLogBranch1Again.toString(),  containsString("The build could not read 1 project"));
    }

    @Test
    public void canFailBuildIfBranchDoesnotExist() throws Exception {
        StringBuilderWriter buildLog = new StringBuilderWriter();
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox(), buildLog);

        try {
            runner.build(buildLog, "a-non-exist-branch");
            fail("It should throw exception when switching a non-exist-branch.");
        } catch (RuntimeException e) {
            e.getMessage().contains("Failed to switch to branch a-non-exist-branch");
        }

    }

    private static void breakTheProject(AppRepo appRepo, String branch) throws IOException, GitAPIException {
        File pom = new File(appRepo.originDir, "pom.xml");
        FileUtils.write(pom, "I am a corrupt pom", StandardCharsets.UTF_8);
        appRepo.origin.checkout().setCreateBranch(false).setName(branch).call();
        appRepo.origin.add().addFilepattern(".").call();
        appRepo.origin.commit().setMessage("Breaking the build").call();
    }
}
