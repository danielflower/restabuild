package com.danielflower.restabuild.build;

public interface BuildProcessListener {

    void onStatusChanged(BuildProcess buildProcess, BuildStatus oldStatus, BuildStatus newStatus) throws Exception;

}
