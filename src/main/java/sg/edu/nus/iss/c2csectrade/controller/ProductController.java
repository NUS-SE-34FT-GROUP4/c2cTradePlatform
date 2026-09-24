package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.dto.ProductPublishRequest;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.ProductAccessDeniedException;
import sg.edu.nus.iss.c2csectrade.service.FileStorageService;
import sg.edu.nus.iss.c2csectrade.service.ProductService;
import sg.edu.nus.iss.c2csectrade.service.SearchService;
import org.springframework.security.core.Authentication;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductService productService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private FileStorageService fileStorageService;

    @GetMapping
    public ResponseEntity<List<ProductDTO>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) java.math.BigDecimal minPrice,
            @RequestParam(required = false) java.math.BigDecimal maxPrice,
            @RequestParam(required = false) Integer conditionLevel,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String categories) {
        try {
            List<Product> products = productService.listProductsWithFilters(keyword, minPrice, maxPrice,
                    conditionLevel, location, categories);
            List<ProductDTO> productDTOs = productService.convertToDTOList(products);
            return ResponseEntity.ok(searchService.highlight(productDTOs, keyword));
        } catch (Exception e) {
            System.err.println("获取商品列表失败: " + e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProductById(@PathVariable Long id) {
        try {
            ProductDTO productDTO = productService.getProductDTOById(id);
            if (productDTO == null) {
                return new ResponseEntity<>("Product not found", HttpStatus.NOT_FOUND);
            }
            return ResponseEntity.ok(productDTO);
        } catch (Exception e) {
            return new ResponseEntity<>("Error fetching product: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping
    public ResponseEntity<?> publish(@RequestBody ProductPublishRequest request, Authentication authentication) {
        return asSeller(authentication, sellerId ->
                ResponseEntity.status(HttpStatus.CREATED)
                        .body(productService.convertToDTO(productService.publish(request, sellerId))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @RequestBody ProductPublishRequest request,
                                    Authentication authentication) {
        return asSeller(authentication, sellerId ->
                ResponseEntity.ok(productService.convertToDTO(productService.update(id, request, sellerId))));
    }

    /** Delisting keeps the row so that existing orders still resolve. */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delist(@PathVariable Long id, Authentication authentication) {
        return asSeller(authentication, sellerId -> {
            productService.delist(id, sellerId);
            return ResponseEntity.ok(Map.of("message", "Listing delisted"));
        });
    }

    /** The seller's own listings, delisted ones included. */
    @GetMapping("/mine")
    public ResponseEntity<?> myListings(Authentication authentication) {
        return asSeller(authentication, sellerId ->
                ResponseEntity.ok(productService.convertToDTOList(productService.listBySeller(sellerId))));
    }

    /** Uploads one media file and returns its URL for the publish payload. */
    @PostMapping("/media")
    public ResponseEntity<?> uploadMedia(@RequestParam("file") MultipartFile file, Authentication authentication) {
        return asSeller(authentication, sellerId -> {
            try {
                return ResponseEntity.ok(Map.of("url", fileStorageService.upload(file, "product")));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
            } catch (Exception e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("message", "Upload failed: " + e.getMessage()));
            }
        });
    }

    private ResponseEntity<?> asSeller(Authentication authentication,
                                       java.util.function.Function<Long, ResponseEntity<?>> action) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Not authenticated"));
        }
        User seller = productService.getUserByUsername(authentication.getName());
        if (seller == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Unknown user"));
        }
        try {
            return action.apply(seller.getId());
        } catch (ProductAccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}