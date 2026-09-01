package com.hwy.devpivot.markdown;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * MarkdownTemplate 单元测试。
 */
public class MarkdownTemplateTest {

    // ── 基础替换 ──────────────────────────────────────────

    @Test
    public void testSinglePlaceholder() {
        String md = "Hello {{name}}!";
        Map<String, String> vals = Map.of("name", "World");
        String result = MarkdownTemplate.replace(md, vals);
        System.out.println(result);
        assertTrue("应包含替换后的值", result.contains("Hello World"));
        assertFalse("不应包含占位符", result.contains("{{"));
    }

    @Test
    public void testMultiplePlaceholders() {
        String md = "{{greeting}} **{{name}}**, count={{count}}";
        Map<String, String> vals = Map.of("greeting", "Hi", "name", "Claude", "count", "42");
        String result = MarkdownTemplate.replace(md, vals);
        assertTrue(result.contains("Hi"));
        assertTrue(result.contains("**Claude**"));
        assertTrue(result.contains("count=42"));
    }

    @Test
    public void testMissingKeyPreserved() {
        String md = "Hello {{name}}, foo={{missing}}";
        Map<String, String> vals = Map.of("name", "World");
        String result = MarkdownTemplate.replace(md, vals);
        assertTrue(result.contains("Hello World"));
        assertTrue("未匹配的占位符应保留", result.contains("{{missing}}"));
    }

    // ── 列表中的占位符 ────────────────────────────────────

    @Test
    public void testPlaceholderInBulletList() {
        String md = "- 平台：{{platform}}\n- Shell：{{shell}}";
        Map<String, String> vals = Map.of("platform", "win32", "shell", "bash");
        String result = MarkdownTemplate.replace(md, vals);
        assertTrue(result.contains("win32"));
        assertTrue(result.contains("bash"));
        assertFalse(result.contains("{{"));
    }

    // ── 格式化文本中的占位符 ──────────────────────────────

    @Test
    public void testPlaceholderInBold() {
        String md = "当前模型：**{{modelName}}**";
        Map<String, String> vals = Map.of("modelName", "deepseek-v4-pro");
        String result = MarkdownTemplate.replace(md, vals);
        // 应保留加粗标记
        assertTrue(result.contains("**deepseek-v4-pro**"));
    }

    @Test
    public void testPlaceholderInItalic() {
        String md = "版本：*{{version}}*";
        Map<String, String> vals = Map.of("version", "1.0.0");
        String result = MarkdownTemplate.replace(md, vals);
        assertTrue(result.contains("*1.0.0*"));
    }

    // ── 边界情况 ──────────────────────────────────────────

    @Test
    public void testNullMarkdown() {
        assertNull(MarkdownTemplate.replace(null, Map.of("k", "v")));
    }

    @Test
    public void testEmptyMarkdown() {
        assertEquals("", MarkdownTemplate.replace("", Map.of("k", "v")));
    }

    @Test
    public void testNullValues() {
        String md = "Hello {{name}}";
        assertEquals(md, MarkdownTemplate.replace(md, null));
    }

    @Test
    public void testEmptyValues() {
        String md = "Hello {{name}}";
        assertEquals(md, MarkdownTemplate.replace(md, Map.of()));
    }

    @Test
    public void testNoPlaceholder() {
        String md = "Plain **markdown** text without placeholders.";
        String result = MarkdownTemplate.replace(md, Map.of("unused", "val"));
        // 无占位符时应保持内容语义不变
        assertTrue(result.contains("Plain"));
        assertTrue(result.contains("**markdown**"));
    }

    // ── 完整模板场景（模拟 coding-agent.md） ──────────────

    @Test
    public void testWorkAgentTemplate() {
        String md = "- 主工作目录：{{currentWorkDir}}\n- Git 仓库：{{isGitRepository}}\n" +
                    "- 平台：{{platform}}\n- Shell：{{shellProvider}}\n" +
                    "- OS 版本：{{osVersion}}\n- 当前模型：{{modelName}}";
        Map<String, String> vals = new HashMap<>();
        vals.put("currentWorkDir", "/home/user/project");
        vals.put("isGitRepository", "true");
        vals.put("platform", "win32");
        vals.put("shellProvider", "bash");
        vals.put("osVersion", "Windows 10 Pro");
        vals.put("modelName", "deepseek-v4-pro");

        String result = MarkdownTemplate.replace(md, vals);
        assertTrue(result.contains("/home/user/project"));
        assertTrue(result.contains("true"));
        assertTrue(result.contains("win32"));
        assertTrue(result.contains("bash"));
        assertTrue(result.contains("Windows 10 Pro"));
        assertTrue(result.contains("deepseek-v4-pro"));
        assertFalse("所有占位符应已替换", result.contains("{{"));
    }

    // ── 从资源文件加载 ────────────────────────────────────

    @Test
    public void testReplaceFromResource() {
        String result = MarkdownTemplate.replaceFromResource(
                "prompt/worker-agent.md",
                Map.of("currentWorkDir", "/tmp/test",
                       "isGitRepository", "false",
                       "platform", "linux",
                       "shellProvider", "zsh",
                       "osVersion", "Ubuntu 22.04",
                       "modelName", "test-model"));
        assertTrue(result.contains("/tmp/test"));
        assertTrue(result.contains("linux"));
        assertTrue(result.contains("zsh"));
        assertTrue(result.contains("test-model"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReplaceFromResourceNotFound() {
        MarkdownTemplate.replaceFromResource("nonexistent/file.md", Map.of());
    }
}
