package com.swiftdrop.delivery.storage;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.UUID;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Keeps proof photos as files under one folder ({@code PROOF_STORAGE_DIR}, default
 * {@code ./data/proofs}), at {@code yyyy/MM/<uuid>.jpg}.
 *
 * <p>Uploads are never stored as sent: the first bytes must be a JPEG or PNG signature, the image
 * size is checked before decoding (so a small file can't expand into a huge image), and the picture
 * is redrawn onto a plain RGB canvas, scaled to at most {@value #MAX_EDGE_PX} px, and written as a
 * new JPEG. Nothing of the original file survives, so EXIF data such as GPS location is dropped.
 *
 * <p>The folder gets its own {@code .gitignore} ({@code *}) so photos can never be committed.
 */
@Slf4j
@Component
public class LocalFileProofStorage implements ProofStorage {

    public static final int MAX_EDGE_PX = 1600;
    /** Larger uploads are refused (Spring's own multipart limit usually stops them earlier). */
    public static final int MAX_UPLOAD_BYTES = 5 * 1024 * 1024;
    /** Refuse images with more pixels than this before decoding them (about 8000 x 5000). */
    static final long MAX_SOURCE_PIXELS = 40_000_000L;
    static final float JPEG_QUALITY = 0.85f;
    static final String CONTENT_TYPE = "image/jpeg";

    private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final Path root;
    private final Clock clock;

    @Autowired
    public LocalFileProofStorage(
            @Value("${swiftdrop.proofs.storage-dir:${PROOF_STORAGE_DIR:./data/proofs}}") String storageDir) {
        this(Path.of(storageDir), Clock.systemUTC());
    }

    LocalFileProofStorage(Path root, Clock clock) {
        this.root = root.toAbsolutePath().normalize();
        this.clock = clock;
        prepareRoot();
    }

    @Override
    public StoredPhoto store(byte[] upload) {
        if (upload == null || upload.length == 0) {
            throw new BadRequestException("The photo is empty");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new BadRequestException("The photo must be at most 5 MB");
        }
        if (!startsWith(upload, JPEG_SIGNATURE) && !startsWith(upload, PNG_SIGNATURE)) {
            throw new BadRequestException("The photo must be a JPEG or PNG image");
        }
        byte[] jpeg = encodeJpeg(scale(decode(upload)));

        String key = newKey();
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, jpeg, StandardOpenOption.CREATE_NEW);
        } catch (FileAlreadyExistsException e) {
            throw new IllegalStateException("Proof photo key collision", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store the proof photo", e);
        }
        deleteOnRollback(key);
        return new StoredPhoto(key, CONTENT_TYPE, jpeg.length, sha256(jpeg));
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Photo not found");
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the proof photo", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            // Leaves an orphaned file behind, which is harmless; never fail the caller for it.
            log.warn("Could not delete proof photo {}: {}", storageKey, e.getClass().getSimpleName());
        }
    }

    /** The storage folder (absolute). */
    public Path root() {
        return root;
    }

    /** The file for a key; a key that would point outside the storage folder is refused. */
    Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new IllegalArgumentException("Invalid proof photo key");
        }
        return path;
    }

    private String newKey() {
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(ZoneOffset.UTC);
        return "%04d/%02d/%s.jpg".formatted(now.getYear(), now.getMonthValue(), UUID.randomUUID());
    }

    /** Inside a transaction, the file is removed again if it rolls back (e.g. the status change failed). */
    private void deleteOnRollback(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    delete(key);
                }
            }
        });
    }

    private static BufferedImage decode(byte[] upload) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_SOURCE_PIXELS) {
                    throw new BadRequestException("The photo's dimensions are too large");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw unreadable();
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BadRequestException bad) {
                throw bad;
            }
            // Corrupt or unsupported files (e.g. CMYK JPEGs) end here; the details stay in the log.
            log.debug("Unreadable proof photo: {}", e.getClass().getSimpleName());
            throw unreadable();
        }
    }

    /** Redraws onto an opaque RGB canvas (white behind transparent PNGs), at most MAX_EDGE_PX. */
    static BufferedImage scale(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        double factor = Math.min(1.0, (double) MAX_EDGE_PX / Math.max(width, height));
        int targetWidth = Math.max(1, (int) Math.round(width * factor));
        int targetHeight = Math.max(1, (int) Math.round(height * factor));

        BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    /** A new JPEG with no metadata of its own beyond the basic JFIF header. */
    static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(JPEG_QUALITY);
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the proof photo", e);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private void prepareRoot() {
        try {
            Files.createDirectories(root);
            Path gitignore = root.resolve(".gitignore");
            if (Files.notExists(gitignore)) {
                // Photos are personal data: keep the whole folder out of git.
                Files.writeString(gitignore, "*\n", StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            // Don't stop the whole app: deliveries without a photo still work. Uploads will fail.
            log.warn("Proof photo folder {} is not writable: {}", root, e.getClass().getSimpleName());
        }
    }

    private static BadRequestException unreadable() {
        return new BadRequestException("The photo could not be read; send a JPEG or PNG image");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
