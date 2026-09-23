package sg.edu.nus.iss.c2csectrade.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "minio")
public class MinioProperties {
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String bucketName;
    /** Host the browser should use. Inside Docker the endpoint is not reachable from the user's machine. */
    private String publicEndpoint;
}
