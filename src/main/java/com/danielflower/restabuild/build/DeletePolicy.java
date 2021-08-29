package com.danielflower.restabuild.build;

public enum DeletePolicy {

    ALWAYS, NEVER, ON_SUCCESS;

    public boolean shouldDelete(BuildStatus buildStatus) {
        return this == ALWAYS || (this == ON_SUCCESS && buildStatus == BuildStatus.SUCCESS);
    }

}
