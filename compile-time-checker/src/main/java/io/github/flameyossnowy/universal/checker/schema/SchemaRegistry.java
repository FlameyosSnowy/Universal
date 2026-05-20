package io.github.flameyossnowy.universal.checker.schema;

import io.github.flameyossnowy.universal.checker.RepositoryModel;

import java.util.*;

public final class SchemaRegistry {

    private final Map<String, RepositorySchema> repositories = new HashMap<>(32);

    public void register(RepositoryModel model) {
        repositories.put(
            model.tableName(),
            RepositorySchema.from(model)
        );
    }

    public RepositorySchema get(String repoName) {
        return repositories.get(repoName);
    }

    public Collection<RepositorySchema> all() {
        return repositories.values();
    }

    public boolean exists(String repoName) {
        return repositories.containsKey(repoName);
    }
}