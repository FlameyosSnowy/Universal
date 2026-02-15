package io.github.flameyossnowy.universal.api.options;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for window function specifications.
 *
 * <pre>{@code
 * field("salary")
 *     .rowNumber()
 *     .partitionBy("departmentId")
 *     .orderBy("hireDate", ASC)
 *     .as("employee_rank")
 * }</pre>
 */
public class WindowFunctionBuilder<B extends Filterable> {
    private final B queryBuilder;
    private final String field;
    private final WindowFunctionType functionType;
    private final List<String> partitionBy = new ArrayList<>();
    private final List<SortOption> orderBy = new ArrayList<>();
    private String frameStart;
    private String frameEnd;
    private String alias;

    public WindowFunctionBuilder(B queryBuilder, String field, WindowFunctionType functionType) {
        this.queryBuilder = queryBuilder;
        this.field = field;
        this.functionType = functionType;
    }

    /**
     * Add partition by clause.
     *
     * <pre>{@code
     * .partitionBy("departmentId", "locationId")
     * }</pre>
     */
    public WindowFunctionBuilder<B> partitionBy(String... fields) {
        this.partitionBy.addAll(List.of(fields));
        return this;
    }

    /**
     * Add order by clause.
     *
     * <pre>{@code
     * .orderBy("salary", DESC)
     * }</pre>
     */
    public WindowFunctionBuilder<B> orderBy(String field, SortOrder direction) {
        this.orderBy.add(new SortOption(field, direction));
        return this;
    }

    /**
     * Define window frame (ROWS or RANGE).
     *
     * <pre>{@code
     * .frame("ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW")
     * }</pre>
     */
    public WindowFunctionBuilder<B> frame(String frameStart, String frameEnd) {
        this.frameStart = frameStart;
        this.frameEnd = frameEnd;
        return this;
    }

    /**
     * Set alias and complete the window function definition.
     */
    public WindowFieldDefinition as(String alias) {
        return new WindowFieldDefinition(
            field,
            functionType,
            partitionBy,
            orderBy,
            frameStart,
            frameEnd,
            alias
        );
    }

    // Common frame shortcuts
    public WindowFunctionBuilder<B> rowsBetweenUnboundedPrecedingAndCurrentRow() {
        return frame("UNBOUNDED PRECEDING", "CURRENT ROW");
    }

    public WindowFunctionBuilder<B> rowsBetweenCurrentRowAndUnboundedFollowing() {
        return frame("CURRENT ROW", "UNBOUNDED FOLLOWING");
    }
}