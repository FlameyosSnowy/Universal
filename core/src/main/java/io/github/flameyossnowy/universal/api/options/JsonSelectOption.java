package io.github.flameyossnowy.universal.api.options;

/**
 * Option for selecting entities based on JSON field values using JSONPath expressions.
 *
 * <p>This record enables filtering entities by JSON document fields stored in
 * JSON columns or fields. The jsonPath follows JSONPath syntax (e.g., {@code $.email},
 * {@code $.profile.name}, {@code $[0].id}).
 *
 * <h3>Security</h3>
 * <ul>
 *   <li>JSONPath expressions should be validated to prevent injection attacks.</li>
 *   <li>Only allow JSONPath expressions from trusted sources or sanitize them.</li>
 * </ul>
 *
 * <h3>Performance</h3>
 * <ul>
 *   <li>JSON queries can be expensive; ensure proper indexes on JSON columns.</li>
 *   <li>Consider materializing frequently accessed JSON fields as separate columns.</li>
 *   <li>Avoid deep JSONPath expressions on large documents.</li>
 * </ul>
 *
 * <h3>Concurrency</h3>
 * <ul>
 *   <li>This record is immutable and thread-safe.</li>
 * </ul>
 *
 * @param field the entity field name (NOT column name) containing JSON data
 * @param jsonPath the JSONPath expression (e.g., {@code $.email}, {@code $.profile.name})
 * @param operator the comparison operator (e.g., {@code =}, {@code !=}, {@code @>}); validated by adapter
 * @param value the value to compare against; type must match the expected JSON field type
 */
public record JsonSelectOption(
    String field,        // entity field name (NOT column name)
    String jsonPath,     // $.email, $.profile.name, etc
    String operator,     // =, !=, @>, etc (adapter validated)
    Object value
) implements FilterOption {}
