package com.hwy.devpivot.env;

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * EnvironmentReader 单元测试。
 */
public class EnvironmentReaderTest {

    @Test
    public void testGetCurrentWorkDir() {
        EnvironmentReader reader = new EnvironmentReader();
        String dir = reader.getCurrentWorkDir();
        assertNotNull(dir);
        assertFalse(dir.isBlank());
        // 工作目录应该以盘符或 / 开头
        assertTrue(dir.contains("\\") || dir.contains("/") || dir.contains(":"));
    }

    @Test
    public void testGetIsGitRepository() {
        EnvironmentReader reader = new EnvironmentReader();
        String isGit = reader.getIsGitRepository();
        assertNotNull(isGit);
        assertTrue("应为 true 或 false", "true".equals(isGit) || "false".equals(isGit));
    }

    @Test
    public void testGetPlatform() {
        EnvironmentReader reader = new EnvironmentReader();
        String platform = reader.getPlatform();
        assertNotNull(platform);
        assertTrue("应为 win32/darwin/linux 之一",
                platform.equals("win32") || platform.equals("darwin") || platform.equals("linux"));
    }

    @Test
    public void testGetShellProvider() {
        EnvironmentReader reader = new EnvironmentReader();
        String shell = reader.getShellProvider();
        assertNotNull(shell);
        assertFalse(shell.isBlank());
    }

    @Test
    public void testGetOsVersion() {
        EnvironmentReader reader = new EnvironmentReader();
        String osVersion = reader.getOsVersion();
        assertNotNull(osVersion);
        assertFalse(osVersion.isBlank());
        assertTrue(osVersion.contains(" ")); // os.name + " " + os.version
    }

    @Test
    public void testGetModelName() {
        EnvironmentReader reader = new EnvironmentReader();
        String model = reader.getModelName();
        assertNotNull(model);
        assertFalse(model.isBlank());
    }

    @Test
    public void testCache() {
        EnvironmentReader reader = new EnvironmentReader();
        String dir1 = reader.getCurrentWorkDir();
        String dir2 = reader.getCurrentWorkDir();
        assertSame("两次调用应返回同一缓存对象", dir1, dir2);
    }

    @Test
    public void testGetCurrentGitBranch() {
        EnvironmentReader reader = new EnvironmentReader();
        String branch = reader.getCurrentGitBranch();
        assertNotNull(branch);
        assertFalse(branch.isBlank());
    }

    @Test
    public void testGetMasterBranchName() {
        EnvironmentReader reader = new EnvironmentReader();
        String master = reader.getMasterBranchName();
        assertNotNull(master);
        assertFalse(master.isBlank());
        assertTrue("应为 main 或 master", "main".equals(master) || "master".equals(master));
    }

    @Test
    public void testGetGitUsername() {
        EnvironmentReader reader = new EnvironmentReader();
        String username = reader.getGitUsername();
        assertNotNull(username);
        assertFalse(username.isBlank());
    }

    @Test
    public void testGetAllVariables() {
        EnvironmentReader reader = new EnvironmentReader();
        Map<String, String> vars = reader.getAllVariables();
        assertNotNull(vars);
        assertEquals(9, vars.size());
        // 验证所有 key 都存在
        assertTrue(vars.containsKey("currentWorkDir"));
        assertTrue(vars.containsKey("isGitRepository"));
        assertTrue(vars.containsKey("platform"));
        assertTrue(vars.containsKey("shellProvider"));
        assertTrue(vars.containsKey("osVersion"));
        assertTrue(vars.containsKey("modelName"));
        assertTrue(vars.containsKey("currentGitBranch"));
        assertTrue(vars.containsKey("masterBranch"));
        assertTrue(vars.containsKey("gitUsername"));
        // 值都不为空
        vars.values().forEach(v -> {
            assertNotNull(v);
            assertFalse(v.isBlank());
        });
    }
}
