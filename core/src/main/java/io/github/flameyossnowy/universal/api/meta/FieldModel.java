package io.github.flameyossnowy.universal.api.meta;

import io.github.flameyossnowy.universal.api.annotations.Condition;
import io.github.flameyossnowy.universal.api.annotations.OnDelete;
import io.github.flameyossnowy.universal.api.annotations.OnUpdate;
import io.github.flameyossnowy.universal.api.annotations.enums.Consistency;
import io.github.flameyossnowy.universal.api.json.JsonCodec;

import java.util.List;

/**
 * Represents metadata about a field in an entity.
 * Generic type T represents the entity type this field belongs to.
 */
public interface FieldModel<T> {
    String name();

    String columnName();

    Class<?> type();

    boolean id();

    boolean autoIncrement();

    boolean nullable();

    boolean relationship();

    RelationshipKind relationshipKind();

    Consistency consistency();

    String getterName();

    String setterName();

    boolean lazy();

    boolean insertable();

    boolean updatable();

    /**
     * Whether this field is annotated with @Now (auto-timestamp on insert/update)
     */
    boolean hasNowAnnotation();

    /**
     * Whether this field is annotated with @Binary (store as binary data)
     */
    boolean hasBinaryAnnotation();

    /**
     * Whether this field is annotated with @Unique
     */
    boolean hasUniqueAnnotation();

    /**
     * Default value for this field, or null if none specified
     */
    String defaultValue();

    /**
     * Default value provider class, or null if none specified
     */
    Class<?> defaultValueProvider();

    /**
     * Whether to store enums as ordinal instead of string
     */
    boolean enumAsOrdinal();

    /**
     * External repository reference, or null if not external
     */
    String externalRepository();

    /**
     * The @Condition annotation for this field, or null if not present.
     * Used for CHECK constraints in SQL.
     */
    Condition condition();

    /**
     * The @OnDelete annotation for this field, or null if not present.
     * Defines foreign key ON DELETE behavior.
     */
    OnDelete onDelete();

    /**
     * The @OnUpdate annotation for this field, or null if not present.
     * Defines foreign key ON UPDATE behavior.
     */
    OnUpdate onUpdate();

    /**
     * The qualified class name of the TypeResolver to use for this field,
     * or null if no custom resolver is specified via @ResolveWith.
     */
    String resolveWithClass();

    /**
     * Whether this field participates in the entity construction
     * (i.e., should be included in INSERT statements and constructors).
     */
    default boolean participatesInConstruction() {
        return !relationship() && insertable();
    }

    /**
     * Get the value of this field from the given entity.
     *
     * @param entity The entity to extract the value from
     * @return The field value
     * @throws RuntimeException if reflection fails
     */
    Object getValue(T entity);

    /**
     * Set the value of this field on the given entity.
     *
     * @param entity The entity to set the value on
     * @param value The value to set
     * @throws RuntimeException if reflection fails
     */
    void setValue(T entity, Object value);

    /**
     * If this field is a collection or generic type, this is the type of its element.
     */
    Class<?> elementType();

    /**
     * If this field is a Map, the key type.
     */
    Class<?> mapKeyType();

    /**
     * If this field is a Map, the value type.
     */
    Class<?> mapValueType();

    /**
     * Whether this field is indexed in the database.
     */
    boolean indexed();

    /**
     * Convenience inverse of indexed().
     */
    default boolean notIndexed() {
        return !indexed();
    }

    boolean isJson();

    JsonStorageKind jsonStorageKind(); // COLUMN or TABLE

    String jsonColumnDefinition(); // nullable

    Class<? extends JsonCodec<?>> jsonCodec();

    boolean jsonQueryable();

    boolean jsonPartialUpdate();

    List<JsonIndexModel> jsonIndexes();

    // ---- convenience ----

    default boolean isSerialized() {
        return isJson() || hasBinaryAnnotation();
    }
}