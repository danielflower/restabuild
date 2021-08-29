package com.danielflower.restabuild.build;

import org.eclipse.jgit.transport.URIish;

public class RepoBranch {
    public final URIish url;
    public final String branch;

    public RepoBranch(URIish url, String branch) {
        this.url = url;
        this.branch = branch;
    }
}
