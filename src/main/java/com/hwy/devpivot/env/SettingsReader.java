package com.hwy.devpivot.env;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 读取 settings.json 配置，优先级：外部文件 > classpath 资源 > 代码默认值。
 */
public class SettingsReader {

    private static final Logger logger = LoggerFactory.getLogger(SettingsReader.class);

    private static final String FILE_NAME = "settings.json";

    private static volatile SettingsConfig settingsConfig;

    public static SettingsConfig getSettings() {
        if (settingsConfig != null) return settingsConfig;
        synchronized (SettingsReader.class) {
            if (settingsConfig != null) return settingsConfig;
            settingsConfig = loadConfig();
            return settingsConfig;
        }
    }

    public static ModelConfig getModelConfig() {
        SettingsConfig settings = getSettings();
        if (settings.getModel() != null) {
            return settings.getModel();
        }
        logger.warn("settings.json 中缺少 model 配置，使用默认值");
        return new ModelConfig();
    }

    private static SettingsConfig loadConfig() {
        String content = null;

        // 1. 尝试外部文件（工作目录下的 settings.json）
        Path externalPath = Paths.get(System.getProperty("user.dir"), FILE_NAME);
        if (Files.exists(externalPath)) {
            try {
                content = Files.readString(externalPath, StandardCharsets.UTF_8);
                logger.info("加载外部配置: {}", externalPath);
            } catch (IOException e) {
                logger.warn("读取外部配置失败: {}", externalPath, e);
            }
        }

        // 2. 回退 classpath 资源
        if (content == null) {
            InputStream is = SettingsReader.class.getClassLoader().getResourceAsStream(FILE_NAME);
            if (is != null) {
                try (is) {
                    content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    logger.info("加载 classpath 配置: {}", FILE_NAME);
                } catch (IOException e) {
                    logger.warn("读取 classpath 配置失败", e);
                }
            }
        }

        // 3. 兜底默认空配置 → POJO 默认值生效
        if (content == null) {
            logger.warn("未找到 settings.json，使用默认值");
            return new SettingsConfig();
        }

        return JSON.parseObject(content, SettingsConfig.class);
    }
}
