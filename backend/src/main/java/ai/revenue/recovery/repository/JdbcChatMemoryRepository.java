package ai.revenue.recovery.repository;

import ai.revenue.recovery.entity.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JdbcChatMemoryRepository extends JpaRepository<ChatMemoryEntity, Long> {
    
    List<ChatMemoryEntity> findTop10ByConversationIdOrderByCreatedAtDesc(String conversationId);
    
    void deleteByConversationId(String conversationId);
}
