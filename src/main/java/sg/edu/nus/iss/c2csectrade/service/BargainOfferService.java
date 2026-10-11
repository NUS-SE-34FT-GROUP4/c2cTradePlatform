package sg.edu.nus.iss.c2csectrade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sg.edu.nus.iss.c2csectrade.entity.BargainOffer;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.BargainOfferMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Offer threads: a buyer names a price, the parties counter, and an accepted
 * offer becomes a real order at the negotiated price.
 *
 * A thread is (product, buyer); the latest entry's status is the state of the
 * thread. Accepting goes through the same checkout the cart uses, so stock is
 * reserved by the same conditional update - two buyers racing to accept on
 * the last unit cannot both win, and the loser sees 409. Notifications ride
 * the existing notification service, never a second path.
 */
@Service
public class BargainOfferService {

    private static final Logger log = LoggerFactory.getLogger(BargainOfferService.class);

    static final String FROM_BUYER = "BUYER";
    static final String FROM_SELLER = "SELLER";

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_COUNTERED = "COUNTERED";
    static final String STATUS_ACCEPTED = "ACCEPTED";
    static final String STATUS_REJECTED = "REJECTED";

    private final BargainOfferMapper offerMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final OrderService orderService;
    private final BargainCampaignService campaignService;
    private final NotificationService notifications;

    public BargainOfferService(BargainOfferMapper offerMapper,
                               ProductMapper productMapper,
                               UserMapper userMapper,
                               OrderService orderService,
                               BargainCampaignService campaignService,
                               NotificationService notifications) {
        this.offerMapper = offerMapper;
        this.productMapper = productMapper;
        this.userMapper = userMapper;
        this.orderService = orderService;
        this.campaignService = campaignService;
        this.notifications = notifications;
    }

    /** The buyer opens or reopens a thread by naming a price. */
    @Transactional
    public Map<String, Object> makeOffer(Long buyerId, Long productId, BigDecimal price) {
        Product product = requireListed(productId);
        if (product.getUserId().equals(buyerId)) {
            throw new IllegalArgumentException("You cannot make an offer on your own listing");
        }
        requirePositive(price);
        List<BargainOffer> thread = offerMapper.selectThread(productId, buyerId);
        requireThreadOpen(thread);

        BargainOffer offer = new BargainOffer();
        offer.setProductId(productId);
        offer.setBuyerId(buyerId);
        offer.setSellerId(product.getUserId());
        offer.setFrom(FROM_BUYER);
        offer.setPrice(price);
        offer.setStatus(STATUS_PENDING);
        supersedeLatest(thread);
        offerMapper.insert(offer);
        log.info("Buyer {} offered {} on listing {}", buyerId, price, productId);

        notifications.deliver(product.getUserId(), null,
                "You received an offer of " + price + " for \"" + product.getName() + "\".");
        return view(offerMapper.selectById(offer.getId()));
    }

    /** Either party answers the latest pending offer with a new price. */
    @Transactional
    public Map<String, Object> counter(Long offerId, Long userId, BigDecimal price) {
        BargainOffer offer = requireParty(offerId, userId);
        List<BargainOffer> thread = offerMapper.selectThread(offer.getProductId(), offer.getBuyerId());
        requireLatestAndPending(thread, offer);
        requirePositive(price);
        requireListed(offer.getProductId());

        BargainOffer reply = new BargainOffer();
        reply.setProductId(offer.getProductId());
        reply.setBuyerId(offer.getBuyerId());
        reply.setSellerId(offer.getSellerId());
        reply.setFrom(offer.getBuyerId().equals(userId) ? FROM_BUYER : FROM_SELLER);
        reply.setPrice(price);
        reply.setStatus(STATUS_PENDING);
        supersedeLatest(thread);
        offerMapper.insert(reply);

        Product product = productMapper.selectById(offer.getProductId());
        Long other = offer.getBuyerId().equals(userId) ? offer.getSellerId() : offer.getBuyerId();
        notifications.deliver(other, null,
                "Your offer on \"" + (product == null ? "a listing" : product.getName())
                        + "\" received a counter of " + price + ".");
        return view(offerMapper.selectById(reply.getId()));
    }

    /**
     * The side that did not make the offer accepts it: a real order is created
     * at the negotiated price, stock reserved like any other checkout, the
     * thread closes, and the buyer's live campaign on this listing, if any,
     * becomes CONVERTED.
     */
    @Transactional
    public Map<String, Object> accept(Long offerId, Long userId) {
        BargainOffer offer = requireParty(offerId, userId);
        List<BargainOffer> thread = offerMapper.selectThread(offer.getProductId(), offer.getBuyerId());
        requireLatestAndPending(thread, offer);
        requireCounterparty(offer, userId);

        Product product = productMapper.selectById(offer.getProductId());
        if (product == null) {
            throw new IllegalArgumentException("Listing not found");
        }
        Order order;
        try {
            order = orderService.checkoutNegotiated(offer.getBuyerId(), offer.getProductId(), offer.getPrice());
        } catch (InsufficientStockException e) {
            // Someone else's checkout took the last unit first; the thread
            // stays open so the parties can renegotiate.
            throw e;
        }
        offerMapper.updateStatus(offer.getId(), STATUS_ACCEPTED, order.getId());
        campaignService.markConverted(offer.getBuyerId(), offer.getProductId());
        log.info("Offer {} accepted into order {} at {}", offerId, order.getId(), offer.getPrice());

        String text = "Offer on \"" + product.getName() + "\" accepted at " + offer.getPrice()
                + ". Order " + order.getOrderNo() + " is awaiting payment.";
        notifications.deliver(offer.getBuyerId(), order.getId(), text);
        notifications.deliver(offer.getSellerId(), order.getId(), text);
        return Map.of("orderId", order.getId());
    }

