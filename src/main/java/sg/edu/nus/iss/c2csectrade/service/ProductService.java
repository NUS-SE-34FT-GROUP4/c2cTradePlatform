package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.dto.ProductPublishRequest;
import sg.edu.nus.iss.c2csectrade.exception.ProductAccessDeniedException;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMediaMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductService {
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private ProductMediaMapper productMediaMapper;
    @Autowired
    private UserMapper userMapper;

    public Product getProductById(Long id) {
        Product product = productMapper.selectById(id);
        if (product != null) {
            List<ProductMedia> media = productMediaMapper.selectByProductId(id);
            product.setMedia(media);
        }
        return product;
    }

    public List<Product> listAllProducts() {
        List<Product> products = productMapper.selectAll();
        // 为每个商品加载媒体和用户信息
        for (Product product : products) {
            List<ProductMedia> media = productMediaMapper.selectByProductId(product.getId());
            product.setMedia(media);
        }
        return products;
    }

    /**
     * 带筛选条件的商品查询
     */
    public List<Product> listProductsWithFilters(String keyword, java.math.BigDecimal minPrice,
            java.math.BigDecimal maxPrice, Integer conditionLevel,
            String location, String category) {
        List<Product> products = productMapper.selectWithFilters(keyword, minPrice, maxPrice, conditionLevel, location,
                category);
        // 为每个商品加载媒体
        for (Product product : products) {
            List<ProductMedia> media = productMediaMapper.selectByProductId(product.getId());
            product.setMedia(media);
        }
        return products;
    }

    /**
     * Convert Product entity to ProductDTO
     */
    public ProductDTO convertToDTO(Product product) {
        if (product == null) {
            return null;
        }

        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId()); // id已经是Long类型，直接使用
        dto.setUserId(product.getUserId());
        dto.setName(product.getName());
        dto.setDescription(product.getDescription());
        dto.setPrice(product.getPrice());
        dto.setStock(product.getStock());
        dto.setConditionLevel(product.getConditionLevel());
        dto.setCategory(product.getCategory()); // 新增：商品分类
        dto.setLocation(product.getLocation());
        dto.setStatus(product.getStatus());
        dto.setCreateTime(product.getCreatedAt());
        dto.setUpdateTime(product.getUpdatedAt());

        // Get user info
        if (product.getUserId() != null) {
            User user = userMapper.selectById(product.getUserId());
            if (user != null) {
                dto.setUsername(user.getUsername());
                dto.setDisplayName(user.getDisplayName());
                dto.setAvatarUrl(user.getAvatarUrl()); // 新增：头像URL
            }
        }

        // Convert media list
        if (product.getMedia() != null && !product.getMedia().isEmpty()) {
            List<String> imageUrls = new ArrayList<>();
            List<String> videoUrls = new ArrayList<>();
            List<ProductDTO.MediaItem> mediaItems = new ArrayList<>();

            for (ProductMedia media : product.getMedia()) {
                // 添加到media列表（用于前端多图展示）
                ProductDTO.MediaItem mediaItem = new ProductDTO.MediaItem(
                        media.getId(), // id已经是Long类型
                        media.getUrl(),
                        media.getMediaType(),
                        media.getSortOrder());
                mediaItems.add(mediaItem);

                // 同时添加到imageUrls或videoUrls（向后兼容）
                if (media.getMediaType() == 1) {
                    imageUrls.add(media.getUrl());
                } else if (media.getMediaType() == 2) {
                    videoUrls.add(media.getUrl());
                }
            }

            dto.setMedia(mediaItems); // 设置media字段
            dto.setImageUrls(imageUrls);
            dto.setVideoUrls(videoUrls);

            // Set cover image as first image
            if (!imageUrls.isEmpty()) {
                dto.setCoverImage(imageUrls.get(0));
            }
        }

        return dto;
    }

    /**
     * Convert Product entity by ID to ProductDTO
     */
    public ProductDTO getProductDTOById(Long id) {
        Product product = getProductById(id);
        return convertToDTO(product);
    }

    /**
     * Convert list of Products to list of ProductDTOs
     */
    public List<ProductDTO> convertToDTOList(List<Product> products) {
        if (products == null) {
            return new ArrayList<>();
        }
        return products.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }


    /**
     * Get user by username
     */
    public User getUserByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    /** Listings owned by one seller, including delisted ones, for the seller's own view. */
    public List<Product> listBySeller(Long sellerId) {
        List<Product> products = productMapper.selectByUserId(sellerId);
        for (Product product : products) {
            product.setMedia(productMediaMapper.selectByProductId(product.getId()));
        }
        return products;
    }

    @Transactional
    public Product publish(ProductPublishRequest request, Long sellerId) {
        validate(request);
        Product product = new Product();
        product.setUserId(sellerId);
        applyEditableFields(product, request);
        product.setStatus(1);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productMapper.insert(product);
        replaceMedia(product.getId(), request);
        return getProductById(product.getId());
    }

    @Transactional
    public Product update(Long productId, ProductPublishRequest request, Long sellerId) {
        Product existing = requireOwned(productId, sellerId);
        validate(request);
        applyEditableFields(existing, request);
        existing.setUpdatedAt(LocalDateTime.now());
        productMapper.update(existing);
        if (request.getMedia() != null) {
            replaceMedia(productId, request);
        }
        return getProductById(productId);
    }

    /**
     * Delisting is a status change, not a delete: orders already placed keep
     * referencing the listing, and their price snapshot keeps them readable.
     */
    @Transactional
    public void delist(Long productId, Long sellerId) {
        Product existing = requireOwned(productId, sellerId);
        existing.setStatus(0);
        existing.setUpdatedAt(LocalDateTime.now());
        productMapper.update(existing);
    }

    private Product requireOwned(Long productId, Long sellerId) {
        Product existing = productMapper.selectById(productId);
        if (existing == null) {
            throw new IllegalArgumentException("Listing not found: " + productId);
        }
        if (!existing.getUserId().equals(sellerId)) {
            throw new ProductAccessDeniedException("You can only modify your own listings");
        }
        return existing;
    }

    private void applyEditableFields(Product product, ProductPublishRequest request) {
        product.setName(request.getName().strip());
        product.setDescription(HtmlSanitizer.clean(request.getDescription()));
        product.setPrice(request.getPrice());
        product.setStock(request.getStock() == null ? 1 : request.getStock());
        product.setConditionLevel(request.getConditionLevel() == null ? 9 : request.getConditionLevel());
        product.setLocation(request.getLocation());
        product.setCategory(request.getCategory() == null ? "other" : request.getCategory());
    }

    private void validate(ProductPublishRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("Listing name is required");
        }
        if (request.getPrice() == null || request.getPrice().signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero");
        }
        if (request.getStock() != null && request.getStock() < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        if (request.getConditionLevel() != null
                && (request.getConditionLevel() < 1 || request.getConditionLevel() > 10)) {
            throw new IllegalArgumentException("Condition must be between 1 and 10");
        }
    }

    /** Media arrives as an ordered list; the index is what makes the gallery order stable. */
    private void replaceMedia(Long productId, ProductPublishRequest request) {
        productMediaMapper.deleteByProductId(productId);
        if (request.getMedia() == null) {
            return;
        }
        Product owner = new Product();
        owner.setId(productId);
        int sortOrder = 0;
        for (ProductPublishRequest.MediaRef ref : request.getMedia()) {
            if (ref.getUrl() == null || ref.getUrl().isBlank()) {
                continue;
            }
            ProductMedia media = new ProductMedia();
            media.setProduct(owner);
            media.setUrl(ref.getUrl());
            media.setMediaType(ref.getMediaType() == null ? 1 : ref.getMediaType());
            media.setSortOrder(sortOrder++);
            productMediaMapper.insert(media);
        }
    }
}
