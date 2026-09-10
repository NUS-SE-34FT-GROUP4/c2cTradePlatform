package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMediaMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for item browsing (Sprint 1 - item list and item detail).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductMapper productMapper;
    @Mock
    private ProductMediaMapper productMediaMapper;
    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private ProductService productService;

    private Product sampleProduct() {
        Product p = new Product();
        p.setId(1L);
        p.setUserId(101L);
        p.setName("九成新 iPhone 13");
        p.setDescription("自用一年，无磕碰");
        p.setPrice(new BigDecimal("2899.00"));
        p.setStock(1);
        p.setConditionLevel(9);
        p.setLocation("北京市");
        p.setCategory("electronics");
        p.setStatus(1);
        return p;
    }

    @Test
    @DisplayName("Converting a null product yields null rather than throwing")
    void convertNullProductReturnsNull() {
        assertNull(productService.convertToDTO(null));
    }

    @Test
    @DisplayName("Converting a product maps every catalogue field onto the DTO")
    void convertProductMapsFields() {
        User seller = new User();
        seller.setId(101L);
        seller.setUsername("seller_lvl1");
        seller.setDisplayName("Seller Level 1");
        when(userMapper.selectById(101L)).thenReturn(seller);

        ProductDTO dto = productService.convertToDTO(sampleProduct());

        assertEquals(1L, dto.getId());
        assertEquals("九成新 iPhone 13", dto.getName());
        assertEquals(new BigDecimal("2899.00"), dto.getPrice());
        assertEquals(9, dto.getConditionLevel());
        assertEquals("北京市", dto.getLocation());
        assertEquals("electronics", dto.getCategory());
        assertEquals(1, dto.getStatus());
    }

    @Test
    @DisplayName("Converting a product resolves the seller's display name")
    void convertProductResolvesSeller() {
        User seller = new User();
        seller.setId(101L);
        seller.setUsername("seller_lvl1");
        seller.setDisplayName("Seller Level 1");
        when(userMapper.selectById(101L)).thenReturn(seller);

        ProductDTO dto = productService.convertToDTO(sampleProduct());

        assertEquals("seller_lvl1", dto.getUsername());
        assertEquals("Seller Level 1", dto.getDisplayName());
    }

    @Test
    @DisplayName("An unknown seller leaves the DTO's seller fields empty instead of failing")
    void convertProductToleratesMissingSeller() {
        when(userMapper.selectById(101L)).thenReturn(null);

        ProductDTO dto = productService.convertToDTO(sampleProduct());

        assertNull(dto.getUsername());
        assertEquals("九成新 iPhone 13", dto.getName());
    }

    @Test
    @DisplayName("Fetching an item by id attaches its media")
    void getProductByIdAttachesMedia() {
        ProductMedia media = new ProductMedia();
        media.setId(10L);
        media.setMediaType(1);
        media.setUrl("https://example.test/1.jpg");
        when(productMapper.selectById(1L)).thenReturn(sampleProduct());
        when(productMediaMapper.selectByProductId(1L)).thenReturn(List.of(media));

        Product result = productService.getProductById(1L);

        assertNotNull(result);
        assertEquals(1, result.getMedia().size());
        assertEquals("https://example.test/1.jpg", result.getMedia().get(0).getUrl());
    }

    @Test
    @DisplayName("Fetching a non-existent item returns null and skips the media lookup")
    void getProductByIdReturnsNullWhenAbsent() {
        when(productMapper.selectById(99L)).thenReturn(null);

        assertNull(productService.getProductById(99L));
        verify(productMediaMapper, never()).selectByProductId(any());
    }

    @Test
    @DisplayName("Filtered listing delegates the criteria to the mapper")
    void listProductsWithFiltersDelegatesCriteria() {
        when(productMapper.selectWithFilters(eq("iPhone"), any(), any(), eq(9), eq("北京市"), eq("electronics")))
                .thenReturn(List.of(sampleProduct()));
        when(productMediaMapper.selectByProductId(1L)).thenReturn(List.of());

        List<Product> products = productService.listProductsWithFilters(
                "iPhone", null, null, 9, "北京市", "electronics");

        assertEquals(1, products.size());
        verify(productMapper).selectWithFilters("iPhone", null, null, 9, "北京市", "electronics");
    }

    @Test
    @DisplayName("Converting a list of products preserves order and size")
    void convertToDTOListPreservesOrder() {
        Product second = sampleProduct();
        second.setId(2L);
        second.setName("ThinkPad X1 Carbon");
        when(userMapper.selectById(101L)).thenReturn(null);

        List<ProductDTO> dtos = productService.convertToDTOList(List.of(sampleProduct(), second));

        assertEquals(2, dtos.size());
        assertEquals(1L, dtos.get(0).getId());
        assertEquals("ThinkPad X1 Carbon", dtos.get(1).getName());
    }
}
