package com.danielflower.restabuild.build;

public class BuildResult {
    public final boolean success;
    public final String errorMessage;


    private BuildResult(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }
    public static BuildResult success() {
        return new BuildResult(true, null);
    }

    public static BuildResult failure(String errorMessage) {
        return new BuildResult(false, errorMessage);
    }
}
