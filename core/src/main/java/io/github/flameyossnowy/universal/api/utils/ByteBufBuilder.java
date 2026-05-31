package io.github.flameyossnowy.universal.api.utils;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A pooled, resizable byte buffer builder optimized for request-scoped operations.
 *
 * <p>This builder is designed primarily for protocol serialization and byte-oriented
 * infrastructure code. It provides:
 * </p>
 *
 * <ul>
 *     <li>Thread-local pooling</li>
 *     <li>UTF-8 safe string appending</li>
 *     <li>ASCII-specialized fast paths</li>
 *     <li>Zero-copy unsafe buffer access</li>
 *     <li>Low allocation overhead</li>
 * </ul>
 */
public final class ByteBufBuilder implements AutoCloseable {

    private static final int DEFAULT_CAPACITY = 512;
    private static final int MAX_POOLED_CAPACITY = 8192;

    private static final ThreadLocal<ByteBufBuilder> POOL = ThreadLocal.withInitial(() ->
            new ByteBufBuilder(DEFAULT_CAPACITY, true)
    );

    private byte[] buf;
    private int pos;

    private final boolean pooled;
    private boolean acquired;

    public static @NotNull ByteBufBuilder acquire() {
        return acquire(DEFAULT_CAPACITY);
    }

    public static @NotNull ByteBufBuilder acquire(int minCapacity) {
        ByteBufBuilder pooled = POOL.get();

        if (!pooled.acquired) {
            pooled.ensureCapacity(minCapacity);
            pooled.pos = 0;
            pooled.acquired = true;
            return pooled;
        }

        return new ByteBufBuilder(Math.max(minCapacity, 64), false);
    }

    private ByteBufBuilder(int initialCapacity, boolean pooled) {
        this.buf = new byte[initialCapacity];
        this.pooled = pooled;
    }

    @Override
    public void close() {
        if (!pooled || !acquired) {
            return;
        }

        acquired = false;
        pos = 0;

        // Trim oversized pooled buffers
        if (buf.length > MAX_POOLED_CAPACITY) {
            buf = new byte[DEFAULT_CAPACITY];
        }
    }

    /**
     * Appends a UTF-8 encoded string safely.
     */
    public void append(@NotNull String s) {
        int len = s.length();

        // Worst case: 4 bytes per char
        ensureCapacity(pos + (len << 2));

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);

            if (c < 0x80) {
                buf[pos++] = (byte) c;
            } else if (c < 0x800) {
                buf[pos++] = (byte) (0b11000000 | (c >> 6));
                buf[pos++] = (byte) (0b10000000 | (c & 0b00111111));
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    throw new IllegalArgumentException("Invalid UTF-16 surrogate pair");
                }

                char low = s.charAt(++i);

                if (!Character.isLowSurrogate(low)) {
                    throw new IllegalArgumentException("Invalid UTF-16 surrogate pair");
                }

                int codePoint = Character.toCodePoint(c, low);

                buf[pos++] = (byte) (0b11110000 | (codePoint >> 18));
                buf[pos++] = (byte) (0b10000000 | ((codePoint >> 12) & 0b00111111));
                buf[pos++] = (byte) (0b10000000 | ((codePoint >> 6) & 0b00111111));
                buf[pos++] = (byte) (0b10000000 | (codePoint & 0b00111111));
            } else {
                buf[pos++] = (byte) (0b11100000 | (c >> 12));
                buf[pos++] = (byte) (0b10000000 | ((c >> 6) & 0b00111111));
                buf[pos++] = (byte) (0b10000000 | (c & 0b00111111));
            }
        }
    }

    /**
     * Appends an ASCII-only string.
     *
     * <p>Behavior is undefined for non-ASCII input.</p>
     */
    @SuppressWarnings("deprecation")
    public void appendAscii(@NotNull String s) {
        int len = s.length();

        ensureCapacity(pos + len);

        s.getBytes(0, len, buf, pos);
        pos += len;
    }

    public void writeAsciiLowercase(@NotNull String s) {
        int len = s.length();

        ensureCapacity(pos + len);

        for (int i = 0; i < len; i++) {
            char c = s.charAt(i);

            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }

            buf[pos++] = (byte) c;
        }
    }

    public void writeCRLF() {
        ensureCapacity(pos + 2);
        buf[pos++] = '\r';
        buf[pos++] = '\n';
    }

    public void writeByteAscii(byte value) {
        appendAscii(String.valueOf(value));
    }

    public void writeShortAscii(short value) {
        appendAscii(String.valueOf(value));
    }

    public void writeIntAscii(int value) {
        appendAscii(String.valueOf(value));
    }

    public void writeLongAscii(long value) {
        appendAscii(String.valueOf(value));
    }

    public void writeFloatAscii(float value) {
        appendAscii(String.valueOf(value));
    }

    public void writeDoubleAscii(double value) {
        appendAscii(String.valueOf(value));
    }

    private static final byte[] HEX = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    public void writeHex(byte value) {
        ensureCapacity(pos + 2);

        buf[pos++] = HEX[(value >>> 4) & 0x0F];
        buf[pos++] = HEX[value & 0x0F];
    }

    public void append(byte b) {
        ensureCapacity(pos + 1);
        buf[pos++] = b;
    }

    public void append(char c) {
        ensureCapacity(pos + 1);
        buf[pos++] = (byte) c;
    }

    public void append(byte @NotNull [] bytes) {
        append(bytes, 0, bytes.length);
    }

    public void append(byte @NotNull [] bytes, int offset, int length) {
        ensureCapacity(pos + length);

        System.arraycopy(bytes, offset, buf, pos, length);
        pos += length;
    }

    /**
     * Returns a copy of the written bytes.
     */
    @Contract(value = "-> new", pure = true)
    public byte @NotNull [] build() {
        return Arrays.copyOf(buf, pos);
    }

    /**
     * Returns the internal backing buffer directly.
     *
     * <p>The returned array may contain unused capacity.</p>
     *
     * <p>This method is unsafe and intended for advanced use cases only.</p>
     */
    public byte @NotNull [] unsafeBuffer() {
        return buf;
    }

    /**
     * Returns a copy of a slice of the written bytes.
     */
    @Contract(value = "_, _ -> new", pure = true)
    public byte @NotNull [] slice(int offset, int length) {
        if (offset < 0 || length < 0 || offset + length > pos) {
            throw new IndexOutOfBoundsException();
        }

        return Arrays.copyOfRange(buf, offset, offset + length);
    }

    public int length() {
        return pos;
    }

    public void clear() {
        pos = 0;
    }

    public void setLength(int length) {
        if (length < 0 || length > pos) {
            throw new IllegalArgumentException("Invalid length");
        }

        pos = length;
    }

    private void ensureCapacity(int required) {
        if (required <= buf.length) {
            return;
        }

        int newCapacity = Math.max(buf.length << 1, required);
        buf = Arrays.copyOf(buf, newCapacity);
    }
}