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
            return this.information == null ? (information = RepositoryMetadata.getMetadata(repositoryType)) : this.information;
        }

        Builder(final Class<?> repositoryType) {
            this.repositoryType = repositoryType;
        }

        /**
         * Sets the name of the index. If not specified, a unique name is generated.
         *
         * @param indexName the name of the index
         * @return this builder
         */
        public Builder indexName(String indexName) {
            this.indexName = indexName;
            return this;
        }

        /**
         * Sets the type of the index.
         *
         * <p>The default value is {@link IndexType#NORMAL}.
         *
         * @param type the type of the index
         * @return this builder
         */
        public Builder type(IndexType type) {
            this.type = type;
            return this;
        }

        /**
         * Adds a single field to the index.
         *
         * @param field the field to be added to the index
         * @return this builder
         * @throws NullPointerException if the given field is null
         */
        public Builder field(@NotNull FieldData<?> field) {
            Objects.requireNonNull(field, "Field cannot be null");
            this.fields.add(field);
            return this;
        }

        /**
         * Adds multiple fields to the index.
         *
         * @param fields the fields to be added to the index
         * @return this builder
         * @throws NullPointerException if the given fields array is null
         */
        public Builder fields(@NotNull FieldData<?>... fields) {
            Objects.requireNonNull(fields, "Fields cannot be null");
            if (fields.length == 0) {
                throw new IllegalArgumentException("Fields cannot be empty");
            }

            Collections.addAll(this.fields, fields);
            return this;
        }

        /**
         * Adds multiple fields to the index.
         *
         * @param fields the fields to be added to the index
         * @return this builder
         * @throws NullPointerException if the given fields array is null
         */
        public Builder fields(Collection<FieldData<?>> fields) {
            Objects.requireNonNull(fields, "Fields cannot be null");
            if (fields.isEmpty()) {
                throw new IllegalArgumentException("Fields cannot be empty");
            }

            this.fields.addAll(fields);
            return this;
        }

        /**
         * Adds one field to the index.
         *
         * @param name the name of the field
         * @return this builder
         * @throws NullPointerException if the given fields array is null
         */
        public Builder field(String name) {
            return this.field(getInformation().getField(name));
        }

        /**
         * Adds multiple field to the index.
         *
         * @param names the name of the fields
         * @return this builder
         * @throws NullPointerException if the given fields array is null
         */
        public Builder fields(String... names) {
            return this.fields(Arrays.stream(names)
                    .map(getInformation()::getField)
                    .toArray(FieldData<?>[]::new));
        }

        /**
         * Adds multiple field to the index.
         *
         * @param names the name of the fields
         * @return this builder
         * @throws NullPointerException if the given fields array is null
         */
        public Builder rawFields(@NotNull Collection<String> names) {
            return this.fields(names.stream()
                    .map(getInformation()::getField)
                    .toArray(FieldData<?>[]::new));
        }

        /**
         * Builds an instance of IndexOptions with the current options.
         *
         * @return an instance of IndexOptions
         */
        public IndexOptions build() {
            return new IndexOptions(type, fields, indexName);
        }
    }
}
