package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Product implements Serializable {
    private Long id;  // 修改为Long以匹配数据库BIGINT类型
    private Long userId;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer conditionLevel;
    private String location; // 新增：位置
    private String category; // 新增：商品分类
    private int stock;
    private int reservedStock;
    private int status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ProductMedia> media;

    /** What a buyer may actually order: total stock less what unpaid orders are holding. */
    public int getAvailableStock() {
        return stock - reservedStock;
    }
}
