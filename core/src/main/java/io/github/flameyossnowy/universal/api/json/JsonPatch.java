package io.github.flameyossnowy.universal.api.json;

public sealed interface JsonPatch
    permits JsonPatch.FullReplace, JsonPatch.Partial {

    record FullReplace(String json) implements JsonPatch {}
    record Partial(String patchExpression) implements JsonPatch {}
}
