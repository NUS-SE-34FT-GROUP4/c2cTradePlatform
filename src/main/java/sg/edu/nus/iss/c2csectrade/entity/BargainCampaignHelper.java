package sg.edu.nus.iss.c2csectrade.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** One friend's single price cut on a campaign. Unique per (campaign, user). */
@Data
public class BargainCampaignHelper implements Serializable {
    private Long id;
    private Long campaignId;
    private Long userId;
    private LocalDateTime createdAt;
}
