package com.danielflower.restabuild;

import org.eclipse.jetty.client.api.ContentResponse;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.AppRepo;
import scaffolding.ContentResponseMatcher;
import scaffolding.RestClient;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

public class SystemTest {

    private static App app;
    private static RestClient client;
    private AppRepo appRepo = AppRepo.create("maven");


    @BeforeClass
    public static void start() throws Exception {
        Config config = Config.load(new String[]{"sample-config.properties"});
        app = new App(config);
        app.start();

        client = RestClient.create("http://localhost:" + config.getInt("restabuild.port"));
    }

    @AfterClass
    public static void stop() {
        app.shutdown();
        client.stop();
    }

    @Test
    public void theRestApiCanBeUsedToBuildStuff() throws Exception {
        ContentResponse response = client.build(appRepo.gitUrl());
        assertThat(response, ContentResponseMatcher.equalTo(200, containsString("BUILD SUCCESS")));
    }

}
