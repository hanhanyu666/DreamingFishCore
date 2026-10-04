package com.hhy.dreamingfishcore.gameplay.raid_system.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import org.junit.jupiter.api.Test;

/**
 * Brigadier 字符串参数类型的字符集（踩过一次坑，钉住它）。
 *
 * <p>背景：{@code /dreamingfish raid_anchor tag <id> add extract:fixed} 实测报
 * 「参数后应有空格分隔，但发现了尾随数据」，位置正好指在冒号上。原因是
 * {@code word()} 与 {@code string()} **读未加引号的 token 时字符集相同**，都不含冒号：
 * {@code [0-9A-Za-z_\-\.+]}。能吃冒号的只有 {@code greedyString()}（读到行尾）或加引号。</p>
 *
 * <p>这个测试直接测 Brigadier 的解析行为，不需要任何 Minecraft 环境，所以能当护栏用：
 * 以后谁把带冒号的参数改回 word()/string()，这里就会红。</p>
 */
class BrigadierArgumentTypeTest {

    private static String parse(StringArgumentType type, String input) throws Exception {
        StringReader reader = new StringReader(input);
        return type.parse(reader);
    }

    private static boolean leavesTrailingData(StringArgumentType type, String input) throws Exception {
        StringReader reader = new StringReader(input);
        type.parse(reader);
        return reader.canRead();
    }

    @Test
    void wordAndStringBothStopAtTheColon() throws Exception {
        assertTrue(leavesTrailingData(StringArgumentType.word(), "extract:fixed"),
                "word() 应在冒号处停下（这正是报错的来源）");
        assertTrue(leavesTrailingData(StringArgumentType.string(), "extract:fixed"),
                "string() 读未加引号的 token 时字符集与 word() 相同，同样停在冒号");
        assertEquals("extract", parse(StringArgumentType.string(), "extract:fixed"));
    }

    @Test
    void quotedStringCanCarryAColon() throws Exception {
        assertFalse(leavesTrailingData(StringArgumentType.string(), "\"extract:fixed\""),
                "加引号后 string() 能整段读完");
        assertEquals("extract:fixed", parse(StringArgumentType.string(), "\"extract:fixed\""));
    }

    @Test
    void greedyStringSwallowsTheWholeRestOfTheLine() throws Exception {
        assertEquals("extract:fixed", parse(StringArgumentType.greedyString(), "extract:fixed"));
        assertFalse(leavesTrailingData(StringArgumentType.greedyString(), "extract:fixed"),
        "greedyString() 读到行尾，冒号、点、连字符都不成问题");
        assertEquals("mindist:350 maxdist:900",
                parse(StringArgumentType.greedyString(), "mindist:350 maxdist:900"),
                "代价是它会连空格一起吃掉——所以只能用在命令最后一个参数上");
    }

    @Test
    void unquotedCharsetAllowsDotsAndDashesButNotColons() throws Exception {
        assertEquals("factory-office.v2", parse(StringArgumentType.string(), "factory-office.v2"),
                "点和连字符是允许的，所以锚点 id 用 string() 够用");
        assertTrue(leavesTrailingData(StringArgumentType.string(), "factory:office"),
                "但冒号不行——锚点 id 里不要用冒号");
    }
}
