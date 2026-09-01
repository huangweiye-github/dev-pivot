package com.hwy.devpivot.cli.tambo;

import java.io.OutputStream;
import java.io.PrintStream;

/**
 * DevPivot CLI (TamboUI) 应用入口。
 */
public class TamboMain {

    public static void main(String[] args) {
        System.setOut(new PrintStream(new OutputStream() {
            @Override
            public void write(int b) {
                // 丢弃，避免杂讯干扰 TUI 渲染
            }
        }));

        TamboConsole console = new TamboConsole();
        console.start();
    }
}
