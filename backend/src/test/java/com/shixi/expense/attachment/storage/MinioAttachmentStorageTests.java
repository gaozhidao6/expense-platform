package com.shixi.expense.attachment.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shixi.expense.attachment.config.MinioStorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import okhttp3.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class MinioAttachmentStorageTests {

    @Mock
    private MinioClient minioClient;

    private MinioAttachmentStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MinioAttachmentStorage(
                minioClient,
                new MinioStorageProperties(
                        "http://localhost:9000",
                        "access-key",
                        "secret-key",
                        "expense-attachments"));
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.save(null, "reports/1/a.pdf"));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.load(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> storage.delete(null));
    }

    @Test
    void uploadsObjectAndReturnsObjectKey() throws Exception {
        String objectKey = "reports/1/a.pdf";
        ByteArrayInputStream inputStream = new ByteArrayInputStream(
                "file-content".getBytes(StandardCharsets.UTF_8));

        String result = storage.save(inputStream, objectKey);

        ArgumentCaptor<PutObjectArgs> captor =
                ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertEquals("expense-attachments", captor.getValue().bucket());
        assertEquals(objectKey, captor.getValue().object());
        assertEquals(-1L, captor.getValue().objectSize());
        assertEquals(10L * 1024 * 1024, captor.getValue().partSize());
        assertEquals(objectKey, result);
    }

    @Test
    void downloadsObjectAsResource() throws Exception {
        byte[] content = "download-content".getBytes(StandardCharsets.UTF_8);
        GetObjectResponse response = new GetObjectResponse(
                new Headers.Builder().build(),
                "expense-attachments",
                null,
                "reports/1/a.pdf",
                new ByteArrayInputStream(content));
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        Resource resource = storage.load("reports/1/a.pdf");

        assertArrayEquals(content, resource.getInputStream().readAllBytes());
        assertEquals(content.length, resource.contentLength());
        ArgumentCaptor<GetObjectArgs> captor =
                ArgumentCaptor.forClass(GetObjectArgs.class);
        verify(minioClient).getObject(captor.capture());
        assertEquals("expense-attachments", captor.getValue().bucket());
        assertEquals("reports/1/a.pdf", captor.getValue().object());
    }

    @Test
    void deletesObject() throws Exception {
        storage.delete("reports/1/a.pdf");

        ArgumentCaptor<RemoveObjectArgs> captor =
                ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        assertEquals("expense-attachments", captor.getValue().bucket());
        assertEquals("reports/1/a.pdf", captor.getValue().object());
    }

    @Test
    void convertsClientFailureToIOException() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO unavailable"));

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.load("reports/1/a.pdf"));

        assertEquals("从 MinIO 读取附件失败", exception.getMessage());
    }

    @Test
    void convertsUploadFailureToIOException() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("MinIO unavailable"));

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.save(
                        new ByteArrayInputStream(new byte[] {1}),
                        "reports/1/a.pdf"));

        assertEquals("向 MinIO 上传附件失败", exception.getMessage());
    }

    @Test
    void convertsDeleteFailureToIOException() throws Exception {
        doThrow(new RuntimeException("MinIO unavailable"))
                .when(minioClient)
                .removeObject(any(RemoveObjectArgs.class));

        IOException exception = assertThrows(
                IOException.class,
                () -> storage.delete("reports/1/a.pdf"));

        assertEquals("从 MinIO 删除附件失败", exception.getMessage());
    }

    @Test
    void rejectsBlankBucketConfiguration() {
        MinioAttachmentStorage invalidStorage = new MinioAttachmentStorage(
                minioClient,
                new MinioStorageProperties(
                        "http://localhost:9000",
                        "access-key",
                        "secret-key",
                        " "));

        assertThrows(
                IllegalArgumentException.class,
                () -> invalidStorage.save(
                        new ByteArrayInputStream(new byte[] {1}),
                        "reports/1/a.pdf"));
        assertThrows(
                IllegalArgumentException.class,
                () -> invalidStorage.load("reports/1/a.pdf"));
        assertThrows(
                IllegalArgumentException.class,
                () -> invalidStorage.delete("reports/1/a.pdf"));
    }
}
