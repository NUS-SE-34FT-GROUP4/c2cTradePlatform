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

    @Mock private CreditService creditService;

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
        when(productMediaMapper.selectByProductIds(List.of(1L))).thenReturn(List.of());

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
        when(userMapper.selectByIds(List.of(101L))).thenReturn(List.of());

        List<ProductDTO> dtos = productService.convertToDTOList(List.of(sampleProduct(), second));

        assertEquals(2, dtos.size());
        assertEquals(1L, dtos.get(0).getId());
        assertEquals("ThinkPad X1 Carbon", dtos.get(1).getName());
    }

    // ---- One query per page, not one per listing (N+1 fix) ----

    private Product productOf(long id, long sellerId) {
        Product p = sampleProduct();
        p.setId(id);
        p.setUserId(sellerId);
        return p;
    }

    private ProductMedia mediaOf(long id, long productId, String url) {
        ProductMedia m = new ProductMedia();
        m.setId(id);
        m.setUrl(url);
        m.setMediaType(1);
        Product owner = new Product();
        owner.setId(productId);
        m.setProduct(owner);
        return m;
    }

    @Test
    @DisplayName("A page of listings loads all its media in one query and gives each listing its own")
    void listingMediaIsLoadedOnceAndGroupedPerListing() {
        when(productMapper.selectWithFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(productOf(1, 101), productOf(2, 102), productOf(3, 101)));
        when(productMediaMapper.selectByProductIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                mediaOf(10, 1, "/a.jpg"), mediaOf(11, 2, "/b.jpg"), mediaOf(12, 1, "/c.jpg")));

        List<Product> products = productService.listProductsWithFilters(null, null, null, null, null, null);

        assertEquals(List.of("/a.jpg", "/c.jpg"), products.get(0).getMedia().stream().map(ProductMedia::getUrl).toList());
        assertEquals(List.of("/b.jpg"), products.get(1).getMedia().stream().map(ProductMedia::getUrl).toList());
        assertTrue(products.get(2).getMedia().isEmpty(), "a listing without media gets an empty list, not null");
        verify(productMediaMapper, times(1)).selectByProductIds(any());
        verify(productMediaMapper, never()).selectByProductId(any());
    }

    @Test
    @DisplayName("Converting a page loads every seller in one query, once per distinct seller")
    void sellersAreLoadedOncePerPage() {
        User alice = new User();
        alice.setId(101L);
        alice.setUsername("alice");
        User bob = new User();
        bob.setId(102L);
        bob.setUsername("bob");
        when(userMapper.selectByIds(List.of(101L, 102L))).thenReturn(List.of(alice, bob));

        List<ProductDTO> dtos = productService.convertToDTOList(
                List.of(productOf(1, 101), productOf(2, 102), productOf(3, 101)));

        assertEquals(List.of("alice", "bob", "alice"), dtos.stream().map(ProductDTO::getUsername).toList());
        verify(userMapper, times(1)).selectByIds(any());
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("Search results keep their relevance order and load media in one query")
    void rankedListingsKeepOrderWithOneMediaQuery() {
        when(productMapper.selectByIds(List.of(3L, 1L))).thenReturn(List.of(productOf(1, 101), productOf(3, 101)));
        when(productMediaMapper.selectByProductIds(List.of(3L, 1L))).thenReturn(List.of(mediaOf(30, 3, "/x.jpg")));

        List<Product> products = productService.listProductsByIdsInOrder(List.of(3L, 1L));

        assertEquals(List.of(3L, 1L), products.stream().map(Product::getId).toList());
        assertEquals("/x.jpg", products.get(0).getMedia().get(0).getUrl());
        verify(productMediaMapper, times(1)).selectByProductIds(any());
    }

    @Test
    @DisplayName("An empty page sends no media or seller query at all (IN () is invalid SQL)")
    void emptyPageSendsNoBatchQueries() {
        when(productMapper.selectWithFilters(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        List<Product> products = productService.listProductsWithFilters(null, null, null, null, null, null);
        productService.convertToDTOList(products);

        assertTrue(products.isEmpty());
        verifyNoInteractions(productMediaMapper, userMapper);
    }
}
