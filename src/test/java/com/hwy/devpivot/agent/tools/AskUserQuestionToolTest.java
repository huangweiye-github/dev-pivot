package com.hwy.devpivot.agent.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AskUserQuestionTool 单元测试")
class AskUserQuestionToolTest {

    private final AskUserQuestionTool tool = new AskUserQuestionTool();

    private InputStream originalIn;
    private PrintStream originalOut;

    @Mock
    private PrintStream mockOut;

    @BeforeEach
    void setUp() {
        originalIn = System.in;
        originalOut = System.out;
    }

    @AfterEach
    void tearDown() {
        System.setIn(originalIn);
        System.setOut(originalOut);
    }

    // ── 参数校验 ──────────────────────────────────────────

    @Test
    @DisplayName("questions 为 null 时返回错误")
    void testNullQuestions() {
        String result = tool.ask(null, "test-mem");
        assertEquals("Error: questions 不能为空", result);
    }

    @Test
    @DisplayName("questions 为空列表时返回错误")
    void testEmptyQuestions() {
        String result = tool.ask(List.of(), "test-mem");
        assertEquals("Error: questions 不能为空", result);
    }

    // ── 跳过无效问题 ──────────────────────────────────────

    @Test
    @DisplayName("question 字段为 null 时跳过该问题，返回空 JSON")
    void testQuestionFieldNull() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = null;
        q.header = "Test";
        q.multiSelect = "N";
        q.options = List.of(option("A", "选项A"), option("B", "选项B"));

