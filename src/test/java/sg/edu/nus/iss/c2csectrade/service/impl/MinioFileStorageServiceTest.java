package sg.edu.nus.iss.c2csectrade.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import sg.edu.nus.iss.c2csectrade.config.MinioProperties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The MinIO strategy behind FileStorageService (Strategy pattern, see
 * docs/design-patterns/M1-yu-chenglin.md). No MinIO server is needed: the
 * client is mocked and the tests check what the strategy asks it to do.
 */
@ExtendWith(MockitoExtension.class)
class MinioFileStorageServiceTest {

    @Mock private MinioClient minioClient;
    private MinioProperties properties;
    private MinioFileStorageService storage;

    @BeforeEach
    void setUp() {
        properties = new MinioProperties();
        properties.setEndpoint("http://minio:9000");
        properties.setPublicEndpoint("http://203.0.113.10:9000/");
        properties.setBucketName("c2c-sectrade");
        storage = new MinioFileStorageService(minioClient, properties);
    }

    private MockMultipartFile image(String name, String type, int bytes) {
        return new MockMultipartFile("file", name, type, new byte[bytes]);
    }

    @Test
    @DisplayName("An upload lands under folder/date/uuid and returns the browser-facing URL")
    void uploadStoresObjectAndReturnsPublicUrl() throws Exception {
        String url = storage.upload(image("cat.PNG", "image/png", 1024), "avatar");

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(put.capture());
        String object = put.getValue().object();
        assertEquals("c2c-sectrade", put.getValue().bucket());
        assertTrue(object.matches("avatar/\\d{4}/\\d{2}/\\d{2}/[0-9a-f-]{36}\\.PNG"), object);
        // public endpoint, not the Docker-internal one, and the trailing slash is not doubled
        assertEquals("http://203.0.113.10:9000/c2c-sectrade/" + object, url);
    }

    @Test
    @DisplayName("Without a public endpoint the URL falls back to the MinIO endpoint")
    void fallsBackToEndpoint() throws Exception {
        properties.setPublicEndpoint(" ");

        String url = storage.upload(image("clip", "video/mp4", 10), "product");

        assertTrue(url.startsWith("http://minio:9000/c2c-sectrade/product/"), url);
        assertFalse(url.endsWith("."), "a file without an extension gets none");
    }

    @Test
    @DisplayName("Empty files are rejected before anything is stored")
    void rejectsEmptyFile() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> storage.upload(image("a.png", "image/png", 0), "avatar"));
        assertThrows(IllegalArgumentException.class, () -> storage.upload(null, "avatar"));
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Files over 10MB are rejected")
    void rejectsOversizedFile() throws Exception {
        MockMultipartFile big = image("big.png", "image/png", 10 * 1024 * 1024 + 1);

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> storage.upload(big, "avatar"));
        assertTrue(error.getMessage().contains("10MB"));
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Only images and mp4 are accepted")
    void rejectsUnsupportedType() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> storage.upload(image("x.html", "text/html", 10), "product"));
        assertThrows(IllegalArgumentException.class,
                () -> storage.upload(image("x", null, 10), "product"));
        verify(minioClient, never()).putObject(any());
    }

    @Test
    @DisplayName("Delete removes the object from the configured bucket")
    void deleteRemovesObject() throws Exception {
        storage.delete("avatar/2026/10/01/abc.png");

        ArgumentCaptor<RemoveObjectArgs> remove = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(remove.capture());
        assertEquals("c2c-sectrade", remove.getValue().bucket());
        assertEquals("avatar/2026/10/01/abc.png", remove.getValue().object());
    }

    @Test
    @DisplayName("On startup a missing bucket is created and made publicly readable")
    void ensureBucketCreatesMissingBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        storage.ensureBucket();

        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
        verify(minioClient).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    @DisplayName("An existing bucket is reused")
    void ensureBucketReusesExistingBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        storage.ensureBucket();

        verify(minioClient, never()).makeBucket(any());
        verify(minioClient).setBucketPolicy(any(SetBucketPolicyArgs.class));
    }

    @Test
    @DisplayName("An unreachable MinIO does not stop the application from starting")
    void ensureBucketToleratesOutage() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenThrow(new RuntimeException("connection refused"));

        assertDoesNotThrow(() -> storage.ensureBucket());
    }
}
