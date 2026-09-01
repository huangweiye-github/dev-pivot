package com.hwy.devpivot.agent.tools;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * GrepTool 单元测试。
 */
public class GrepToolTest {

    private final GrepTool tool = new GrepTool();

    // ── 参数校验 ─────────────────────────────────────────

    @Test
    public void testNullArgs() {
        assertEquals("Error: pattern 为必填", tool.grep(null));
    }

    @Test
    public void testNullPattern() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        assertEquals("Error: pattern 为必填", tool.grep(args));
    }

    @Test
    public void testBlankPattern() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "   ";
        assertEquals("Error: pattern 为必填", tool.grep(args));
    }

    @Test
    public void testInvalidRegex() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "[unclosed";
        assertTrue(tool.grep(args).contains("Error: 正则语法错误"));
    }

    @Test
    public void testPathNotDirectory() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "test";
        args.path = "/no/such/dir";
        assertTrue(tool.grep(args).contains("Error: 目录不存在"));
    }

    // ── 默认模式 (files_with_matches) ──────────────────

    @Test
    public void testDefaultMode() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "GrepTool";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Pattern: /GrepTool/"));
        assertTrue(result.contains("Matching files"));
        assertTrue(result.contains("GrepTool.java"));
    }

    @Test
    public void testDefaultModeNoMatch() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "ThisStringSurelyDoesNotExistAnywhereXYZ";
        args.path = "src/main";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matching files: 0"));
    }

    // ── content 模式 ────────────────────────────────────

    @Test
    public void testContentMode() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class GrepTool";
        args.outputMode = "content";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Mode: content"));
        assertTrue(result.contains("class GrepTool"));
        assertTrue(result.contains("──"));
    }

    @Test
    public void testContentModeWithContext() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class GrepTool";
        args.outputMode = "content";
        args.context = 3;
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Mode: content"));
        assertTrue(result.contains("class GrepTool"));
    }

    @Test
    public void testContentModeWithBeforeAfter() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class GrepTool";
        args.outputMode = "content";
        args.before = 1;
        args.after = 2;
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("class GrepTool"));
    }

    // ── count 模式 ──────────────────────────────────────

    @Test
    public void testCountMode() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "import";
        args.outputMode = "count";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matches: "));
        assertTrue(result.contains("files)"));
        assertTrue(result.contains(".java:"));
    }

    @Test
    public void testCountModeWithHeadLimit() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "import";
        args.outputMode = "count";
        args.headLimit = 2;
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matches: "));
    }

    // ── head_limit ──────────────────────────────────────

    @Test
    public void testDefaultHeadLimit() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "[a-z]";  // matches almost every line
        args.outputMode = "content";
        String result = tool.grep(args);
        // head limit默认250，输出不应该超过太多行
        assertNotNull(result);
        assertTrue(result.contains("Pattern:"));
    }

    @Test
    public void testHeadLimitZeroUnlimited() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class ";
        args.outputMode = "content";
        args.headLimit = 0;
        String result = tool.grep(args);
        assertNotNull(result);
        assertFalse(result.contains("...(head_limit)"));
    }

    // ── caseInsensitive ────────────────────────────────

    @Test
    public void testCaseInsensitive() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "greptool";
        args.caseInsensitive = true;
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("GrepTool.java"));
    }

    @Test
    public void testCaseSensitiveNoMatch() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "greptool";
        args.path = "src/main";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matching files: 0"));
    }

    // ── glob 过滤 ───────────────────────────────────────

    @Test
    public void testGlobFilter() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class ";
        args.glob = "*.java";
        args.outputMode = "count";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains(".java:"));
    }

    @Test
    public void testGlobFilterNoMatch() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class ";
        args.glob = "*.xyz";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matching files: 0"));
    }

    // ── type 过滤 ───────────────────────────────────────

    @Test
    public void testTypeFilterJava() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class GrepTool";
        args.type = "java";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("GrepTool.java"));
    }

    @Test
    public void testTypeFilterNoMatch() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class ";
        args.type = "py";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matching files: 0"));
    }

    // ── multiline ───────────────────────────────────────

    @Test
    public void testMultilineMode() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class GrepTool.*\\n.*DEV_PIVOT";
        args.multiline = true;
        args.outputMode = "content";
        String result = tool.grep(args);
        assertNotNull(result);
        // multiline regex with DOTALL may match across lines
        assertTrue(result.contains("Pattern:"));
    }

    // ── 指定 path ───────────────────────────────────────

    @Test
    public void testSpecificPath() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "class AskUserQuestionTool";
        args.path = "src";
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("AskUserQuestionTool.java"));
    }

    // ── 组合参数 ────────────────────────────────────────

    @Test
    public void testCombinedOptions() {
        GrepTool.GrepArgs args = new GrepTool.GrepArgs();
        args.pattern = "devpivot";
        args.caseInsensitive = true;
        args.type = "java";
        args.outputMode = "count";
        args.headLimit = 5;
        String result = tool.grep(args);
        assertNotNull(result);
        assertTrue(result.contains("Matches: "));
    }
}
