package io.github.flameyossnowy.universal.api.resolver;

import org.jetbrains.annotations.Nullable;

public record SqlTypeMapping(
    String visual,
    @Nullable String binary
) {
    public String resolve(SqlEncoding encoding) {
        if (encoding == SqlEncoding.BINARY && binary != null) {
            return binary;
        }
        return visual;
    }

    public static SqlTypeMapping of(String visual) {
        return new SqlTypeMapping(visual, null);
    }

    public static SqlTypeMapping of(String visual, String binary) {
        return new SqlTypeMapping(visual, binary);
    }
}
