package ai.revenue.recovery.Whatsapp;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class JdbcChatMemory implements ChatMemory {

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatMemory(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS chat_memory (" +
            "id VARCHAR(36) PRIMARY KEY, " +
            "conversation_id VARCHAR(255) NOT NULL, " +
            "message_type VARCHAR(50) NOT NULL, " +
            "content TEXT NOT NULL, " +
            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"
        );
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String sql = "INSERT INTO chat_memory (id, conversation_id, message_type, content, created_at) VALUES (?, ?, ?, ?, ?)";
        for (Message msg : messages) {
            String content = msg.toString();
            jdbcTemplate.update(sql, UUID.randomUUID().toString(), conversationId, 
                msg.getMessageType().getValue(), content, LocalDateTime.now());
        }
    }

    public List<Message> get(String conversationId, int lastN) {
        return getMessages(conversationId, lastN);
    }
    
    // To satisfy interface whether it takes 1 or 2 args in this specific Spring AI version
    @Override
    public List<Message> get(String conversationId) {
        return getMessages(conversationId, 100);
    }

    private List<Message> getMessages(String conversationId, int limit) {
        String sql = "SELECT message_type, content FROM chat_memory WHERE conversation_id = ? ORDER BY created_at ASC LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String type = rs.getString("message_type");
            String content = rs.getString("content");
            if ("USER".equalsIgnoreCase(type)) {
                return new UserMessage(content);
            } else {
                return new AssistantMessage(content);
            }
        }, conversationId, limit);
    }

    @Override
    public void clear(String conversationId) {
        jdbcTemplate.update("DELETE FROM chat_memory WHERE conversation_id = ?", conversationId);
    }
}
