package com.swiftdrop.delivery.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.swiftdrop.common.exception.BadRequestException;
import com.swiftdrop.common.exception.NotFoundException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LocalFileProofStorageTest {

    @TempDir
    Path root;

    private LocalFileProofStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalFileProofStorage(root, Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static byte[] image(String format, int width, int height, int type) throws Exception {
        BufferedImage image = new BufferedImage(width, height, type);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, bytes)).isTrue();
        return bytes.toByteArray();
    }

    /** A JPEG with an EXIF (APP1) block, as phones write them, inserted right after the SOI marker. */
    private static byte[] jpegWithExif() throws Exception {
        byte[] plain = image("jpg", 40, 30, BufferedImage.TYPE_INT_RGB);
        byte[] payload = "Exif\0\0GPS-position-secret".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        int length = payload.length + 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(plain, 0, 2);
        out.write(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) (length >> 8), (byte) length});
        out.write(payload);
        out.write(plain, 2, plain.length - 2);
        return out.toByteArray();
    }

    private static BufferedImage read(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void storesAJpegUnderYearAndMonthWithItsSizeAndHash() throws Exception {
        StoredPhoto photo = storage.store(image("jpg", 200, 100, BufferedImage.TYPE_INT_RGB));

        assertThat(photo.storageKey()).matches("2026/11/[0-9a-f-]{36}\\.jpg");
        assertThat(photo.contentType()).isEqualTo("image/jpeg");
        byte[] stored = Files.readAllBytes(root.resolve(photo.storageKey()));
        assertThat(photo.sizeBytes()).isEqualTo(stored.length);
        assertThat(photo.sha256()).isEqualTo(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stored)));
        assertThat(storage.read(photo.storageKey())).isEqualTo(stored);
        assertThat(read(stored).getWidth()).isEqualTo(200);
    }

    @Test
    void scalesLargePhotosDownToTheMaximumEdgeKeepingTheShape() throws Exception {
        StoredPhoto photo = storage.store(image("png", 3200, 1600, BufferedImage.TYPE_INT_RGB));

        BufferedImage stored = read(storage.read(photo.storageKey()));
        assertThat(stored.getWidth()).isEqualTo(LocalFileProofStorage.MAX_EDGE_PX);
        assertThat(stored.getHeight()).isEqualTo(800);
    }

    @Test
    void reEncodesPngWithTransparencyAsAJpeg() throws Exception {
        StoredPhoto photo = storage.store(image("png", 50, 50, BufferedImage.TYPE_INT_ARGB));

        byte[] stored = storage.read(photo.storageKey());
        assertThat(stored[0]).isEqualTo((byte) 0xFF);
        assertThat(stored[1]).isEqualTo((byte) 0xD8);
        assertThat(read(stored).getColorModel().hasAlpha()).isFalse();
    }

    @Test
    void dropsExifDataSuchAsTheLocation() throws Exception {
        byte[] upload = jpegWithExif();
        assertThat(new String(upload, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("GPS-position-secret");

        byte[] stored = storage.read(storage.store(upload).storageKey());

        String text = new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(text).doesNotContain("GPS-position-secret").doesNotContain("Exif");
    }

    @Test
    void refusesFilesThatAreNotJpegOrPng() throws Exception {
        assertThatThrownBy(() -> storage.store(image("gif", 10, 10, BufferedImage.TYPE_INT_RGB)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("JPEG or PNG");
        assertThatThrownBy(() -> storage.store("<html>not a photo</html>".getBytes()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.store(new byte[0])).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> storage.store(null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAFileThatOnlyLooksLikeAJpeg() {
        byte[] fake = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02, 0x03};

        assertThatThrownBy(() -> storage.store(fake))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("could not be read");
    }

    @Test
    void refusesUploadsOverFiveMegabytes() {
        byte[] huge = new byte[LocalFileProofStorage.MAX_UPLOAD_BYTES + 1];
        huge[0] = (byte) 0xFF;
        huge[1] = (byte) 0xD8;
        huge[2] = (byte) 0xFF;

        assertThatThrownBy(() -> storage.store(huge)).isInstanceOf(BadRequestException.class)
                .hasMessageContaining("5 MB");
    }

    @Test
    void refusesImagesWithTooManyPixelsBeforeDecodingThem() throws Exception {
        // A tiny PNG file whose header claims 10000 x 10000 pixels.
        byte[] png = image("png", 1, 1, BufferedImage.TYPE_INT_RGB);
        java.nio.ByteBuffer.wrap(png, 16, 8).putInt(10_000).putInt(10_000);

        assertThatThrownBy(() -> storage.store(png)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesKeysOutsideTheStorageFolder() {
        assertThatThrownBy(() -> storage.read("../outside.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve("2026/../../x.jpg")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.read("2026/11/missing.jpg")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void keepsTheFolderOutOfGit() throws Exception {
        assertThat(Files.readString(root.resolve(".gitignore"))).isEqualTo("*\n");
    }

    @Test
    void deletesTheFileWhenTheTransactionRollsBackAndKeepsItOnCommit() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        StoredPhoto rolledBack = storage.store(image("jpg", 20, 20, BufferedImage.TYPE_INT_RGB));
        StoredPhoto committed = storage.store(image("jpg", 20, 20, BufferedImage.TYPE_INT_RGB));
        var synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(2);

        synchronizations.get(0).afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        synchronizations.get(1).afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        assertThat(root.resolve(rolledBack.storageKey())).doesNotExist();
        assertThat(root.resolve(committed.storageKey())).exists();
    }

    @Test
    void deleteIgnoresMissingFiles() {
        storage.delete("2026/11/never-stored.jpg");
    }
}
