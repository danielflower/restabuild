package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import com.danielflower.restabuild.build.BuildDatabase;
import com.danielflower.restabuild.build.BuildQueue;
import com.danielflower.restabuild.build.BuildResult;
import com.danielflower.restabuild.build.GitRepo;
import io.muserver.ContentTypes;
import io.muserver.HeaderNames;
import io.muserver.HeaderValues;
import io.muserver.MuResponse;
import io.muserver.rest.ApiResponse;
import io.muserver.rest.Description;
import io.muserver.rest.ResponseHeader;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("api/v1/builds")
@Description("Builds")
public class BuildResource {

    private final FileSandbox fileSandbox;
    private final BuildQueue buildQueue;
    private final BuildDatabase database;

    public BuildResource(FileSandbox fileSandbox, BuildQueue buildQueue, BuildDatabase database) {
        this.fileSandbox = fileSandbox;
        this.buildQueue = buildQueue;
        this.database = database;
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
                           @FormParam("branch") @Description(value = "The value of the git branch. This parameter is optional.") String branch,
                           @FormParam("buildParam") @Description(value = "The parameter for the `build.sh` or `build.bat` file. This parameter is optional.") String buildParam,
                           @Context UriInfo uriInfo) {
        BuildResult result = createInternal(gitUrl, branch, buildParam, uriInfo);
        UriBuilder buildPath = uriInfo.getRequestUriBuilder().path(result.id);
        return Response.seeOther(uriInfo.getRequestUriBuilder().path(result.id).path("log").build())
            .header("Content-Type", MediaType.APPLICATION_JSON)
            .header("Build-URL", buildPath.build())
            .entity(jsonForResult(buildPath, result).toString(4))
            .header(HeaderNames.CACHE_CONTROL.toString(), HeaderValues.NO_CACHE)
            .build();
    }

    private BuildResult createInternal(String gitUrl, String branch, String buildParam, UriInfo uriInfo) {
        if (gitUrl == null || gitUrl.isEmpty()) {
            throw new BadRequestException("A form parameter named gitUrl must point to a valid git repo");
        }

        String gitBranch = StringUtils.trimToNull(branch);
        GitRepo gitRepo = new GitRepo(gitUrl, gitBranch);
        String id = UUID.randomUUID().toString().replace("-", "");
        Map<String, String> environment = getEnrichedEnvironment(id, uriInfo);
        BuildResult result = new BuildResult(fileSandbox, gitRepo, buildParam, id, environment);
        database.save(result);
        buildQueue.enqueue(result);
        return result;
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
    public String getAll(@Context UriInfo uriInfo,
                         @Description("The number to skip") @QueryParam("skip") @DefaultValue("0") long skip,
                         @Description("The maximum number to return") @QueryParam("limit") @DefaultValue("100") long limit) {
        JSONObject result = new JSONObject()
            .put("builds", new JSONArray(
                database.all().stream()
                    .sorted((o1, o2) -> (int)(o2.queueStart - o1.queueStart))
                    .skip(skip)
                    .limit(limit)
                    .map(br -> jsonForResult(uriInfo.getRequestUriBuilder().path(br.id), br))
                    .collect(Collectors.toList()))
            );
        return result.toString(4);
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Gets the build information for a specific build")
    @ApiResponse(code="200", message="Success")
    @ApiResponse(code="404", message="No build with that ID exists", contentType = "text/plain")
    public String get(@PathParam("id") @Description("The generated build ID which is returned when a new build is posted")
                              String id, @Context UriInfo uriInfo) {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            return jsonForResult(uriInfo.getRequestUriBuilder(), br.get()).toString(4);
        } else {
            throw new NotFoundException();
        }
    }

    private static JSONObject jsonForResult(UriBuilder resourcePath, BuildResult result) {
        return result.toJson()
            .put("url", resourcePath.replaceQuery(null).build())
            .put("logUrl", resourcePath.path("log").replaceQuery(null).build());
    }

    @GET
    @Path("{id}/log")
    @Produces("text/plain; charset=utf-8")
    @Description(value = "Gets the build log as plain text", details = "If the build is in progress then it will stream the response until it is complete")
    @ApiResponse(code="200", message="Success")
    @ApiResponse(code="404", message="No build with that ID exists")
    public void getLog(@PathParam("id") @Description("The generated build ID which is returned when a new build is posted")
                               String id, @Context MuResponse resp, @Context UriInfo uriInfo) throws IOException {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            BuildResult result = br.get();
            resp.contentType(ContentTypes.TEXT_PLAIN_UTF8);
            UriBuilder buildPath = uriInfo.getRequestUriBuilder().replacePath(uriInfo.getAbsolutePath().getPath().replace("/log", ""));
            JSONObject jsonObject = jsonForResult(buildPath, result);
            String header = jsonObject.toString(4) + "\n\n";
            if (result.hasFinished()) {
                resp.write(header + result.log());
            } else {

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
