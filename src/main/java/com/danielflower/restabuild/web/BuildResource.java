package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import com.danielflower.restabuild.build.ProjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.*;

import static com.danielflower.restabuild.FileSandbox.dirPath;

@Path("/v1/builds")
public class BuildResource {
    private static final Logger log = LoggerFactory.getLogger(BuildResource.class);

    private final FileSandbox fileSandbox;

    public BuildResource(FileSandbox fileSandbox) {
        this.fileSandbox = fileSandbox;
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response build(@Context UriInfo uriInfo, @QueryParam("gitUrl") String gitUrl) throws IOException {
        StreamingOutput stream = new BuildStreamer(gitUrl, fileSandbox);
        return Response.ok(stream).build();
    }

    private static class BuildStreamer implements StreamingOutput {
        private final String gitUrl;
        private final FileSandbox fileSandbox;

        public BuildStreamer(String gitUrl, FileSandbox fileSandbox) {
            this.gitUrl = gitUrl;
            this.fileSandbox = fileSandbox;
        }

        @Override
        public void write(OutputStream output) throws IOException, WebApplicationException {
            try (PrintWriter writer = new PrintWriter(output)) {
                try {
                    ProjectManager manager = ProjectManager.create(gitUrl, fileSandbox);
                    doubleLog(writer, "Fetching from git...");
                    File buildDir = manager.pullFromGitAndCopyWorkingCopyToNewDir(writer);
                    doubleLog(writer, "Working copy is at " + dirPath(buildDir));
                    manager.build(writer);

                    doubleLog(writer, "Successfully built " + gitUrl);
                } catch (Exception e) {
                    log.error("Error while building " + gitUrl, e);
                    doubleLog(writer,"Error while building: " + e);
                }
            }
        }
    }

    private static void doubleLog(PrintWriter writer, String message) {
        writer.println(message);
        writer.flush();
    }

}
