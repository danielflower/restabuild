package com.danielflower.restabuild.web;

import io.muserver.Method;
import io.muserver.MuServer;
import io.muserver.Mutils;
import io.muserver.rest.CORSConfigBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static io.muserver.ContextHandlerBuilder.context;
import static io.muserver.MuServerBuilder.muServer;
import static io.muserver.handlers.ResourceHandlerBuilder.fileOrClasspath;
import static io.muserver.openapi.InfoObjectBuilder.infoObject;
import static io.muserver.openapi.OpenAPIObjectBuilder.openAPIObject;
import static io.muserver.rest.RestHandlerBuilder.restHandler;

public class WebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    public MuServer server;

    private WebServer(MuServer server) {
        this.server = server;
    }

    public static WebServer start(int port, String context, BuildResource buildResource) throws IOException {
        boolean hasContext = !Mutils.nullOrEmpty(context);
        MuServer server = muServer()
            .withHttpPort(port)
            .addHandler((request, response) -> {
                log.info(request.toString());
                if (hasContext) {
                    if (request.uri().getPath().equals("/")) {
                        response.redirect("/" + context + "/");
                        return true;
                    }
                }
                return false;
            })
            .addHandler(
                context(context)
                    .addHandler(restHandler(buildResource)
                        .withCORS(CORSConfigBuilder.corsConfig().withAllOriginsAllowed())
                        .withOpenApiJsonUrl("/openapi.json")
                        .withOpenApiHtmlUrl("/api.html")
                        .withOpenApiDocument(
                            openAPIObject()
                            .withInfo(infoObject()
                                .withTitle("Restabuild API")
                                .withDescription("An API to queue and interact with builds.")
                                .build())
                        )
                    )
                    .addHandler(Method.GET, "/", new IndexHtmlHandler())
                    .addHandler(fileOrClasspath("src/main/resources/web", "/web")))
            .start();

        String serverUri = server.uri().resolve(hasContext ? "/" + context + "/" : "/").toString();
        System.setProperty("RESTABUILD_URI", serverUri);
        log.info("Started web server at {}", serverUri);
        return new WebServer(server);
    }


    public void close() {
        server.stop();
    }
}
