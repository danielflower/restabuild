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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProjectManagerTest {

    private AppRepo appRepo = AppRepo.create("maven");

    @Test
    public void canBuildProjectsAndPickUpChanges() throws Exception {
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox());

        StringBuilderWriter buildLog = new StringBuilderWriter();
        BuildResult result = runner.build(buildLog);
        assertThat(result.success, is(true));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));

        breakTheProject(appRepo);

        StringBuilderWriter badBuildLog = new StringBuilderWriter();
        BuildResult result2 = runner.build(badBuildLog);
        assertThat(result2.success, is(false));
        assertThat(badBuildLog.toString(), containsString("The build could not read 1 project"));

    }

    private static void breakTheProject(AppRepo appRepo) throws IOException, GitAPIException {
        File pom = new File(appRepo.originDir, "pom.xml");
        FileUtils.write(pom, "I am a corrupt pom", StandardCharsets.UTF_8);
        appRepo.origin.add().addFilepattern(".").call();
        appRepo.origin.commit().setMessage("Breaking the build").call();
    }
}
