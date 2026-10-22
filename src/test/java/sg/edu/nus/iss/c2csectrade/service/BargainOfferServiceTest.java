package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.BargainOffer;
import sg.edu.nus.iss.c2csectrade.entity.Order;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.BargainOfferMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BargainOfferServiceTest {

    @Mock private BargainOfferMapper offerMapper;
    @Mock private ProductMapper productMapper;
    @Mock private UserMapper userMapper;
    @Mock private OrderService orderService;
    @Mock private BargainCampaignService campaignService;
    @Mock private NotificationService notifications;
    @InjectMocks private BargainOfferService offerService;

    private static final Long BUYER = 1L;
    private static final Long SELLER = 20L;
    private static final Long OUTSIDER = 99L;
    private static final Long PRODUCT = 101L;
    private static final Long OFFER = 8L;
    private static final Long REPLY = 9L;

    private Product listing(int status) {
        Product product = new Product();
        product.setId(PRODUCT);
        product.setUserId(SELLER);
        product.setName("Textbook");
        product.setPrice(new BigDecimal("100.00"));
        product.setStatus(status);
        return product;
    }

    private BargainOffer offer(Long id, String from, String status, String price) {
        BargainOffer offer = new BargainOffer();
        offer.setId(id);
        offer.setProductId(PRODUCT);
        offer.setBuyerId(BUYER);
        offer.setSellerId(SELLER);
        offer.setFrom(from);
        offer.setPrice(new BigDecimal(price));
        offer.setStatus(status);
        return offer;
    }

    /** The insert is a mapper call, so the generated key is handed back the way MyBatis would. */
    private void insertAssignsId(Long id, BargainOffer stored) {
        when(offerMapper.insert(any(BargainOffer.class))).thenAnswer(call -> {
            call.getArgument(0, BargainOffer.class).setId(id);
            return 1;
        });
        when(offerMapper.selectById(id)).thenReturn(stored);
    }

    private Order order(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("ORD-20261026-0001");
        return order;
    }

    @Test
    @DisplayName("An offer supersedes the pending entry before it and reaches the seller")
    void makeOfferSupersedesThePendingEntryAndNotifiesTheSeller() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(offerMapper.selectThread(PRODUCT, BUYER))
                .thenReturn(List.of(offer(OFFER, "SELLER", "PENDING", "90.00")));
        insertAssignsId(REPLY, offer(REPLY, "BUYER", "PENDING", "70.00"));

        Map<String, Object> view = offerService.makeOffer(BUYER, PRODUCT, new BigDecimal("70.00"));

        verify(offerMapper).updateStatus(OFFER, "COUNTERED", null);
        verify(notifications).deliver(eq(SELLER), isNull(), contains("70.00"));
        assertEquals("PENDING", view.get("threadStatus"));
        assertEquals(new BigDecimal("70.00"), view.get("price"));
    }

    @Test
    @DisplayName("A seller cannot make an offer on their own listing")
    void makeOfferRejectsTheSellersOwnListing() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));

        assertThrows(IllegalArgumentException.class,
                () -> offerService.makeOffer(SELLER, PRODUCT, new BigDecimal("70.00")));
        verify(offerMapper, never()).insert(any());
    }

    @Test
    @DisplayName("An offer of zero is refused")
    void makeOfferRejectsANonPositivePrice() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));

        assertThrows(IllegalArgumentException.class,
                () -> offerService.makeOffer(BUYER, PRODUCT, BigDecimal.ZERO));
        verify(offerMapper, never()).insert(any());
    }

    @Test
    @DisplayName("A delisted listing takes no offer")
    void makeOfferRejectsADelistedListing() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(0));

        assertThrows(IllegalArgumentException.class,
                () -> offerService.makeOffer(BUYER, PRODUCT, new BigDecimal("70.00")));
    }

    @Test
    @DisplayName("Once an offer was accepted the thread is closed for good")
    void makeOfferRejectsAThreadThatAlreadyClosed() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(offerMapper.selectThread(PRODUCT, BUYER))
                .thenReturn(List.of(offer(OFFER, "BUYER", "ACCEPTED", "70.00")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> offerService.makeOffer(BUYER, PRODUCT, new BigDecimal("75.00")));

        assertTrue(error.getMessage().contains("already accepted"));
        verify(offerMapper, never()).insert(any());
    }

    @Test
    @DisplayName("A counter is recorded as coming from the side that sent it, and tells the other side")
    void counterRecordsWhoSentItAndNotifiesTheOtherSide() {
        BargainOffer pending = offer(OFFER, "BUYER", "PENDING", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(pending);
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of(pending));
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        insertAssignsId(REPLY, offer(REPLY, "SELLER", "PENDING", "90.00"));

        offerService.counter(OFFER, SELLER, new BigDecimal("90.00"));

        ArgumentCaptor<BargainOffer> saved = ArgumentCaptor.forClass(BargainOffer.class);
        verify(offerMapper).insert(saved.capture());
        assertEquals("SELLER", saved.getValue().getFrom());
        verify(offerMapper).updateStatus(OFFER, "COUNTERED", null);
        verify(notifications).deliver(eq(BUYER), isNull(), contains("90.00"));
    }

    @Test
    @DisplayName("Only the latest entry in a thread can be answered")
    void counterRejectsAnEntryThatIsNoLongerTheLatest() {
        BargainOffer superseded = offer(OFFER, "BUYER", "COUNTERED", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(superseded);
        when(offerMapper.selectThread(PRODUCT, BUYER))
                .thenReturn(List.of(superseded, offer(REPLY, "SELLER", "PENDING", "90.00")));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> offerService.counter(OFFER, SELLER, new BigDecimal("80.00")));

        assertTrue(error.getMessage().contains("latest"));
    }

    @Test
    @DisplayName("An offer that was already answered cannot be answered twice")
    void counterRejectsAnOfferAlreadyAnswered() {
        BargainOffer rejected = offer(OFFER, "BUYER", "REJECTED", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(rejected);
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of(rejected));

        assertThrows(IllegalStateException.class,
                () -> offerService.counter(OFFER, SELLER, new BigDecimal("80.00")));
    }

    @Test
    @DisplayName("Accepting books a real order at the negotiated price and retires the buyer's campaign")
    void acceptCreatesTheNegotiatedOrderAndConvertsTheCampaign() {
        BargainOffer pending = offer(OFFER, "BUYER", "PENDING", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(pending);
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of(pending));
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(orderService.checkoutNegotiated(BUYER, PRODUCT, new BigDecimal("70.00"))).thenReturn(order(77L));

        Map<String, Object> result = offerService.accept(OFFER, SELLER);

        assertEquals(77L, result.get("orderId"));
        verify(offerMapper).updateStatus(OFFER, "ACCEPTED", 77L);
        verify(campaignService).markConverted(BUYER, PRODUCT);
        // Both sides are told once, and both notifications carry the order.
        verify(notifications).deliver(eq(BUYER), eq(77L), contains("ORD-20261026-0001"));
        verify(notifications).deliver(eq(SELLER), eq(77L), contains("ORD-20261026-0001"));
    }

    @Test
    @DisplayName("Nobody can accept their own offer")
    void acceptRejectsTheMakersOwnOffer() {
        BargainOffer pending = offer(OFFER, "BUYER", "PENDING", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(pending);
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of(pending));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> offerService.accept(OFFER, BUYER));

        assertTrue(error.getMessage().contains("your own offer"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("If another checkout took the last unit first, the thread stays open")
    void acceptLeavesTheThreadOpenWhenTheLastUnitIsGone() {
        BargainOffer pending = offer(OFFER, "BUYER", "PENDING", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(pending);
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of(pending));
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(orderService.checkoutNegotiated(BUYER, PRODUCT, new BigDecimal("70.00")))
                .thenThrow(new InsufficientStockException("Only 0 left"));

        assertThrows(InsufficientStockException.class, () -> offerService.accept(OFFER, SELLER));

        verify(offerMapper, never()).updateStatus(any(), anyString(), any());
        verify(campaignService, never()).markConverted(any(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    @DisplayName("A rejection closes that offer but leaves the thread open for a fresh one")
    void rejectKeepsTheThreadOpenAndTellsTheMaker() {
        BargainOffer pending = offer(OFFER, "BUYER", "PENDING", "70.00");
        when(offerMapper.selectById(OFFER)).thenReturn(pending);
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of(pending));
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));

        offerService.reject(OFFER, SELLER);

        verify(offerMapper).updateStatus(OFFER, "REJECTED", null);
        verify(notifications).deliver(eq(BUYER), isNull(), contains("rejected"));
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("A stranger cannot answer somebody else's negotiation")
    void anOutsiderCannotAnswerTheThread() {
        when(offerMapper.selectById(OFFER)).thenReturn(offer(OFFER, "BUYER", "PENDING", "70.00"));

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, () -> offerService.reject(OFFER, OUTSIDER));

        assertTrue(error.getMessage().contains("not yours"));
    }

    @Test
    @DisplayName("Answering an offer that is not there is refused")
    void answeringAMissingOfferIsRefused() {
        when(offerMapper.selectById(OFFER)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> offerService.accept(OFFER, SELLER));
    }

    @Test
    @DisplayName("The seller sees every thread on the listing")
    void listForProductShowsTheSellerEveryThread() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(offerMapper.selectByProduct(PRODUCT))
                .thenReturn(List.of(offer(OFFER, "BUYER", "PENDING", "70.00")));

        List<Map<String, Object>> threads = offerService.listForProduct(PRODUCT, SELLER);

        assertEquals(1, threads.size());
        assertEquals("Unknown", ((Map<?, ?>) threads.get(0).get("buyer")).get("displayName"));
        verify(offerMapper, never()).selectThread(any(), any());
    }

    @Test
    @DisplayName("A buyer sees only their own thread")
    void listForProductShowsTheBuyerOnlyTheirOwnThread() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(offerMapper.selectThread(PRODUCT, BUYER)).thenReturn(List.of());

        assertTrue(offerService.listForProduct(PRODUCT, BUYER).isEmpty());
        verify(offerMapper, never()).selectByProduct(any());
    }
}
