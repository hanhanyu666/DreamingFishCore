package com.hhy.dreamingfishcore.client.ui.util;

import com.hhy.dreamingfishcore.common.util.Utf8JsonFileIO;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.neoforged.fml.loading.FMLPaths;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LoadingTips {

    private static final int CURRENT_COPY_REVISION = 3;
    private static final List<String> tips = new ArrayList<>();
    private static final List<String> DEFAULT_TIPS = List.of(
        "§b欢迎来到梦屿。§7这里曾是普通人重新开始做梦的地方——而现在，危机刚刚到来。",
        "§7梦屿不是逃离世界的避难所，而是证明人类可以换一种方式生活的尝试。",
        "§7慢下来，建一间屋子，种一片田。生活本身，从来不需要先证明价值才能拥有。",
        "§7如果你刚刚抵达：先到阿拜多斯安顿下来。登记、医疗与第一份补给都在那里等你。",
        "§6逐光会§7由梦屿的多支救援力量在危机中临时组成。它刚刚成立，总部正在阿拜多斯筹建。",
        "§7是否加入逐光会由你自己决定。不加入的协作者，同样能参与救援、建设与调查。",
        "§7外缘带的旧电网与旧泵站仍在运转，那里的居民还在等一封没有写完的承诺。",
        "§e重生技术§7并不是无限的奇迹。每一次回来，都意味着你离极限更近了一步。",
        "§c复活点数§7耗尽后，你将无法通过常规方式继续重生。请认真对待每一次死亡。",
        "§7如果有人愿意消耗自己的复活点数救你，请记住：那不是系统奖励，是另一个人的选择。",
        "§c感染值§7会因受伤、污染环境和靠近感染者而上升。疼痛有时比丧尸更早提醒你危险。",
        "§7关于感染，目前没有人拥有完整答案。别把恐惧当成证据，也别把猜测当成结论。",
        "§a幸存者§7更容易吸引怪物注意。你看起来越像旧世界的人类，黑暗越容易盯上你。",
        "§7远离感染者可以保护自己，但远离不等于理解。理解需要记录、时间与交叉验证。",
        "§d故事阶段§7由服务器依据世界进展推进。任务的成功与失败都会被保留。",
        "§e蓝图§7代表你重新学会制作某件东西。梦屿的文明，也是这样一点点捡回来的。",
        "§7丧尸、感染和死亡并不是灾难最深的部分。恐惧让人互相放弃，才是。",
        "§7如果世界已经改变，活下去的人也必须学会承认改变。",
        "§b终端§7是你最重要的工具：广播、NPC 私信、个人引导与阶段任务都在这里。",
        "§b守望不是回到过去，而是在废墟上重新相信彼此。"
    );
    private static final Random RANDOM = new Random();
    private static boolean loaded = false;
    private static String lastTip = "";

    private LoadingTips() {}

    public static String getRandomTip() {
        if (!loaded) {
            load();
            loaded = true;
        }
        if (tips.isEmpty()) {
            return "欢迎来到梦屿";
        }
        if (tips.size() == 1) {
            return tips.get(0);
        }
        String tip;
        do {
            tip = tips.get(RANDOM.nextInt(tips.size()));
        } while (tip.equals(lastTip) && tips.size() > 1);
        lastTip = tip;
        return tip;
    }

    private static void load() {
        Path configPath = FMLPaths.CONFIGDIR.get().resolve(DreamingFishCore.MODID).resolve("loading_tips.json");
        File configFile = configPath.toFile();

        try {
            ensureDefaultConfig(configFile);
            try (Reader reader = Utf8JsonFileIO.openReader(configFile)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (json.has("copyRevision")
                        && json.get("copyRevision").getAsInt() == CURRENT_COPY_REVISION) {
                    parse(json);
                } else {
                    // 旧版加载提示不再继续作为第二套可见文案保留。
                    writeDefaultConfig(configFile);
                    loadDefaultTips();
                }
            }
        } catch (Exception e) {
            DreamingFishCore.LOGGER.warn("Failed to load loading_tips.json from config, using defaults", e);
            loadDefaultTips();
        }
    }

    private static void parse(JsonObject json) {
        tips.clear();
        JsonArray arr = json.has("tips") ? json.getAsJsonArray("tips") : null;
        if (arr != null) {
            for (JsonElement element : arr) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    tips.add(element.getAsString());
                }
            }
        }
        if (tips.isEmpty()) {
            loadDefaultTips();
        }
    }

    private static void ensureDefaultConfig(File configFile) throws IOException {
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        if (!configFile.exists()) {
            writeDefaultConfig(configFile);
        }
    }

    private static void writeDefaultConfig(File configFile) throws IOException {
        JsonObject json = new JsonObject();
        json.addProperty("copyRevision", CURRENT_COPY_REVISION);
        JsonArray arr = new JsonArray();
        for (String tip : DEFAULT_TIPS) {
            arr.add(tip);
        }
        json.add("tips", arr);
        try (Writer writer = Utf8JsonFileIO.openWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        }
    }

    private static void loadDefaultTips() {
        tips.clear();
        tips.addAll(DEFAULT_TIPS);
    }
}
