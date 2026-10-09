package dev.aegisac.common.packet;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class BrandDecoderTest {
    @Test void decodesOnlyBoundedUtf8ProtocolStrings() {
        assertEquals("vanilla",BrandDecoder.decode(new byte[]{7,'v','a','n','i','l','l','a'}));
        assertEquals("unknown",BrandDecoder.decode(new byte[]{2,'a'}));
        assertEquals("unknown",BrandDecoder.decode(new byte[]{1,'a','b'}));
        assertEquals("unknown",BrandDecoder.decode(new byte[]{1,(byte)0xff}));
        assertEquals("unknown",BrandDecoder.decode(new byte[]{1,10}));
        assertEquals("unknown",BrandDecoder.decode(new byte[]{(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff,(byte)0xff}));
        assertEquals("unknown",BrandDecoder.decode(new byte[200]));
        assertEquals("unknown",BrandDecoder.decode("vanilla".getBytes(StandardCharsets.UTF_8)));
    }
}
