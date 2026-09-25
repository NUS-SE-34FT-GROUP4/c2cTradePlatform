package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.CartItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock private CartItemMapper cartItemMapper;
    @Mock private ProductMapper productMapper;
    @InjectMocks private CartService cartService;

    private Product listing(int stock, int reserved) {
        Product product = new Product();
        product.setId(101L);
        product.setUserId(20L);
        product.setName("Textbook");
        product.setPrice(new BigDecimal("30.00"));
        product.setStock(stock);
        product.setReservedStock(reserved);
        product.setStatus(1);
        return product;
    }

    @Test
    @DisplayName("Adding a product already in the cart raises its quantity instead of duplicating the line")
    void addMergesIntoExistingLine() {
        when(productMapper.selectById(101L)).thenReturn(listing(10, 0));
        CartItem existing = new CartItem();
        existing.setId(7L);
        existing.setUserId(1L);
        existing.setProductId(101L);
        existing.setQuantity(2);
        when(cartItemMapper.selectByUserAndProduct(1L, 101L)).thenReturn(existing);

        cartService.add(1L, 101L, 3);

        verify(cartItemMapper).updateQuantity(7L, 5);
        verify(cartItemMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Availability is total stock less what unpaid orders hold")
    void addRespectsReservedStock() {
        // 5 in stock but 4 already held by unpaid orders, so only 1 is buyable.
        when(productMapper.selectById(101L)).thenReturn(listing(5, 4));
        when(cartItemMapper.selectByUserAndProduct(1L, 101L)).thenReturn(null);

        InsufficientStockException error =
                assertThrows(InsufficientStockException.class, () -> cartService.add(1L, 101L, 2));
        assertTrue(error.getMessage().contains("Only 1 left"));
    }

    @Test
    @DisplayName("A seller cannot add their own listing to their cart")
    void rejectsOwnListing() {
        when(productMapper.selectById(101L)).thenReturn(listing(5, 0));
        assertThrows(IllegalArgumentException.class, () -> cartService.add(20L, 101L, 1));
    }

    @Test
    @DisplayName("A delisted product cannot be added")
    void rejectsDelistedListing() {
        Product delisted = listing(5, 0);
        delisted.setStatus(0);
        when(productMapper.selectById(101L)).thenReturn(delisted);
        assertThrows(IllegalArgumentException.class, () -> cartService.add(1L, 101L, 1));
    }

    @Test
    @DisplayName("Setting a quantity of zero removes the line rather than storing zero")
    void zeroQuantityRemovesLine() {
        CartItem item = new CartItem();
        item.setId(7L);
        item.setUserId(1L);
        item.setProductId(101L);
        when(cartItemMapper.selectById(7L)).thenReturn(item);

        assertNull(cartService.updateQuantity(1L, 7L, 0));
        verify(cartItemMapper).deleteById(7L);
    }

    @Test
    @DisplayName("A cart line belonging to someone else cannot be touched")
    void rejectsOtherUsersLine() {
        CartItem item = new CartItem();
        item.setId(7L);
        item.setUserId(999L);
        when(cartItemMapper.selectById(7L)).thenReturn(item);

        assertThrows(IllegalArgumentException.class, () -> cartService.remove(1L, 7L));
        verify(cartItemMapper, never()).deleteById(anyLong());
    }
}
