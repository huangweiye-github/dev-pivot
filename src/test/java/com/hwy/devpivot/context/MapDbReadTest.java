package com.hwy.devpivot.context;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import dev.langchain4j.data.message.ChatMessage;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * MapDB 数据读取测试类。
 * 验证对 multi-user-chat-memory.db 的读取能力。
 */
public class MapDbReadTest {

    private DB db;
    private Map<String, String> messages;

    @Before
    public void setUp() {
        db = DBMaker.fileDB("multi-user-chat-memory.db").readOnly().make();
        messages = db.hashMap("messages", Serializer.STRING, Serializer.STRING).open();
    }

    @After
    public void tearDown() {
        if (db != null && !db.isClosed()) {
            db.close();
        }
    }

    @Test
    public void testDbOpen() {
        assertNotNull("DB 应成功打开", db);
        assertFalse("DB 不应已关闭", db.isClosed());
    }

    @Test
    public void testMessagesMapOpen() {
        assertNotNull("messages map 应成功打开", messages);
    }

    @Test
    public void testReadAllEntries() {
        System.out.println("=== MapDB entries ===");
        System.out.println("Total entries: " + messages.size());
        System.out.println("=====================");

        for (Map.Entry<String, String> entry : messages.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            System.out.println("Key: " + key);
            // 截断过长内容
            if (value.length() > 200) {
                System.out.println("Value: " + value.substring(0, 200) + "... (total " + value.length() + " chars)");
            } else {
                System.out.println("Value: " + value);
            }
            System.out.println("---");
        }

        assertNotNull("messages map 不应为 null", messages);
    }

    @Test
    public void testEntryCount() {
        int count = messages.size();
        System.out.println("MapDB messages 条目数: " + count);
        assertTrue("条目数应 >= 0", count >= 0);
    }

    @Test
    public void testReadAllViaPersistentChatMemoryStore() {
        PersistentChatMemoryStore store = new PersistentChatMemoryStore();
        DB readDb = DBMaker.fileDB("multi-user-chat-memory.db").readOnly().make();
        Map<String, String> allKeys = readDb.hashMap("messages", Serializer.STRING, Serializer.STRING).open();

        System.out.println("=== PersistentChatMemoryStore entries ===");
        System.out.println("Total memoryId count: " + allKeys.size());
        System.out.println("=========================================");

        int msgCount = 0;
        for (String memoryId : allKeys.keySet()) {
            List<ChatMessage> messages = store.getMessages(memoryId);
            System.out.println("memoryId: " + memoryId + " -> messages: " + (messages != null ? messages.size() : 0));
            if (messages != null) {
                for (ChatMessage msg : messages) {
                    System.out.println("  [" + msg.type() + "] " + msg);
                    msgCount++;
                }
            }
            System.out.println("---");
        }

        System.out.println("Total messages: " + msgCount);
        assertTrue("消息总数应 >= 0", msgCount >= 0);

        readDb.close();
    }

    @Test
    public void testValuesNotEmpty() {
        for (Map.Entry<String, String> entry : messages.entrySet()) {
            assertNotNull("key 不应为 null", entry.getKey());
            assertNotNull("value 不应为 null", entry.getValue());
            assertFalse("key 不应为空", entry.getKey().isEmpty());
            assertFalse("value 不应为空", entry.getValue().isEmpty());
        }
    }
}
