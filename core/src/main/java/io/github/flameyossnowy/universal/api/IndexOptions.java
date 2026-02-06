package io.github.flameyossnowy.universal.api;

import io.github.flameyossnowy.universal.api.annotations.enums.IndexType;
import io.github.flameyossnowy.universal.api.meta.FieldModel;
import io.github.flameyossnowy.universal.api.meta.GeneratedMetadata;
import io.github.flameyossnowy.universal.api.meta.RepositoryModel;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record IndexOptions(IndexType type, List<FieldModel<?>> fields, String indexName) {
    public String getJoinedFields() {
        StringJoiner joiner = new StringJoiner(", ");
        for (FieldModel<?> field : fields) {
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
        private final List<FieldModel<?>> fields = new ArrayList<>(1);

        private RepositoryModel<?, ?> information;
        private final Class<?> repositoryType;

        private String indexName;

        private RepositoryModel<?, ?> getInformation() {
            return this.information == null ? (information = GeneratedMetadata.getByEntityClass(repositoryType)) : this.information;
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
        public Builder field(@NotNull FieldModel<?> field) {
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
        public Builder fields(@NotNull FieldModel<?>... fields) {
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
        public Builder fields(Collection<FieldModel<?>> fields) {
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
            return this.field(getInformation().fieldByName(name));
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
                    .map(getInformation()::fieldByName)
                    .toArray(FieldModel<?>[]::new));
        }

        /**
         * Adds multiple field to the index.
         *
         * @param names the name of the fields
         * @return this builder
         * @throws NullPointerException if the given fields array is null
         */
        public Builder rawFields(@NotNull Collection<String> names) {
            List<FieldModel<?>> list = new ArrayList<>();
            RepositoryModel<?, ?> repositoryModel = getInformation();
            for (String name : names) {
                FieldModel<?> fieldModel = repositoryModel.fieldByName(name);
                list.add(fieldModel);
            }
            return this.fields(list.toArray(new FieldModel<?>[0]));
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
