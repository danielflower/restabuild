package com.danielflower.restabuild;

import com.danielflower.restabuild.build.BuildState;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.Fields;
import org.hamcrest.Matchers;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.AppRepo;
import scaffolding.RestClient;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class SystemTest {

    private static App app;
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
        AppRepo appRepo = AppRepo.create("maven");

        ContentResponse response = createBuild(appRepo);
        assertThat(response.getStatus(), equalTo(303));
        JSONObject build = new JSONObject(response.getContentAsString());

        System.out.println("build = " + build.toString(4));

        String id = build.getString("id");
        assertThat(id.isEmpty(), is(false));
        String url = build.getString("url");
        assertThat(url, equalTo("http://localhost:8080/restabuild/api/v1/builds/" + id));
        String logUrl = build.getString("logUrl");
        assertThat(logUrl, equalTo("http://localhost:8080/restabuild/api/v1/builds/" + id + "/log"));


        int attempts = 0;
        while (true) {
            JSONObject buildResource = new JSONObject(client.GET(url).getContentAsString());
            BuildState status = BuildState.valueOf(buildResource.getString("status"));

            assertThat(status, not(equalTo(BuildState.FAILURE)));

            if (status == BuildState.SUCCESS) {
                break;
            }
            if (attempts > 2000) {
                Assert.fail("Build never finished");
            }

            Thread.sleep(500);
            attempts++;
        }

    }


    @Test
    public void gettingTheLogBlocksUntilItIsComplete() throws Exception {
        AppRepo appRepo = AppRepo.create("maven");

        JSONObject build = new JSONObject(createBuild(appRepo).getContentAsString());

        String url = build.getString("url");
        String logUrl = build.getString("logUrl");

        String log = client.GET(logUrl).getContentAsString();
        JSONObject buildResource = new JSONObject(client.GET(url).getContentAsString());
        BuildState status = BuildState.valueOf(buildResource.getString("status"));

        assertThat(status, equalTo(BuildState.SUCCESS));
        assertThat(log, containsString("BUILD SUCCESS"));

        // Make sure getting it after completion still works
        assertThat(client.GET(logUrl).getContentAsString(), containsString("BUILD SUCCESS"));
    }

    private ContentResponse createBuild(AppRepo appRepo) throws InterruptedException, ExecutionException, TimeoutException {
        Fields fields = new Fields();
        fields.add("gitUrl", appRepo.gitUrl());
        return client.FORM(app.uri().resolve("/restabuild/api/v1/builds"), fields);
    }

    @Test
    public void ifNoBuildScriptsThenAnErrorIsReturned() throws InterruptedException, ExecutionException, TimeoutException {
        AppRepo appRepo = AppRepo.create("no-build-script");
        JSONObject build = new JSONObject(createBuild(appRepo).getContentAsString());
        String url = build.getString("url");
        String logUrl = build.getString("logUrl");
        String log = client.GET(logUrl).getContentAsString();

        JSONObject buildResource = new JSONObject(client.GET(url).getContentAsString());
        BuildState status = BuildState.valueOf(buildResource.getString("status"));

        assertThat(status, equalTo(BuildState.FAILURE));

        assertThat(log, Matchers.anyOf(
            containsString("Please place a file called build.bat in the root of your repo"),
            containsString("Please place a file called build.sh in the root of your repo")
        ));

    }

}