    /** The side that did not make the offer turns it down; the thread stays open for new offers. */
    @Transactional
    public Map<String, Object> reject(Long offerId, Long userId) {
        BargainOffer offer = requireParty(offerId, userId);
        List<BargainOffer> thread = offerMapper.selectThread(offer.getProductId(), offer.getBuyerId());
        requireLatestAndPending(thread, offer);
        requireCounterparty(offer, userId);

        offerMapper.updateStatus(offer.getId(), STATUS_REJECTED, null);
        Product product = productMapper.selectById(offer.getProductId());
        notifications.deliver(offer.getBuyerId().equals(userId) ? offer.getSellerId() : offer.getBuyerId(), null,
                "Your offer of " + offer.getPrice() + " on \""
                        + (product == null ? "a listing" : product.getName()) + "\" was rejected.");
        return view(offerMapper.selectById(offer.getId()));
    }

    /** The seller sees every thread on the listing; a buyer sees their own. */
    public List<Map<String, Object>> listForProduct(Long productId, Long userId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Listing not found");
        }
        List<BargainOffer> offers = product.getUserId().equals(userId)
                ? offerMapper.selectByProduct(productId)
                : offerMapper.selectThread(productId, userId);
        return offers.stream().map(this::view).toList();
    }

    private BargainOffer requireParty(Long offerId, Long userId) {
        BargainOffer offer = offerMapper.selectById(offerId);
        if (offer == null) {
            throw new IllegalArgumentException("Offer not found");
        }
        if (!offer.getBuyerId().equals(userId) && !offer.getSellerId().equals(userId)) {
            throw new IllegalArgumentException("This negotiation is not yours");
        }
        return offer;
    }

    private void requireCounterparty(BargainOffer offer, Long userId) {
        boolean maker = offer.getBuyerId().equals(userId)
                ? FROM_BUYER.equals(offer.getFrom())
                : FROM_SELLER.equals(offer.getFrom());
        if (maker) {
            throw new IllegalArgumentException("You cannot accept or reject your own offer");
        }
    }

    private Product requireListed(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new IllegalArgumentException("Listing not found");
        }
        if (product.getStatus() != 1) {
            throw new IllegalArgumentException("Listing is no longer for sale");
        }
        return product;
    }

    private void requirePositive(BigDecimal price) {
        if (price == null || price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
    }

    /** An accepted thread is closed for good; rejected ones can be re-opened by a fresh offer. */
    private void requireThreadOpen(List<BargainOffer> thread) {
        if (thread.stream().anyMatch(o -> STATUS_ACCEPTED.equals(o.getStatus()))) {
            throw new IllegalStateException("This thread is closed - the offer was already accepted");
        }
    }

    private void requireLatestAndPending(List<BargainOffer> thread, BargainOffer offer) {
        requireThreadOpen(thread);
        BargainOffer latest = thread.get(thread.size() - 1);
        if (!latest.getId().equals(offer.getId())) {
            throw new IllegalStateException("Only the latest offer can be answered");
        }
        if (!STATUS_PENDING.equals(latest.getStatus())) {
            throw new IllegalStateException("This offer was already answered");
        }
    }

    /** A new entry answers the previous one, so the previous is marked COUNTERED. */
    private void supersedeLatest(List<BargainOffer> thread) {
        if (!thread.isEmpty() && STATUS_PENDING.equals(thread.get(thread.size() - 1).getStatus())) {
            offerMapper.updateStatus(thread.get(thread.size() - 1).getId(), STATUS_COUNTERED, null);
        }
    }

    /**
     * Shaped to the S4-07 interface contract. buyer is included so the seller,
     * who sees several threads on one listing, can tell them apart.
     */
    private Map<String, Object> view(BargainOffer offer) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", offer.getId());
        view.put("productId", offer.getProductId());
        User buyer = userMapper.selectById(offer.getBuyerId());
        view.put("buyer", Map.of("id", offer.getBuyerId(),
                "displayName", buyer == null ? "Unknown" : buyer.getDisplayName()));
        view.put("from", offer.getFrom());
        view.put("price", offer.getPrice());
        view.put("threadStatus", offer.getStatus());
        view.put("orderId", offer.getOrderId());
        view.put("createdAt", offer.getCreatedAt() == null
                ? LocalDateTime.now().toString() : offer.getCreatedAt().toString());
        return view;
    }
}
