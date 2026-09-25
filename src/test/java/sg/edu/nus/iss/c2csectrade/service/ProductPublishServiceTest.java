package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.dto.ProductPublishRequest;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import sg.edu.nus.iss.c2csectrade.exception.ProductAccessDeniedException;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMediaMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Sprint 2 acceptance checks for publishing, editing and delisting. */
@ExtendWith(MockitoExtension.class)
class ProductPublishServiceTest {

    @Mock private ProductMapper productMapper;
    @Mock private ProductMediaMapper productMediaMapper;
    @Mock private UserMapper userMapper;
    @InjectMocks private ProductService productService;

    private ProductPublishRequest request(String name, String price) {
        ProductPublishRequest request = new ProductPublishRequest();
        request.setName(name);
        request.setPrice(price == null ? null : new BigDecimal(price));
        request.setStock(1);
        request.setConditionLevel(9);
        request.setCategory("books");
        return request;
    }

    @Test
    @DisplayName("Publishing strips markup out of the description")
    void sanitisesDescription() {
        ProductPublishRequest request = request("Textbook", "30.00");
        request.setDescription("Good condition <script>alert('x')</script><b>really</b>");
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            invocation.<Product>getArgument(0).setId(1L);
            return 1;
        });

        productService.publish(request, 20L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).insert(captor.capture());
        String stored = captor.getValue().getDescription();
        assertFalse(stored.contains("<script>"));
        assertFalse(stored.contains("alert"));
        assertTrue(stored.contains("really"));
    }

    @Test
    @DisplayName("Media order in the payload becomes sort order")
    void mediaKeepsSellerOrdering() {
        ProductPublishRequest request = request("Textbook", "30.00");
        ProductPublishRequest.MediaRef first = new ProductPublishRequest.MediaRef();
        first.setUrl("https://example.test/a.jpg");
        ProductPublishRequest.MediaRef second = new ProductPublishRequest.MediaRef();
        second.setUrl("https://example.test/b.jpg");
        request.setMedia(List.of(first, second));
        when(productMapper.insert(any(Product.class))).thenAnswer(invocation -> {
            invocation.<Product>getArgument(0).setId(1L);
            return 1;
        });

        productService.publish(request, 20L);

        ArgumentCaptor<ProductMedia> captor = ArgumentCaptor.forClass(ProductMedia.class);
        verify(productMediaMapper, times(2)).insert(captor.capture());
        assertEquals(0, captor.getAllValues().get(0).getSortOrder());
        assertEquals("https://example.test/a.jpg", captor.getAllValues().get(0).getUrl());
        assertEquals(1, captor.getAllValues().get(1).getSortOrder());
    }

    @Test
    @DisplayName("A seller cannot edit someone else's listing")
    void rejectsEditingOthersListing() {
        Product other = new Product();
        other.setId(101L);
        other.setUserId(999L);
        when(productMapper.selectById(101L)).thenReturn(other);

        assertThrows(ProductAccessDeniedException.class,
                () -> productService.update(101L, request("Renamed", "10.00"), 20L));
        verify(productMapper, never()).update(any());
    }

    @Test
    @DisplayName("Delisting sets the status and keeps the row, so existing orders still resolve")
    void delistKeepsRow() {
        Product own = new Product();
        own.setId(101L);
        own.setUserId(20L);
        own.setStatus(1);
        when(productMapper.selectById(101L)).thenReturn(own);

        productService.delist(101L, 20L);

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productMapper).update(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(productMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("A listing priced at zero or below is refused")
    void rejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () -> productService.publish(request("Free", "0"), 20L));
        assertThrows(IllegalArgumentException.class, () -> productService.publish(request("Blank", null), 20L));
    }
}
