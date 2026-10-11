package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.BargainCampaignHelper;

import java.util.List;

@Mapper
public interface BargainCampaignHelperMapper {
    int insert(BargainCampaignHelper helper);

    List<BargainCampaignHelper> selectByCampaignId(@Param("campaignId") Long campaignId);
}
