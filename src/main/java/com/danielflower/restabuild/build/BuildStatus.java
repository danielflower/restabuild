package com.danielflower.restabuild.build;

public enum BuildStatus {

    QUEUED(false), IN_PROGRESS(false), SUCCESS(true), FAILURE(true), CANCELLING(false), CANCELLED(true), TIMED_OUT(true);

    private final boolean endState;

    BuildStatus(boolean endState) {
        this.endState = endState;
    }

    public boolean endState() {
        return endState;
    }
    public boolean isCancellable() { return !endState && this != CANCELLING; }
}
