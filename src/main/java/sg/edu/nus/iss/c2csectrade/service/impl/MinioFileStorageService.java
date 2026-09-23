package sg.edu.nus.iss.c2csectrade.service.impl;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sg.edu.nus.iss.c2csectrade.config.MinioProperties;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class MinioFileStorageService implements FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioFileStorageService.class);
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final Set<String> ALLOWED_TYPES =
            Set.of("image/jpeg", "image/png", "image/gif", "image/webp", "video/mp4");
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    private final MinioClient minioClient;
    private final MinioProperties properties;

    public MinioFileStorageService(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            String bucket = properties.getBucketName();
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket {}", bucket);
            }
            minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                    .bucket(bucket)
                    .config(publicReadPolicy(bucket))
                    .build());
        } catch (Exception e) {
            // A missing object store must not stop the application from booting:
            // everything except upload still works without it.
            log.warn("MinIO bucket initialisation failed, uploads will be unavailable: {}", e.getMessage());
        }
    }

    @Override
    public String upload(MultipartFile file, String folder) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("File exceeds the 10MB limit");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }

        String objectName = folder + "/" + LocalDate.now().format(DATE_PATH) + "/"
                + UUID.randomUUID() + extensionOf(file.getOriginalFilename());

        try (var in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(properties.getBucketName())
                    .object(objectName)
                    .stream(in, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
        }
        return publicUrl(objectName);
    }

    @Override
    public void delete(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(properties.getBucketName())
                .object(objectName)
                .build());
    }

    /**
     * The backend talks to MinIO over the Docker network, but the URL is handed
     * to a browser that cannot resolve that host, so a separate public endpoint
     * is configured for what we hand out.
     */
    private String publicUrl(String objectName) {
        String base = properties.getPublicEndpoint() != null && !properties.getPublicEndpoint().isBlank()
                ? properties.getPublicEndpoint()
                : properties.getEndpoint();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/" + properties.getBucketName() + "/" + objectName;
    }

    private String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private String publicReadPolicy(String bucket) {
        return "{\"Version\":\"2012-10-17\",\"Statement\":[{"
                + "\"Effect\":\"Allow\",\"Principal\":{\"AWS\":\"*\"},"
                + "\"Action\":[\"s3:GetObject\"],"
                + "\"Resource\":[\"arn:aws:s3:::" + bucket + "/*\"]}]}";
    }
}
