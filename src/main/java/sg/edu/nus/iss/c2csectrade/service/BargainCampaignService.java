package sg.edu.nus.iss.c2csectrade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.BargainCampaign;
import sg.edu.nus.iss.c2csectrade.entity.BargainCampaignHelper;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.BargainCampaignHelperMapper;
import sg.edu.nus.iss.c2csectrade.mapper.BargainCampaignMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Buyer-run price-cut campaigns on one listing.
 *
 * The money rules are platform-fixed, not per campaign: the floor sits at 60%
 * of the listing price, each helper cuts another 5% of it, and the campaign
 * opens at 80% unless the buyer names a price. Sellers have nowhere to set
 * their own floor yet; when they do, only {@link #floorPrice} changes.
 *
 * A campaign holds no stock and takes no money, so expiring or converting one
 * releases nothing - the sweep can run freely and idempotently.
 */
@Service
public class BargainCampaignService {

    private static final Logger log = LoggerFactory.getLogger(BargainCampaignService.class);

    static final String STATUS_ACTIVE = "ACTIVE";
    static final String STATUS_CONVERTED = "CONVERTED";

    private final BargainCampaignMapper campaignMapper;
    private final BargainCampaignHelperMapper helperMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;

    public BargainCampaignService(BargainCampaignMapper campaignMapper,
                                  BargainCampaignHelperMapper helperMapper,
                                  ProductMapper productMapper,
                                  UserMapper userMapper) {
        this.campaignMapper = campaignMapper;
        this.helperMapper = helperMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
    }

    /**
     * Opens a campaign for the buyer on one listing. One active campaign per
     * buyer per item; the start price must sit between the floor and the
     * listing price.
     */
    @Transactional
    public Map<String, Object> create(Long buyerId, Long productId, BigDecimal startPrice, LocalDateTime deadline) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Listing not found");
        }
        if (product.getStatus() != 1) {
            throw new IllegalArgumentException("Listing is no longer for sale");
        }
        if (product.getUserId().equals(buyerId)) {
            throw new IllegalArgumentException("You cannot bargain on your own listing");
        }
        if (campaignMapper.selectActiveByBuyerAndProduct(buyerId, productId) != null) {
            throw new IllegalStateException("You already have an active campaign on this item");
        }
        BigDecimal floor = floorPrice(product);
        BigDecimal start = startPrice == null ? fraction(product.getPrice(), 0.80) : startPrice;
        if (start.compareTo(floor) <= 0 || start.compareTo(product.getPrice()) > 0) {
            throw new IllegalArgumentException(
                    "Starting price must sit between the floor (" + floor + ") and the listing price");
        }
        if (deadline == null || !deadline.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Deadline must be in the future");
        }

        BargainCampaign campaign = new BargainCampaign();
        campaign.setProductId(productId);
        campaign.setBuyerId(buyerId);
        campaign.setStartPrice(start);
        campaign.setCurrentPrice(start);
        campaign.setFloorPrice(floor);
        campaign.setStatus(STATUS_ACTIVE);
        campaign.setDeadline(deadline);
        campaignMapper.insert(campaign);
        return view(campaign);
    }

    /**
     * A friend helps once per campaign, enforced by a unique key in the
     * database. Each help lowers the price by a bounded step that never
     * crosses the floor, however many helpers pile in.
     */
    @Transactional
    public Map<String, Object> help(Long campaignId, Long userId) {
        BargainCampaign campaign = campaignMapper.selectById(campaignId);
        if (campaign == null) {
            throw new IllegalArgumentException("Campaign not found");
        }
        if (!STATUS_ACTIVE.equals(campaign.getStatus()) || !campaign.getDeadline().isAfter(LocalDateTime.now())) {
            throw new IllegalStateException("Campaign is no longer active");
        }
        if (campaign.getBuyerId().equals(userId)) {
            throw new IllegalArgumentException("You cannot help your own campaign");
        }
        boolean helped = helperMapper.selectByCampaignId(campaignId).stream()
                .anyMatch(h -> h.getUserId().equals(userId));
        if (helped) {
            throw new IllegalStateException("You already helped this campaign");
        }

        BargainCampaignHelper helper = new BargainCampaignHelper();
        helper.setCampaignId(campaignId);
        helper.setUserId(userId);
        helperMapper.insert(helper);

        Product product = productMapper.selectById(campaign.getProductId());
        BigDecimal cut = fraction(product.getPrice(), 0.05);
        BigDecimal lowered = campaign.getCurrentPrice().subtract(cut).max(campaign.getFloorPrice());
        campaignMapper.updateCurrentPrice(campaignId, lowered);
        campaign.setCurrentPrice(lowered);
        return view(campaign);
    }

    /** Every campaign on the listing, expired and converted included. */
    public List<Map<String, Object>> listForProduct(Long productId) {
        return campaignMapper.selectByProductId(productId).stream().map(this::view).toList();
    }

    /**
     * Sweeps campaigns past their deadline. Same idea as the unpaid-order
     * sweep: idempotent, so running it repeatedly is harmless.
     */
    @Transactional
    public int expireDue() {
        int expired = campaignMapper.expireDue(LocalDateTime.now());
        if (expired > 0) {
            log.info("Expired {} bargain campaigns past their deadline", expired);
        }
        return expired;
    }

    /** The buyer closed a deal on this listing: retire their live campaign. */
    @Transactional
    public void markConverted(Long buyerId, Long productId) {
        campaignMapper.markConverted(buyerId, productId);
    }

    /** The floor is 60% of the listing price until sellers can set their own. */
    private BigDecimal floorPrice(Product product) {
        return fraction(product.getPrice(), 0.60);
    }

    private BigDecimal fraction(BigDecimal price, double factor) {
        return price.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.DOWN);
    }

    /**
     * Shaped to the S4-07 interface contract: buyer as an object, helper
     * display names, deadline as an ISO string the page can render directly.
     */
    private Map<String, Object> view(BargainCampaign campaign) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", campaign.getId());
        view.put("productId", campaign.getProductId());
        view.put("buyer", userView(campaign.getBuyerId(), false));
        view.put("startPrice", campaign.getStartPrice());
        view.put("currentPrice", campaign.getCurrentPrice());
        view.put("floorPrice", campaign.getFloorPrice());
        view.put("status", campaign.getStatus());
        view.put("deadline", campaign.getDeadline() == null ? null : campaign.getDeadline().toString());
        view.put("helpers", helperMapper.selectByCampaignId(campaign.getId()).stream()
                .map(h -> userView(h.getUserId(), true))
                .toList());
        return view;
    }

    private Map<String, Object> userView(Long userId, boolean nameOnly) {
        User user = userMapper.selectById(userId);
        String displayName = user == null ? "Unknown" : user.getDisplayName();
        return nameOnly ? Map.of("displayName", displayName) : Map.of("id", userId, "displayName", displayName);
    }
}
