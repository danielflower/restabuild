package com.danielflower.restabuild;

import com.danielflower.restabuild.build.BuildStatus;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.util.Fields;
import org.hamcrest.Matchers;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import scaffolding.AppRepo;
import scaffolding.RestClient;

import java.net.URI;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static scaffolding.AssertUtil.assertEventually;

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

        String id = build.getString("id");
        assertThat(id.isEmpty(), is(false));
        String logUrl = build.getString("logUrl");
        assertThat(logUrl, equalTo("http://localhost:8080/restabuild/api/v1/builds/" + id + "/log"));

        assertThat(build.getString("url"),
            equalTo("http://localhost:8080/restabuild/api/v1/builds/" + build.getString("id")));
        waitForBuildToFinish(build);

        JSONObject afterBuild = new JSONObject(client.GET(build.getString("url")).getContentAsString());
        assertThat(afterBuild.has("commitIDBeforeBuild"), is(true));
        assertThat(afterBuild.getString("commitIDBeforeBuild"),
            equalTo(afterBuild.getString("commitIDAfterBuild")));
        assertThat(afterBuild.getJSONArray("tagsCreated").get(0), is("my-maven-app-1.0.0"));
    }

    private JSONObject waitForBuildToFinish(JSONObject build) throws InterruptedException, ExecutionException, TimeoutException {
        String url = build.getString("url");

        int attempts = 0;
        JSONObject buildResource;
        while (true) {
            buildResource = new JSONObject(client.GET(url).getContentAsString());
            BuildStatus status = BuildStatus.valueOf(buildResource.getString("status"));

            assertThat(buildResource.toString(), status, not(equalTo(BuildStatus.FAILURE)));

            if (status == BuildStatus.SUCCESS) {
                break;
            }
            if (attempts > 2000) {
                Assert.fail("Build never finished");
            }

            Thread.sleep(500);
            attempts++;
        }
        return buildResource;
    }

    @Test
    public void buildLogsAreAvailableInTheAPI() throws Exception {
        AppRepo appRepo = AppRepo.create("maven");
        JSONObject build1 = new JSONObject(createBuild(appRepo).getContentAsString());
        build1 = waitForBuildToFinish(build1);
        JSONObject build2 = new JSONObject(createBuild(appRepo).getContentAsString());
        build2 = waitForBuildToFinish(build2);

        JSONObject api = new JSONObject(
            client.GET("http://localhost:8080/restabuild/api/v1/builds").getContentAsString()
        );

        JSONArray builds = api.getJSONArray("builds");
        assertThat(builds.length(), greaterThanOrEqualTo(2));
        assertBuildSameIgnoringProcessTree(build1, (JSONObject) builds.get(1));
        assertBuildSameIgnoringProcessTree(build2, (JSONObject) builds.get(0));
    }

    private void assertBuildSameIgnoringProcessTree(JSONObject one, JSONObject two) {
        var copy1 = new JSONObject(one.toString());
        copy1.remove("processTress");
        copy1.remove("cancelUrl");
        var copy2 = new JSONObject(two.toString());
        copy2.remove("processTress");
        copy2.remove("cancelUrl");
        assertThat(copy1.toString(4), equalTo(copy2.toString(4)));
    }

    @Test
    public void gettingTheLogBlocksUntilItIsComplete() throws Exception {
        AppRepo appRepo = AppRepo.create("maven");

        JSONObject build = new JSONObject(createBuild(appRepo).getContentAsString());

        String url = build.getString("url");
        String logUrl = build.getString("logUrl");

        String log = client.GET(logUrl).getContentAsString();
        JSONObject buildResource = new JSONObject(client.GET(url).getContentAsString());
        BuildStatus status = BuildStatus.valueOf(buildResource.getString("status"));

        assertThat(status, equalTo(BuildStatus.SUCCESS));
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
        BuildStatus status = BuildStatus.valueOf(buildResource.getString("status"));

        assertThat(status, equalTo(BuildStatus.FAILURE));

        assertThat(log, Matchers.anyOf(
            containsString("Please place a file called build.bat in the root of your repo"),
            containsString("Please place a file called build.sh in the root of your repo")
        ));

    }

    @Test
    public void buildIdAndLogUrlAreAvailableInEnvVars() throws Exception {
        AppRepo appRepo = AppRepo.create("env-vars");
        JSONObject build = new JSONObject(createBuild(appRepo).getContentAsString());
        String id = build.getString("id");
        String logUrl = build.getString("logUrl");
        String log = client.GET(logUrl).getContentAsString();

        assertThat(log, Matchers.allOf(
            containsString("Build ID: " + id),
            containsString(String.format("Build Log URL: http://localhost:8080/restabuild/api/v1/builds/%s/log", id))
        ));
    }

    @Test
    public void canCancelBuilds() throws Exception {
        AppRepo appRepo = AppRepo.create("hung-build");
        JSONObject build = new JSONObject(createBuild(appRepo).getContentAsString());
        URI resourceUrl = URI.create(build.getString("url"));
        URI cancelUrl = URI.create(build.getString("cancelUrl"));

        assertEventually(() -> new JSONObject(client.GET(resourceUrl).getContentAsString()).getString("status"), equalTo("IN_PROGRESS"));
        System.out.println("cancelUrl = " + cancelUrl);

        assertEventually(() -> new JSONObject(client.GET(resourceUrl).getContentAsString()).toString(4), containsString("processTree"));

        System.out.println("client.GET(resourceUrl).getContentAsString() = " + client.GET(resourceUrl).getContentAsString());

        Thread.sleep(500);
        ContentResponse cancelResp = client.POST(cancelUrl).send();
        System.out.println("cancelResp = " + cancelResp);
        assertThat(cancelResp.getStatus(), is(200));
        assertThat(new JSONObject(cancelResp.getContentAsString()).getString(("url")), equalTo(resourceUrl.toString()));


        assertEventually(() -> new JSONObject(client.GET(build.getString("url")).getContentAsString()).getString("status"), equalTo("CANCELLED"));
    }
}
