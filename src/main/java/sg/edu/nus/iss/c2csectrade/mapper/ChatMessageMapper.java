package sg.edu.nus.iss.c2csectrade.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import sg.edu.nus.iss.c2csectrade.entity.ChatMessage;

import java.util.List;

@Mapper
public interface ChatMessageMapper {
    int insert(ChatMessage message);
    List<ChatMessage> selectByConversation(@Param("conversationId") String conversationId);
    /** One row per thread the user takes part in, newest message first. */
    List<ChatMessage> selectConversationsForUser(@Param("userId") Long userId);
    int markRead(@Param("conversationId") String conversationId, @Param("receiverId") Long receiverId);
    int countUnread(@Param("userId") Long userId);
}
