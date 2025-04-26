package io.github.flameyossnowy.universal.api;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.reflect.FieldData;
import io.github.flameyossnowy.universal.api.reflect.RepositoryInformation;
import io.github.flameyossnowy.universal.api.reflect.RepositoryMetadata;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record IndexOptions(IndexType type, List<FieldData<?>> fields, String indexName) {
    public String getJoinedFields() {
        StringJoiner joiner = new StringJoiner(", ");
        for (FieldData<?> field : fields) {
            joiner.add(field.name());
        }
        return joiner.toString();
    }

    @Contract("_ -> new")
    public static @NotNull Builder builder(Class<?> repositoryType) {
        return new Builder(repositoryType);
    }

    public static class Builder {
        private IndexType type = IndexType.NORMAL;
        private final List<FieldData<?>> fields = new ArrayList<>(1);

        private RepositoryInformation information;
        private final Class<?> repositoryType;

        private String indexName;

        private RepositoryInformation getInformation() {
            return this.information == null ? RepositoryMetadata.getMetadata(repositoryType) : this.information;
        }

        Builder(final Class<?> repositoryType) {
            this.repositoryType = repositoryType;
        }

        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        public Builder type(IndexType type) {
            this.type = type;
            return this;
        }

        public Builder field(FieldData<?> field) {
            Objects.requireNonNull(field, "Field cannot be null");
            this.fields.add(field);
            return this;
        }

        public Builder fields(FieldData<?>... fields) {
            Objects.requireNonNull(fields, "Fields cannot be null");
            if (fields.length == 0) return this;

            Collections.addAll(this.fields, fields);
            return this;
        }

        public Builder fields(Collection<FieldData<?>> fields) {
            Objects.requireNonNull(fields, "Fields cannot be null");
            if (fields.isEmpty()) return this;

            this.fields.addAll(fields);
            return this;
        }

        public Builder field(String name) {
            return this.field(getInformation().getField(name));
        }

        public Builder fields(String... names) {
            return this.fields(Arrays.stream(names)
                    .map(getInformation()::getField)
                    .toArray(FieldData<?>[]::new));
        }

        public Builder rawFields(@NotNull Collection<String> names) {
            return this.fields(names.stream()
                    .map(getInformation()::getField)
                    .toArray(FieldData<?>[]::new));
        }

        public IndexOptions build() {
            return new IndexOptions(type, fields, indexName);
        }
    }
}
