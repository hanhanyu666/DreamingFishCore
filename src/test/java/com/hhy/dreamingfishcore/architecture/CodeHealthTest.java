package com.hhy.dreamingfishcore.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 代码健康护栏：用测试把"屎山预算"钉住，防止大文件继续长。
 *
 * <p>这不是风格洁癖，是**可执行的约定**：评审时报"这个类有点大"没人会改，
 * 但 CI 红一次就必须处理。规则来自 {@code docs/CODE_HEALTH_REVIEW.md}。</p>
 *
 * <p>两条规则：</p>
 * <ol>
 *   <li><b>单文件软上限 800 行</b>：超限的文件数**不允许增加**（当前基线见
 *       {@link #BASELINE_OVERSIZED}）。要加一个新的大文件，必须先拆一个旧的——
 *       这就是"债务预算"。</li>
 *   <li><b>单文件硬上限 2500 行</b>：任何情况下都不许突破（现在的最大文件约 1941 行）。</li>
 * </ol>
 *
 * <p><b>只统计主代码</b>（{@code src/main/java}）：测试文件天然会长一些，不在此列。</p>
 */
class CodeHealthTest {

    /** 单文件软上限（行）。 */
    private static final int LINE_SOFT_LIMIT = 800;
    /** 当前超过软上限的文件数量基线（只许减，不许增）。 */
    private static final int BASELINE_OVERSIZED = 11;
    /** 单文件硬上限（行）。 */
    private static final int LINE_HARD_LIMIT = 2500;

    @Test
    void oversizedFileCountMustNotGrow() throws IOException {
        List<String> oversized = new ArrayList<>();
        for (Path file : mainSourceFiles()) {
            long lines = countLines(file);
            if (lines > LINE_SOFT_LIMIT) {
                oversized.add(lines + " 行  " + file.toString().replace('\\', '/'));
            }
        }
        oversized.sort((left, right) -> Integer.compare(
                Integer.parseInt(right.substring(0, right.indexOf(' '))),
                Integer.parseInt(left.substring(0, left.indexOf(' ')))));

        assertTrue(oversized.size() <= BASELINE_OVERSIZED,
                "超过 " + LINE_SOFT_LIMIT + " 行的文件有 " + oversized.size()
                        + " 个，超过基线 " + BASELINE_OVERSIZED + "。先把现有的大文件拆掉，再新增：\n  "
                        + String.join("\n  ", oversized));
    }

    @Test
    void noFileMayExceedTheHardLimit() throws IOException {
        for (Path file : mainSourceFiles()) {
            long lines = countLines(file);
            assertTrue(lines <= LINE_HARD_LIMIT,
                    file + " 有 " + lines + " 行，超过硬上限 " + LINE_HARD_LIMIT
                            + "。这个体量的类已经不可能一次读懂，必须拆。");
        }
    }

    private static List<Path> mainSourceFiles() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root), "找不到主代码目录：" + root.toAbsolutePath());
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private static long countLines(Path file) throws IOException {
        try (Stream<String> stream = Files.lines(file)) {
            return stream.count();
        }
    }
}
