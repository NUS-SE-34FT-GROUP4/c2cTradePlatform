package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Shared upload capability. Avatars (WP1) and product media (WP2) both go
 * through this, so there is one place that decides where bytes live.
 */
public interface FileStorageService {

    /**
     * @param folder logical prefix, e.g. "avatar" or "product"
     * @return a URL the browser can load
     */
    String upload(MultipartFile file, String folder) throws Exception;

    void delete(String objectName) throws Exception;
}
