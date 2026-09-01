package com.hwy.devpivot.prompt;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * PromptUtil 单元测试。
 */
public class PromptUtilTest {

    // ── readAgentPrompt 正常路径 ──────────────────────────

    @Test
    public void testReadWorkerAgent() {
        String result = PromptUtil.readAgentPrompt("worker-agent.md");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertTrue("应包含 Role 章节", result.contains("Role"));
    }

    @Test
    public void testReadSupervisorAgent() {
        String result = PromptUtil.readAgentPrompt("supervisor-agent.md");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testReadLoopAgent() {
        String result = PromptUtil.readAgentPrompt("loop-agent.md");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    @Test
    public void testReadSameFileConsistent() {
        String r1 = PromptUtil.readAgentPrompt("worker-agent.md");
        String r2 = PromptUtil.readAgentPrompt("worker-agent.md");
        assertEquals(r1, r2);
    }

    // ── readAgentPrompt 边界与异常 ───────────────────────

    @Test(expected = IllegalArgumentException.class)
    public void testReadAgentPromptFileNotFound() {
        PromptUtil.readAgentPrompt("non-exist-file.md");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadAgentPromptNullFileName() {
        PromptUtil.readAgentPrompt(null);
    }

    // ── readWorkSystemPrompt 正常路径 ─────────────────────

    @Test
    public void testReadWorkSystemPromptSuccess() {
        Map<String, String> vars = Map.of(
                "currentWorkDir", "/tmp/test",
                "isGitRepository", "false",
                "currentGitBranch", "feature/test",
                "masterBranch", "main",
                "gitUsername", "test-user",
                "platform", "linux",
                "shellProvider", "zsh",
                "osVersion", "Ubuntu 22.04");
        String result = PromptUtil.readWorkSystemPrompt("worker-system.md", vars);

        assertNotNull(result);
        assertTrue("应包含工作目录", result.contains("/tmp/test"));
        assertTrue("应包含平台", result.contains("linux"));
        assertTrue("应包含 shell", result.contains("zsh"));
        assertTrue("应包含分支", result.contains("feature/test"));
        assertTrue("应包含主分支", result.contains("main"));
    }

    @Test
    public void testReadWorkSystemPromptAllPlaceholdersReplaced() {
        Map<String, String> vars = Map.of(
                "currentWorkDir", "/home/user/project",
                "isGitRepository", "true",
                "currentGitBranch", "feature/dev",
                "masterBranch", "main",
                "gitUsername", "dev-user",
                "platform", "win32",
                "shellProvider", "bash",
                "osVersion", "Windows 10 Pro");
        String result = PromptUtil.readWorkSystemPrompt("worker-system.md", vars);

        assertNotNull(result);
        assertFalse("所有占位符应已替换", result.contains("{{"));
    }

    // ── readWorkSystemPrompt 边界情况 ─────────────────────

    @Test
    public void testReadWorkSystemPromptEmptyVariables() {
        String result = PromptUtil.readWorkSystemPrompt("worker-system.md", Map.of());
        assertNotNull(result);
        assertTrue("空变量时占位符应保留", result.contains("{{"));
    }

    @Test
    public void testReadWorkSystemPromptMissingKeyPreserved() {
        String result = PromptUtil.readWorkSystemPrompt("worker-system.md",
                Map.of("currentWorkDir", "/tmp/test"));
        assertNotNull(result);
        assertTrue("应包含已替换的值", result.contains("/tmp/test"));
        assertTrue("未匹配的占位符应保留",
                result.contains("{{isGitRepository}}")
                        || result.contains("{{platform}}")
                        || result.contains("{{shellProvider}}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadWorkSystemPromptFileNotFound() {
        PromptUtil.readWorkSystemPrompt("non-exist-file.md", Map.of());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReadWorkSystemPromptNullFileName() {
        PromptUtil.readWorkSystemPrompt(null, Map.of());
    }
}
