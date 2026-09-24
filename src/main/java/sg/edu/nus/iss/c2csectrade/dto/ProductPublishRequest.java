package sg.edu.nus.iss.c2csectrade.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** Payload for publishing or editing a listing. */
@Data
public class ProductPublishRequest {
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer conditionLevel;
    private String location;
    private String category;
    /** Media URLs in the order the seller arranged them; index becomes sortOrder. */
    private List<MediaRef> media;

    @Data
    public static class MediaRef {
        private String url;
        /** 1 = image, 2 = video */
        private Integer mediaType;
    }
}
