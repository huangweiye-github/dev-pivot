package com.hwy.devpivot.cli.jline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

/**
 * DevPivot CLI (JLine) 应用入口 —— 引导启动 {@link JlineConsole} REPL。
 */
public class JlienMain {

    private static final Logger logger = LoggerFactory.getLogger(JlienMain.class);

    public static void main(String[] args) {
//        System.setOut(new PrintStream(new OutputStream() {
//            @Override
//            public void write(int b) {
//                // 什么都不做，直接丢弃
//            }
//        }));
        System.setProperty("jansi.passthrough", "true");

        try {
            logger.debug("Starting CliConsole...");
            JlineConsole console = new JlineConsole();

            Runtime.getRuntime().addShutdownHook(
                    new Thread(() -> {
                        try {
                            console.getTerminal().close();
                        } catch (IOException ignored) {
                        }
                    }, "terminal-closer")
            );

            console.start();

            console.getTerminal().writer().println();
            console.getCollector().printAll(console.getTerminal().writer());
            logger.debug("CliConsole exited normally");

        } catch (IOException e) {
            logger.error("Failed to init terminal", e);
            System.exit(1);
        }
    }
}
