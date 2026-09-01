package com.hwy.devpivot.context;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.List;
import java.util.Map;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

public class PersistentChatMemoryStore  implements ChatMemoryStore {

    private final DB db = DBMaker.fileDB("multi-user-chat-memory.db").transactionEnable().make();
    private final Map<String, String> map = db.hashMap("messages", Serializer.STRING, Serializer.STRING).createOrOpen();

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String json = map.get(memoryId);
        return messagesFromJson(json);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String json = messagesToJson(messages);
        map.put((String) memoryId, json);
        db.commit();
    }

    @Override
    public void deleteMessages(Object memoryId) {
        map.remove((int) memoryId);
        db.commit();
    }
}
