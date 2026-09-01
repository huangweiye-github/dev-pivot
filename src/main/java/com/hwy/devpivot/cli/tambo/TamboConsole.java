package com.hwy.devpivot.cli.tambo;

import com.hwy.devpivot.agent.AgentChat;
import com.hwy.devpivot.cli.CliConsoleManager;
import com.hwy.devpivot.cli.ConsoleSession;
import com.hwy.devpivot.cli.InputCollector;
import com.hwy.devpivot.cli.UserInput;
import dev.tamboui.style.Color;
import dev.tamboui.toolkit.app.ToolkitRunner;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.Column;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.elements.Panel;
import dev.tamboui.toolkit.elements.TextElement;
import dev.tamboui.toolkit.elements.TextInputElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.event.Event;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.widgets.input.TextInputState;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * TamboUI 版 DevPivot CLI —— 基于 Toolkit DSL 的现代化终端交互界面。
 *
 * <p>功能:
 * <ul>
 *   <li>TextInput 接收用户输入（Enter 提交）</li>
 *   <li>流式展示 Agent 的 Thinking / Result 输出</li>
 *   <li>内置命令: /help /exit /clear /history /status /reset</li>
 *   <li>输入历史记录（通过 /history 查看）</li>
 * </ul>
 */
public class TamboConsole implements ConsoleSession {

    private static final DateTimeFormatter TF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SUGGESTIONS = 20;

    // ── Slash commands ───────────────────────────────
    private static final LinkedHashMap<String, String> SLASH_COMMANDS = buildSlashCommands();

    private static LinkedHashMap<String, String> buildSlashCommands() {
        LinkedHashMap<String, String> cmds = new LinkedHashMap<>();
        cmds.put("/help",    "Show help");
        cmds.put("/exit",    "Quit");
        cmds.put("/quit",    "Quit (alias)");
        cmds.put("/clear",   "Clear screen");
        cmds.put("/history", "Show input history");
        cmds.put("/status",  "Session status");
        cmds.put("/reset",   "Reset session");
        return cmds;
    }

    private final String conversationId = UUID.randomUUID().toString();
    private final LocalDateTime startTime = LocalDateTime.now();
    private final InputCollector collector = new InputCollector();
    private final TextInputState inputState = new TextInputState();
    private final List<String> outputLines = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean processing = new AtomicBoolean(false);
    private final StringBuilder printBuffer = new StringBuilder();

    // ── Suggestion state ─────────────────────────────
    private final List<String> suggestions = Collections.synchronizedList(new ArrayList<>());
    private volatile int selectedSuggestionIdx = 0;
    private volatile boolean suggestionActive;

    private volatile boolean running = true;
    private ToolkitRunner runner;

