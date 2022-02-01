package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import com.danielflower.restabuild.build.BuildDatabase;
import com.danielflower.restabuild.build.BuildQueue;
import com.danielflower.restabuild.build.BuildResult;
import com.danielflower.restabuild.build.RepoBranch;
import io.muserver.ContentTypes;
import io.muserver.HeaderNames;
import io.muserver.HeaderValues;
import io.muserver.MuResponse;
import io.muserver.rest.ApiResponse;
import io.muserver.rest.Description;
import io.muserver.rest.ResponseHeader;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

@Path("api/v1/builds")
@Description("Builds")
public class BuildResource {

    /**
     * A bunch of whitespace to force some browsers to render small streamed responses immediately
     * (some browsers will not render text until a certain tipping point it reached apparently to detect
     * things like character encoding, even though encoding is explicitly set).
     */
    private final String bufferBuster = " ".repeat(1024);
    private final FileSandbox fileSandbox;
    private final BuildDatabase database;
    private final BuildQueue buildQueue;
    private final ExecutorService executorService;

    public BuildResource(FileSandbox fileSandbox, BuildDatabase database, BuildQueue buildQueue, ExecutorService executorService) {
        this.fileSandbox = fileSandbox;
        this.buildQueue = buildQueue;
        this.database = database;
        this.executorService = executorService;
    }

    @POST
    @Consumes({MediaType.APPLICATION_FORM_URLENCODED, "*/*"})
    @Produces({MediaType.APPLICATION_JSON, "*/*"})
    @Description(value = "Submits a git URL to be built", details = "Note that the response will contain a JSON document" +
        " describing the build, and also a redirect to redirect to the log file. If consuming as an API, ignore the" +
        " redirect and parse the body as JSON.")
    @ApiResponse(code = "303", message = "Build successfully queued. Check the build's URL to track status.",
        contentType = "application/json",
        responseHeaders = {@ResponseHeader(name = "Location", description = "The URL of the Log output for the build"),
            @ResponseHeader(name = "Build-URL", description = "The URL of the build resource. Query this to find the build status etc.")})
    @ApiResponse(code = "400", message = "No gitUrl form parameter was specified.", contentType = "text/plain")
    public Response create(@FormParam("gitUrl") @Description(value = "The URL of a git repo that includes a `build.sh` or `build.bat` file. " +
        "It can be any type of Git URL (e.g. SSH or HTTPS) that the server has permission for.", example = "https://github.com/3redronin/mu-server-sample.git") String gitUrl,
                           @DefaultValue("master") @FormParam("branch") @Description(value = "The value of the git branch. This parameter is optional.") String branch,
                           @FormParam("buildParam") @Description(value = "The parameter for the `build.sh` or `build.bat` file. This parameter is optional.") String buildParam,
                           @Context UriInfo uriInfo) throws IOException {
        BuildResult result = createInternal(gitUrl, branch, buildParam, uriInfo);
        UriBuilder buildPath = uriInfo.getRequestUriBuilder().path(result.id);
        return Response.seeOther(uriInfo.getRequestUriBuilder().path(result.id).path("log").build())
            .cacheControl(CacheControl.valueOf("no-cache"))
            .header("Content-Type", MediaType.APPLICATION_JSON)
            .header("Build-URL", buildPath.build())
            .entity(jsonForResult(buildPath, result).toString(4))
            .build();
    }

    private BuildResult createInternal(String gitUrl, String branch, String buildParam, UriInfo uriInfo) throws IOException {
        URIish gitURIish = validateGitUrl(gitUrl);

        String gitBranch = branch;
        if (null == branch || branch.trim().isEmpty()) {
            gitBranch = "master";
        }

        RepoBranch repoBranch = new RepoBranch(gitURIish, gitBranch);
        String id = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> environment = getEnrichedEnvironment(id, uriInfo);
        BuildResult result = new BuildResult(fileSandbox, repoBranch, buildParam, id, environment, executorService);
        database.save(result);
        buildQueue.enqueue(result);
        return result;
    }

    @NotNull
    private URIish validateGitUrl(String gitUrl) {
        if (gitUrl == null || gitUrl.isEmpty()) {
            throw new BadRequestException("A form parameter named gitUrl must point to a valid git repo");
        }
        URIish gitURIish;
        try {
            gitURIish = new URIish(gitUrl);
        } catch (URISyntaxException e) {
            throw new BadRequestException("An invalid Git URL was specified: " + e.getMessage());
        }
        return gitURIish;
    }

