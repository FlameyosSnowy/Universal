package io.github.flameyossnowy.universal.api.resolver;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class FileTypeResolver implements TypeResolver<File> {
    @Override
    public Class<File> getType() {
        return File.class;
    }

    @Override
    public Class<String> getDatabaseType() {
        return String.class;
    }

    @Override
    public @Nullable File resolve(DatabaseResult result, String columnName) {
        String path = result.get(columnName, String.class);
        return path != null ? new File(path) : null;
    }

    @Override
    public void insert(DatabaseParameters parameters, String index, File value) {
        parameters.set(index, value != null ? value.getPath() : null, String.class);
    }
}
