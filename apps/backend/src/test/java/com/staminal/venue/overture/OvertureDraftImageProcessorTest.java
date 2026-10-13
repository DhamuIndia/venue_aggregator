package com.staminal.venue.overture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

class OvertureDraftImageProcessorTest {
    private final OvertureDraftImageProcessor processor = new OvertureDraftImageProcessor();

    @Test void normalizesTransparentPngToWhiteJpegWithoutSourceMetadata() throws Exception {
        BufferedImage source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        byte[] png = encode(source, "png"); source.flush();
        byte[] metadata = "Description\0QA GPS private metadata".getBytes(StandardCharsets.ISO_8859_1);
        png = insertPngChunk(png, "tEXt", metadata);
        var image = processor.process(file("image/png", png));
        assertThat(image.contentType()).isEqualTo("image/jpeg");
        assertThat(image.width()).isEqualTo(20); assertThat(image.height()).isEqualTo(10);
        assertThat(image.bytes().length).isLessThanOrEqualTo(OvertureDraftImageProcessor.MAX_OUTPUT_BYTES);
        assertThat(new String(image.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("QA GPS", "Description");
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
        Color pixel = new Color(decoded.getRGB(10, 5));
        assertThat(pixel.getRed()).isGreaterThan(245); assertThat(pixel.getGreen()).isGreaterThan(245); assertThat(pixel.getBlue()).isGreaterThan(245);
        decoded.flush();
    }

    @ParameterizedTest @ValueSource(ints = {1,2,3,4,5,6,7,8})
    void appliesBoundedExifOrientationAndRemovesExif(int orientation) throws Exception {
        byte[] jpeg = exif(photo(), orientation, false);
        var image = processor.process(file("image/jpeg", jpeg));
        assertThat(image.width()).isEqualTo(orientation >= 5 ? 40 : 80);
        assertThat(image.height()).isEqualTo(orientation >= 5 ? 80 : 40);
        assertThat(new String(image.bytes(), StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        if (orientation == 6) {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(image.bytes()));
            Color top = new Color(decoded.getRGB(20, 20)), bottom = new Color(decoded.getRGB(20, 60));
            assertThat(top.getRed()).isGreaterThan(top.getBlue()); assertThat(bottom.getBlue()).isGreaterThan(bottom.getRed()); decoded.flush();
        }
    }

    @Test void supportsBigEndianExifAndRejectsOffsetsOutsideTheApp1Segment() throws Exception {
        assertThat(processor.process(file("image/jpeg", exif(photo(), 8, true))).width()).isEqualTo(40);
        byte[] corrupt = exif(photo(), 6, false);
        // APP1 starts after SOI; TIFF starts at byte 12, IFD0 offset at 16.
        java.util.Arrays.fill(corrupt, 16, 20, (byte) 255);
        rejected(() -> processor.process(file("image/jpeg", corrupt)), HttpStatus.BAD_REQUEST);
    }

    @Test void rejectsMimeSpoofingUnsupportedFormatsAndTruncation() throws Exception {
        byte[] jpeg = photo();
        rejected(() -> processor.process(file("image/png", jpeg)), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        rejected(() -> processor.process(file("image/svg+xml", "<svg/>".getBytes(StandardCharsets.UTF_8))), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        rejected(() -> processor.process(file("image/webp", "RIFFxxxxWEBP".getBytes(StandardCharsets.UTF_8))), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        rejected(() -> processor.process(file("image/jpeg", new byte[]{(byte)255,(byte)216,(byte)255,(byte)217})), HttpStatus.BAD_REQUEST);
        rejected(() -> processor.process(file("image/jpeg", java.util.Arrays.copyOf(jpeg, jpeg.length - 2))), HttpStatus.BAD_REQUEST);
        byte[] png = encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png");
        rejected(() -> processor.process(file("image/png", java.util.Arrays.copyOf(png, png.length - 12))), HttpStatus.BAD_REQUEST);
        byte[] badCrc = png.clone(); badCrc[29] ^= 1;
        rejected(() -> processor.process(file("image/png", badCrc)), HttpStatus.BAD_REQUEST);
        byte[] corruptScan = new byte[jpeg.length - 20];
        System.arraycopy(jpeg, 0, corruptScan, 0, corruptScan.length - 2); corruptScan[corruptScan.length - 2] = (byte)255; corruptScan[corruptScan.length - 1] = (byte)217;
        rejected(() -> processor.process(file("image/jpeg", corruptScan)), HttpStatus.BAD_REQUEST);
    }

    @Test void rejectsDecompressionHeadersBeforeAllocatingDecodedPixels() throws Exception {
        byte[] png = encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), "png");
        byte[] tooWide = dimensions(png, 6001, 1), tooManyPixels = dimensions(png, 5000, 4000);
        rejected(() -> processor.process(file("image/png", tooWide)), HttpStatus.PAYLOAD_TOO_LARGE);
        rejected(() -> processor.process(file("image/png", tooManyPixels)), HttpStatus.PAYLOAD_TOO_LARGE);
        byte[] zero = dimensions(png, 0, 1);
        assertThatThrownBy(() -> processor.process(file("image/png", zero))).isInstanceOf(ResponseStatusException.class);
    }

    @Test void validatesActualBytesNotOnlyDeclaredSizeAndClosesTheInput() throws Exception {
        MultipartFile liar = mock(MultipartFile.class);
        when(liar.isEmpty()).thenReturn(false); when(liar.getSize()).thenReturn(1L); when(liar.getContentType()).thenReturn("image/png");
        when(liar.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[OvertureDraftImageProcessor.MAX_INPUT_BYTES + 1]));
        rejected(() -> processor.process(liar), HttpStatus.PAYLOAD_TOO_LARGE);
        when(liar.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[2]));
        rejected(() -> processor.process(liar), HttpStatus.BAD_REQUEST);
        rejected(() -> processor.process(file("image/png", new byte[0])), HttpStatus.BAD_REQUEST);
        rejected(() -> processor.process(file("image/png", new byte[OvertureDraftImageProcessor.MAX_INPUT_BYTES + 1])), HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test void processingSlotsAreNonblockingAndReleasedAfterFailure() throws Exception {
        Semaphore one = new Semaphore(1);
        OvertureDraftImageProcessor limited = new OvertureDraftImageProcessor(one);
        rejected(() -> limited.process(file("image/jpeg", new byte[]{1,2,3})), HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(one.availablePermits()).isEqualTo(1);
        assertThat(limited.process(file("image/jpeg", photo())).contentType()).isEqualTo("image/jpeg");
        assertThat(one.availablePermits()).isEqualTo(1);
        assertThat(one.tryAcquire()).isTrue();
        rejected(() -> limited.process(file("image/jpeg", photo())), HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(one.availablePermits()).isZero(); one.release();
    }

    @Test void outputStreamBoundsWritesAndHeaderSeeks() throws Exception {
        try (var output = new OvertureDraftImageProcessor.BoundedImageOutputStream(8)) {
            output.write(new byte[8]); output.seek(2); output.write(9);
            assertThat(output.bytes()).hasSize(8); assertThat(output.bytes()[2]).isEqualTo((byte)9);
            output.seek(8);
            assertThatThrownBy(() -> output.write(1)).isInstanceOf(OvertureDraftImageProcessor.LimitExceeded.class);
            assertThatThrownBy(() -> output.seek(9)).isInstanceOf(OvertureDraftImageProcessor.LimitExceeded.class);
        }
    }

    private static MockMultipartFile file(String mime, byte[] bytes) { return new MockMultipartFile("file", "../../ignored-name.jpg", mime, bytes); }
    private static byte[] photo() throws Exception {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB); Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED); graphics.fillRect(0, 0, 40, 40); graphics.setColor(Color.BLUE); graphics.fillRect(40, 0, 40, 40); graphics.dispose();
        byte[] result = encode(image, "jpeg"); image.flush(); return result;
    }
    private static byte[] encode(BufferedImage image, String format) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); assertThat(ImageIO.write(image, format, bytes)).isTrue(); return bytes.toByteArray();
    }
    private static byte[] dimensions(byte[] png, int width, int height) {
        byte[] result = png.clone(); ByteBuffer buffer = ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN); buffer.putInt(16, width); buffer.putInt(20, height);
        CRC32 crc = new CRC32(); crc.update(result, 12, 17); buffer.putInt(29, (int)crc.getValue()); return result;
    }
    private static byte[] insertPngChunk(byte[] png, String type, byte[] content) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); output.write(png, 0, png.length - 12);
        output.write(ByteBuffer.allocate(4).putInt(content.length).array()); byte[] name = type.getBytes(StandardCharsets.US_ASCII); output.write(name); output.write(content);
        CRC32 crc = new CRC32(); crc.update(name); crc.update(content); output.write(ByteBuffer.allocate(4).putInt((int)crc.getValue()).array());
        output.write(png, png.length - 12, 12); return output.toByteArray();
    }
    private static byte[] exif(byte[] jpeg, int orientation, boolean big) throws Exception {
        ByteBuffer tiff = ByteBuffer.allocate(26).order(big ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        tiff.put((byte)(big ? 'M' : 'I')).put((byte)(big ? 'M' : 'I')).putShort((short)42).putInt(8).putShort((short)1);
        tiff.putShort((short)0x112).putShort((short)3).putInt(1).putShort((short)orientation).putShort((short)0).putInt(0);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); output.write(jpeg, 0, 2); output.write(255); output.write(225);
        output.write(ByteBuffer.allocate(2).putShort((short)34).array()); output.write(new byte[]{'E','x','i','f',0,0}); output.write(tiff.array()); output.write(jpeg, 2, jpeg.length - 2); return output.toByteArray();
    }
    private static void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, HttpStatus status) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }
}
