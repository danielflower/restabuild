package com.danielflower.restabuild.web;

import com.danielflower.restabuild.build.ProjectManager;
import io.muserver.MuRequest;
import io.muserver.MuResponse;
import io.muserver.Mutils;
import io.muserver.RouteHandler;

import java.io.IOException;
import java.util.Map;

import static io.muserver.Mutils.coalesce;

public class IndexHtmlHandler implements RouteHandler {

    private final String template;

    public IndexHtmlHandler() throws IOException {

        String version = coalesce(getClass().getPackage().getImplementationVersion(), "dev");

        template = new String(Mutils.toByteArray(IndexHtmlHandler.class.getResourceAsStream("/web/index.html"), 8192), "UTF-8")
            .replace("{{buildfilename}}", ProjectManager.defaultBuildFile)
            .replace("{{restabuildversion}}", version);
    }

    @Override
    public void handle(MuRequest request, MuResponse response, Map<String, String> pathParams) throws Exception {
        response.contentType("text/html");
        response.write(template);
    }
}