    private Map<String, String> getEnrichedEnvironment(String buildId, UriInfo uriInfo) {
        String logUrl = uriInfo.getRequestUriBuilder().path(buildId).path("log").build().toString();
        Map<String, String> envMap = new HashMap<>(System.getenv());
        envMap.put("RESTABUILD_ID", buildId);
        envMap.put("RESTABUILD_LOG_URL", logUrl);
        return envMap;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Gets all the builds that have been submitted ordered by newest to oldest")
    public Response getAll(@Context UriInfo uriInfo,
                         @Description("The number to skip") @QueryParam("skip") @DefaultValue("0") long skip,
                         @Description("The maximum number to return") @QueryParam("limit") @DefaultValue("100") long limit) {
        JSONObject result = new JSONObject()
            .put("builds", new JSONArray(
                database.all().stream()
                    .sorted((o1, o2) -> (int) (o2.queueStart - o1.queueStart))
                    .skip(skip)
                    .limit(limit)
                    .map(br -> jsonForResult(uriInfo.getRequestUriBuilder().path(br.id), br))
                    .collect(Collectors.toList()))
            );
        return Response.ok(result.toString(4))
            .cacheControl(CacheControl.valueOf("no-cache"))
            .build();
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Gets the build information for a specific build")
    @ApiResponse(code = "200", message = "Success")
    @ApiResponse(code = "404", message = "No build with that ID exists", contentType = "text/plain")
    public Response get(@PathParam("id") @Description("The generated build ID which is returned when a new build is posted")
                          String id, @Context UriInfo uriInfo) {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            return Response.ok(jsonForResult(uriInfo.getRequestUriBuilder(), br.get()).toString(4))
                .cacheControl(CacheControl.valueOf("no-cache"))
                .build();
        } else {
            throw new NotFoundException();
        }
    }

    @POST
    @Path("{id}/cancel")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Cancels an in-progress build")
    @ApiResponse(code = "200", message = "Success")
    @ApiResponse(code = "404", message = "No build with that ID exists", contentType = "text/plain")
    @ApiResponse(code = "409", message = "The build was not in a cancelable state", contentType = "text/plain")
    public Response cancel(@PathParam("id") @Description("The generated build ID which is returned when a new build is posted")
                             String id, @Context UriInfo uriInfo) throws InterruptedException {
        Optional<BuildResult> obr = database.get(id);
        if (obr.isPresent()) {
            BuildResult br = obr.get();
            try {
                buildQueue.cancel(br);
                UriBuilder buildPath = uriInfo.getRequestUriBuilder().replacePath(uriInfo.getAbsolutePath().getPath().replace("/cancel", ""));
                return Response.ok(jsonForResult(buildPath, br).toString(4))
                    .cacheControl(CacheControl.valueOf("no-cache"))
                    .build();
            } catch (IllegalStateException ise) {
                throw new ClientErrorException(ise.getMessage(), Response.Status.CONFLICT);
            }
        } else {
            throw new NotFoundException();
        }
    }

    private static JSONObject jsonForResult(UriBuilder resourcePath, BuildResult result) {
        JSONObject json = result.toJson()
            .put("url", resourcePath.replaceQuery(null).build())
            .put("logUrl", resourcePath.clone().path("log").replaceQuery(null).build());
        if (result.isCancellable()) {
            json.put("cancelUrl", resourcePath.clone().path("cancel").replaceQuery(null).build());
        }
        return json;
    }

    @GET
    @Path("{id}/log")
    @Produces("text/plain; charset=utf-8")
    @Description(value = "Gets the build log as plain text", details = "If the build is in progress then it will stream the response until it is complete")
    @ApiResponse(code = "200", message = "Success")
    @ApiResponse(code = "404", message = "No build with that ID exists")
    public void getLog(@PathParam("id") @Description("The generated build ID which is returned when a new build is posted")
                           String id, @Context MuResponse resp, @Context UriInfo uriInfo) throws IOException {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            BuildResult result = br.get();
            resp.contentType(ContentTypes.TEXT_PLAIN_UTF8);
            UriBuilder buildPath = uriInfo.getRequestUriBuilder().replacePath(uriInfo.getAbsolutePath().getPath().replace("/log", ""));
            JSONObject jsonObject = jsonForResult(buildPath, result);
            String header = jsonObject.toString(4) + "\n" + bufferBuster + "\n";
            if (result.hasFinished()) {
                resp.headers().set(HeaderNames.CACHE_CONTROL, "public, max-age=86400, immutable");
                resp.write(header + result.log());
            } else {
                resp.headers().set(HeaderNames.CACHE_CONTROL, HeaderValues.NO_CACHE);

                resp.sendChunk(header);

                result.streamLog(new BuildResult.StringListener() {
                    public void onString(String value) {
                        try {
                            resp.sendChunk(value);
                        } catch (Exception e) {
                            result.stopListening(this);
                        }
                    }
                });
                while (!result.hasFinished()) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            }
        } else {
            throw new NotFoundException();
        }
    }
}
