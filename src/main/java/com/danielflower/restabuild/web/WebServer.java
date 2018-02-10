package com.danielflower.restabuild.web;

import com.danielflower.restabuild.FileSandbox;
import io.muserver.MuHandler;
import io.muserver.MuRequest;
import io.muserver.MuResponse;
import io.muserver.MuServer;
import io.muserver.rest.RestHandlerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.muserver.MuServerBuilder.muServer;

public class WebServer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private int port;
    private MuServer jettyServer;
    private final FileSandbox fileSandbox;

    public WebServer(int port, FileSandbox fileSandbox) {
        this.port = port;
        this.fileSandbox = fileSandbox;
    }

    public void start() throws Exception {
        jettyServer = muServer()
            .withHttpConnection(port)
            .addHandler((request, response) -> {
                log.info(response.toString());
                return false;
            })
            .addHandler(new CORSFilter())
            .addHandler(RestHandlerBuilder.create(new BuildResource(fileSandbox)))
            .start();


        log.info("Started web server at " + jettyServer.uri());
        log.info("POST to " + jettyServer.uri() + "/v1/builds?gitUrl={url} to run a build");
    }

    private static class CORSFilter implements MuHandler {
        @Override
        public boolean handle(MuRequest request, MuResponse response) throws Exception {
            response.headers().add("Access-Control-Allow-Origin", "*");
            response.headers().add("Access-Control-Allow-Headers", "origin, content-type, accept, authorization");
            response.headers().add("Access-Control-Allow-Credentials", "true");
            response.headers().add("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
            return false;
        }
    }

    public void close() throws Exception {
        jettyServer.stop();
    }
}
