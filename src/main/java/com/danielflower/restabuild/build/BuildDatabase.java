package com.danielflower.restabuild.build;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BuildDatabase {

    private final ConcurrentHashMap<String, BuildResult> db = new ConcurrentHashMap<>();

    public void save(BuildResult br) {
        db.put(br.id, br);
    }

    public Optional<BuildResult> get(String id) {
        return Optional.of(db.get(id));
    }

}
