package io.github.flameyossnowy.universal.api.utils;

import java.util.UUID;
import java.util.Arrays;

/**
 * @author <a href="https://github.com/jchambers/fast-uuid/">jchambers/fast-uuid</a>
 */
public class FastUUID {
    private static final long[] HEX_VALUES = new long[128];

    private static final long UUID_STRING_LENGTH = 36;

    static {
        Arrays.fill(HEX_VALUES, -1);

        HEX_VALUES['0'] = 0x0;
        HEX_VALUES['1'] = 0x1;
        HEX_VALUES['2'] = 0x2;
        HEX_VALUES['3'] = 0x3;
        HEX_VALUES['4'] = 0x4;
        HEX_VALUES['5'] = 0x5;
        HEX_VALUES['6'] = 0x6;
        HEX_VALUES['7'] = 0x7;
        HEX_VALUES['8'] = 0x8;
        HEX_VALUES['9'] = 0x9;

        HEX_VALUES['a'] = 0xa;
        HEX_VALUES['b'] = 0xb;
        HEX_VALUES['c'] = 0xc;
        HEX_VALUES['d'] = 0xd;
        HEX_VALUES['e'] = 0xe;
        HEX_VALUES['f'] = 0xf;

        HEX_VALUES['A'] = 0xa;
        HEX_VALUES['B'] = 0xb;
        HEX_VALUES['C'] = 0xc;
        HEX_VALUES['D'] = 0xd;
        HEX_VALUES['E'] = 0xe;
        HEX_VALUES['F'] = 0xf;
    }

    private FastUUID() {
        // A private constructor prevents callers from accidentally instantiating FastUUID objects
    }

    /**
     * Parses a UUID from the given character sequence. The character sequence must represent a UUID as described in
     * {@link UUID#toString()}.
     *
     * @param uuidSequence the character sequence from which to parse a UUID
     *
     * @return the UUID represented by the given character sequence
     *
     * @throws IllegalArgumentException if the given character sequence does not conform to the string representation as
     * described in {@link UUID#toString()}
     */
    public static UUID parseUUID(final CharSequence uuidSequence) {
        if (uuidSequence.length() != UUID_STRING_LENGTH ||
                uuidSequence.charAt(8) != '-' ||
                uuidSequence.charAt(13) != '-' ||
                uuidSequence.charAt(18) != '-' ||
                uuidSequence.charAt(23) != '-') {

            throw new IllegalArgumentException("Illegal UUID string: " + uuidSequence);
        }

        long mostSignificantBits = getHexValueForChar(uuidSequence.charAt(0)) << 60;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(1)) << 56;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(2)) << 52;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(3)) << 48;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(4)) << 44;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(5)) << 40;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(6)) << 36;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(7)) << 32;

        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(9)) << 28;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(10)) << 24;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(11)) << 20;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(12)) << 16;

        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(14)) << 12;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(15)) << 8;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(16)) << 4;
        mostSignificantBits |= getHexValueForChar(uuidSequence.charAt(17));

        long leastSignificantBits = getHexValueForChar(uuidSequence.charAt(19)) << 60;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(20)) << 56;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(21)) << 52;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(22)) << 48;

        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(24)) << 44;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(25)) << 40;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(26)) << 36;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(27)) << 32;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(28)) << 28;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(29)) << 24;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(30)) << 20;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(31)) << 16;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(32)) << 12;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(33)) << 8;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(34)) << 4;
        leastSignificantBits |= getHexValueForChar(uuidSequence.charAt(35));

        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    static long getHexValueForChar(final char c) {
        long value = HEX_VALUES[c];
        try {
            if (value < 0) {
                throw new IllegalArgumentException("Illegal hexadecimal digit: " + c);
            }
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Illegal hexadecimal digit: " + c);
        }

        return value;
    }
}
