package com.danielflower.restabuild.build;

public class RestaBuildException extends RuntimeException {
    public RestaBuildException(String message) {
        super(message);
    }

    public RestaBuildException(String message, Throwable cause) {
        super(message, cause);
    }

    public RestaBuildException(Throwable cause) {
        super(cause);
    }
}
