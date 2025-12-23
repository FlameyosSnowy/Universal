package io.github.flameyossnowy.universal.api.handler;

import io.github.flameyossnowy.universal.api.params.DatabaseParameters;
import io.github.flameyossnowy.universal.api.result.DatabaseResult;

public interface PrimitiveHandler {
    void set(DatabaseParameters params, String index, Object value);

    Object get(DatabaseResult rs, String index) throws Exception;
}