package ai.revenue.recovery.service;

import ai.revenue.recovery.entity.ChatMemoryEntity;
import ai.revenue.recovery.repository.JdbcChatMemoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Primary
@Service
public class HybridChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(HybridChatMemory.class);

    private final JdbcChatMemoryRepository repository;
    
    // In-memory cache for active sessions
    private final Map<String, List<Message>> inMemoryCache = new ConcurrentHashMap<>();

    public HybridChatMemory(JdbcChatMemoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> existing = inMemoryCache.computeIfAbsent(conversationId, k -> new ArrayList<>());
        existing.addAll(messages);
        
        saveNewMessagesToDb(conversationId, messages);
    }
    
    @Override
    public void add(String conversationId, Message message) {
        List<Message> existing = inMemoryCache.computeIfAbsent(conversationId, k -> new ArrayList<>());
        existing.add(message);
        
        saveNewMessagesToDb(conversationId, List.of(message));
    }

    // @Override
    public List<Message> get(String conversationId, int lastN) {
        if (!inMemoryCache.containsKey(conversationId)) {
            loadFromDb(conversationId);
        }
        
        List<Message> messages = inMemoryCache.getOrDefault(conversationId, new ArrayList<>());
        if (messages.size() > lastN) {
            return messages.subList(messages.size() - lastN, messages.size());
        }
        return messages;
    }
    
    public List<Message> get(String conversationId) {
        // Fetch up to 10 as requested
        return get(conversationId, 10);
    }

    @Transactional
    public void clear(String conversationId) {
        inMemoryCache.remove(conversationId);
        repository.deleteByConversationId(conversationId);
    }

    private void saveNewMessagesToDb(String conversationId, List<Message> newMessages) {
        try {
            if (newMessages == null || newMessages.isEmpty()) return;

            List<ChatMemoryEntity> entitiesToSave = newMessages.stream().map(m -> {
                String type = "USER";
                if (m instanceof AssistantMessage) type = "ASSISTANT";
                else if (m instanceof SystemMessage) type = "SYSTEM";
                
                ChatMemoryEntity entity = new ChatMemoryEntity();
                entity.setConversationId(conversationId);
                entity.setMessageType(type);
                entity.setContent(m.getText());
                entity.setCreatedAt(LocalDateTime.now());
                return entity;
            }).collect(Collectors.toList());

            repository.saveAll(entitiesToSave);
        } catch (Exception e) {
            log.error("Failed to save new chat messages to DB for {}", conversationId, e);
        }
    }

    private void loadFromDb(String conversationId) {
        try {
            // Fetch top 10 descending (newest first)
            List<ChatMemoryEntity> entities = repository.findTop10ByConversationIdOrderByCreatedAtDesc(conversationId);
            
            // Reverse so they are chronologically ordered for the LLM
            Collections.reverse(entities);
            
            List<Message> messages = entities.stream().map(entity -> {
                if ("ASSISTANT".equals(entity.getMessageType())) return (Message) new AssistantMessage(entity.getContent());
                else if ("SYSTEM".equals(entity.getMessageType())) return (Message) new SystemMessage(entity.getContent());
                else return (Message) new UserMessage(entity.getContent());
            }).collect(Collectors.toList());

            inMemoryCache.put(conversationId, messages);
            log.info("Loaded last {} chat messages for {} from DB", messages.size(), conversationId);
        } catch (Exception e) {
            log.error("Failed to load chat memory from DB for {}", conversationId, e);
        }
    }
}
