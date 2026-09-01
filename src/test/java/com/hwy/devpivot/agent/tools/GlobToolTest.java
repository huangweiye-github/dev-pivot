package com.hwy.devpivot.agent.tools;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * GlobTool 单元测试。
 */
public class GlobToolTest {

    private final GlobTool tool = new GlobTool();
    private Path tmpDir;

    @Before
    public void setUp() throws IOException {
        tmpDir = Files.createTempDirectory("globtool-test-");
        // 创建测试目录结构
        Files.createDirectories(tmpDir.resolve("src/main/java/com/test"));
        Files.createDirectories(tmpDir.resolve("src/test/java/com/test"));
        Files.createDirectories(tmpDir.resolve("node_modules/lib"));
        Files.createDirectories(tmpDir.resolve(".hidden_dir"));
        Files.createDirectories(tmpDir.resolve("target/classes"));
        Files.createDirectories(tmpDir.resolve("src/main/resources"));

        // 创建测试文件
        Files.writeString(tmpDir.resolve("README.md"), "# Test");
        Files.writeString(tmpDir.resolve("pom.xml"), "<xml>");
        Files.writeString(tmpDir.resolve("src/main/java/com/test/Hello.java"), "class Hello {}");
        Files.writeString(tmpDir.resolve("src/main/java/com/test/Service.java"), "class Service {}");
        Files.writeString(tmpDir.resolve("src/test/java/com/test/HelloTest.java"), "class HelloTest {}");
        Files.writeString(tmpDir.resolve("src/main/resources/application.yml"), "server: 8080");
        Files.writeString(tmpDir.resolve(".hidden_dir/secret.txt"), "secret");
        Files.writeString(tmpDir.resolve("node_modules/lib/index.js"), "module.exports = {};");
        Files.writeString(tmpDir.resolve("target/classes/Hello.class"), "binary");
        Files.writeString(tmpDir.resolve(".gitignore"), "*.class");
    }

    @After
    public void tearDown() throws IOException {
        if (tmpDir != null && Files.exists(tmpDir)) {
            try (var stream = Files.walk(tmpDir)) {
                stream.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
            }
        }
    }

    // ── 正常场景 ────────────────────────────────────────────

