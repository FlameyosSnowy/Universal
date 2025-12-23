package io.github.flameyossnowy.universal.microservices.file.indexes;

import java.nio.file.Path;

public final class IndexPathStrategies {

    private IndexPathStrategies() {}

    /** basePath/index */
    public static IndexPathStrategy underBase() {
        return (base, entity) -> base.resolve("index");
    }

    /** basePath/../indexes/<EntityName> */
    public static IndexPathStrategy siblingGlobal() {
        return (base, entity) ->
                base.getParent()
                    .resolve("indexes")
                    .resolve(entity.getSimpleName());
    }

    /** Fully custom root */
    public static IndexPathStrategy fixed(Path root) {
        return (base, entity) ->
                root.resolve(entity.getSimpleName());
    }
}
