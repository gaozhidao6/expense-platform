package com.shixi.expense.attachment.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationRunner;

@ExtendWith(MockitoExtension.class)
class MinioStorageConfigTests {

    @Mock
    private MinioClient minioClient;

    private ApplicationRunner initializer;

    @BeforeEach
    void setUp() {
        MinioStorageProperties properties = new MinioStorageProperties(
                "http://localhost:9000",
                "access-key",
                "secret-key",
                "expense-attachments");
        initializer = new MinioStorageConfig()
                .minioBucketInitializer(minioClient, properties);
    }

    @Test
    void createsBucketWhenItDoesNotExist() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenReturn(false);

        initializer.run(null);

        ArgumentCaptor<MakeBucketArgs> captor =
                ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient).makeBucket(captor.capture());
        assertEquals(
                "expense-attachments",
                captor.getValue().bucket());
    }

    @Test
    void keepsExistingBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class)))
                .thenReturn(true);

        initializer.run(null);

        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void rejectsBlankBucketConfiguration() {
        MinioStorageProperties invalidProperties = new MinioStorageProperties(
                "http://localhost:9000",
                "access-key",
                "secret-key",
                " ");
        ApplicationRunner invalidInitializer = new MinioStorageConfig()
                .minioBucketInitializer(minioClient, invalidProperties);

        assertThrows(
                IllegalStateException.class,
                () -> invalidInitializer.run(null));
    }
}