    @Test
    public void testGlobAllJavaFiles() {
        String result = tool.glob("**/*.java", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含 Hello.java", result.contains("Hello.java"));
        assertTrue("应包含 Service.java", result.contains("Service.java"));
        assertTrue("应包含 HelloTest.java", result.contains("HelloTest.java"));
        assertTrue("应包含 Matches", result.contains("Matches:"));
    }

    @Test
    public void testGlobSingleLevelPattern() {
        String result = tool.glob("*.md", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应匹配 README.md", result.contains("README.md"));
    }

    @Test
    public void testGlobWithCurrentDir() {
        String result = tool.glob("pom.xml", null);
        assertNotNull(result);
        assertTrue("应包含 pom.xml 或无匹配", result.contains("pom.xml") || result.contains("无匹配"));
    }

    @Test
    public void testGlobWithBlankPath() {
        String result = tool.glob("pom.xml", "  ");
        assertNotNull(result);
        assertTrue("应包含 pom.xml 或无匹配", result.contains("pom.xml") || result.contains("无匹配"));
    }

    @Test
    public void testGlobYmlFiles() {
        String result = tool.glob("**/*.yml", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应匹配 application.yml", result.contains("application.yml"));
    }

    @Test
    public void testGlobInSubdirectory() {
        String result = tool.glob("*.java", tmpDir.resolve("src/main/java/com/test").toString());
        assertNotNull(result);
        assertTrue("应包含 Hello.java", result.contains("Hello.java"));
        assertTrue("应包含 Service.java", result.contains("Service.java"));
        assertFalse("不应包含测试目录下的文件", result.contains("HelloTest.java"));
    }

    // ── 边界条件 ────────────────────────────────────────────

    @Test
    public void testNullPattern() {
        String result = tool.glob(null, tmpDir.toString());
        assertEquals("Error: pattern 为必填参数", result);
    }

    @Test
    public void testBlankPattern() {
        String result = tool.glob("  ", tmpDir.toString());
        assertEquals("Error: pattern 为必填参数", result);
    }

    @Test
    public void testEmptyPattern() {
        String result = tool.glob("", tmpDir.toString());
        assertEquals("Error: pattern 为必填参数", result);
    }

    @Test
    public void testNonExistentDirectory() {
        String result = tool.glob("*.java", tmpDir.resolve("nonexistent_dir").toString());
        assertNotNull(result);
        assertTrue("应提示目录不存在", result.startsWith("Error: 目录不存在"));
    }

    @Test
    public void testPathIsFileNotDirectory() {
        Path file = tmpDir.resolve("README.md");
        String result = tool.glob("*.md", file.toString());
        assertNotNull(result);
        assertTrue("应提示目录不存在", result.startsWith("Error: 目录不存在"));
    }

    @Test
    public void testNoMatchPattern() {
        String result = tool.glob("*.cpp", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含无匹配", result.contains("(无匹配)"));
        assertTrue("Matches 应为 0", result.contains("Matches: 0"));
    }

    @Test
    public void testOutputFormatHeader() {
        String result = tool.glob("*.md", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含 Glob 头部", result.contains("Glob: *.md"));
        assertTrue("应包含 Path 头部", result.contains("Path: "));
        assertTrue("应包含 Matches", result.contains("Matches:"));
    }

    // ── 忽略规则 ────────────────────────────────────────────

    @Test
    public void testIgnoresNodeModules() {
        String result = tool.glob("**/*.js", tmpDir.toString());
        assertNotNull(result);
        assertFalse("不应包含 node_modules 下的文件", result.contains("index.js"));
        assertTrue("应显示无匹配", result.contains("(无匹配)"));
    }

    @Test
    public void testIgnoresHiddenDirectories() {
        String result = tool.glob("**/*.txt", tmpDir.toString());
        assertNotNull(result);
        assertFalse("不应包含 .hidden_dir 下的文件", result.contains("secret.txt"));
        assertTrue("应显示无匹配", result.contains("(无匹配)"));
    }

    @Test
    public void testIgnoresTarget() {
        String result = tool.glob("**/*.class", tmpDir.toString());
        assertNotNull(result);
        assertFalse("不应包含 target 下的文件", result.contains("target"));
        assertTrue("应显示无匹配", result.contains("(无匹配)"));
    }

    @Test
    public void testVisibleDotFiles() {
        String result = tool.glob(".git*", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含 .gitignore", result.contains(".gitignore"));
    }

    // ── 输出格式 ────────────────────────────────────────────

    @Test
    public void testResultSeparatorLine() {
        String result = tool.glob("*.md", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含分隔线", result.contains("─"));
    }

    @Test
    public void testFileSizeDisplayed() {
        String result = tool.glob("README.md", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含文件大小 (B/KB/MB/GB)", result.contains(" B") || result.contains("KB") || result.contains("MB"));
    }

    // ── 特殊字符模式 ────────────────────────────────────────

    @Test
    public void testPatternWithExtensionBrace() {
        String result = tool.glob("pom.{xml,yml}", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应匹配 pom.xml", result.contains("pom.xml"));
    }

    @Test
    public void testPatternWithQuestionMark() {
        String result = tool.glob("????.md", tmpDir.toString());
        assertNotNull(result);
        assertTrue("README.md 长度为10，不匹配 ????", result.contains("(无匹配)"));
    }

    @Test
    public void testPatternWithWildcardInMiddle() {
        String result = tool.glob("src/**/com/test/*.java", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应包含 Hello.java", result.contains("Hello.java"));
    }

    // ── 更多忽略目录 ────────────────────────────────────────

    @Test
    public void testIgnoresBuildDir() throws IOException {
        Files.createDirectories(tmpDir.resolve("build/classes"));
        Files.writeString(tmpDir.resolve("build/classes/Built.class"), "binary");
        String result = tool.glob("**/*.class", tmpDir.toString());
        assertFalse("不应包含 build 下的文件", result.contains("build"));
    }

    @Test
    public void testIgnoresDistDir() throws IOException {
        Files.createDirectories(tmpDir.resolve("dist"));
        Files.writeString(tmpDir.resolve("dist/bundle.js"), "code");
        String result = tool.glob("**/*.js", tmpDir.toString());
        assertFalse("不应包含 dist 下的文件", result.contains("dist"));
    }

    @Test
    public void testIgnoresIdea() throws IOException {
        Files.createDirectories(tmpDir.resolve(".idea"));
        Files.writeString(tmpDir.resolve(".idea/workspace.xml"), "<xml>");
        String result = tool.glob("**/*.xml", tmpDir.toString());
        assertFalse("不应包含 .idea 下的文件", result.contains("workspace.xml"));
    }

    @Test
    public void testIgnoresPycache() throws IOException {
        Files.createDirectories(tmpDir.resolve("__pycache__"));
        Files.writeString(tmpDir.resolve("__pycache__/mod.cpython-39.pyc"), "binary");
        String result = tool.glob("**/*.pyc", tmpDir.toString());
        assertFalse("不应包含 __pycache__ 下的文件", result.contains("__pycache__"));
    }

    // ── 空目录 & 相对路径 ────────────────────────────────────

    @Test
    public void testGlobInEmptyDirectory() throws IOException {
        Path emptyDir = Files.createTempDirectory("globtool-empty-");
        try {
            String result = tool.glob("*.java", emptyDir.toString());
            assertNotNull(result);
            assertTrue("应显示无匹配", result.contains("(无匹配)"));
            assertTrue("Matches 应为 0", result.contains("Matches: 0"));
        } finally {
            Files.deleteIfExists(emptyDir);
        }
    }

    @Test
    public void testGlobWithRelativePath() {
        String result = tool.glob("pom.xml", ".");
        assertNotNull(result);
        assertTrue("应包含 pom.xml 或无匹配", result.contains("pom.xml") || result.contains("(无匹配)"));
    }

    // ── Windows **/ flatMatcher 兜底 ─────────────────────────

    @Test
    public void testDoubleStarPrefixMatchesRootFiles() {
        String result = tool.glob("**/*.xml", tmpDir.toString());
        assertNotNull(result);
        assertTrue("**/*.xml 应匹配根目录的 pom.xml", result.contains("pom.xml"));
    }

    @Test
    public void testDoubleStarPrefixMatchesRootFilesByName() {
        String result = tool.glob("**/pom.xml", tmpDir.toString());
        assertNotNull(result);
        assertTrue("**/pom.xml 应匹配根目录的 pom.xml", result.contains("pom.xml"));
    }

    // ── 深层嵌套（在 MAX_DEPTH 限制内） ──────────────────────

    @Test
    public void testDeepNestedWithinLimit() throws IOException {
        Path deep = tmpDir;
        for (int i = 0; i < 10; i++) {
            deep = deep.resolve("level" + i);
            Files.createDirectories(deep);
        }
        Files.writeString(deep.resolve("deep.txt"), "deep");
        String result = tool.glob("**/*.txt", tmpDir.toString());
        assertTrue("应匹配深层文件", result.contains("deep.txt"));
    }

    // ── 结果排序验证 ────────────────────────────────────────

    @Test
    public void testResultsSortedByModificationTimeDesc() throws Exception {
        Path a = tmpDir.resolve("a-sort.txt");
        Path b = tmpDir.resolve("b-sort.txt");
        Files.writeString(a, "first");
        Thread.sleep(10);
        Files.writeString(b, "second");
        String result = tool.glob("*-sort.txt", tmpDir.toString());
        int idxA = result.indexOf("a-sort.txt");
        int idxB = result.indexOf("b-sort.txt");
        assertTrue("b-sort.txt 应在 a-sort.txt 前面（修改时间更晚）", idxB < idxA);
    }

    // ── 精确匹配与单文件 ────────────────────────────────────

    @Test
    public void testExactFileNameMatch() {
        String result = tool.glob("README.md", tmpDir.toString());
        assertNotNull(result);
        assertTrue("应匹配 README.md", result.contains("README.md"));
        assertTrue("Matches 应为 1", result.contains("Matches: 1"));
    }
}
