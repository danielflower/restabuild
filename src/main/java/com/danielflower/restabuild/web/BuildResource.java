package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import com.danielflower.restabuild.build.BuildDatabase;
import com.danielflower.restabuild.build.BuildQueue;
import com.danielflower.restabuild.build.BuildResult;
import com.danielflower.restabuild.build.GitRepo;
import io.muserver.HeaderNames;
import io.muserver.HeaderValues;
import io.muserver.MuResponse;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.util.Optional;
import java.util.stream.Collectors;

@Path("/restabuild/api/v1/builds")
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
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response create(@FormParam("gitUrl") String gitUrl,
                           @FormParam("responseType") @DefaultValue("json") String responseType,
                           @Context UriInfo uriInfo) {
        BuildResult result = createInternal(gitUrl);

        UriBuilder buildPath = uriInfo.getRequestUriBuilder().path(result.id);
        Response.ResponseBuilder responseBuilder;
        if (responseType.equals("json")) {
            responseBuilder = Response.created(buildPath.build())
                .header("Content-Type", MediaType.APPLICATION_JSON)
                .entity(jsonForResult(buildPath, result).toString(4));
        } else {
            responseBuilder = Response.seeOther(uriInfo.getRequestUriBuilder().path(result.id).path("log").build());
        }
        return responseBuilder
            .header(HeaderNames.CACHE_CONTROL.toString(), HeaderValues.NO_CACHE)
            .build();

    }

    private BuildResult createInternal(String gitUrl) {
        if (gitUrl == null || gitUrl.isEmpty()) {
            throw new BadRequestException("A form parameter named gitUrl must point to a valid git repo");
        }
        GitRepo gitRepo = new GitRepo(gitUrl);
        BuildResult result = new BuildResult(fileSandbox, gitRepo);
        database.save(result);
        buildQueue.enqueue(result);
        return result;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getAll(@Context UriInfo uriInfo) {
        UriBuilder uriBuilder = uriInfo.getRequestUriBuilder();
        JSONObject result = new JSONObject()
            .put("builds", new JSONArray(database.all().stream().map(br -> jsonForResult(uriBuilder.path(br.id), br)).collect(Collectors.toList())));
        return result.toString(4);
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@PathParam("id") String id, @Context UriInfo uriInfo) {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            return jsonForResult(uriInfo.getRequestUriBuilder(), br.get()).toString(4);
        } else {
            throw new NotFoundException();
        }
    }

    private static JSONObject jsonForResult(UriBuilder resourcePath, BuildResult result) {
        return result.toJson()
            .put("url", resourcePath.build())
            .put("logUrl", resourcePath.path("log").build().toString());
    }

    @GET
    @Path("{id}/log")
    @Produces(MediaType.TEXT_PLAIN)
    public void getLog(@PathParam("id") String id, @Context MuResponse resp, @Context UriInfo uriInfo) throws IOException {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            BuildResult result = br.get();
            resp.contentType(MediaType.TEXT_PLAIN);
            JSONObject jsonObject = jsonForResult(uriInfo.getRequestUriBuilder().replacePath(uriInfo.getPath().replace("/log", "")), result);
            String header = jsonObject.toString(4) + "\n\n";
            if (result.hasFinished()) {
                resp.write(header + result.log());
            } else {

                resp.sendChunk(header);

                // HACK forces some buffer somewhere to trip and so the browser renders immediately
                resp.sendChunk("*...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*" +
                    "...................................................................................................*\n\n");

                result.streamLog(resp::sendChunk);
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
