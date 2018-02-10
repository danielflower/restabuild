package com.danielflower.restabuild.web;

import io.muserver.*;
import io.muserver.handlers.ResourceHandler;
import io.muserver.rest.RestHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.muserver.MuServerBuilder.muServer;

public class WebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    public MuServer server;

    private WebServer(MuServer server) {
        this.server = server;
    }

    public static WebServer start(int port, BuildResource buildResource) {
        MuServer server = muServer()
            .withHttpConnection(port)
            .addHandler((request, response) -> {
                log.info(request.toString());
                if (request.uri().getPath().equals("/")) {
                    response.redirect("/restabuild/");
                    return true;
                }
                return false;
            })
            .addHandler(new CORSFilter())
            .addHandler(ResourceHandler
                .fileOrClasspath("src/main/resources/web", "/web")
                .withPathToServeFrom("/restabuild"))
            .addHandler(RestHandlerBuilder.create(buildResource))
            .start();

        log.info("Started web server at " + server.uri());
        return new WebServer(server);
    }

    private static class CORSFilter implements MuHandler {
        @Override
        public boolean handle(MuRequest request, MuResponse response) {
            Headers headers = response.headers();
            headers.add("Access-Control-Allow-Origin", "*");
            headers.add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
            headers.add("Access-Control-Allow-Credentials", "true");
            headers.add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
            return false;
        }
    }

    public void close() {
        server.stop();
    }
}
