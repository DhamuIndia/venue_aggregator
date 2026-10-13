package com.staminal.venue.overture;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStreamImpl;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Bounded JPEG/PNG decode followed by metadata-free, white-background JPEG encoding. */
@Component
public class OvertureDraftImageProcessor {
    public static final int MAX_INPUT_BYTES = 8_388_608;
    public static final int MAX_OUTPUT_BYTES = 4_194_304;
    public static final int MAX_DIMENSION = 6_000;
    public static final int MAX_PIXELS = 16_000_000;
    private static final byte[] PNG = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};
    private final Semaphore slots;

    public record Image(byte[] bytes, String contentType, int width, int height) { }

    public OvertureDraftImageProcessor() { this(new Semaphore(2)); }
    OvertureDraftImageProcessor(Semaphore slots) { this.slots = slots; }

    public Image process(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() <= 0) throw bad("A photo file is required");
        if (file.getSize() > MAX_INPUT_BYTES) throw large("Photo exceeds the 8 MiB input limit");
        String mime = file.getContentType() == null ? "" : file.getContentType().strip().toLowerCase(Locale.ROOT);
        if (!mime.equals("image/jpeg") && !mime.equals("image/png")) throw unsupported();
        if (!slots.tryAcquire()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Photo processing is busy; try again later");
        BufferedImage decoded = null, rgb = null;
        ImageReader reader = null;
        ImageWriter writer = null;
        try {
            byte[] input;
            try (InputStream stream = file.getInputStream()) { input = stream.readNBytes(MAX_INPUT_BYTES + 1); }
            if (input.length > MAX_INPUT_BYTES) throw large("Photo exceeds the 8 MiB input limit");
            if (input.length == 0 || input.length != file.getSize()) throw bad("Photo upload is incomplete");
            String sniffed = sniff(input);
            if (!mime.equals(sniffed)) throw unsupported();
            requireCompleteContainer(input, mime);
            int orientation = mime.equals("image/jpeg") ? orientation(input) : 1;
            try (MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw unsupported();
                reader = readers.next();
                reader.addIIOReadWarningListener((source, warning) -> { throw bad("Photo contains incomplete or corrupt image data"); });
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(mime.equals("image/jpeg") && (format.equals("jpeg") || format.equals("jpg"))
                        || mime.equals("image/png") && format.equals("png"))) throw unsupported();
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) throw large("Photo dimensions exceed the processing limit");
                ImageReadParam parameters = reader.getDefaultReadParam();
                decoded = reader.read(0, parameters);
                if (decoded == null || decoded.getWidth() != width || decoded.getHeight() != height) throw bad("Photo could not be decoded");
                int outputWidth = orientation >= 5 ? height : width;
                int outputHeight = orientation >= 5 ? width : height;
                rgb = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = rgb.createGraphics();
                try {
                    graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, outputWidth, outputHeight);
                    graphics.drawImage(decoded, transform(orientation, width, height), null);
                } finally { graphics.dispose(); }
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
                if (!writers.hasNext()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Photo encoding is unavailable");
                writer = writers.next();
                try (BoundedImageOutputStream output = new BoundedImageOutputStream(MAX_OUTPUT_BYTES)) {
                    writer.setOutput(output);
                    ImageWriteParam encoding = writer.getDefaultWriteParam();
                    encoding.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    encoding.setCompressionQuality(0.85f);
                    if (encoding.canWriteProgressive()) encoding.setProgressiveMode(ImageWriteParam.MODE_DISABLED);
                    writer.write(null, new IIOImage(rgb, null, null), encoding);
                    return new Image(output.bytes(), "image/jpeg", outputWidth, outputHeight);
                }
            }
        } catch (IOException | IllegalArgumentException exception) {
            if (hasLimitCause(exception)) throw large("Normalized photo exceeds the 4 MiB storage limit");
            throw bad("Photo is not a complete, supported JPEG or PNG image");
        } finally {
            try {
                if (reader != null) reader.dispose();
                if (writer != null) writer.dispose();
                if (decoded != null) decoded.flush();
                if (rgb != null) rgb.flush();
            } finally { slots.release(); }
        }
    }

    private static String sniff(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216 && (bytes[2] & 255) == 255) return "image/jpeg";
        if (bytes.length >= PNG.length && Arrays.equals(Arrays.copyOf(bytes, PNG.length), PNG)) return "image/png";
        throw unsupported();
    }
    private static void requireCompleteContainer(byte[] bytes, String mime) {
        if (mime.equals("image/jpeg")) {
            if (bytes.length < 4 || (bytes[bytes.length - 2] & 255) != 255 || (bytes[bytes.length - 1] & 255) != 217)
                throw bad("Photo is not a complete JPEG image");
        } else {
            byte[] end = {0, 0, 0, 0, 73, 69, 78, 68, (byte)174, 66, 96, (byte)130};
            if (bytes.length < end.length || !Arrays.equals(Arrays.copyOfRange(bytes, bytes.length - end.length, bytes.length), end))
                throw bad("Photo is not a complete PNG image");
            int position = 8, chunks = 0; boolean idat = false;
            while (position < bytes.length) {
                if (++chunks > 4096 || position > bytes.length - 12) throw bad("Photo contains an invalid PNG container");
                long length = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, position, 4).getInt());
                if (length > bytes.length - position - 12L) throw bad("Photo contains an invalid PNG container");
                String type = new String(bytes, position + 4, 4, java.nio.charset.StandardCharsets.US_ASCII);
                if (chunks == 1 && (!type.equals("IHDR") || length != 13) || chunks > 1 && type.equals("IHDR"))
                    throw bad("Photo contains an invalid PNG container");
                java.util.zip.CRC32 crc = new java.util.zip.CRC32(); crc.update(bytes, position + 4, (int) length + 4);
                long expected = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, position + 8 + (int) length, 4).getInt());
                if (crc.getValue() != expected) throw bad("Photo contains corrupt PNG data");
                if (type.equals("IDAT")) idat = true;
                if (type.equals("IEND") && (length != 0 || position + 12 != bytes.length)) throw bad("Photo contains an invalid PNG container");
                position += (int) length + 12;
            }
            if (!idat) throw bad("Photo contains no PNG image data");
        }
    }

    /** Only the bounded TIFF IFD0 orientation scalar is read; no metadata offsets leave the APP1 segment. */
    private static int orientation(byte[] bytes) throws IOException {
        int position = 2;
        while (position + 4 <= bytes.length) {
            if ((bytes[position] & 255) != 255) break;
            while (position < bytes.length && (bytes[position] & 255) == 255) position++;
            if (position >= bytes.length) break;
            int marker = bytes[position++] & 255;
            if (marker == 218 || marker == 217) break;
            if (marker == 1 || marker >= 208 && marker <= 215) continue;
            if (position + 2 > bytes.length) throw new IOException("Invalid JPEG segment");
            int length = ((bytes[position] & 255) << 8) | (bytes[position + 1] & 255);
            if (length < 2 || (long) position + length > bytes.length) throw new IOException("Invalid JPEG segment");
            int start = position + 2, end = position + length;
            if (marker == 225 && end - start >= 14 && bytes[start] == 'E' && bytes[start + 1] == 'x'
                    && bytes[start + 2] == 'i' && bytes[start + 3] == 'f' && bytes[start + 4] == 0 && bytes[start + 5] == 0) {
                int tiff = start + 6;
                boolean little = bytes[tiff] == 'I' && bytes[tiff + 1] == 'I';
                if (!little && !(bytes[tiff] == 'M' && bytes[tiff + 1] == 'M')) throw new IOException("Invalid EXIF");
                if (unsigned(bytes, tiff + 2, 2, little, end) != 42) throw new IOException("Invalid EXIF");
                long offset = unsigned(bytes, tiff + 4, 4, little, end);
                if (offset < 8 || offset > end - tiff - 2) throw new IOException("Invalid EXIF");
                int ifd = tiff + (int) offset;
                int count = (int) unsigned(bytes, ifd, 2, little, end);
                if (count > 512 || (long) ifd + 2 + count * 12L > end) throw new IOException("Invalid EXIF");
                for (int entry = ifd + 2; entry < ifd + 2 + count * 12; entry += 12) {
                    if (unsigned(bytes, entry, 2, little, end) != 0x112) continue;
                    if (unsigned(bytes, entry + 2, 2, little, end) != 3 || unsigned(bytes, entry + 4, 4, little, end) != 1)
                        throw new IOException("Invalid EXIF orientation");
                    int result = (int) unsigned(bytes, entry + 8, 2, little, end);
                    if (result < 1 || result > 8) throw new IOException("Invalid EXIF orientation");
                    return result;
                }
            }
            position = end;
        }
        return 1;
    }

    private static long unsigned(byte[] data, int offset, int size, boolean little, int end) throws IOException {
        if (offset < 0 || (long) offset + size > end) throw new IOException("Invalid EXIF offset");
        long result = 0;
        for (int i = 0; i < size; i++) result |= (long) (data[offset + i] & 255) << ((little ? i : size - i - 1) * 8);
        return result;
    }
    private static AffineTransform transform(int orientation, int width, int height) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, width, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, width, height);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, height);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, height, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, height, width);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, width);
            default -> new AffineTransform();
        };
    }
    private static boolean hasLimitCause(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) if (current instanceof LimitExceeded) return true;
        return false;
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException large(String message) { return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, message); }
    private static ResponseStatusException unsupported() { return new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only valid JPEG and PNG photos are accepted"); }
    static final class LimitExceeded extends IOException { }

    /** ImageIO writers may seek to patch headers; all buffered writes/seeks remain bounded. */
    static final class BoundedImageOutputStream extends ImageOutputStreamImpl {
        private final int limit;
        private byte[] buffer = new byte[8192];
        private int size;
        BoundedImageOutputStream(int limit) { this.limit = limit; buffer = new byte[Math.min(8192, limit)]; }
        private void space(int length) throws IOException {
            checkClosed();
            if (streamPos > limit - (long) length) throw new LimitExceeded();
            int needed = (int) streamPos + length;
            if (needed > buffer.length) buffer = Arrays.copyOf(buffer, Math.min(limit, Math.max(needed, buffer.length * 2)));
        }
        @Override public void write(int value) throws IOException { flushBits(); space(1); buffer[(int) streamPos++] = (byte) value; size = Math.max(size, (int) streamPos); }
        @Override public void write(byte[] bytes, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length);
            flushBits(); space(length); System.arraycopy(bytes, offset, buffer, (int) streamPos, length); streamPos += length; size = Math.max(size, (int) streamPos);
        }
        @Override public int read() throws IOException { checkClosed(); bitOffset = 0; return streamPos >= size ? -1 : buffer[(int) streamPos++] & 255; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            java.util.Objects.checkFromIndexSize(offset, length, bytes.length); checkClosed(); bitOffset = 0;
            if (length == 0) return 0; if (streamPos >= size) return -1;
            int count = Math.min(length, size - (int) streamPos); System.arraycopy(buffer, (int) streamPos, bytes, offset, count); streamPos += count; return count;
        }
        @Override public void seek(long position) throws IOException { if (position > limit) throw new LimitExceeded(); super.seek(position); }
        @Override public long length() { return size; }
        byte[] bytes() { return Arrays.copyOf(buffer, size); }
    }
}
