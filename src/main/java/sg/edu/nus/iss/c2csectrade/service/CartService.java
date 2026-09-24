package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.CartItem;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.CartItemMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;

import java.util.List;

/**
 * The cart holds intent, not price. Quantities are checked against available
 * stock when they change, but nothing is reserved until checkout — leaving an
 * item in a cart must not stop anyone else from buying it.
 */
@Service
public class CartService {

    private final CartItemMapper cartItemMapper;
    private final ProductMapper productMapper;

    public CartService(CartItemMapper cartItemMapper, ProductMapper productMapper) {
        this.cartItemMapper = cartItemMapper;
        this.productMapper = productMapper;
    }

    public List<CartItem> list(Long userId) {
        return cartItemMapper.selectByUserId(userId);
    }

    /** Adding an item already in the cart increases its quantity instead of duplicating the line. */
    @Transactional
    public CartItem add(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be at least 1");
        }
        Product product = requireBuyable(productId);
        if (product.getUserId().equals(userId)) {
            throw new IllegalArgumentException("You cannot buy your own listing");
        }

        CartItem existing = cartItemMapper.selectByUserAndProduct(userId, productId);
        int target = existing == null ? quantity : existing.getQuantity() + quantity;
        requireAvailable(product, target);

        if (existing == null) {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setProductId(productId);
            item.setQuantity(target);
            cartItemMapper.insert(item);
            return cartItemMapper.selectById(item.getId());
        }
        cartItemMapper.updateQuantity(existing.getId(), target);
        return cartItemMapper.selectById(existing.getId());
    }

    @Transactional
    public CartItem updateQuantity(Long userId, Long cartItemId, int quantity) {
        CartItem item = requireOwned(userId, cartItemId);
        if (quantity <= 0) {
            cartItemMapper.deleteById(item.getId());
            return null;
        }
        requireAvailable(requireBuyable(item.getProductId()), quantity);
        cartItemMapper.updateQuantity(item.getId(), quantity);
        return cartItemMapper.selectById(item.getId());
    }

    @Transactional
    public void remove(Long userId, Long cartItemId) {
        cartItemMapper.deleteById(requireOwned(userId, cartItemId).getId());
    }

    @Transactional
    public void clear(Long userId) {
        cartItemMapper.deleteByUserId(userId);
    }

    private CartItem requireOwned(Long userId, Long cartItemId) {
        CartItem item = cartItemMapper.selectById(cartItemId);
        if (item == null || !item.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cart item not found");
        }
        return item;
    }

    private Product requireBuyable(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Listing not found");
        }
        if (product.getStatus() != 1) {
            throw new IllegalArgumentException("Listing is no longer for sale: " + product.getName());
        }
        return product;
    }

    private void requireAvailable(Product product, int wanted) {
        if (product.getAvailableStock() < wanted) {
            throw new InsufficientStockException(
                    "Only " + product.getAvailableStock() + " left of " + product.getName());
        }
    }
}
