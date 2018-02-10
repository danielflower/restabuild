package com.danielflower.restabuild;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.Fields;
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
    private final AppRepo appRepo = AppRepo.create("maven");
    private final HttpClient client = RestClient.instance;


    @BeforeClass
    public static void start() throws Exception {
        Config config = Config.load(new String[]{"sample-config.properties"});
        app = new App(config);
        app.start();
    }

    @AfterClass
    public static void stop() {
        app.shutdown();
    }

    @Test
    public void theRestApiCanBeUsedToBuildStuff() throws Exception {
        Fields fields = new Fields();
        fields.add("gitUrl", appRepo.gitUrl());
        ContentResponse response = client.FORM(app.uri().resolve("/restabuild/api/v1/builds"), fields);
        assertThat(response, ContentResponseMatcher.equalTo(201, containsString("status")));


    }

}
