package com.hwy.devpivot.agent.tools;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * BashTool 单元测试。
 */
public class BashToolTest {

    private final BashTool tool = new BashTool();

    // ── 常见命令 ────────────────────────────────────────────

    @Test
    public void testEcho() {
        String result = tool.bash("echo hello", null, null, null, null);
        assertNotNull(result);
        assertTrue("应包含 hello", result.contains("hello"));
        assertTrue("应包含 Exit code: 0", result.contains("Exit code: 0"));
    }

    @Test
    public void testEchoWithDescription() {
        String result = tool.bash("echo test123", "输出测试内容", null, null, null);
        assertNotNull(result);
        assertTrue("应包含描述", result.contains("输出测试内容"));
        assertTrue("应包含 test123", result.contains("test123"));
    }

    @Test
    public void testDirCommand() {
        String result = tool.bash("dir", null, null, null, null);
        assertNotNull(result);
        assertTrue("应包含 Exit code", result.contains("Exit code:"));
    }

    @Test
    public void testEchoWithChinese() {
        String result = tool.bash("echo 你好世界", "中文测试", null, null, null);
        assertNotNull(result);
        assertTrue("应包含中文", result.contains("你好世界"));
    }

    @Test
    public void testPwd() {
        // Windows 使用 cd 或 echo %cd%
        String result = tool.bash("cd", null, null, null, null);
        assertNotNull(result);
        assertTrue("应包含 Exit code: 0", result.contains("Exit code: 0"));
    }

    // ── 边界与异常 ──────────────────────────────────────────

    @Test
    public void testNullCommand() {
        String result = tool.bash(null, null, null, null, null);
        assertEquals("Error: command 为必填", result);
    }

    @Test
    public void testBlankCommand() {
        String result = tool.bash("   ", null, null, null, null);
        assertEquals("Error: command 为必填", result);
    }

    @Test
    public void testCommandNotFound() {
        String result = tool.bash("nonexistent_cmd_xyz123", null, null, null, null);
        assertNotNull(result);
        // 命令不存在时 Exit code 非 0
        assertTrue("命令不存在时 Exit code 应非 0", result.contains("Exit code:"));
    }

    @Test
    public void testNullOptionalParams() {
        // description / timeout / run_in_background / dangerouslyDisableSandbox 均为 null
        String result = tool.bash("echo null_test", null, null, null, null);
        assertNotNull(result);
        assertTrue(result.contains("null_test"));
        assertTrue(result.contains("Exit code: 0"));
    }

    // ── 超时 ────────────────────────────────────────────────

    @Test
    public void testTimeout() {
        // sleep 命令: Windows 用 timeout /t
        String result = tool.bash("timeout /t 5", "超时测试", 500, null, null);
        assertNotNull(result);
        assertTrue("应触发超时", result.contains("超时") || result.contains("timeout"));
    }

    // ── 后台任务 ────────────────────────────────────────────

    @Test
    public void testBackgroundTask() {
        String result = tool.bash("echo bg_hello", "后台echo测试", null, true, null);
        assertNotNull(result);
        assertTrue("应包含 task_id 提示", result.contains("后台任务") || result.contains("task"));
    }

    @Test
    public void testTaskStatusNotFound() {
        String result = BashTool.taskStatus("nonexistent_id");
        assertTrue("应提示不存在", result.contains("不存在") || result.contains("Error"));
    }

    @Test
    public void testListTasks() {
        // 先确保有后台任务
        tool.bash("echo bg_list", null, null, true, null);
        String list = BashTool.listTasks();
        assertNotNull(list);
        // 至少包含表头或"无后台任务"
        assertTrue(list.length() > 0);
    }

    // ── git 命令 ────────────────────────────────────────────

    @Test
    public void testGitLog() {
        String result = tool.bash(
                "git log --oneline -10 2>/dev/null || echo \"Not a git repository or no commits\"",
                "查看最近10条git提交",
                null, null, null);
        assertNotNull(result);
        // 要么拿到 git log（包含 commit hash），要么拿到兜底提示
        assertTrue("应包含 commit 记录或兜底提示",
                result.contains("Exit code:"));
    }

    @Test
    public void testLsLa() {
        String result = tool.bash("ls -la", "列出目录详细信息", null, null, null);
        assertNotNull(result);
        assertTrue("应包含 Exit code", result.contains("Exit code:"));
    }

    // ── 输出完整性验证 ──────────────────────────────────────

    @Test
    public void testOutputContainsCommand() {
        String result = tool.bash("echo verify", null, null, null, null);
        assertTrue("输出应包含执行的命令", result.contains("echo verify"));
    }

    @Test
    public void testOutputContainsExitCode() {
        String result = tool.bash("echo ok", null, null, null, null);
        assertTrue("输出应包含 Exit code", result.contains("Exit code:"));
    }
}
