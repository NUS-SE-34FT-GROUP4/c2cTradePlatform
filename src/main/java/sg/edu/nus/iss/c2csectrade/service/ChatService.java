package sg.edu.nus.iss.c2csectrade.service;

import org.springframework.stereotype.Service;
import sg.edu.nus.iss.c2csectrade.entity.ChatMessage;
import sg.edu.nus.iss.c2csectrade.mapper.ChatMessageMapper;

import java.util.List;

/**
 * Buyer-seller messaging.
 *
 * A conversation is keyed by the two participants and the listing, with the
 * ids sorted, so whoever sends first the pair lands in the same thread. That
 * key is also what enforces access: a caller may only read a thread whose key
 * they can be derived into.
 */
@Service
public class ChatService {

    private final ChatMessageMapper chatMessageMapper;

    public ChatService(ChatMessageMapper chatMessageMapper) {
        this.chatMessageMapper = chatMessageMapper;
    }

    public static String conversationId(Long userA, Long userB, Long productId) {
        long low = Math.min(userA, userB);
        long high = Math.max(userA, userB);
        return low + ":" + high + ":" + (productId == null ? 0 : productId);
    }

    public ChatMessage send(Long senderId, Long receiverId, Long productId, String content) {
        if (senderId.equals(receiverId)) {
            throw new IllegalArgumentException("You cannot message yourself");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }
        if (content.length() > 2000) {
            throw new IllegalArgumentException("Message is too long");
        }
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId(senderId, receiverId, productId));
        message.setSenderId(senderId);
        message.setReceiverId(receiverId);
        message.setProductId(productId);
        message.setContent(HtmlSanitizer.clean(content));
        chatMessageMapper.insert(message);
        return message;
    }

    /**
     * History for a thread the caller is part of. The id is rebuilt from the
     * caller and the other participant rather than trusted from the request,
     * so a third party cannot read a thread by guessing its key.
     */
    public List<ChatMessage> history(Long callerId, Long otherUserId, Long productId) {
        String conversationId = conversationId(callerId, otherUserId, productId);
        chatMessageMapper.markRead(conversationId, callerId);
        return chatMessageMapper.selectByConversation(conversationId);
    }

    public List<ChatMessage> conversations(Long userId) {
        return chatMessageMapper.selectConversationsForUser(userId);
    }

    public int unreadCount(Long userId) {
        return chatMessageMapper.countUnread(userId);
    }
}
