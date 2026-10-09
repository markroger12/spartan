package dev.aegisac.common.packet;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
/** Bounded 1.8+ protocol-string decoding; brand is informational and client-controlled. */
public final class BrandDecoder {
    private BrandDecoder() { }
    public static String decode(byte[] data) {
        if (data == null || data.length > 133) return "unknown";
        int size = 0, index = 0;
        for (; index < Math.min(5, data.length); index++) {
            int value = data[index] & 255;
            if (index == 4 && (value & 0xf0) != 0) return "unknown";
            size |= (value & 127) << (index * 7);
            if ((value & 128) == 0) {
                index++;
                if (size < 1 || size > 128 || data.length - index != size) return "unknown";
                try {
                    String brand = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data, index, size)).toString();
                    if (brand.codePoints().anyMatch(point -> Character.isISOControl(point) || point == 0x00a7)) return "unknown";
                    return brand;
                } catch (CharacterCodingException malformed) { return "unknown"; }
            }
        }
        return "unknown";
    }
}
