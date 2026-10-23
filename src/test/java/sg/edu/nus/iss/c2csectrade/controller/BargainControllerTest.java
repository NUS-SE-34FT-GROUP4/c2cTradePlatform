package sg.edu.nus.iss.c2csectrade.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.exception.InsufficientStockException;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.BargainCampaignService;
import sg.edu.nus.iss.c2csectrade.service.BargainOfferService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BargainControllerTest {

    @Mock private BargainCampaignService campaignService;
    @Mock private BargainOfferService offerService;
    @Mock private UserMapper userMapper;
    @InjectMocks private BargainController controller;

    private static final String USERNAME = "demo_buyer";
    private static final Long USER_ID = 201L;
    private static final Long PRODUCT = 101L;

    private Authentication signedIn() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(USERNAME);
        User user = new User();
        user.setId(USER_ID);
        user.setUsername(USERNAME);
        when(userMapper.selectByUsername(USERNAME)).thenReturn(user);
        return authentication;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(ResponseEntity<?> response) {
        return (Map<String, Object>) response.getBody();
    }

    @Test
    @DisplayName("A campaign is created for the signed-in buyer and comes back as 201")
    void createCampaignAnswersCreated() {
        when(campaignService.create(eq(USER_ID), eq(PRODUCT), any(), any())).thenReturn(Map.of("id", 5L));
        Map<String, Object> request = new HashMap<>();
        request.put("productId", PRODUCT);
        request.put("startPrice", "70.00");
        request.put("deadline", "2026-10-26T12:00:00Z");

        ResponseEntity<?> response = controller.createCampaign(request, signedIn());

        assertEquals(201, response.getStatusCode().value());
        verify(campaignService).create(USER_ID, PRODUCT, new BigDecimal("70.00"),
                LocalDateTime.of(2026, 10, 26, 12, 0));
    }

    @Test
    @DisplayName("A deadline without a zone suffix is accepted as a local time")
    void createCampaignAcceptsALocalDeadline() {
        when(campaignService.create(eq(USER_ID), eq(PRODUCT), any(), any())).thenReturn(Map.of("id", 5L));
        Map<String, Object> request = new HashMap<>();
        request.put("productId", PRODUCT);
        request.put("deadline", "2026-10-26T12:00:00");

        controller.createCampaign(request, signedIn());

        ArgumentCaptor<LocalDateTime> deadline = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(campaignService).create(eq(USER_ID), eq(PRODUCT), isNull(), deadline.capture());
        assertEquals(LocalDateTime.of(2026, 10, 26, 12, 0), deadline.getValue());
    }

    @Test
    @DisplayName("A campaign request without a listing is refused before the service is called")
    void createCampaignNeedsAProductId() {
        ResponseEntity<?> response = controller.createCampaign(new HashMap<>(), signedIn());

        assertEquals(400, response.getStatusCode().value());
        verifyNoInteractions(campaignService);
    }

    @Test
    @DisplayName("A rule the service refuses comes back as 409 with its message")
    void aRefusedHelpIsAConflict() {
        when(campaignService.help(5L, USER_ID))
                .thenThrow(new IllegalStateException("You already helped this campaign"));

        ResponseEntity<?> response = controller.helpCampaign(5L, signedIn());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("You already helped this campaign", body(response).get("message"));
    }

    @Test
    @DisplayName("A bad argument comes back as 400 with its message")
    void aRejectedArgumentIsABadRequest() {
        when(campaignService.help(5L, USER_ID))
                .thenThrow(new IllegalArgumentException("You cannot help your own campaign"));

        ResponseEntity<?> response = controller.helpCampaign(5L, signedIn());

        assertEquals(400, response.getStatusCode().value());
    }

    @Test
    @DisplayName("Listing campaigns requires the listing they belong to")
    void campaignsNeedAProductId() {
        assertEquals(400, controller.campaigns(null, signedIn()).getStatusCode().value());
        verifyNoInteractions(campaignService);
    }

    @Test
    @DisplayName("Campaigns on a listing are read without a sign-in check beyond the session")
    void campaignsAreListedForTheListing() {
        when(campaignService.listForProduct(PRODUCT)).thenReturn(List.of(Map.of("id", 5L)));

        ResponseEntity<?> response = controller.campaigns(PRODUCT, signedIn());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(1, ((List<?>) response.getBody()).size());
    }

    @Test
    @DisplayName("An offer needs both a listing and a price")
    void makeOfferNeedsAListingAndAPrice() {
        Map<String, Object> request = new HashMap<>();
        request.put("productId", PRODUCT);

        assertEquals(400, controller.makeOffer(request, signedIn()).getStatusCode().value());
        verifyNoInteractions(offerService);
    }

    @Test
    @DisplayName("An offer is made for the signed-in buyer and comes back as 201")
    void makeOfferAnswersCreated() {
        when(offerService.makeOffer(USER_ID, PRODUCT, new BigDecimal("70.00"))).thenReturn(Map.of("id", 8L));
        Map<String, Object> request = new HashMap<>();
        request.put("productId", PRODUCT);
        request.put("price", "70.00");

        assertEquals(201, controller.makeOffer(request, signedIn()).getStatusCode().value());
    }

    @Test
    @DisplayName("A counter needs a price")
    void counterNeedsAPrice() {
        assertEquals(400, controller.counterOffer(8L, new HashMap<>(), signedIn()).getStatusCode().value());
        verifyNoInteractions(offerService);
    }

    @Test
    @DisplayName("Accepting an offer returns the negotiated order")
    void acceptReturnsTheOrder() {
        when(offerService.accept(8L, USER_ID)).thenReturn(Map.of("orderId", 77L));

        assertEquals(77L, body(controller.acceptOffer(8L, signedIn())).get("orderId"));
    }

    @Test
    @DisplayName("Losing the last unit while accepting comes back as 409, so the page can say so")
    void acceptingWithoutStockIsAConflict() {
        when(offerService.accept(8L, USER_ID)).thenThrow(new InsufficientStockException("Only 0 left"));

        ResponseEntity<?> response = controller.acceptOffer(8L, signedIn());

        assertEquals(409, response.getStatusCode().value());
        assertEquals("Only 0 left", body(response).get("message"));
    }

    @Test
    @DisplayName("Rejecting an offer answers with the closed offer")
    void rejectAnswersWithTheOffer() {
        when(offerService.reject(8L, USER_ID)).thenReturn(Map.of("threadStatus", "REJECTED"));

        assertEquals("REJECTED", body(controller.rejectOffer(8L, signedIn())).get("threadStatus"));
    }

    @Test
    @DisplayName("Listing offers requires the listing they belong to")
    void offersNeedAProductId() {
        assertEquals(400, controller.offers(null, signedIn()).getStatusCode().value());
        verifyNoInteractions(offerService);
    }

    @Test
    @DisplayName("Offers on a listing are scoped to the caller by the service")
    void offersArePassedTheCaller() {
        when(offerService.listForProduct(PRODUCT, USER_ID)).thenReturn(List.of());

        assertEquals(200, controller.offers(PRODUCT, signedIn()).getStatusCode().value());
    }

    @Test
    @DisplayName("Without a session nothing reaches the bargaining services")
    void anUnauthenticatedCallerIsRefused() {
        ResponseEntity<?> response = controller.helpCampaign(5L, null);

        assertEquals(401, response.getStatusCode().value());
        verifyNoInteractions(campaignService, offerService);
    }

    @Test
    @DisplayName("A session naming an account that no longer exists is refused")
    void aSessionForAVanishedAccountIsRefused() {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("ghost");
        when(userMapper.selectByUsername("ghost")).thenReturn(null);

        assertEquals(401, controller.helpCampaign(5L, authentication).getStatusCode().value());
        verifyNoInteractions(campaignService);
    }
}
