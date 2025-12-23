package io.github.flameyossnowy.universal.api.annotations;

import io.github.flameyossnowy.universal.api.annotations.enums.CompressionType;
import io.github.flameyossnowy.universal.api.annotations.enums.FileFormat;

import java.lang.annotation.*;

/**
 * Marks a repository as file-based storage (e.g., JSON, CSV, compressed files).
 * <p>
 * This annotation allows you to store entities in files with various formats
 * and compression options.
 * <p>
 * Example:
 * <pre>
 * {@code
 * @Repository(name = "file-cache")
 * @FileRepository(path = "/data/cache", format = FileFormat.JSON, compressed = true)
 * record CachedData(@PrimaryKey String key, String value, long timestamp) {}
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface FileRepository {
    /**
     * The base path where files will be stored.
     * Can be absolute or relative to the application directory.
     */
    String path() default "data";

    /**
     * The file format to use for storage.
     */
    FileFormat format() default FileFormat.JSON;

    /**
     * Whether to compress files.
     */
    boolean compressed() default false;

    /**
     * Compression algorithm to use when compressed is true.
     */
    CompressionType compression() default CompressionType.GZIP;

    /**
     * File extension (without dot). If empty, uses default for format.
     */
    String extension() default "";

    /**
     * Whether to create subdirectories based on entity ID.
     * Useful for large datasets to avoid having too many files in one directory.
     */
    boolean sharding() default false;

    /**
     * Number of shards (subdirectories) to use when sharding is enabled.
     */
    int shardCount() default 256;
}
