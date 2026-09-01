package com.hwy.devpivot.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class WriteTool implements DevPivotTool {

@Tool(name = "Write", value = "写入文件到本地文件系统。若文件已存在则覆盖，自动创建父级目录。")
    public String write(
            @P("文件绝对路径") String file_path,
            @P("要写入的完整内容") String content) {

        if (file_path == null || file_path.isBlank()) return "Error: file_path 为必填参数";
        if (content == null) return "Error: content 为必填参数";

        Path path = Paths.get(file_path);
        if (!path.isAbsolute()) return "Error: 文件路径必须是绝对路径";

        try {
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) Files.createDirectories(parent);
            boolean existed = Files.exists(path);
            long oldSize = existed ? Files.size(path) : 0;

            Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            long newSize = Files.size(path);
            int lines = (int) content.lines().count();
            return "✅ 文件写入成功\n   路径: " + path.toAbsolutePath()
                    + "\n   大小: " + fmtSize(newSize) + " (" + lines + " 行)"
                    + "\n   状态: " + (existed ? "已覆盖 (旧文件 " + fmtSize(oldSize) + ")" : "新建");
        } catch (IOException e) { return "Error: 写入失败: " + e.getMessage(); }
    }

    private static String fmtSize(long b) {
        if (b < 1024) return b+" B";
        if (b < 1024*1024) return String.format("%.1f KB", b/1024.0);
        if (b < 1024*1024*1024) return String.format("%.1f MB", b/(1024.0*1024));
        return String.format("%.2f GB", b/(1024.0*1024*1024));
    }
}
