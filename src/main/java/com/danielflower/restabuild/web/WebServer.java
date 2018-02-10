package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import io.muserver.*;
import io.muserver.rest.RestHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.muserver.MuServerBuilder.muServer;

public class WebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private MuServer server;

    private WebServer(MuServer server) {
        this.server = server;
    }

    public static WebServer start(int port, FileSandbox fileSandbox) {
        MuServer server = muServer()
            .withHttpConnection(port)
            .addHandler((request, response) -> {
                log.info(response.toString());
                return false;
            })
            .addHandler(new CORSFilter())
            .addHandler(RestHandlerBuilder.create(new BuildResource(fileSandbox)))
            .start();

        log.info("Started web server at " + server.uri());
        log.info("POST to " + server.uri() + "/v1/builds?gitUrl={url} to run a build");
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
