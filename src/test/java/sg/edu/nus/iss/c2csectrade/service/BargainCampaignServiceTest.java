package sg.edu.nus.iss.c2csectrade.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sg.edu.nus.iss.c2csectrade.entity.BargainCampaign;
import sg.edu.nus.iss.c2csectrade.entity.BargainCampaignHelper;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.BargainCampaignHelperMapper;
import sg.edu.nus.iss.c2csectrade.mapper.BargainCampaignMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BargainCampaignServiceTest {

    @Mock private BargainCampaignMapper campaignMapper;
    @Mock private BargainCampaignHelperMapper helperMapper;
    @Mock private ProductMapper productMapper;
    @Mock private UserMapper userMapper;
    @InjectMocks private BargainCampaignService campaignService;

    private static final Long BUYER = 1L;
    private static final Long HELPER = 2L;
    private static final Long SELLER = 20L;
    private static final Long PRODUCT = 101L;
    private static final Long CAMPAIGN = 5L;

    /** A listing at 100.00, so the floor is 60.00, the opening price 80.00 and each help 5.00. */
    private Product listing(int status) {
        Product product = new Product();
        product.setId(PRODUCT);
        product.setUserId(SELLER);
        product.setName("Textbook");
        product.setPrice(new BigDecimal("100.00"));
        product.setStatus(status);
        return product;
    }

    private BargainCampaign campaign(String status, String currentPrice, LocalDateTime deadline) {
        BargainCampaign campaign = new BargainCampaign();
        campaign.setId(CAMPAIGN);
        campaign.setProductId(PRODUCT);
        campaign.setBuyerId(BUYER);
        campaign.setStartPrice(new BigDecimal("80.00"));
        campaign.setCurrentPrice(new BigDecimal(currentPrice));
        campaign.setFloorPrice(new BigDecimal("60.00"));
        campaign.setStatus(status);
        campaign.setDeadline(deadline);
        return campaign;
    }

    private BargainCampaignHelper helper(Long userId) {
        BargainCampaignHelper helper = new BargainCampaignHelper();
        helper.setCampaignId(CAMPAIGN);
        helper.setUserId(userId);
        return helper;
    }

    @Test
    @DisplayName("A campaign with no named price opens at 80% of the listing price, with a 60% floor")
    void createOpensAtEightyPercentWithASixtyPercentFloor() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(campaignMapper.selectActiveByBuyerAndProduct(BUYER, PRODUCT)).thenReturn(null);

        campaignService.create(BUYER, PRODUCT, null, LocalDateTime.now().plusDays(1));

        ArgumentCaptor<BargainCampaign> saved = ArgumentCaptor.forClass(BargainCampaign.class);
        verify(campaignMapper).insert(saved.capture());
        assertEquals(new BigDecimal("80.00"), saved.getValue().getStartPrice());
        assertEquals(new BigDecimal("80.00"), saved.getValue().getCurrentPrice());
        assertEquals(new BigDecimal("60.00"), saved.getValue().getFloorPrice());
        assertEquals("ACTIVE", saved.getValue().getStatus());
    }

    @Test
    @DisplayName("A buyer may name any start price between the floor and the listing price")
    void createAcceptsABuyerNamedStartPrice() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(campaignMapper.selectActiveByBuyerAndProduct(BUYER, PRODUCT)).thenReturn(null);

        campaignService.create(BUYER, PRODUCT, new BigDecimal("70.00"), LocalDateTime.now().plusDays(1));

        ArgumentCaptor<BargainCampaign> saved = ArgumentCaptor.forClass(BargainCampaign.class);
        verify(campaignMapper).insert(saved.capture());
        assertEquals(new BigDecimal("70.00"), saved.getValue().getStartPrice());
    }

    @Test
    @DisplayName("A start price at the floor is refused: there would be nothing left to cut")
    void createRejectsAStartPriceAtTheFloor() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(campaignMapper.selectActiveByBuyerAndProduct(BUYER, PRODUCT)).thenReturn(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> campaignService.create(BUYER, PRODUCT, new BigDecimal("60.00"), LocalDateTime.now().plusDays(1)));

        assertTrue(error.getMessage().contains("60.00"));
        verify(campaignMapper, never()).insert(any());
    }

    @Test
    @DisplayName("A start price above the listing price is refused")
    void createRejectsAStartPriceAboveTheListingPrice() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(campaignMapper.selectActiveByBuyerAndProduct(BUYER, PRODUCT)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.create(BUYER, PRODUCT, new BigDecimal("120.00"), LocalDateTime.now().plusDays(1)));
        verify(campaignMapper, never()).insert(any());
    }

    @Test
    @DisplayName("A seller cannot run a campaign on their own listing")
    void createRejectsTheSellersOwnListing() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.create(SELLER, PRODUCT, null, LocalDateTime.now().plusDays(1)));
        verify(campaignMapper, never()).insert(any());
    }

    @Test
    @DisplayName("One active campaign per buyer per item")
    void createRejectsASecondActiveCampaignOnTheSameItem() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(campaignMapper.selectActiveByBuyerAndProduct(BUYER, PRODUCT))
                .thenReturn(campaign("ACTIVE", "80.00", LocalDateTime.now().plusDays(1)));

        assertThrows(IllegalStateException.class,
                () -> campaignService.create(BUYER, PRODUCT, null, LocalDateTime.now().plusDays(1)));
        verify(campaignMapper, never()).insert(any());
    }

    @Test
    @DisplayName("A delisted listing takes no campaign")
    void createRejectsADelistedListing() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(0));

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.create(BUYER, PRODUCT, null, LocalDateTime.now().plusDays(1)));
    }

    @Test
    @DisplayName("A missing listing takes no campaign")
    void createRejectsAMissingListing() {
        when(productMapper.selectById(PRODUCT)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.create(BUYER, PRODUCT, null, LocalDateTime.now().plusDays(1)));
    }

    @Test
    @DisplayName("A deadline that has already passed is refused")
    void createRejectsADeadlineInThePast() {
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));
        when(campaignMapper.selectActiveByBuyerAndProduct(BUYER, PRODUCT)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> campaignService.create(BUYER, PRODUCT, null, LocalDateTime.now().minusMinutes(1)));
        verify(campaignMapper, never()).insert(any());
    }

    @Test
    @DisplayName("Each help cuts 5% of the listing price, not of the current price")
    void helpCutsFivePercentOfTheListingPrice() {
        when(campaignMapper.selectById(CAMPAIGN))
                .thenReturn(campaign("ACTIVE", "80.00", LocalDateTime.now().plusDays(1)));
        when(helperMapper.selectByCampaignId(CAMPAIGN)).thenReturn(List.of());
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));

        campaignService.help(CAMPAIGN, HELPER);

        verify(helperMapper).insert(any(BargainCampaignHelper.class));
        verify(campaignMapper).updateCurrentPrice(CAMPAIGN, new BigDecimal("75.00"));
    }

    @Test
    @DisplayName("However many helpers pile in, the price stops at the floor")
    void helpStopsAtTheFloor() {
        // 61.00 with a 60.00 floor: a full 5.00 cut would cross it, so the floor wins.
        when(campaignMapper.selectById(CAMPAIGN))
                .thenReturn(campaign("ACTIVE", "61.00", LocalDateTime.now().plusDays(1)));
        when(helperMapper.selectByCampaignId(CAMPAIGN)).thenReturn(List.of());
        when(productMapper.selectById(PRODUCT)).thenReturn(listing(1));

        campaignService.help(CAMPAIGN, HELPER);

        verify(campaignMapper).updateCurrentPrice(CAMPAIGN, new BigDecimal("60.00"));
    }

    @Test
    @DisplayName("The buyer cannot help their own campaign")
    void helpRejectsTheBuyersOwnCampaign() {
        when(campaignMapper.selectById(CAMPAIGN))
                .thenReturn(campaign("ACTIVE", "80.00", LocalDateTime.now().plusDays(1)));

        assertThrows(IllegalArgumentException.class, () -> campaignService.help(CAMPAIGN, BUYER));
        verify(campaignMapper, never()).updateCurrentPrice(any(), any());
    }

    @Test
    @DisplayName("The same helper is refused a second cut")
    void helpRejectsTheSameHelperTwice() {
        when(campaignMapper.selectById(CAMPAIGN))
                .thenReturn(campaign("ACTIVE", "75.00", LocalDateTime.now().plusDays(1)));
        when(helperMapper.selectByCampaignId(CAMPAIGN)).thenReturn(List.of(helper(HELPER)));

        IllegalStateException error =
                assertThrows(IllegalStateException.class, () -> campaignService.help(CAMPAIGN, HELPER));

        assertTrue(error.getMessage().contains("already helped"));
        verify(helperMapper, never()).insert(any());
        verify(campaignMapper, never()).updateCurrentPrice(any(), any());
    }

    @Test
    @DisplayName("A campaign past its deadline takes no further help, whatever its stored status says")
    void helpRejectsACampaignPastItsDeadline() {
        when(campaignMapper.selectById(CAMPAIGN))
                .thenReturn(campaign("ACTIVE", "80.00", LocalDateTime.now().minusMinutes(1)));

        assertThrows(IllegalStateException.class, () -> campaignService.help(CAMPAIGN, HELPER));
        verify(campaignMapper, never()).updateCurrentPrice(any(), any());
    }

    @Test
    @DisplayName("An expired campaign takes no further help")
    void helpRejectsAnExpiredCampaign() {
        when(campaignMapper.selectById(CAMPAIGN))
                .thenReturn(campaign("EXPIRED", "80.00", LocalDateTime.now().plusDays(1)));

        assertThrows(IllegalStateException.class, () -> campaignService.help(CAMPAIGN, HELPER));
    }

    @Test
    @DisplayName("Helping a campaign that is not there is refused")
    void helpRejectsAMissingCampaign() {
        when(campaignMapper.selectById(CAMPAIGN)).thenReturn(null);

        assertThrows(IllegalArgumentException.class, () -> campaignService.help(CAMPAIGN, HELPER));
    }

    @Test
    @DisplayName("The sweep reports how many campaigns it expired")
    void expireDueReportsHowManyWereSwept() {
        when(campaignMapper.expireDue(any(LocalDateTime.class))).thenReturn(3);

        assertEquals(3, campaignService.expireDue());
    }

    @Test
    @DisplayName("A closed deal retires the buyer's live campaign on that listing")
    void markConvertedRetiresTheLiveCampaign() {
        campaignService.markConverted(BUYER, PRODUCT);

        verify(campaignMapper).markConverted(BUYER, PRODUCT);
    }

    @Test
    @DisplayName("The listing shows every campaign, with its helpers named and the deadline as a string")
    void listForProductNamesTheHelpers() {
        when(campaignMapper.selectByProductId(PRODUCT))
                .thenReturn(List.of(campaign("ACTIVE", "75.00", LocalDateTime.now().plusDays(1))));
        when(helperMapper.selectByCampaignId(CAMPAIGN)).thenReturn(List.of(helper(HELPER)));
        User buyer = new User();
        buyer.setId(BUYER);
        buyer.setDisplayName("Demo Buyer");
        when(userMapper.selectById(BUYER)).thenReturn(buyer);
        when(userMapper.selectById(HELPER)).thenReturn(null);

        List<Map<String, Object>> campaigns = campaignService.listForProduct(PRODUCT);

        assertEquals(1, campaigns.size());
        Map<String, Object> view = campaigns.get(0);
        assertEquals(new BigDecimal("75.00"), view.get("currentPrice"));
        assertEquals("Demo Buyer", ((Map<?, ?>) view.get("buyer")).get("displayName"));
        assertInstanceOf(String.class, view.get("deadline"));
        List<?> helpers = (List<?>) view.get("helpers");
        assertEquals(1, helpers.size());
        // A deleted account still leaves its help on the campaign.
        assertEquals("Unknown", ((Map<?, ?>) helpers.get(0)).get("displayName"));
    }
}
