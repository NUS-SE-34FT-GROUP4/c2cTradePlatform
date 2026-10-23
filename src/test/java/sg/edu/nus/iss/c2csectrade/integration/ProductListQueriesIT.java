package sg.edu.nus.iss.c2csectrade.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import sg.edu.nus.iss.c2csectrade.dto.ProductDTO;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.ProductMedia;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMediaMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.ProductService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The batched list queries against the real schema: a page of listings must
 * come back with exactly the media and seller that the one-at-a-time queries
 * return for each listing on its own.
 */
class ProductListQueriesIT extends MySqlIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ProductService products;
    @Autowired ProductMediaMapper media;
    @Autowired UserMapper users;

    private List<Long> listedIds;

    // Media is inserted through JdbcTemplate before any MyBatis query runs: the
    // test is one transaction, so a MyBatis query made earlier would be served
    // from the session cache and never see these rows.
    @BeforeEach
    void giveTwoListingsMedia() {
        List<Long> active = jdbc.queryForList(
                "SELECT id FROM pms_product WHERE status = 1 AND stock > 0 ORDER BY id", Long.class);
        assertTrue(active.size() >= 2, "init.sql seeds active listings");
        Long first = active.get(0);
        Long second = active.get(1);
        jdbc.update("INSERT INTO pms_product_media (url, media_type, sort_order, product_id) VALUES (?, 1, 0, ?)", "/it/a.jpg", first);
        jdbc.update("INSERT INTO pms_product_media (url, media_type, sort_order, product_id) VALUES (?, 1, 1, ?)", "/it/b.jpg", first);
        jdbc.update("INSERT INTO pms_product_media (url, media_type, sort_order, product_id) VALUES (?, 2, 0, ?)", "/it/c.mp4", second);
        listedIds = products.listProductsWithFilters(null, null, null, null, null, null)
                .stream().map(Product::getId).toList();
        firstWithMedia = first;
        secondWithMedia = second;
    }

    private Long firstWithMedia;
    private Long secondWithMedia;

    @Test
    @DisplayName("Batched media query returns each listing's own rows, linked to the right listing")
    void batchedMediaMatchesPerListingMedia() {
        List<ProductMedia> batched = media.selectByProductIds(listedIds);
        for (Long id : listedIds) {
            List<String> single = media.selectByProductId(id).stream().map(ProductMedia::getUrl).sorted().toList();
            List<String> fromBatch = batched.stream()
                    .filter(m -> m.getProduct() != null && id.equals(m.getProduct().getId()))
                    .map(ProductMedia::getUrl).sorted().toList();
            assertEquals(single, fromBatch, "media of listing " + id);
        }
    }

    @Test
    @DisplayName("Batched seller query returns the same users as one-by-one lookups")
    void batchedSellersMatchSingleLookups() {
        List<Long> sellerIds = products.listProductsWithFilters(null, null, null, null, null, null)
                .stream().map(Product::getUserId).distinct().toList();
        List<User> batched = users.selectByIds(sellerIds);
        assertEquals(sellerIds.size(), batched.size());
        for (User user : batched) {
            assertEquals(users.selectById(user.getId()).getUsername(), user.getUsername());
        }
    }

    @Test
    @DisplayName("A listed page renders exactly like each listing's detail view")
    void listedPageMatchesDetailView() {
        List<ProductDTO> page = products.convertToDTOList(
                products.listProductsWithFilters(null, null, null, null, null, null));
        assertEquals(listedIds.size(), page.size());
        for (ProductDTO listed : page) {
            ProductDTO detail = products.getProductDTOById(listed.getId());
            assertEquals(detail.getUsername(), listed.getUsername(), "seller of " + listed.getId());
            assertEquals(detail.getDisplayName(), listed.getDisplayName());
            assertEquals(detail.getImageUrls(), listed.getImageUrls(), "images of " + listed.getId());
            assertEquals(detail.getVideoUrls(), listed.getVideoUrls(), "videos of " + listed.getId());
            assertEquals(detail.getCoverImage(), listed.getCoverImage());
        }
        // init.sql seeds media too, so check ours are among them rather than all of them
        ProductDTO first = page.stream().filter(p -> p.getId().equals(firstWithMedia)).findFirst().orElseThrow();
        ProductDTO second = page.stream().filter(p -> p.getId().equals(secondWithMedia)).findFirst().orElseThrow();
        assertTrue(first.getImageUrls().containsAll(List.of("/it/a.jpg", "/it/b.jpg")));
        assertTrue(second.getVideoUrls().contains("/it/c.mp4"));
    }
}
