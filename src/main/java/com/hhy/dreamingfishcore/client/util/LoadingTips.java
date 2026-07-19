package com.hhy.dreamingfishcore.client.util;

import com.hhy.dreamingfishcore.utils.Utf8JsonFileIO;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hhy.dreamingfishcore.DreamingFishCore;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class LoadingTips {

    private static final List<String> tips = new ArrayList<>();
    private static final List<String> DEFAULT_TIPS = List.of(
        "§b欢迎来到梦屿。§7这里曾是普通人重新开始做梦的地方。",
        "§7梦屿不是逃离世界的避难所，而是证明人类可以换一种方式生活的尝试。",
        "§7慢下来，建一间屋子，种一片田。梦屿最初的意义，就是让普通生活重新变得珍贵。",
        "§e重生技术§7并不是无限的奇迹。每一次回来，都意味着你离极限更近了一步。",
        "§c复活点数§7耗尽后，你将无法通过常规方式继续重生。请认真对待每一次死亡。",
        "§7如果有人愿意消耗自己的复活点数救你，请记住：那不是系统奖励，是另一个人的选择。",
        "§c感染值§7会因受伤、污染环境和靠近感染者而上升。疼痛有时比丧尸更早提醒你危险。",
        "§7感染并不总是立刻夺走理智。真正可怕的，是所有人都急着给未知下定论。",
        "§a幸存者§7更容易吸引怪物注意。你看起来越像旧世界的人类，黑暗越容易盯上你。",
        "§c感染者§7并不等同于丧尸。他们仍可能记得自己是谁，也仍可能想活下去。",
        "§7靠近感染者会让幸存者的感染值慢慢上升，但远离他们并不等于理解了真相。",
        "§6逐光会§7宣称自己正在守护梦屿。相信之前，先看看他们希望你忽略什么。",
        "§7逐光会说解药是唯一希望。可希望如果只有一种形状，它也可能是一种控制。",
        "§7短时间记录往往充满恐惧，长时间观察才更接近真相。",
        "§d线索§7不是越新越可靠。记录时间越长，越值得你停下来读完。",
        "§6随记本§7会保存你发现的碎片。别急着判断，把矛盾的记录放在一起看。",
        "§d故事阶段§7会随着你收集线索和理解梦屿而推进，而不是只靠击败敌人。",
        "§7第一阶段：余梦期。先活下来，再慢慢听见这片大陆的旧梦。",
        "§7第二阶段：管制期。隔离区的墙不只挡住感染，也挡住了很多声音。",
        "§7第三阶段：疑光期。当记录彼此矛盾时，试着问问是谁最害怕长期观察。",
        "§7第四阶段：破晓期。幸存者和感染者缺一不可，梦屿不会靠排斥迎来黎明。",
        "§6白芷§7留下的记录也许不完整，但她至少试着看见感染者仍是人。",
        "§7祁临曾相信秩序。也许真正困难的，不是反抗谎言，而是承认自己曾经相信它。",
        "§7废墟里的告示、广播里的停顿、NPC 的沉默，都可能比一份正式通告更诚实。",
        "§e蓝图§7代表你重新学会制作某件东西。梦屿的文明，也是这样一点点捡回来的。",
        "§7不要只问感染者会不会伤害你，也问问他们为什么必须躲起来。",
        "§7丧尸、感染和死亡并不是灾难最深的部分。恐惧让人互相放弃，才是。",
        "§7梦屿真正需要的，也许不是消灭所有感染者，而是找到人类继续活下去的方法。",
        "§7如果世界已经改变，活下去的人也必须学会承认改变。",
        "§b守望梦屿§7不是回到过去，而是在废墟上重新相信彼此。"
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
                parse(JsonParser.parseReader(reader).getAsJsonObject());
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
            JsonObject json = new JsonObject();
            JsonArray arr = new JsonArray();
            for (String tip : DEFAULT_TIPS) {
                arr.add(tip);
            }
            json.add("tips", arr);
            try (Writer writer = Utf8JsonFileIO.openWriter(configFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        }
    }

    private static void loadDefaultTips() {
        tips.clear();
        tips.addAll(DEFAULT_TIPS);
    }
}
