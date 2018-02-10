package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import com.danielflower.restabuild.build.BuildDatabase;
import com.danielflower.restabuild.build.BuildQueue;
import com.danielflower.restabuild.build.BuildResult;
import com.danielflower.restabuild.build.GitRepo;
import io.muserver.HeaderNames;
import io.muserver.HeaderValues;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.util.Optional;

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
                .entity(getJson(result.id, buildPath));
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
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@PathParam("id") String id, @Context UriInfo uriInfo) {
        return getJson(id, uriInfo.getRequestUriBuilder());
    }

    private String getJson(String id, UriBuilder requestUriBuilder) {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            return br.get().toJson()
                .put("url", requestUriBuilder.build())
                .put("logUrl", requestUriBuilder.path("log").build().toString())
                .toString(4);
        } else {
            throw new NotFoundException();
        }
    }

    @GET
    @Path("{id}/log")
    @Produces(MediaType.TEXT_PLAIN)
    public String getLog(@PathParam("id") String id) throws IOException {
        Optional<BuildResult> br = database.get(id);
        if (br.isPresent()) {
            String log = br.get().log();
            System.out.println("log = " + log);
            return log;
        } else {
            throw new NotFoundException();
        }
    }


}