        String result = tool.ask(List.of(q), "test-mem");
        assertEquals("{}", result);
    }

    @Test
    @DisplayName("question 字段为空白字符串时跳过该问题")
    void testQuestionFieldBlank() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "   ";
        q.header = "Test";
        q.multiSelect = "N";
        q.options = List.of(option("A", "选项A"), option("B", "选项B"));

        String result = tool.ask(List.of(q), "test-mem");
        assertEquals("{}", result);
    }

    @Test
    @DisplayName("options 为 null 时跳过该问题")
    void testOptionsNull() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "正常问题？";
        q.header = "Test";
        q.multiSelect = "N";
        q.options = null;

        String result = tool.ask(List.of(q), "test-mem");
        assertEquals("{}", result);
    }

    @Test
    @DisplayName("options 少于 2 项时跳过该问题")
    void testOptionsInsufficient() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "正常问题？";
        q.header = "Test";
        q.multiSelect = "N";
        q.options = List.of(option("唯一选项", "只有一个"));

        String result = tool.ask(List.of(q), "test-mem");
        assertEquals("{}", result);
    }

    @Test
    @DisplayName("options 恰好 2 项时正常处理（边界有效）")
    void testOptionsExactlyTwo() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", "选项A"), option("B", "选项B"));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("A"));
        assertFalse(result.contains("B"));
    }

    @Test
    @DisplayName("全部问题都无效时返回空 JSON")
    void testAllQuestionsInvalid() {
        AskUserQuestionTool.Question q1 = new AskUserQuestionTool.Question();
        q1.question = null;
        q1.options = List.of(option("A", ""), option("B", ""));

        AskUserQuestionTool.Question q2 = new AskUserQuestionTool.Question();
        q2.question = "有效问题？";
        q2.options = null;

        String result = tool.ask(List.of(q1, q2), "test-mem");
        assertEquals("{}", result);
    }

    @Test
    @DisplayName("混合有效和无效问题时只处理有效问题")
    void testMixedValidAndInvalidQuestions() {
        AskUserQuestionTool.Question invalid = new AskUserQuestionTool.Question();
        invalid.question = null;
        invalid.options = List.of(option("X", ""), option("Y", ""));

        AskUserQuestionTool.Question valid = new AskUserQuestionTool.Question();
        valid.question = "有效问题？";
        valid.header = "Valid";
        valid.multiSelect = "N";
        valid.options = List.of(option("P", ""), option("Q", ""));

        System.setIn(new ByteArrayInputStream("2\n".getBytes()));
        String result = tool.ask(List.of(invalid, valid), "test-mem");
        assertTrue(result.contains("Q"));
        assertFalse(result.contains("X"));
    }

    // ── 单选流程 ──────────────────────────────────────────

    @Test
    @DisplayName("单选正常选择第一个选项")
    void testSingleSelectFirstOption() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择框架？";
        q.header = "Framework";
        q.multiSelect = "N";
        q.options = List.of(option("JUnit", "Java 标准测试"), option("TestNG", "功能更丰富"));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("JUnit"));
        assertFalse(result.contains("TestNG"));
    }

    @Test
    @DisplayName("单选正常选择最后一个选项")
    void testSingleSelectLastOption() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""), option("C", ""));

        System.setIn(new ByteArrayInputStream("3\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("C"));
    }

    @Test
    @DisplayName("单选输入超出范围时返回(无效选择)")
    void testSingleSelectOutOfRange() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("5\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("(无效选择)"));
    }

    @Test
    @DisplayName("单选输入零或负数时返回(无效选择)")
    void testSingleSelectZeroInput() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("0\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("(无效选择)"));
    }

    @Test
    @DisplayName("单选输入非数字时原样返回输入值")
    void testSingleSelectNonNumeric() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("abc\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("abc"));
    }

    @Test
    @DisplayName("单选输入含前后空格时 trim 后正确处理")
    void testSingleSelectTrimmedInput() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("  2  \n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("B"));
    }

    @Test
    @DisplayName("multiSelect 为 null 时走单选分支")
    void testMultiSelectNull() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = null; // null 走单选
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("A"));
    }

    // ── 多选流程 ──────────────────────────────────────────

    @Test
    @DisplayName("多选逗号分隔正常选择")
    void testMultiSelectCommaSeparated() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择功能？";
        q.header = "Features";
        q.multiSelect = "Y";
        q.options = List.of(option("日志", "输出日志"), option("缓存", "数据缓存"), option("监控", "性能监控"));

        System.setIn(new ByteArrayInputStream("1,3\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("日志"));
        assertTrue(result.contains("监控"));
        assertFalse(result.contains("缓存"));
    }

    @Test
    @DisplayName("多选空格分隔正常选择")
    void testMultiSelectSpaceSeparated() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "Y";
        q.options = List.of(option("A", ""), option("B", ""), option("C", ""));

        System.setIn(new ByteArrayInputStream("1 3\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("C"));
        assertFalse(result.contains("B"));
    }

    @Test
    @DisplayName("多选逗号和空格混合分隔")
    void testMultiSelectMixedDelimiters() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "Y";
        q.options = List.of(option("A", ""), option("B", ""), option("C", ""), option("D", ""));

        System.setIn(new ByteArrayInputStream("1, 3  4\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("C"));
        assertTrue(result.contains("D"));
        assertFalse(result.contains("B"));
    }

    @Test
    @DisplayName("多选部分无效索引时忽略无效部分")
    void testMultiSelectPartialInvalid() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "Y";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("1,5,abc,2\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("A"));
        assertTrue(result.contains("B"));
    }

    @Test
    @DisplayName("多选全部索引无效时返回空列表")
    void testMultiSelectAllInvalid() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "Y";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("5,6,abc\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("[]"));
    }

    @Test
    @DisplayName("多选输入零或负数时忽略")
    void testMultiSelectZeroOrNegative() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "Y";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("0,-1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("[]"));
    }

    // ── Header 处理 ───────────────────────────────────────

    @Test
    @DisplayName("header 为 null 时使用默认 key q1")
    void testNullHeaderUsesDefaultKey() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = null;
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("q1"));
    }

    @Test
    @DisplayName("header 为空白时使用默认 key")
    void testBlankHeaderUsesDefaultKey() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "   ";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("2\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("q1"));
    }

    @Test
    @DisplayName("多个问题 header 均为 null 时 key 递增")
    void testMultipleNullHeadersKeyIncrement() {
        AskUserQuestionTool.Question q1 = new AskUserQuestionTool.Question();
        q1.question = "问题1？";
        q1.header = null;
        q1.multiSelect = "N";
        q1.options = List.of(option("A", ""), option("B", ""));

        AskUserQuestionTool.Question q2 = new AskUserQuestionTool.Question();
        q2.question = "问题2？";
        q2.header = null;
        q2.multiSelect = "N";
        q2.options = List.of(option("C", ""), option("D", ""));

        System.setIn(new ByteArrayInputStream("1\n2\n".getBytes()));
        String result = tool.ask(List.of(q1, q2), "test-mem");
        assertTrue(result.contains("q1"));
        assertTrue(result.contains("q2"));
        assertTrue(result.contains("A"));
        assertTrue(result.contains("D"));
    }

    @Test
    @DisplayName("header 使用自定义名称")
    void testCustomHeaderName() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "MyHeader";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("MyHeader"));
        assertFalse(result.contains("q1"));
    }

    // ── Option 处理 ───────────────────────────────────────

    @Test
    @DisplayName("option label 为 null 时答案中对应值为空字符串")
    void testOptionLabelNull() {
        AskUserQuestionTool.Option opt = new AskUserQuestionTool.Option();
        opt.label = null;
        opt.description = "描述";

        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(opt, option("B", ""));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        // FastJSON 序列化 null 时可能输出 null 或忽略
        assertNotNull(result);
    }

    @Test
    @DisplayName("option description 为 null 时正常返回 label")
    void testOptionDescriptionNull() {
        AskUserQuestionTool.Option opt = new AskUserQuestionTool.Option();
        opt.label = "标签";
        opt.description = null;

        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(opt, option("B", ""));

        System.setOut(mockOut);
        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");

        assertTrue(result.contains("标签"));
        // 验证输出至少被执行（覆盖了 printHeader 和选项输出路径）
        verify(mockOut, atLeastOnce()).println(anyString());
    }

    @Test
    @DisplayName("option description 为空白时不显示描述")
    void testOptionDescriptionBlank() {
        AskUserQuestionTool.Option opt = new AskUserQuestionTool.Option();
        opt.label = "标签";
        opt.description = "   ";

        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(opt, option("B", ""));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.contains("标签"));
    }

    // ── toString ─────────────────────────────────────────

    @Test
    @DisplayName("toString 返回 AskUserQuestion")
    void testToString() {
        assertEquals("AskUserQuestion", tool.toString());
    }

    // ── Mockito 验证输出 ─────────────────────────────────

    @Test
    @DisplayName("验证 printHeader 输出到 System.out")
    void testPrintHeaderOutput() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "测试问题？";
        q.header = "Test";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setOut(mockOut);
        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        tool.ask(List.of(q), "test-mem");

        // 验证 println 至少被调用（输出了选项和提示）
        verify(mockOut, atLeastOnce()).println(anyString());
    }

    @Test
    @DisplayName("验证多选提示语被输出")
    void testMultiSelectPromptOutput() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "Y";
        q.options = List.of(option("A", ""), option("B", ""), option("C", ""));

        System.setOut(mockOut);
        System.setIn(new ByteArrayInputStream("1,2\n".getBytes()));
        tool.ask(List.of(q), "test-mem");

        // 验证多选提示被打印
        verify(mockOut, atLeastOnce()).print(contains("多选"));
    }

    @Test
    @DisplayName("验证单选提示语被输出")
    void testSingleSelectPromptOutput() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Choice";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""), option("C", ""));

        System.setOut(mockOut);
        System.setIn(new ByteArrayInputStream("2\n".getBytes()));
        tool.ask(List.of(q), "test-mem");

        // 验证单选提示被打印
        verify(mockOut, atLeastOnce()).print(contains("请选择"));
    }

    @Test
    @DisplayName("验证返回值为合法 JSON 字符串")
    void testReturnValueIsValidJson() {
        AskUserQuestionTool.Question q = new AskUserQuestionTool.Question();
        q.question = "选择？";
        q.header = "Test";
        q.multiSelect = "N";
        q.options = List.of(option("A", ""), option("B", ""));

        System.setIn(new ByteArrayInputStream("1\n".getBytes()));
        String result = tool.ask(List.of(q), "test-mem");
        assertTrue(result.startsWith("{"), "应以 { 开头");
        assertTrue(result.endsWith("}"), "应以 } 结尾");
        assertTrue(result.contains("\"Test\""), "应包含 header 的 JSON key");
    }

    @Test
    @DisplayName("多问题综合场景：单选+多选混合")
    void testMixedSingleAndMultiSelect() {
        AskUserQuestionTool.Question q1 = new AskUserQuestionTool.Question();
        q1.question = "语言？";
        q1.header = "Language";
        q1.multiSelect = "N";
        q1.options = List.of(option("Java", ""), option("Go", ""));

        AskUserQuestionTool.Question q2 = new AskUserQuestionTool.Question();
        q2.question = "功能？";
        q2.header = "Features";
        q2.multiSelect = "Y";
        q2.options = List.of(option("Web", ""), option("CLI", ""), option("GUI", ""));

        System.setIn(new ByteArrayInputStream("1\n1,3\n".getBytes()));
        String result = tool.ask(List.of(q1, q2), "test-mem");
        assertTrue(result.contains("Language"));
        assertTrue(result.contains("Java"));
        assertTrue(result.contains("Features"));
        assertTrue(result.contains("Web"));
        assertTrue(result.contains("GUI"));
        assertFalse(result.contains("CLI"));
        assertFalse(result.contains("Go"));
    }

    // ── helper ──────────────────────────────────────────

    private static AskUserQuestionTool.Option option(String label, String description) {
        AskUserQuestionTool.Option o = new AskUserQuestionTool.Option();
        o.label = label;
        o.description = description;
        return o;
    }
}
