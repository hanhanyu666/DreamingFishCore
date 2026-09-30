package com.hhy.dreamingfishcore.client.debug;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 开发用 UI 截图验证工具。
 *
 * <p>仅在 {@code -Ddreamingfishcore.uiHarness=true}（Gradle 的 {@code runUiHarness}）时启用：
 * 进入存档后按 {@code harness_steps.txt} 逐行执行步骤，每步打开一个已登记的界面，
 * 等待动画稳定后把主渲染目标保存到 {@code screenshots/harness/}，全部完成后退出游戏。</p>
 *
 * <p>步骤格式：{@code 场景名 [gui=缩放] [wait=tick] [mouse=x,y] [as=文件名]}。</p>
 */
@EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public final class UiHarness {
    private static final boolean ENABLED = Boolean.getBoolean("dreamingfishcore.uiHarness");
    private static final int WORLD_SETTLE_TICKS = 60;
    private static final int DEFAULT_WAIT_TICKS = 30;
    private static final int WORLD_TIMEOUT_TICKS = 20 * 240;
    private static final Map<String, Consumer<Minecraft>> SCENARIOS = new LinkedHashMap<>();

    private static List<Step> steps;
    private static int stepIndex = -1;
    private static int waitTicks;
    private static int readyTicks;
    private static int totalTicks;
    private static boolean captureRequested;
    private static boolean finished;

    private UiHarness() {
    }

    public static boolean enabled() {
        return ENABLED;
    }

    /** 登记一个可在步骤文件中引用的场景。 */
    public static void register(String name, Consumer<Minecraft> opener) {
        SCENARIOS.put(name, opener);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        totalTicks++;
        if (steps == null) {
            steps = loadSteps(minecraft);
            UiHarnessScenarios.registerAll();
        }
        if (stepIndex < 0) {
            if (minecraft.level == null || minecraft.player == null) {
                readyTicks = 0;
                if (totalTicks > WORLD_TIMEOUT_TICKS) {
                    log(minecraft, "进入存档超时");
                    finish(minecraft);
                }
                return;
            }
            if (++readyTicks < WORLD_SETTLE_TICKS) {
                return;
            }
            minecraft.setScreen(null);
            startStep(minecraft, 0);
            return;
        }
        if (captureRequested) {
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            if (waitTicks == 0) {
                captureRequested = true;
            }
        }
    }

    @SubscribeEvent
    public static void onRenderFramePost(RenderFrameEvent.Post event) {
        if (!ENABLED || !captureRequested || finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        captureRequested = false;
        Step step = steps.get(stepIndex);
        Path directory = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("harness");
        try (NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            Files.createDirectories(directory);
            image.writeToFile(directory.resolve(step.fileName() + ".png"));
            log(minecraft, "已截图 " + step.fileName());
        } catch (IOException | RuntimeException exception) {
            log(minecraft, "截图失败 " + step.fileName() + ": " + exception);
        }
        startStep(minecraft, stepIndex + 1);
    }

    private static void startStep(Minecraft minecraft, int index) {
        stepIndex = index;
        if (index >= steps.size()) {
            finish(minecraft);
            return;
        }
        Step step = steps.get(index);
        try {
            if (step.guiScale() >= 0 && minecraft.options.guiScale().get() != step.guiScale()) {
                minecraft.options.guiScale().set(step.guiScale());
                minecraft.resizeDisplay();
            }
            Consumer<Minecraft> opener = SCENARIOS.get(step.scenario());
            if (opener == null) {
                log(minecraft, "未知场景 " + step.scenario());
            } else {
                opener.accept(minecraft);
            }
            if (step.mouseX() >= 0) {
                double scale = minecraft.getWindow().getGuiScale();
                GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(),
                        step.mouseX() * scale, step.mouseY() * scale);
            }
        } catch (RuntimeException exception) {
            log(minecraft, "场景执行失败 " + step.scenario() + ": " + exception);
            DreamingFishCore.LOGGER.error("UI harness 场景失败", exception);
        }
        waitTicks = Math.max(1, step.waitTicks());
    }

    private static void finish(Minecraft minecraft) {
        finished = true;
        try {
            Path directory = minecraft.gameDirectory.toPath().resolve("screenshots").resolve("harness");
            Files.createDirectories(directory);
            Files.writeString(directory.resolve("_done.txt"), "done", StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
        minecraft.stop();
    }

    private static List<Step> loadSteps(Minecraft minecraft) {
        List<Step> result = new ArrayList<>();
        Path file = minecraft.gameDirectory.toPath().resolve("harness_steps.txt");
        try {
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.strip();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                result.add(Step.parse(line));
            }
        } catch (IOException exception) {
            log(minecraft, "读取步骤文件失败: " + exception);
        }
        return result;
    }

    private static void log(Minecraft minecraft, String message) {
        DreamingFishCore.LOGGER.info("[UiHarness] {}", message);
    }

    private record Step(String scenario, int guiScale, int waitTicks, int mouseX, int mouseY, String fileName) {
        static Step parse(String line) {
            String[] parts = line.split("\\s+");
            String scenario = parts[0];
            int gui = -1;
            int wait = DEFAULT_WAIT_TICKS;
            int mouseX = -1;
            int mouseY = -1;
            String name = null;
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                if (part.startsWith("gui=")) {
                    gui = Integer.parseInt(part.substring(4));
                } else if (part.startsWith("wait=")) {
                    wait = Integer.parseInt(part.substring(5));
                } else if (part.startsWith("mouse=")) {
                    String[] xy = part.substring(6).split(",");
                    mouseX = Integer.parseInt(xy[0]);
                    mouseY = Integer.parseInt(xy[1]);
                } else if (part.startsWith("as=")) {
                    name = part.substring(3);
                }
            }
            if (name == null) {
                name = scenario.replace(':', '_').replace('/', '_') + (gui >= 0 ? "_g" + gui : "");
            }
            return new Step(scenario, gui, wait, mouseX, mouseY, name);
        }
    }
}
