package sg.edu.nus.iss.c2csectrade.service.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.InnerField;
import org.springframework.data.elasticsearch.annotations.MultiField;
import sg.edu.nus.iss.c2csectrade.entity.Product;

/**
 * Search projection of a listing. MySQL owns the canonical Product record;
 * this document contains only fields needed to rank and filter catalogue hits.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "products", createIndex = false)
public class ProductSearchDocument {

    @Id
    private Long id;

    @Field(type = FieldType.Long)
    private Long sellerId;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private Double price;

    @Field(type = FieldType.Integer)
    private Integer conditionLevel;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String location;

    @MultiField(
            mainField = @Field(type = FieldType.Text),
            otherFields = @InnerField(suffix = "keyword", type = FieldType.Keyword))
    private String category;

    @Field(type = FieldType.Integer)
    private Integer availableStock;

    @Field(type = FieldType.Integer)
    private Integer status;

    @Field(type = FieldType.Keyword)
    private String createdAt;

    @Field(type = FieldType.Keyword)
    private String updatedAt;

    public static ProductSearchDocument from(Product product) {
        return new ProductSearchDocument(
                product.getId(),
                product.getUserId(),
                product.getName(),
                product.getDescription(),
                product.getPrice() == null ? null : product.getPrice().doubleValue(),
                product.getConditionLevel(),
                product.getLocation(),
                product.getCategory(),
                product.getAvailableStock(),
                product.getStatus(),
                product.getCreatedAt() == null ? null : product.getCreatedAt().toString(),
                product.getUpdatedAt() == null ? null : product.getUpdatedAt().toString());
    }
}
