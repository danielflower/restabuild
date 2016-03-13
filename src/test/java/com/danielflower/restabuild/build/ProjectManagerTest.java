package com.danielflower.restabuild.build;

import org.apache.commons.io.output.StringBuilderWriter;
import org.junit.Test;
import scaffolding.AppRepo;
import scaffolding.TestConfig;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

public class ProjectManagerTest {

    AppRepo appRepo = AppRepo.create("maven");
    StringBuilderWriter buildLog = new StringBuilderWriter();

    @Test
    public void canStartAndStopLeinProjects() throws Exception {
        ProjectManager runner = ProjectManager.create(appRepo.gitUrl(), TestConfig.testSandbox());
        runner.build(new OutputToWriterBridge(buildLog));
        assertThat(buildLog.toString(), containsString("BUILD SUCCESS"));
    }
}
