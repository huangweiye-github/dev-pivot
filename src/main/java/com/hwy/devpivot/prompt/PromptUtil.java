package com.hwy.devpivot.prompt;

import com.hwy.devpivot.markdown.MarkdownTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 文件配置，通过常量管理 prompt 目录下的文件名，提供统一读取入口。
 */
public class PromptUtil {

    private static final String PROMPT_SYSTEM_DIR = "prompt/system/";
    private static final String PROMPT_AGENT_DIR = "prompt/agent/";


    private PromptUtil() {
    }

    /** 读取 agent 描述文件（无变量替换） */
    public static String readAgentPrompt(String fileName) {
        String resourcePath = PROMPT_AGENT_DIR + fileName;
        InputStream inputStream = PromptUtil.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalArgumentException("Agent prompt file not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read agent prompt file: " + resourcePath, e);
        }
    }

    public static String readWorkSystemPrompt(String fileName,Map<String,String> variables) {
        String resourcePath = PROMPT_SYSTEM_DIR + fileName;
        InputStream inputStream = PromptUtil.class.getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new IllegalArgumentException("Prompt file not found: " + resourcePath);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            return MarkdownTemplate.replace(reader.lines().collect(Collectors.joining("\n")),variables);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read prompt file: " + resourcePath, e);
        }
    }
}
