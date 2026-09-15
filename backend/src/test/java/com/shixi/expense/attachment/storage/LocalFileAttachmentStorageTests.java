package com.shixi.expense.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.shixi.expense.attachment.config.AttachmentStorageProperties;

/** 本地附件存储的单元测试，不依赖数据库或 Spring 容器。 */
class LocalFileAttachmentStorageTests {

    @TempDir
    Path temporaryDirectory;

    private LocalFileAttachmentStorage storage;

    @BeforeEach
    void setUp() {
        AttachmentStorageProperties properties =
                new AttachmentStorageProperties();
        properties.setRootDirectory(temporaryDirectory.toString());
        storage = new LocalFileAttachmentStorage(properties);
    }

    @Test
    void shouldCreateDirectoriesAndSaveFile() throws Exception {
        String objectKey = "reports/1/20260913/example.pdf";
        byte[] content = "test attachment".getBytes(StandardCharsets.UTF_8);

        String savedKey = storage.save(
                new ByteArrayInputStream(content),
                objectKey);

        Path savedFile = temporaryDirectory.resolve(objectKey);
        assertEquals(objectKey, savedKey);
        assertTrue(Files.exists(savedFile));
        assertEquals("test attachment", Files.readString(savedFile));
    }

    @Test
    void shouldRejectPathTraversal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.save(
                        new ByteArrayInputStream(new byte[] {1}),
                        "../outside.pdf"));
    }

    @Test
    void shouldNotOverwriteExistingFile() throws Exception {
        String objectKey = "reports/1/same.pdf";
        storage.save(
                new ByteArrayInputStream(new byte[] {1}),
                objectKey);

        assertThrows(
                FileAlreadyExistsException.class,
                () -> storage.save(
                        new ByteArrayInputStream(new byte[] {2}),
                        objectKey));
    }

    @Test
    void shouldDeleteExistingFile() throws Exception {
        String objectKey = "reports/1/delete.pdf";
        storage.save(
                new ByteArrayInputStream(new byte[] {1}),
                objectKey);

        Path savedFile = temporaryDirectory.resolve(objectKey);
        assertTrue(Files.exists(savedFile));

        storage.delete(objectKey);

        assertFalse(Files.exists(savedFile));
    }
}
