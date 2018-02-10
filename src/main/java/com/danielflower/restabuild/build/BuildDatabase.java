package com.danielflower.restabuild.build;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class BuildDatabase {

    private final ConcurrentHashMap<String, BuildResult> db = new ConcurrentHashMap<>();

    public void save(BuildResult br) {
        db.put(br.id, br);
    }

    public Collection<BuildResult> all() {
        return db.values();
    }

    public Optional<BuildResult> get(String id) {
        return Optional.ofNullable(db.get(id));
    }

}