    @Override
    public void start() {
        CliConsoleManager.register(conversationId, this);
        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                CliConsoleManager.remove(conversationId)));

        outputLines.add("+------------------------------------------+");
        outputLines.add("|     DevPivot CLI  --  TamboUI TUI       |");
        outputLines.add("+------------------------------------------+");
        outputLines.add("|  Enter     -> submit input               |");
        outputLines.add("|  Tab       -> autocomplete / @           |");
        outputLines.add("|  /help     -> show help                  |");
        outputLines.add("|  /exit     -> quit                       |");
        outputLines.add("|  /history  -> show input history         |");
        outputLines.add("|  /status   -> show session status        |");
        outputLines.add("|  /clear    -> clear screen               |");
        outputLines.add("|  /reset    -> reset session              |");
        outputLines.add("+------------------------------------------+");
        outputLines.add("");

        try (ToolkitRunner r = ToolkitRunner.create()) {
            this.runner = r;
            r.eventRouter().addGlobalHandler(this::handleGlobalEvent);
            r.run(this::render);
        } catch (Exception e) {
            System.err.println("TamboUI error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Element render() {
        List<String> snapshot = new ArrayList<>(outputLines);

        Element outputArea;
        if (snapshot.isEmpty()) {
            outputArea = new TextElement("Ready. Type a message or /help to start.").dim();
        } else {
            outputArea = new ListElement(snapshot).displayOnly().autoScroll();
        }

        // 根据当前输入更新建议列表
        updateSuggestions(inputState.text());
        Element suggestionBar = buildSuggestionBar();

        return new Column(
                new TextElement("DevPivot CLI").bold().cyan(),

                new Panel("Conversation", outputArea)
                        .rounded()
                        .borderColor(Color.CYAN)
                        .fill(3),

                suggestionBar,

                new Panel("Input",
                        new TextInputElement(inputState)
                                .placeholder(processing.get()
                                        ? "Processing..."
                                        : "Type a message or /command...")
                                .onSubmit(this::handleSubmit)
                )
                        .rounded()
                        .borderColor(processing.get() ? Color.DARK_GRAY : Color.YELLOW)
                        .focusedBorderColor(Color.CYAN)
                        .length(3)
        );
    }

    // ── Global key handler (fires before TextInputElement) ──

    private EventResult handleGlobalEvent(Event event) {
        if (processing.get()) return EventResult.UNHANDLED;
        if (!(event instanceof KeyEvent keyEvent)) return EventResult.UNHANDLED;

        if (suggestionActive && !suggestions.isEmpty()) {
            if (keyEvent.isKey(KeyCode.TAB)) {
                acceptSuggestion();
                return EventResult.HANDLED;
            }
            if (keyEvent.isDown() || keyEvent.isUp()) {
                int size = suggestions.size();
                if (keyEvent.isDown()) {
                    selectedSuggestionIdx = (selectedSuggestionIdx + 1) % size;
                } else {
                    selectedSuggestionIdx = (selectedSuggestionIdx - 1 + size) % size;
                }
                return EventResult.HANDLED;
            }
        }

        return EventResult.UNHANDLED;
    }

    // ── Suggestions ──────────────────────────────────

    private void acceptSuggestion() {
        if (suggestions.isEmpty()) return;
        int idx = Math.min(selectedSuggestionIdx, suggestions.size() - 1);
        String selected = suggestions.get(idx);
        if (selected == null || selected.isEmpty()) return;

        String text = inputState.text();
        if (text == null) return;

        if (text.startsWith("/")) {
            inputState.setText(selected);
        } else {
            // @ file path: find last @ and replace from there
            int atIdx = text.lastIndexOf('@');
            if (atIdx < 0) return;
            inputState.setText(text.substring(0, atIdx) + "@" + selected);
        }
        selectedSuggestionIdx = 0;
    }

    private void updateSuggestions(String text) {
        suggestions.clear();
        suggestionActive = false;
        selectedSuggestionIdx = 0;

        if (text == null || text.isEmpty()) return;
        String stripped = text.stripLeading();

        if (stripped.startsWith("/")) {
            computeSlashSuggestions(stripped);
        } else {
            // check for @ mention
            int atIdx = text.lastIndexOf('@');
            if (atIdx >= 0) {
                computeAtSuggestions(text.substring(atIdx + 1));
            }
        }
    }

    private void computeSlashSuggestions(String prefix) {
        String lower = prefix.toLowerCase();
        for (String cmd : SLASH_COMMANDS.keySet()) {
            if (cmd.toLowerCase().startsWith(lower) && !cmd.equals(lower)) {
                suggestions.add(cmd);
            }
        }
        if (!suggestions.isEmpty()) {
            suggestionActive = true;
        }
    }

    private void computeAtSuggestions(String query) {
        if (query.isEmpty()) {
            // Show current directory contents
            query = "";
        }

        // Normalize Windows drive letter path
        if (query.length() >= 3
                && Character.isLetter(query.charAt(0))
                && query.charAt(1) == ':'
                && query.charAt(2) != '/'
                && query.charAt(2) != '\\') {
            query = query.substring(0, 2) + '/' + query.substring(2);
        }

        String baseDir;
        String nameFilter;
        int lastSlash = Math.max(query.lastIndexOf('/'), query.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            baseDir = query.substring(0, lastSlash + 1);
            nameFilter = query.substring(lastSlash + 1).toLowerCase();
        } else {
            if (isWindowsDriveLetter(query)) {
                baseDir = query + "/";
                nameFilter = "";
            } else {
                baseDir = "";
                nameFilter = query.toLowerCase();
            }
        }

        Path searchRoot = resolveAtSearchRoot(baseDir);
        if (searchRoot == null || !Files.isDirectory(searchRoot)) return;

        java.io.File[] children = searchRoot.toFile().listFiles();
        if (children == null || children.length > 500) return;

        Arrays.sort(children, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
            return a.getName().compareToIgnoreCase(b.getName());
        });

        for (java.io.File f : children) {
            String name = f.getName();
            if (name.startsWith(".")) continue;
            if (!matchesFileFilter(name.toLowerCase(), nameFilter)) continue;
            String entry = baseDir + name;
            if (f.isDirectory()) entry += "/";
            suggestions.add(entry);
            if (suggestions.size() >= MAX_SUGGESTIONS) break;
        }

        if (!suggestions.isEmpty()) {
            suggestionActive = true;
        }
    }

    private static boolean isWindowsDriveLetter(String s) {
        return s.length() == 2
                && Character.isLetter(s.charAt(0))
                && s.charAt(1) == ':';
    }

    private static Path resolveAtSearchRoot(String baseDir) {
        if (baseDir.isEmpty()) return Paths.get(".").toAbsolutePath().normalize();
        if (baseDir.startsWith("~")) {
            String home = System.getProperty("user.home");
            String rest = baseDir.length() > 1 ? baseDir.substring(1) : "";
            return Paths.get(home + rest).normalize();
        }
        if (baseDir.length() >= 2
                && Character.isLetter(baseDir.charAt(0))
                && baseDir.charAt(1) == ':') {
            String drive = baseDir.substring(0, 1).toUpperCase() + ":\\";
            String rest = baseDir.substring(2).replace('/', '\\');
            if (rest.startsWith("\\")) rest = rest.substring(1);
            return Paths.get(drive + rest).normalize().toAbsolutePath();
        }
        Path p = Paths.get(baseDir);
        return p.isAbsolute() ? p.normalize() : Paths.get(".").resolve(p).normalize();
    }

    private static boolean matchesFileFilter(String name, String filter) {
        if (filter.isEmpty()) return true;
        int maxTrim = Math.min(filter.length() / 4, 3);
        for (int trim = 0; trim <= maxTrim; trim++) {
            String f = filter.substring(0, filter.length() - trim);
            if (f.isEmpty()) return true;
            if (name.contains(f)) return true;
        }
        return false;
    }

    private Element buildSuggestionBar() {
        List<String> snapshot = new ArrayList<>(suggestions);
        if (snapshot.isEmpty()) {
            return new TextElement(""); // invisible spacer
        }

        int count = Math.min(snapshot.size(), 12);
        List<String> display = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String prefix = (i == selectedSuggestionIdx % count) ? " > " : "   ";
            display.add(prefix + snapshot.get(i));
        }
        if (snapshot.size() > count) {
            display.add("   ... +" + (snapshot.size() - count) + " more (Tab to complete)");
        }

        return new Panel("Suggestions",
                new ListElement(display).displayOnly())
                .rounded()
                .borderColor(Color.YELLOW)
                .length(Math.min(count + 1, 6));
    }

    // ── Input handler ───────────────────────────────

    private void handleSubmit() {
        String raw = inputState.text();
        if (raw == null) return;
        final String text = raw.stripTrailing();
        if (text.isEmpty()) return;

        collector.addAuto(text);
        inputState.clear();

        if (text.startsWith("/")) {
            handleBuiltin(text.strip());
            return;
        }

        outputLines.add("> " + text);
        processing.set(true);

        new Thread(() -> {
            try {
                String result = AgentChat.chat(text, conversationId);
                if (result != null && !result.isEmpty()) {
                    outputLines.add("");
                    outputLines.add(result);
                }
                outputLines.add("");
            } catch (Exception e) {
                outputLines.add("Error: " + e.getMessage());
            } finally {
                processing.set(false);
            }
        }, "agent-invoke").start();
    }

    // ── Built-in commands ──────────────────────────

    private void handleBuiltin(String cmd) {
        switch (cmd) {
            case "/exit", "/quit" -> stop();
            case "/clear"  -> outputLines.clear();
            case "/help"   -> printHelp();
            case "/history" -> printHistory();
            case "/status"  -> printStatus();
            case "/reset"   -> {
                outputLines.clear();
                outputLines.add("[Session reset]");
                collector.clear();
            }
            default -> outputLines.add(
                    "Unknown command: " + cmd + "  (type /help for available commands)");
        }
    }

    @Override
    public void stop() {
        running = false;
        if (runner != null) {
            runner.quit();
        }
    }

    @Override
    public String getConversationId() {
        return conversationId;
    }

    // ── ConsoleSession output（剥离 ANSI 码后写入 outputLines，由渲染循环即时呈现） ──

    @Override
    public void print(String text) {
        if (text != null) {
            printBuffer.append(stripAnsi(text));
        }
    }

    @Override
    public void println() {
        outputLines.add(printBuffer.toString());
        printBuffer.setLength(0);
    }

    @Override
    public void println(String text) {
        flushBuffer();
        if (text != null) {
            outputLines.add(stripAnsi(text));
        }
    }

    @Override
    public void flush() {
        flushBuffer();
    }

    private void flushBuffer() {
        if (printBuffer.length() > 0) {
            outputLines.add(printBuffer.toString());
            printBuffer.setLength(0);
        }
    }

    private static String stripAnsi(String s) {
        return s.replaceAll("\\[[;\\d]*m", "");
    }

    private void printHelp() {
        outputLines.add("");
        outputLines.add("Built-in commands:");
        outputLines.add("  /help       show this help");
        outputLines.add("  /exit       quit program");
        outputLines.add("  /clear      clear screen");
        outputLines.add("  /history    show input history");
        outputLines.add("  /status     show session status");
        outputLines.add("  /reset      reset session");
        outputLines.add("");
        outputLines.add("Key bindings:");
        outputLines.add("  Enter       submit input");
        outputLines.add("  Tab         autocomplete /command or @path");
        outputLines.add("  Ctrl+C      quit");
        outputLines.add("");
    }

    private void printHistory() {
        java.util.List<UserInput> all = collector.getAll();
        if (all.isEmpty()) {
            outputLines.add("[No input history]");
            return;
        }
        outputLines.add("");
        outputLines.add("-- Input History (" + all.size() + " items) --");
        for (UserInput ui : all) {
            String preview = ui.getText().replace("\n", "~");
            if (preview.length() > 80) preview = preview.substring(0, 80) + "...";
            outputLines.add("  #" + ui.getId() + " [" + ui.getTimestamp().toString().replace("T", " ") + "] " + preview);
        }
        outputLines.add("");
    }

    private void printStatus() {
        outputLines.add("");
        outputLines.add("  Session ID : " + conversationId.substring(0, 8));
        outputLines.add("  Start time : " + startTime.format(TF));
        outputLines.add("  Input count: " + collector.count());
        outputLines.add("");
    }
}
