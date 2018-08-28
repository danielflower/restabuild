package com.danielflower.restabuild.build;

public class GitRepo {
    public final String url;
    public final String branch;

    public GitRepo(String url, String branch) {
        this.url = url;
        this.branch = branch;
    }
}
