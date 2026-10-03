package com.hhy.dreamingfishcore.client.ui.notification;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.client.ui.framework.render.UiCanvas;
import com.hhy.dreamingfishcore.client.ui.framework.text.TextFit;
import com.hhy.dreamingfishcore.client.ui.framework.theme.Theme;
import com.hhy.dreamingfishcore.client.ui.framework.theme.UiColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 区域提示：屏幕上方居中的“风景明信片”。
 *
 * <p>卡片里是一幅随所在区域、游戏时间与天气变化的分层风景：天空与日月、最远的淡色山影、远景、中景剪影
 * （树林、村庄、鸟居、金字塔、灯塔……）、前景地面或水面。剪影贴图由 {@code tools/generate_region_scenes.py}
 * 生成，按场景着色；前景的小草与小花、飘落的花瓣、萤火虫、蝴蝶、飞鸟、雨雪、风车与炊烟等都在这里逐帧绘制。</p>
 *
 * <p>入场像立体书：各层依次从下方弹起，小花按从左到右的顺序开放；退场时各层从近到远收回。</p>
 *
 * <p>模组群系：先按群系标签（NeoForge 的 {@code c:is_*} 通用标签与原版 {@code minecraft:is_*}）选场景，
 * 认不出时看群系 id 里的关键词，再不行按温度与降水推断；树、草、水、天空再混入群系自己的颜色，
 * 所以模组里的紫色树林、红叶林会画成对应颜色的剪影。</p>
 */
public final class RegionPostcard {
    private static final int TEX_W = 1280;
    private static final int TEX_H = 160;
    private static final int GROUND_TEX_H = 64;
    private static final float RADIUS = 5.0F;
    /** 与原来的区域横幅一样大：最窄 190，有说明行时高 38，只有地名时高 29。 */
    private static final float MIN_WIDTH = 190.0F;
    private static final float HEIGHT = 38.0F;
    private static final float PLAIN_HEIGHT = 29.0F;
    /** 卡片顶边离屏幕上沿的距离，与原来的区域横幅相同。 */
    private static final float TOP = 16.0F;
    /** 场景里各种小物件（太阳、花草）按卡片高度相对 66 的比例缩放，画面的比例不变。 */
    private static final float DESIGN_HEIGHT = 66.0F;
    private static final float TITLE_SCALE = 1.2F;
    private static final float TITLE_TRACKING = 1.0F;
    private static final float DETAIL_SCALE = 0.64F;
    private static final float DETAIL_TRACKING = 0.25F;
    private static final String DETAIL_SEPARATOR = "  ·  ";
    private static final int TITLE_COLOR = 0xFFFFF8EC;
    private static final int DETAIL_COLOR = 0xFFF5E9D0;
    private static final int FIRST_COLOR = 0xFFF3CF8A;
    private static final int NIGHT_INK = 0xFF16243F;
    private static final int DUSK_INK = 0xFF3B2A48;

    private static final ResourceLocation SNOW_CAPS = texture("far_mountains_snow");
    private static final ResourceLocation VILLAGE_LIGHTS = texture("mid_village_lights");
    private static final ResourceLocation GRASS = texture("near_grass");
    private static final ResourceLocation SOFT = texture("near_soft");
    private static final ResourceLocation BEAM = ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID,
            "textures/gui/loading/lighthouse_beam.png");

    /** 中景贴图里需要配动态效果的位置（贴图坐标，见 tools/region_scene_anchors.json）。 */
    private static final float[] WINDMILL_HUB = {0.5F, 0.296F};
    private static final float[][] CHIMNEYS = {{0.4196F, 0.5375F}, {0.5887F, 0.54F}, {0.7708F, 0.5519F},
            {0.2551F, 0.5339F}, {0.921F, 0.5319F}, {0.1059F, 0.5412F}};
    private static final float[] LIGHTHOUSE_LAMP = {0.4938F, 0.0787F};

    private static final int PETALS = 1;
    private static final int FIREFLIES = 1 << 1;
    private static final int BUTTERFLIES = 1 << 2;
    private static final int BIRDS = 1 << 3;
    private static final int SNOW = 1 << 4;
    private static final int DUST = 1 << 5;
    private static final int SPORES = 1 << 6;
    private static final int EMBERS = 1 << 7;
    private static final int GLOW = 1 << 8;
    private static final int STARS = 1 << 9;

    /** 同一类场景里的变化：积雪、桦木林、黑森林、繁花、繁茂洞穴。 */
    private static final int LOOK_SNOWY = 1;
    private static final int LOOK_BIRCH = 1 << 1;
    private static final int LOOK_DARK = 1 << 2;
    private static final int LOOK_FLORAL = 1 << 3;
    private static final int LOOK_LUSH = 1 << 4;

    private static final Map<Notification, State> STATES = new WeakHashMap<>();
    private static volatile Preview preview;

    private RegionPostcard() {
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "textures/gui/region/" + name + ".png");
    }

    // ==================== 场景 ====================

    enum Ground {
        GRASS, SOFT, WATER, NONE
    }

    enum Time {
        DAY, DUSK, NIGHT
    }

    enum Scene {
        FOREST("far_mountains", false, "far_hills", "mid_forest", Ground.GRASS,
                0xFF8DA6BC, 0xFF5D8086, 0xFF2D5838, 0xFF3D6B35, 0, FIREFLIES | BUTTERFLIES,
                0xFFFFFFFF, 0xFFFFE066, 0xFFA9D2FF),
        PINES("far_mountains", true, "far_hills", "mid_pines", Ground.GRASS,
                0xFF8197B8, 0xFF4F6E73, 0xFF22423A, 0xFF3A5E3A, 0, BIRDS | FIREFLIES, 0xFFFFFFFF),
        MOUNTAINS("far_mountains", true, "far_mountains", "mid_pines", Ground.GRASS,
                0xFF9AAECB, 0xFF6F8399, 0xFF2A4440, 0xFF4C5E4A, 0, BIRDS, 0xFFFFFFFF, 0xFFC9A8FF),
        SNOWY("far_mountains", true, "far_hills", "mid_pines", Ground.SOFT,
                0xFFA9BCD4, 0xFFD6E2EE, 0xFF35505F, 0xFFE9F0F7, 0xFFDCE8F5, SNOW),
        CHERRY("far_mountains", false, "far_hills", "mid_cherry", Ground.GRASS,
                0xFFB9A6C9, 0xFFB08CAA, 0xFFD9849E, 0xFF79A266, 0xFFF7B6C8, PETALS | FIREFLIES,
                0xFFFFFFFF, 0xFFFFB3CB),
        VILLAGE("far_mountains", false, "far_hills", "mid_village", Ground.GRASS,
                0xFF95AEC2, 0xFF6E918E, 0xFF3A4842, 0xFF4E7A3A, 0, BUTTERFLIES | FIREFLIES,
                0xFFFF6B5E, 0xFFFFE066, 0xFFFFFFFF, 0xFFC9A8FF),
        DESERT("far_dunes", false, "far_dunes", "mid_desert", Ground.SOFT,
                0xFFE9C08A, 0xFFD39A5E, 0xFF9A5A36, 0xFFE2B47A, 0xFFF5D39A, DUST),
        BADLANDS("far_dunes", false, "far_dunes", "mid_desert", Ground.SOFT,
                0xFFE0A07A, 0xFFC0683E, 0xFF7E3A26, 0xFFD08A55, 0xFFF0B080, DUST),
        COAST("far_mountains", false, "far_sea", "mid_coast", Ground.WATER,
                0xFFA3B7CB, 0xFF7E9CB4, 0xFF27323F, 0xFF2A5B82, 0, BIRDS),
        RIVER("far_mountains", false, "far_hills", "mid_forest", Ground.WATER,
                0xFF8DA6BC, 0xFF5D8086, 0xFF2D5838, 0xFF2F6688, 0, BIRDS | FIREFLIES),
        SWAMP("far_hills", false, "far_hills", "mid_swamp", Ground.WATER,
                0xFF8A9C8A, 0xFF5A7160, 0xFF29392E, 0xFF3A5442, 0xFFB7C9A0, FIREFLIES),
        JUNGLE("far_mountains", false, "far_hills", "mid_jungle", Ground.GRASS,
                0xFF7FA79A, 0xFF3F7C5E, 0xFF1C5431, 0xFF2D6A2D, 0, BUTTERFLIES | FIREFLIES,
                0xFFFF5A4E, 0xFFFF9A3C, 0xFFFFE066),
        SAVANNA("far_mountains", false, "far_hills", "mid_savanna", Ground.SOFT,
                0xFFD9BE94, 0xFFBC9C6A, 0xFF57502F, 0xFFB59D58, 0xFFF2CF8E, BIRDS | DUST),
        MUSHROOM("far_hills", false, "far_hills", "mid_mushroom", Ground.SOFT,
                0xFFA99BBA, 0xFF85769C, 0xFFAE4848, 0xFF857590, 0, SPORES),
        CAVE("far_cave", false, null, "mid_cave", Ground.NONE,
                0xFF323543, 0, 0xFF15161C, 0xFF111216, 0, GLOW),
        NETHER("far_cave", false, null, "mid_cave", Ground.NONE,
                0xFF5A1C16, 0, 0xFF240A08, 0xFF1A0605, 0, EMBERS),
        END("far_mountains", false, null, null, Ground.NONE,
                0xFF3E2E58, 0, 0, 0, 0, STARS);

        final ResourceLocation back;
        final boolean snowCaps;
        final ResourceLocation far;
        final ResourceLocation mid;
        final Ground ground;
        final int backColor;
        final int farColor;
        final int midColor;
        final int groundColor;
        final int tint;
        final int fx;
        final int[] flowers;

        Scene(String back, boolean snowCaps, String far, String mid, Ground ground, int backColor, int farColor,
              int midColor, int groundColor, int tint, int fx, int... flowers) {
            this.back = texture(back);
            this.snowCaps = snowCaps;
            this.far = far == null ? null : texture(far);
            this.mid = mid == null ? null : texture(mid);
            this.ground = ground;
            this.backColor = backColor;
            this.farColor = farColor;
            this.midColor = midColor;
            this.groundColor = groundColor;
            this.tint = tint;
            this.fx = fx;
            this.flowers = flowers;
        }

        /** 洞穴、下界、末地不分昼夜，天空用自己的颜色。 */
        boolean timeless() {
            return this == CAVE || this == NETHER || this == END;
        }

        /** 中景里有固定的焦点（风车、鸟居、金字塔、灯塔），让它出现在卡片右侧。 */
        boolean focal() {
            return this == VILLAGE || this == CHERRY || this == DESERT || this == BADLANDS || this == COAST;
        }
    }

    /**
     * 选场景与群系配色：区域提示按通知带的群系 id；其他横幅（服务器广播、剧情阶段）画玩家当前所在的群系；
     * 世界还没加载时用梦屿的海岸灯塔。
     */
    private static Classified classify(Notification notification, Minecraft minecraft) {
        Holder<Biome> holder = null;
        String name = notification.biomeId();
        if (minecraft.level != null) {
            if (name != null) {
                ResourceLocation id = ResourceLocation.tryParse(name);
                if (id != null) {
                    holder = minecraft.level.registryAccess().registryOrThrow(Registries.BIOME)
                            .getHolder(ResourceKey.create(Registries.BIOME, id)).orElse(null);
                }
            } else if (minecraft.player != null) {
                holder = minecraft.level.getBiome(minecraft.player.blockPosition());
                name = holder.unwrapKey().map(key -> key.location().toString()).orElse("");
            }
        }
        if (name == null) {
            // 旧数据或其他来源：标题若是群系的翻译键，也能认出来
            Component title = notification.title();
            name = title.getContents() instanceof TranslatableContents contents ? contents.getKey() : "";
        }
        Scene scene = holder != null ? sceneFor(holder, name) : name.isEmpty() ? Scene.COAST : sceneForName(name, Scene.COAST);
        BiomeTint tint = null;
        String text = name.toLowerCase(Locale.ROOT);
        int look = 0;
        if (holder != null) {
            Biome biome = holder.value();
            double x = minecraft.player != null ? minecraft.player.getX() : 0.0;
            double z = minecraft.player != null ? minecraft.player.getZ() : 0.0;
            tint = new BiomeTint(biome.getFoliageColor(), biome.getGrassColor(x, z), biome.getWaterColor(), biome.getSkyColor());
            look |= is(holder, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_ICY) || biome.getBaseTemperature() < 0.15F ? LOOK_SNOWY : 0;
            look |= is(holder, Tags.Biomes.IS_BIRCH_FOREST) ? LOOK_BIRCH : 0;
            look |= is(holder, Tags.Biomes.IS_SPOOKY) ? LOOK_DARK : 0;
            look |= is(holder, Tags.Biomes.IS_FLOWER_FOREST, Tags.Biomes.IS_FLORAL) ? LOOK_FLORAL : 0;
            look |= is(holder, Tags.Biomes.IS_LUSH) ? LOOK_LUSH : 0;
        }
        look |= has(text, "snow", "frozen", "ice", "雪", "冰") ? LOOK_SNOWY : 0;
        look |= has(text, "birch", "桦") ? LOOK_BIRCH : 0;
        look |= has(text, "dark", "黑森林") ? LOOK_DARK : 0;
        look |= has(text, "flower", "花") ? LOOK_FLORAL : 0;
        look |= has(text, "lush", "繁茂") ? LOOK_LUSH : 0;
        return new Classified(scene, tint, look);
    }

    private record Classified(Scene scene, BiomeTint tint, int look) {
    }

    /** 群系自己的树叶、草、水与天空颜色（不含透明度）。 */
    private record BiomeTint(int foliage, int grass, int water, int sky) {
    }

    /** 按群系标签判断；模组群系一般都打了 NeoForge 的通用标签。认不出时看名字，再按气候推断。 */
    static Scene sceneFor(Holder<Biome> biome, String name) {
        String text = name.toLowerCase(Locale.ROOT);
        if (is(biome, BiomeTags.IS_NETHER, Tags.Biomes.IS_NETHER)) {
            return Scene.NETHER;
        }
        if (is(biome, BiomeTags.IS_END, Tags.Biomes.IS_END)) {
            return Scene.END;
        }
        if (is(biome, Tags.Biomes.IS_CAVE, Tags.Biomes.IS_UNDERGROUND)) {
            return Scene.CAVE;
        }
        if (has(text, "cherry", "sakura", "blossom")) {
            return Scene.CHERRY;
        }
        if (is(biome, Tags.Biomes.IS_MUSHROOM)) {
            return Scene.MUSHROOM;
        }
        if (is(biome, BiomeTags.IS_OCEAN, BiomeTags.IS_BEACH, Tags.Biomes.IS_OCEAN, Tags.Biomes.IS_BEACH,
                Tags.Biomes.IS_STONY_SHORES)) {
            return Scene.COAST;
        }
        if (is(biome, BiomeTags.IS_RIVER, Tags.Biomes.IS_RIVER)) {
            return Scene.RIVER;
        }
        if (is(biome, Tags.Biomes.IS_SWAMP)) {
            return Scene.SWAMP;
        }
        if (is(biome, BiomeTags.IS_JUNGLE, Tags.Biomes.IS_JUNGLE)) {
            return Scene.JUNGLE;
        }
        if (is(biome, BiomeTags.IS_BADLANDS, Tags.Biomes.IS_BADLANDS)) {
            return Scene.BADLANDS;
        }
        if (is(biome, Tags.Biomes.IS_DESERT)) {
            return Scene.DESERT;
        }
        if (is(biome, BiomeTags.IS_SAVANNA, Tags.Biomes.IS_SAVANNA)) {
            return Scene.SAVANNA;
        }
        boolean mountain = is(biome, BiomeTags.IS_MOUNTAIN, Tags.Biomes.IS_MOUNTAIN, Tags.Biomes.IS_MOUNTAIN_PEAK,
                Tags.Biomes.IS_MOUNTAIN_SLOPE, Tags.Biomes.IS_WINDSWEPT);
        if (is(biome, Tags.Biomes.IS_SNOWY, Tags.Biomes.IS_ICY)) {
            return mountain ? Scene.MOUNTAINS : Scene.SNOWY;
        }
        if (mountain) {
            return Scene.MOUNTAINS;
        }
        if (is(biome, BiomeTags.IS_TAIGA, Tags.Biomes.IS_TAIGA, Tags.Biomes.IS_CONIFEROUS_TREE)) {
            return Scene.PINES;
        }
        if (is(biome, BiomeTags.IS_FOREST, Tags.Biomes.IS_FOREST, Tags.Biomes.IS_BIRCH_FOREST,
                Tags.Biomes.IS_FLOWER_FOREST, Tags.Biomes.IS_DECIDUOUS_TREE)) {
            return Scene.FOREST;
        }
        if (is(biome, Tags.Biomes.IS_PLAINS)) {
            return Scene.VILLAGE;
        }
        Scene byName = sceneForName(text, null);
        if (byName != null) {
            return byName;
        }
        Biome value = biome.value();
        float temperature = value.getBaseTemperature();
        float downfall = value.getModifiedClimateSettings().downfall();
        if (!value.hasPrecipitation() && temperature >= 1.5F) {
            return Scene.DESERT;
        }
        if (temperature < 0.15F) {
            return Scene.SNOWY;
        }
        if (downfall >= 0.85F && temperature >= 0.9F) {
            return Scene.JUNGLE;
        }
        return downfall >= 0.6F ? Scene.FOREST : Scene.VILLAGE;
    }

    @SafeVarargs
    private static boolean is(Holder<Biome> biome, TagKey<Biome>... tags) {
        for (TagKey<Biome> tag : tags) {
            if (biome.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /** 由群系 id 或名称里的关键词选场景，认不出时返回 {@code fallback}。 */
    static Scene sceneForName(String name, Scene fallback) {
        String text = name.toLowerCase(Locale.ROOT);
        if (has(text, "deep_dark", "dripstone", "lush_cave", "cave", "洞穴", "溶洞")) {
            return Scene.CAVE;
        }
        if (has(text, "nether", "crimson", "warped", "basalt", "soul_sand", "下界", "绯红", "诡异", "玄武岩", "灵魂沙")) {
            return Scene.NETHER;
        }
        if (has(text, ".the_end", "end_highlands", "end_midlands", "end_barrens", "small_end", "末地")) {
            return Scene.END;
        }
        if (has(text, "cherry", "sakura", "blossom", "樱")) {
            return Scene.CHERRY;
        }
        if (has(text, "mushroom", "蘑菇")) {
            return Scene.MUSHROOM;
        }
        // 不用 "sea"：会误中 seasonal 一类的名字
        if (has(text, "ocean", "beach", "shore", "coast", "海")) {
            return Scene.COAST;
        }
        if (has(text, "river", "河")) {
            return Scene.RIVER;
        }
        if (has(text, "swamp", "marsh", "bog", "mangrove", "沼")) {
            return Scene.SWAMP;
        }
        if (has(text, "jungle", "bamboo", "rainforest", "丛林", "竹")) {
            return Scene.JUNGLE;
        }
        if (has(text, "badlands", "mesa", "canyon", "恶地")) {
            return Scene.BADLANDS;
        }
        if (has(text, "desert", "dune", "沙漠")) {
            return Scene.DESERT;
        }
        if (has(text, "savanna", "稀树")) {
            return Scene.SAVANNA;
        }
        if (has(text, "snow", "frozen", "ice", "glacier", "tundra", "雪", "冰", "冻")) {
            return has(text, "peak", "slope", "峰", "坡") ? Scene.MOUNTAINS : Scene.SNOWY;
        }
        if (has(text, "peak", "slope", "mountain", "windswept", "stony", "alps", "峰", "山")) {
            return Scene.MOUNTAINS;
        }
        if (has(text, "taiga", "pine", "spruce", "conifer", "针叶")) {
            return Scene.PINES;
        }
        if (has(text, "plains", "meadow", "field", "prairie", "lavender", "平原", "草甸", "草原")) {
            return Scene.VILLAGE;
        }
        if (has(text, "forest", "wood", "birch", "grove", "森林", "林")) {
            return Scene.FOREST;
        }
        return fallback;
    }

    private static boolean has(String text, String... words) {
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // ==================== 预览（截图工具用） ====================

    /** 截图工具指定场景、时间与天气；传 null 取消。 */
    public static void preview(String scene, String time, boolean rain) {
        if (scene == null) {
            preview = null;
            return;
        }
        preview = new Preview(Scene.valueOf(scene.toUpperCase(Locale.ROOT)), Time.valueOf(time.toUpperCase(Locale.ROOT)), rain);
        synchronized (STATES) {
            STATES.clear();
        }
    }

    public static List<String> sceneNames() {
        List<String> names = new ArrayList<>();
        for (Scene scene : Scene.values()) {
            names.add(scene.name().toLowerCase(Locale.ROOT));
        }
        return names;
    }

    private record Preview(Scene scene, Time time, boolean rain) {
    }

    // ==================== 状态 ====================

    private record Palette(int skyTop, int horizon, int back, int far, int mid, int ground, int snow, int sun,
                           float sunY, float sunRadius, boolean sunVisible) {
    }

    private record Decor(float x, float height, int kind, int color, float phase) {
    }

    /** 一条通知从出现到消失都用同一份场景、配色与随机布局。 */
    private static final class State {
        final Scene scene;
        final Time time;
        final boolean rain;
        final long seed;
        final Palette palette;
        final String title;
        final List<String> details;
        final boolean first;
        final int look;
        final float width;
        final float height;
        final float backOffset;
        final float farOffset;
        final float midOffset;
        final float groundOffset;
        final List<Decor> decor = new ArrayList<>();

        State(Notification notification, Minecraft minecraft, Font font, int screenWidth) {
            Preview forced = preview;
            Classified classified = forced != null
                    ? new Classified(forced.scene(), null, forced.scene() == Scene.CAVE ? LOOK_LUSH : 0)
                    : classify(notification, minecraft);
            scene = classified.scene();
            look = classified.look() | (scene == Scene.SNOWY ? LOOK_SNOWY : 0);
            time = forced != null ? forced.time() : timeOf(minecraft);
            rain = forced != null ? forced.rain() : minecraft.level != null && minecraft.level.isRaining();
            seed = System.identityHashCode(notification) * 0x9E3779B97F4A7C15L + scene.ordinal();
            palette = palette(scene, time, rain && !dry(scene), classified.tint(), look);

            List<String> parts = new ArrayList<>();
            for (String part : notification.message().getString().split("[·|]")) {
                String trimmed = part.trim().replaceAll("\\+\\s+", "+");
                if (!trimmed.isEmpty()) {
                    parts.add(trimmed);
                }
            }
            details = List.copyOf(parts);
            first = !parts.isEmpty() && parts.get(0).contains("首次");
            float maxWidth = Math.max(MIN_WIDTH, Math.min(300.0F, screenWidth - 24.0F));
            String fullTitle = notification.title().getString().trim();
            title = TextFit.trim(fullTitle, font, (int) ((maxWidth - 56.0F) / TITLE_SCALE));
            float contentWidth = Math.max(trackedWidth(font, title, TITLE_SCALE, TITLE_TRACKING), detailWidth(font, details));
            width = Math.round(Math.max(MIN_WIDTH, Math.min(maxWidth, contentWidth + 56.0F)));
            height = details.isEmpty() ? PLAIN_HEIGHT : HEIGHT;

            backOffset = hash(seed, 1, 0);
            farOffset = hash(seed, 2, 0);
            float midDraw = TEX_W * midHeight(scene, height) / TEX_H;
            midOffset = scene.focal() ? 0.5F - width * 0.66F / midDraw : hash(seed, 3, 0);
            groundOffset = hash(seed, 4, 0);
            plant();
        }

        boolean snowy() {
            return (look & LOOK_SNOWY) != 0;
        }

        /** 在前景地面上种小草、小花或小蘑菇；位置是卡片内的横坐标，随地面一起缓慢平移。 */
        private void plant() {
            if (scene.ground == Ground.WATER) {
                if (scene == Scene.SWAMP) {
                    for (int i = 0; i < 7; i++) {
                        decor.add(new Decor(hash(seed, 50, i) * (width + 20.0F), 0.0F, 3, 0xFF4E7A44, hash(seed, 51, i) * 6.28F));
                    }
                }
                return;
            }
            if (scene.ground == Ground.NONE || scene == Scene.DESERT || scene == Scene.BADLANDS || snowy()) {
                return;
            }
            int count = (int) (width / 5.5F);
            float flowerShare = (look & LOOK_FLORAL) != 0 ? 0.75F : 0.3F;
            for (int i = 0; i < count; i++) {
                float x = hash(seed, 10, i) * (width + 20.0F);
                float pick = hash(seed, 11, i);
                if (scene == Scene.MUSHROOM) {
                    if (pick < 0.35F) {
                        decor.add(new Decor(x, (2.2F + hash(seed, 12, i) * 2.0F) * Math.max(0.6F, height / DESIGN_HEIGHT), 2,
                                hash(seed, 13, i) < 0.6F ? 0xFFD9534F : 0xFFB48A62, hash(seed, 14, i) * 6.28F));
                    }
                    continue;
                }
                boolean flower = scene.flowers.length > 0 && pick < flowerShare;
                int color = flower ? scene.flowers[(int) (hash(seed, 13, i) * scene.flowers.length) % scene.flowers.length] : 0;
                float height = flower ? 3.2F + hash(seed, 12, i) * 2.6F : 2.4F + hash(seed, 12, i) * 3.2F;
                if (scene == Scene.SAVANNA) {
                    height += 1.5F;
                }
                height *= Math.max(0.6F, this.height / DESIGN_HEIGHT);
                decor.add(new Decor(x, height, flower ? 1 : 0, color, hash(seed, 14, i) * 6.28F));
            }
        }
    }

    private static boolean dry(Scene scene) {
        return scene == Scene.DESERT || scene == Scene.BADLANDS || scene == Scene.SAVANNA || scene.timeless();
    }

    private static Time timeOf(Minecraft minecraft) {
        if (minecraft.level == null) {
            return Time.DAY;
        }
        long t = Math.floorMod(minecraft.level.getDayTime(), 24000L);
        if (t >= 13000L && t < 23000L) {
            return Time.NIGHT;
        }
        if (t >= 11500L || t < 800L) {
            return Time.DUSK;
        }
        return Time.DAY;
    }

    /** 卡片里各层的高度；洞穴与下界的岩壁铺满整张卡片。 */
    private static float backHeight(Scene scene, float h) {
        return scene == Scene.CAVE || scene == Scene.NETHER ? h : h * 0.56F;
    }

    private static float midHeight(Scene scene, float h) {
        return scene == Scene.CAVE || scene == Scene.NETHER ? h : h * 0.46F;
    }

    private static boolean leafy(Scene scene) {
        return switch (scene) {
            case FOREST, PINES, MOUNTAINS, JUNGLE, SWAMP, RIVER, SAVANNA, VILLAGE -> true;
            default -> false;
        };
    }

    private static Palette palette(Scene scene, Time time, boolean rain, BiomeTint biome, int look) {
        int top;
        int horizon;
        float ink = 0.0F;
        int inkColor = NIGHT_INK;
        switch (scene) {
            case CAVE -> {
                top = 0xFF0B0C11;
                horizon = 0xFF2A2C38;
            }
            case NETHER -> {
                top = 0xFF1A0505;
                horizon = 0xFF7A2A14;
            }
            case END -> {
                top = 0xFF07040F;
                horizon = 0xFF2A1A44;
            }
            default -> {
                switch (time) {
                    case DUSK -> {
                        top = 0xFF29285A;
                        horizon = 0xFFF29C5A;
                        ink = 0.45F;
                        inkColor = DUSK_INK;
                    }
                    case NIGHT -> {
                        top = 0xFF070C1E;
                        horizon = 0xFF2B3F6E;
                        ink = 0.62F;
                    }
                    default -> {
                        top = 0xFF3E7DC0;
                        horizon = 0xFFCDE6F2;
                    }
                }
                if (scene.tint != 0) {
                    horizon = UiColor.lerp(horizon, scene.tint, time == Time.DAY ? 0.35F : 0.18F);
                }
                if (biome != null && time == Time.DAY) {
                    top = UiColor.lerp(top, 0xFF000000 | biome.sky(), 0.3F);
                }
                if (rain) {
                    top = UiColor.lerp(top, 0xFF4D5562, time == Time.DAY ? 0.6F : 0.35F);
                    horizon = UiColor.lerp(horizon, 0xFF8A929C, time == Time.DAY ? 0.6F : 0.3F);
                }
            }
        }
        int midBase = scene.midColor;
        int groundBase = scene.groundColor;
        int farBase = scene.farColor;
        if (biome != null) {
            // 群系自己的颜色：树叶染中景、草色染地面与远景、水色染水面
            if (leafy(scene)) {
                int foliage = UiColor.lerp(0xFF000000 | biome.foliage(), 0xFF000000, 0.5F);
                midBase = UiColor.lerp(midBase, foliage, scene == Scene.VILLAGE ? 0.35F : 0.6F);
            }
            if (scene.ground == Ground.GRASS) {
                int grass = UiColor.lerp(0xFF000000 | biome.grass(), 0xFF000000, 0.35F);
                groundBase = UiColor.lerp(groundBase, grass, 0.6F);
                farBase = UiColor.lerp(farBase, UiColor.lerp(grass, 0xFF8DA6BC, 0.5F), 0.3F);
            } else if (scene.ground == Ground.WATER) {
                groundBase = UiColor.lerp(groundBase, UiColor.lerp(0xFF000000 | biome.water(), 0xFF000000, 0.3F), 0.6F);
            }
        }
        if ((look & LOOK_BIRCH) != 0) {
            midBase = UiColor.lerp(midBase, 0xFF86A866, 0.45F);
        }
        if ((look & LOOK_DARK) != 0) {
            midBase = UiColor.lerp(midBase, 0xFF13271A, 0.5F);
            farBase = UiColor.lerp(farBase, 0xFF2E4A3A, 0.4F);
        }
        if ((look & LOOK_SNOWY) != 0 && scene.ground != Ground.WATER) {
            groundBase = 0xFFE9F0F7;
            farBase = UiColor.lerp(farBase, 0xFFD6E2EE, 0.5F);
        }
        int back = UiColor.lerp(UiColor.lerp(scene.backColor, inkColor, ink), horizon, 0.6F);
        int far = UiColor.lerp(UiColor.lerp(farBase, inkColor, ink), horizon, 0.32F);
        int mid = UiColor.lerp(UiColor.lerp(midBase, inkColor, ink), horizon, 0.1F);
        int ground = UiColor.lerp(groundBase, inkColor, ink);
        int snow = switch (time) {
            case DUSK -> UiColor.lerp(0xFFF3F7FB, 0xFFF6B99C, 0.5F);
            case NIGHT -> UiColor.lerp(0xFFF3F7FB, 0xFF8EA4CC, 0.5F);
            default -> 0xFFF3F7FB;
        };
        snow = UiColor.lerp(snow, horizon, 0.35F);
        int sun = switch (time) {
            case DUSK -> 0xFFFFD08A;
            case NIGHT -> 0xFFE9F0FF;
            default -> 0xFFFFF6DE;
        };
        float sunY = switch (time) {
            case DUSK -> 0.56F;
            case NIGHT -> 0.27F;
            default -> 0.3F;
        };
        float sunRadius = switch (time) {
            case DUSK -> 8.5F;
            case NIGHT -> 5.0F;
            default -> 6.0F;
        };
        return new Palette(top, horizon, back, far, mid, ground, snow, sun, sunY, sunRadius, !rain && !scene.timeless());
    }

    // ==================== 绘制 ====================

    static void render(UiCanvas canvas, Minecraft minecraft, NotificationManager.ActiveNotification entry) {
        Notification notification = entry.notification();
        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        State state;
        synchronized (STATES) {
            state = STATES.get(notification);
            if (state == null) {
                state = new State(notification, minecraft, font, screenWidth);
                STATES.put(notification, state);
            }
        }
        long t = entry.ageMs(System.currentTimeMillis());
        long duration = notification.durationMs();
        boolean finite = duration < Long.MAX_VALUE / 2L;
        long outro = finite ? duration - 900L : Long.MAX_VALUE / 2L;

        float cardIn = easeOutCubic(range(t, 0L, 420L));
        float cardOut = finite ? easeInCubic(range(t, duration - 320L, duration)) : 0.0F;
        float alpha = cardIn * (1.0F - cardOut);
        if (alpha <= 0.01F) {
            return;
        }
        float w = state.width;
        float h = state.height;
        float x0 = Math.round((screenWidth - w) / 2.0F);
        float y0 = TOP - 6.0F * (1.0F - cardIn) - 4.0F * cardOut;

        canvas.push();
        canvas.translate(x0, y0);
        canvas.pushAlpha(alpha);
        Palette p = state.palette;

        // 卡片底与投影；天空在 0.72 处到达地平线色
        canvas.shape(0.0F, 0.0F, w, h).radius(RADIUS).verticalGradient(p.skyTop(), UiColor.lerp(p.ground(), 0xFF000000, 0.3F))
                .shadow(new Theme.Shadow(0.0F, 3.0F, 10.0F, 0.0F, 0x70000000)).draw();
        canvas.pushClip(0.0F, 0.0F, w, h, RADIUS);
        canvas.shape(0.0F, 0.0F, w, h * 0.72F).verticalGradient(p.skyTop(), p.horizon()).draw();
        canvas.fill(0.0F, h * 0.72F - 0.5F, w, h * 0.28F + 0.5F, p.horizon());

        boolean night = state.time == Time.NIGHT || state.scene == Scene.END;
        float fxIn = range(t, 900L, 1500L) * (1.0F - range(t, outro, outro + 400L));
        if ((night && !state.scene.timeless() && !state.rain) || (state.scene.fx & STARS) != 0) {
            paintStars(canvas, state, w, h, t, fxIn);
        }
        if (p.sunVisible()) {
            paintSun(canvas, p, w, h, t, state.time == Time.NIGHT);
        }
        if (!state.rain && state.time != Time.NIGHT && !state.scene.timeless()) {
            paintClouds(canvas, state, w, h, t, fxIn);
        }

        // 立体书一样依次弹起；退场时从近到远收回
        float backH = backHeight(state.scene, h);
        float backY = h - backH + lift(t, 120L, outro + 240L, backH);
        drawLayer(canvas, state.scene.back, w, backY, backH, state.backOffset + drift(t, 0.5F, backH), p.back());
        if (state.scene.snowCaps) {
            drawLayer(canvas, SNOW_CAPS, w, backY, backH, state.backOffset + drift(t, 0.5F, backH), p.snow());
        }
        mist(canvas, p, w, backY + backH * 0.5F, backH * 0.5F, state.time == Time.NIGHT ? 0.3F : 0.45F);
        if (state.scene.far != null) {
            float farH = h * 0.42F;
            float farY = h - farH + lift(t, 220L, outro + 160L, farH);
            drawLayer(canvas, state.scene.far, w, farY, farH, state.farOffset + drift(t, 0.9F, farH), p.far());
            mist(canvas, p, w, farY + farH * 0.55F, farH * 0.45F, state.time == Time.NIGHT ? 0.18F : 0.3F);
        }
        float midH = midHeight(state.scene, h);
        float midY = h - midH + lift(t, 320L, outro + 80L, midH);
        float midOffset = state.midOffset + drift(t, 1.4F, midH);
        if (state.scene.mid != null) {
            drawLayer(canvas, state.scene.mid, w, midY, midH, midOffset, p.mid());
            paintMidDetails(canvas, state, p, w, midY, midH, midOffset, t, fxIn);
        }

        float groundH = h * 0.2F;
        float groundY = h - groundH + lift(t, 420L, outro, groundH);
        float groundOffset = state.groundOffset + drift(t, 2.0F, groundH * TEX_H / GROUND_TEX_H);
        switch (state.scene.ground) {
            case GRASS, SOFT -> {
                ResourceLocation ground = state.scene.ground == Ground.GRASS && !state.snowy() ? GRASS : SOFT;
                drawLayer(canvas, ground, GROUND_TEX_H, w, groundY, groundH, groundOffset, p.ground());
                paintDecor(canvas, state, p, w, groundY, groundH, groundOffset, t, outro);
            }
            case WATER -> paintWater(canvas, state, p, w, h, h * 0.9F + lift(t, 420L, outro, h * 0.1F), t, outro);
            default -> {
            }
        }
        paintParticles(canvas, state, p, w, h, t, fxIn);

        // 顶部压暗托住标题；四角轻微暗角
        canvas.shape(0.0F, 0.0F, w, h * 0.55F).verticalGradient(0x40000000, 0x00000000).draw();
        float scrimW = Math.min(w * 0.5F, 70.0F);
        canvas.shape(w / 2.0F - scrimW, 0.0F, scrimW * 2.0F, h).radial(0x34000000, 0x00000000, scrimW, h * 0.36F, scrimW).draw();
        canvas.shape(0.0F, 0.0F, w, h).radial(0x00000000, 0x38000000, w / 2.0F, h / 2.0F,
                (float) Math.hypot(w, h) * 0.58F).draw();
        canvas.popClip();

        // 边框：外圈暖色细线，内圈一道淡白线，像明信片的压边
        canvas.shape(0.0F, 0.0F, w, h).radius(RADIUS).fill(0).border(1.0F, UiColor.withAlpha(0xFFF6E7C8, 0.3F)).draw();
        canvas.shape(2.0F, 2.0F, w - 4.0F, h - 4.0F).radius(RADIUS - 1.5F).fill(0)
                .border(0.6F, UiColor.withAlpha(0xFFFFFFFF, 0.08F)).draw();

        paintText(canvas, font, state, w, t, outro);
        canvas.popAlpha();
        canvas.pop();
    }

    private static void drawLayer(UiCanvas canvas, ResourceLocation texture, float w, float y, float height,
                                  float offset, int tint) {
        drawLayer(canvas, texture, TEX_H, w, y, height, offset, tint);
    }

    private static void drawLayer(UiCanvas canvas, ResourceLocation texture, int textureHeight, float w, float y,
                                  float height, float offset, int tint) {
        float drawWidth = TEX_W * height / textureHeight;
        float u0 = offset - (float) Math.floor(offset);
        canvas.image(texture, 0.0F, y, w, height, u0, 0.0F, u0 + w / drawWidth, 1.0F, 0.0F, tint);
    }

    private static void mist(UiCanvas canvas, Palette p, float w, float top, float height, float amount) {
        canvas.shape(0.0F, top, w, height).verticalGradient(UiColor.withAlpha(p.horizon(), 0),
                UiColor.withAlpha(p.horizon(), amount)).draw();
    }

    /** 入场时从下方弹起（略带回弹），退场时沉下去；返回相对正常位置的下移量。 */
    private static float lift(long t, long start, long sinkStart, float height) {
        float rise = easeOutBack(range(t, start, start + 640L));
        float sink = easeInBack(range(t, sinkStart, sinkStart + 460L));
        return (1.0F - rise) * height * 0.9F + sink * height * 0.9F;
    }

    /** 各层缓慢平移的贴图偏移量：越近越快，像镜头在横移。 */
    private static float drift(long t, float pixelsPerSecond, float layerHeight) {
        float drawWidth = TEX_W * layerHeight / TEX_H;
        return t / 1000.0F * pixelsPerSecond / drawWidth;
    }

    private static void paintSun(UiCanvas canvas, Palette p, float w, float h, long t, boolean moon) {
        float rise = easeOutCubic(range(t, 150L, 1000L));
        float x = w * 0.82F;
        float y = h * p.sunY() + (1.0F - rise) * h * 0.25F;
        float r = p.sunRadius() * Math.max(0.6F, h / DESIGN_HEIGHT);
        float glow = r * 5.0F;
        canvas.shape(x - glow, y - glow, glow * 2.0F, glow * 2.0F)
                .radial(UiColor.withAlpha(p.sun(), moon ? 0.3F : 0.45F), UiColor.withAlpha(p.sun(), 0), glow, glow, glow).draw();
        canvas.circle(x, y, r, p.sun());
        if (moon) {
            // 弯月：用天空色的圆挡住一侧
            int sky = UiColor.lerp(p.skyTop(), p.horizon(), Math.min(1.0F, y / (h * 0.72F)));
            canvas.circle(x + r * 0.45F, y - r * 0.25F, r * 0.88F, sky);
        }
    }

    private static void paintStars(UiCanvas canvas, State s, float w, float h, long t, float shown) {
        float base = range(t, 300L, 1200L);
        for (int i = 0; i < 30; i++) {
            float x = hash(s.seed, 20, i) * w;
            float y = hash(s.seed, 21, i) * h * 0.55F;
            float r = 0.3F + hash(s.seed, 22, i) * 0.45F;
            float twinkle = 0.55F + 0.45F * (float) Math.sin(t / 650.0 + hash(s.seed, 23, i) * 30.0);
            canvas.circle(x, y, r, UiColor.withAlpha(0xFFFFFFFF, 0.85F * twinkle * Math.max(base, shown)));
        }
    }

    /** 几缕细长的云丝，缓慢飘过；黄昏时染上暖色。 */
    private static void paintClouds(UiCanvas canvas, State s, float w, float h, long t, float shown) {
        int color = s.time == Time.DUSK ? 0xFFFFC2A6 : 0xFFFFFFFF;
        float a = 0.2F * range(t, 200L, 1100L);
        for (int i = 0; i < 4; i++) {
            float length = 16.0F + hash(s.seed, 32, i) * 22.0F;
            float span = w + length + 20.0F;
            float x = (hash(s.seed, 30, i) * span + t / 1000.0F * (1.2F + hash(s.seed, 33, i))) % span - length - 10.0F;
            float y = h * (0.16F + 0.3F * hash(s.seed, 31, i));
            float thick = 1.2F + hash(s.seed, 34, i) * 1.2F;
            canvas.shape(x, y, length, thick).radius(thick / 2.0F)
                    .horizontalGradient(UiColor.withAlpha(color, 0), UiColor.withAlpha(color, a)).draw();
            canvas.shape(x + length * 0.35F, y - thick * 0.6F, length * 0.5F, thick).radius(thick / 2.0F)
                    .fill(UiColor.withAlpha(color, a * 0.6F)).draw();
        }
    }

    /** 中景的动态细节：风车转动、炊烟、亮起的窗户、灯塔的灯与光束。 */
    private static void paintMidDetails(UiCanvas canvas, State s, Palette p, float w, float top, float height,
                                        float offset, long t, float shown) {
        float drawWidth = TEX_W * height / TEX_H;
        float lights = s.time == Time.NIGHT ? 1.0F : s.time == Time.DUSK ? 0.75F : 0.0F;
        if (s.scene == Scene.VILLAGE) {
            if (lights > 0.0F) {
                float flicker = 0.9F + 0.1F * (float) Math.sin(t / 230.0);
                drawLayer(canvas, VILLAGE_LIGHTS, w, top, height, offset, UiColor.withAlpha(0xFFFFC66B, lights * flicker));
            }
            for (float x : anchorXs(WINDMILL_HUB[0], offset, drawWidth, w)) {
                paintWindmill(canvas, x, top + WINDMILL_HUB[1] * height, height * 0.26F, t, p.mid());
            }
            for (float[] chimney : CHIMNEYS) {
                for (float x : anchorXs(chimney[0], offset, drawWidth, w)) {
                    paintSmoke(canvas, s, x, top + chimney[1] * height, t, shown);
                }
            }
        } else if (s.scene == Scene.COAST) {
            for (float x : anchorXs(LIGHTHOUSE_LAMP[0], offset, drawWidth, w)) {
                paintLighthouse(canvas, x, top + LIGHTHOUSE_LAMP[1] * height, w, t, lights, shown);
            }
        }
    }

    /** 贴图坐标 u 在卡片里可能出现的横坐标（平铺时左右各算一次）。 */
    private static float[] anchorXs(float u, float offset, float drawWidth, float w) {
        float x = (float) ((u - offset) - Math.floor(u - offset)) * drawWidth;
        float other = x - drawWidth;
        boolean first = x > -20.0F && x < w + 20.0F;
        boolean second = other > -20.0F && other < w + 20.0F;
        if (first && second) {
            return new float[]{x, other};
        }
        return first ? new float[]{x} : second ? new float[]{other} : new float[0];
    }

    private static void paintWindmill(UiCanvas canvas, float x, float y, float length, long t, int color) {
        double angle = t / 1000.0 * 0.9;
        for (int i = 0; i < 4; i++) {
            double a = angle + i * Math.PI / 2.0;
            float dx = (float) Math.cos(a);
            float dy = (float) Math.sin(a);
            float tipX = x + dx * length;
            float tipY = y + dy * length;
            canvas.line(x, y, tipX, tipY, 0.7F, color, true);
            // 帆布：贴着帆杆一侧的宽条
            float nx = -dy * 1.3F;
            float ny = dx * 1.3F;
            canvas.line(x + dx * length * 0.32F + nx, y + dy * length * 0.32F + ny, tipX + nx * 0.9F, tipY + ny * 0.9F,
                    2.2F, color, false);
        }
        canvas.circle(x, y, 1.1F, color);
    }

    private static void paintSmoke(UiCanvas canvas, State s, float x, float y, long t, float shown) {
        int color = UiColor.lerp(s.palette.horizon(), 0xFFFFFFFF, 0.45F);
        for (int i = 0; i < 3; i++) {
            float phase = (float) ((t / 3200.0 + i / 3.0 + hash(s.seed, 40, (int) x) * 0.3) % 1.0);
            float fadeIn = Math.min(1.0F, phase / 0.15F);
            float a = 0.32F * (1.0F - phase) * fadeIn * shown;
            canvas.circle(x + phase * 5.0F, y - phase * 11.0F, 0.8F + phase * 2.4F, UiColor.withAlpha(color, a));
        }
    }

    private static void paintLighthouse(UiCanvas canvas, float x, float y, float w, long t, float lights, float shown) {
        float on = Math.max(0.25F, lights) * shown;
        if (on <= 0.01F) {
            return;
        }
        if (lights > 0.0F) {
            double angle = t / 5200.0 * Math.PI * 2.0 + 1.2;
            float side = (float) Math.sin(angle);
            float length = w * 0.55F * Math.abs(side);
            if (length > 3.0F) {
                float beamH = length * 0.26F;
                float bx = side < 0.0F ? x - length : x;
                float a = 0.42F * lights * shown * (0.3F + 0.7F * Math.abs(side));
                canvas.image(BEAM, bx, y - beamH / 2.0F, length, beamH, side < 0.0F ? 1.0F : 0.0F, 0.0F,
                        side < 0.0F ? 0.0F : 1.0F, 1.0F, 0.0F, UiColor.withAlpha(0xFFFFE2A8, a));
            }
        }
        float glow = 7.0F;
        canvas.shape(x - glow, y - glow, glow * 2.0F, glow * 2.0F)
                .radial(UiColor.withAlpha(0xFFFFD27A, 0.55F * on), 0x00FFD27A, glow, glow, glow).draw();
        canvas.circle(x, y, 1.1F, UiColor.withAlpha(0xFFFFF4D6, on));
    }

    /** 前景的小草、小花、小蘑菇：沿地面曲线生长，从左到右依次冒出来，随风轻轻摆动。 */
    private static void paintDecor(UiCanvas canvas, State s, Palette p, float w, float groundTop, float groundH,
                                   float groundOffset, long t, long outro) {
        if (s.decor.isEmpty()) {
            return;
        }
        float drawWidth = TEX_W * groundH / GROUND_TEX_H;
        float span = w + 20.0F;
        float driftPx = t / 1000.0F * 2.0F;
        int blade = UiColor.lerp(p.ground(), 0xFFFFFFFF, s.time == Time.NIGHT ? 0.06F : 0.14F);
        float dim = s.time == Time.NIGHT ? 0.55F : s.time == Time.DUSK ? 0.8F : 1.0F;
        float shrink = 1.0F - easeInCubic(range(t, outro, outro + 350L));
        for (Decor d : s.decor) {
            float x = ((d.x() - driftPx) % span + span) % span - 10.0F;
            if (x < -6.0F || x > w + 6.0F) {
                continue;
            }
            float delay = 700.0F + (x / w) * 520.0F;
            float grow = easeOutBack(range(t, (long) delay, (long) delay + 380L)) * shrink;
            if (grow <= 0.01F) {
                continue;
            }
            float u = groundOffset + x / drawWidth;
            float gy = groundTop + groundSurface(u - (float) Math.floor(u)) * groundH + 0.6F;
            float wind = (float) (Math.sin(t / 720.0 + x * 0.17 + d.phase()) * 0.9 + Math.sin(t / 1300.0 + x * 0.05) * 0.5);
            switch (d.kind()) {
                case 0 -> {
                    for (int k = -1; k <= 1; k++) {
                        float bh = d.height() * grow * (k == 0 ? 1.0F : 0.7F);
                        canvas.line(x + k * 0.9F, gy, x + k * 1.6F + wind * bh * 0.28F, gy - bh, 0.6F, blade, true);
                    }
                }
                case 1 -> {
                    float sh = d.height() * grow;
                    float hx = x + wind * sh * 0.25F;
                    float hy = gy - sh;
                    canvas.line(x, gy, hx, hy, 0.5F, blade, true);
                    int petal = UiColor.lerp(p.ground(), d.color(), dim);
                    canvas.circle(hx, hy, 1.05F * Math.min(1.0F, grow), petal);
                    canvas.circle(hx, hy, 0.42F * Math.min(1.0F, grow), UiColor.lerp(petal, 0xFFFFD34D, 0.7F));
                }
                case 2 -> {
                    float sh = d.height() * grow;
                    canvas.line(x, gy, x, gy - sh, 0.8F, UiColor.lerp(0xFFEDE3D3, p.ground(), 1.0F - dim * 0.8F), false);
                    int cap = UiColor.lerp(p.ground(), d.color(), dim);
                    canvas.shape(x - 1.6F * grow, gy - sh - 1.0F, 3.2F * grow, 1.8F).radius(0.9F).fill(cap).draw();
                }
                default -> {
                }
            }
        }
    }

    /** 与生成脚本一致的前景地面顶边（占地面贴图高度的比例）。 */
    private static float groundSurface(float u) {
        return (float) (0.42 + 0.1 * Math.sin(2.0 * Math.PI * (2.0 * u) + 0.6) + 0.05 * Math.sin(2.0 * Math.PI * (5.0 * u) + 1.9));
    }

    private static void paintWater(UiCanvas canvas, State s, Palette p, float w, float h, float top, long t, long outro) {
        int water = p.ground();
        int surface = UiColor.lerp(water, p.horizon(), 0.35F);
        canvas.shape(0.0F, top, w, h - top + 1.0F).verticalGradient(surface, UiColor.lerp(water, 0xFF000000, 0.25F)).draw();
        canvas.fill(0.0F, top, w, 0.6F, UiColor.withAlpha(p.horizon(), 0.6F));
        float shown = range(t, 700L, 1300L) * (1.0F - range(t, outro, outro + 400L));
        if (p.sunVisible()) {
            // 日光或月光在水面拉出的一道碎光
            float sx = w * 0.82F;
            for (int i = 0; i < 4; i++) {
                float wave = 0.6F + 0.4F * (float) Math.sin(t / 300.0 + i * 1.7);
                float len = (7.0F - i * 1.3F) * wave;
                float drift = (float) Math.sin(t / 900.0 + i) * 1.2F;
                canvas.fill(sx - len / 2.0F + drift, top + 1.4F + i * 1.5F, len, 0.6F,
                        UiColor.withAlpha(p.sun(), 0.55F * (1.0F - i / 5.0F) * shown));
            }
        }
        for (int i = 0; i < 12; i++) {
            float span = w + 10.0F;
            float x = (hash(s.seed, 60, i) * span + (float) Math.sin(t / 1400.0 + i) * 3.0F + t / 1000.0F * 1.2F) % span - 5.0F;
            float y = top + 1.5F + hash(s.seed, 61, i) * (h - top - 2.5F);
            float a = 0.12F + 0.18F * (float) Math.max(0.0, Math.sin(t / 500.0 + i * 2.1));
            canvas.fill(x, y, 2.0F + hash(s.seed, 62, i) * 4.0F, 0.5F, UiColor.withAlpha(0xFFFFFFFF, a * shown));
        }
        for (Decor d : s.decor) {
            if (d.kind() != 3) {
                continue;
            }
            float span = w + 20.0F;
            float x = ((d.x() - t / 1000.0F * 2.0F) % span + span) % span - 10.0F;
            float y = top + 1.6F + (float) Math.sin(t / 900.0 + d.phase()) * 0.3F + (d.phase() % 1.0F) * 1.6F;
            canvas.shape(x - 1.8F, y, 3.6F, 1.2F).radius(0.6F).fill(UiColor.withAlpha(UiColor.lerp(d.color(), water, 0.25F), shown)).draw();
        }
    }

    private static void paintParticles(UiCanvas canvas, State s, Palette p, float w, float h, long t, float shown) {
        if (shown <= 0.01F) {
            return;
        }
        int fx = s.scene.fx;
        boolean dark = s.time != Time.DAY;
        if ((fx & PETALS) != 0) {
            for (int i = 0; i < 11; i++) {
                float speed = 0.0075F + 0.005F * hash(s.seed, 70, i);
                float y = (hash(s.seed, 71, i) * (h + 8.0F) + t * speed) % (h + 8.0F) - 4.0F;
                float span = w + 20.0F;
                float x = (hash(s.seed, 72, i) * span + t * 0.006F + (float) Math.sin(t / 600.0 + i) * 4.0F) % span - 10.0F;
                float flip = Math.abs((float) Math.cos(t / 380.0 + i * 1.3));
                int color = UiColor.lerp(p.mid(), 0xFFFFC7DA, dark ? 0.55F : 0.95F);
                canvas.shape(x, y, 0.7F + 1.3F * flip, 1.1F).radius(0.55F).fill(UiColor.withAlpha(color, 0.9F * shown)).draw();
            }
        }
        if ((fx & FIREFLIES) != 0 && dark && !s.rain) {
            float strength = s.time == Time.NIGHT ? 1.0F : 0.55F;
            for (int i = 0; i < 9; i++) {
                float x = hash(s.seed, 80, i) * w + (float) Math.sin(t / 1700.0 + i * 2.3) * 6.0F;
                float y = h * (0.5F + 0.42F * hash(s.seed, 81, i)) + (float) Math.sin(t / 1300.0 + i * 1.7) * 3.0F;
                float blink = (float) Math.pow(Math.max(0.0, Math.sin(t / 620.0 + i * 5.1)), 2.0) * strength * shown;
                canvas.circle(x, y, 2.4F, UiColor.withAlpha(0xFFFFE680, 0.22F * blink));
                canvas.circle(x, y, 0.6F, UiColor.withAlpha(0xFFFFF6C0, 0.95F * blink));
            }
        }
        if ((fx & BUTTERFLIES) != 0 && s.time == Time.DAY && !s.rain) {
            int[] colors = {0xFFFFFFFF, 0xFFFFE066, 0xFFFFA64D};
            for (int i = 0; i < 2; i++) {
                float span = w + 30.0F;
                float x = (hash(s.seed, 90, i) * span + t * 0.011F) % span - 15.0F;
                float y = h * (0.62F + 0.12F * hash(s.seed, 91, i)) + (float) Math.sin(t / 700.0 + i * 2.0) * 3.0F;
                float flap = 0.3F + 0.7F * Math.abs((float) Math.sin(t / 85.0 + i));
                int color = UiColor.withAlpha(colors[(int) (hash(s.seed, 92, i) * colors.length) % colors.length], shown);
                canvas.circle(x - 0.9F * flap, y, 1.0F, color);
                canvas.circle(x + 0.9F * flap, y, 1.0F, color);
            }
        }
        if ((fx & BIRDS) != 0 && s.time != Time.NIGHT && !s.rain) {
            int color = UiColor.withAlpha(UiColor.lerp(p.mid(), 0xFF000000, 0.3F), 0.8F * shown);
            float span = w + 50.0F;
            float lead = (hash(s.seed, 100, 0) * span + t * 0.009F) % span - 25.0F;
            float baseY = h * 0.52F + (float) Math.sin(t / 2100.0) * 2.0F;
            float[][] flock = {{0.0F, 0.0F}, {-5.0F, 2.6F}, {-9.5F, 4.6F}};
            for (int i = 0; i < flock.length; i++) {
                float x = lead + flock[i][0];
                float y = baseY + flock[i][1];
                float flap = (float) Math.sin(t / 170.0 + i * 1.4);
                float tip = -1.1F * flap - 0.4F;
                canvas.line(x - 2.3F, y + tip, x, y, 0.55F, color, true);
                canvas.line(x, y, x + 2.3F, y + tip, 0.55F, color, true);
            }
        }
        if ((fx & SNOW) != 0 || s.snowy() && !s.scene.timeless()) {
            for (int i = 0; i < 26; i++) {
                float speed = 0.007F + 0.006F * hash(s.seed, 110, i);
                float y = (hash(s.seed, 111, i) * (h + 6.0F) + t * speed) % (h + 6.0F) - 3.0F;
                float span = w + 10.0F;
                float x = ((hash(s.seed, 112, i) * span - t * 0.003F + (float) Math.sin(t / 900.0 + i) * 3.0F) % span + span) % span - 5.0F;
                canvas.circle(x, y, 0.45F + 0.5F * hash(s.seed, 113, i), UiColor.withAlpha(0xFFFFFFFF, 0.85F * shown));
            }
        } else if (s.rain && !dry(s.scene)) {
            int color = UiColor.withAlpha(0xFFD3E2F0, 0.35F * shown);
            for (int i = 0; i < 30; i++) {
                float y = (hash(s.seed, 120, i) * (h + 10.0F) + t * 0.16F) % (h + 10.0F) - 5.0F;
                float span = w + 20.0F;
                float x = (hash(s.seed, 121, i) * span + t * 0.04F) % span - 10.0F;
                canvas.line(x, y, x - 1.2F, y + 4.0F, 0.45F, color, false);
            }
        }
        if ((fx & DUST) != 0) {
            int color = UiColor.withAlpha(UiColor.lerp(p.horizon(), 0xFFFFFFFF, 0.5F), 0.28F * shown);
            for (int i = 0; i < 12; i++) {
                float span = w + 20.0F;
                float x = (hash(s.seed, 130, i) * span + t * 0.02F) % span - 10.0F;
                float y = h * (0.55F + 0.4F * hash(s.seed, 131, i)) + (float) Math.sin(t / 800.0 + i) * 1.5F;
                canvas.circle(x, y, 0.5F, color);
            }
        }
        if ((fx & SPORES) != 0) {
            for (int i = 0; i < 12; i++) {
                float rise = (hash(s.seed, 140, i) * h * 0.8F + t * 0.006F) % (h * 0.8F);
                float x = hash(s.seed, 141, i) * w + (float) Math.sin(t / 1000.0 + i) * 3.0F;
                float y = h - rise;
                float a = (dark ? 0.7F : 0.35F) * shown * Math.min(1.0F, rise / 8.0F);
                if (dark) {
                    canvas.circle(x, y, 1.8F, UiColor.withAlpha(0xFFC8A8FF, a * 0.3F));
                }
                canvas.circle(x, y, 0.55F, UiColor.withAlpha(dark ? 0xFFE2D2FF : 0xFFFFFFFF, a));
            }
        }
        if ((fx & EMBERS) != 0) {
            for (int i = 0; i < 16; i++) {
                float rise = (hash(s.seed, 150, i) * h + t * 0.02F) % h;
                float x = hash(s.seed, 151, i) * w + (float) Math.sin(t / 500.0 + i) * 2.0F;
                float flicker = 0.6F + 0.4F * (float) Math.sin(t / 120.0 + i * 3.0);
                canvas.circle(x, h - rise, 0.55F, UiColor.withAlpha(0xFFFF9A3C, 0.85F * flicker * shown));
            }
        }
        if ((fx & GLOW) != 0 && (s.look & LOOK_LUSH) != 0) {
            int vine = UiColor.lerp(p.mid(), 0xFF3E7A4A, 0.6F);
            for (int i = 0; i < 7; i++) {
                float x = 6.0F + hash(s.seed, 170, i) * (w - 12.0F);
                float length = h * (0.18F + 0.3F * hash(s.seed, 171, i));
                float sway = (float) Math.sin(t / 1400.0 + i * 1.3) * 1.2F;
                float top = h * 0.05F;
                canvas.line(x, top, x + sway, top + length, 0.7F, UiColor.withAlpha(vine, shown), true);
                for (int k = 1; k <= 2; k++) {
                    float by = top + length * (0.45F + 0.4F * k / 2.0F);
                    float bx = x + sway * (by - top) / length + (k == 1 ? -0.9F : 0.9F);
                    float pulse = 0.7F + 0.3F * (float) Math.sin(t / 800.0 + i * 2.0 + k);
                    canvas.circle(bx, by, 2.6F, UiColor.withAlpha(0xFFFFB347, 0.25F * pulse * shown));
                    canvas.circle(bx, by, 0.9F, UiColor.withAlpha(0xFFFFD27A, pulse * shown));
                }
            }
        }
        if ((fx & GLOW) != 0) {
            for (int i = 0; i < 14; i++) {
                boolean ceiling = i % 3 == 0;
                float x = hash(s.seed, 160, i) * w;
                float y = ceiling ? h * 0.08F + hash(s.seed, 161, i) * 5.0F : h * 0.9F - hash(s.seed, 161, i) * 5.0F;
                float pulse = 0.55F + 0.45F * (float) Math.sin(t / 900.0 + i * 1.9);
                int color = i % 2 == 0 ? 0xFF7CF0C8 : 0xFF6FC3FF;
                canvas.circle(x, y, 2.6F, UiColor.withAlpha(color, 0.22F * pulse * shown));
                canvas.circle(x, y, 0.7F, UiColor.withAlpha(color, 0.95F * pulse * shown));
            }
            for (int i = 0; i < 3; i++) {
                float fall = (hash(s.seed, 165, i) * h * 0.8F + t * 0.03F) % (h * 0.8F);
                canvas.circle(hash(s.seed, 166, i) * w, h * 0.1F + fall, 0.5F, UiColor.withAlpha(0xFFBFE6FF, 0.6F * shown));
            }
        }
    }

    private static void paintText(UiCanvas canvas, Font font, State s, float w, long t, long outro) {
        float out = range(t, outro, outro + 350L);
        float titleIn = easeOutCubic(range(t, 650L, 1400L));
        float alpha = titleIn * (1.0F - out);
        if (alpha <= 0.01F) {
            return;
        }
        float scale = TITLE_SCALE;
        float tracking = TITLE_TRACKING + 3.0F * (1.0F - titleIn);
        float titleW = trackedWidth(font, s.title, scale, tracking);
        boolean twoLines = !s.details.isEmpty();
        float titleH = font.lineHeight * scale;
        float detailH = font.lineHeight * DETAIL_SCALE;
        float block = twoLines ? titleH + 2.0F + detailH : titleH;
        // 文字放在天空里，尽量不压中景：两行时贴近上沿，只有地名时略高于正中
        float titleY = (twoLines ? s.height * 0.12F : (s.height - block) / 2.0F - 2.5F) + 2.0F * (1.0F - titleIn);
        drawTracked(canvas, font, s.title, (w - titleW) / 2.0F, titleY, scale, tracking,
                UiColor.withAlpha(TITLE_COLOR, alpha), 0.55F * alpha);
        if (!twoLines) {
            return;
        }
        // 说明行：“首次发现”用淡金色点一下，其余为米白，段与段之间是暗一些的间隔点
        float detailIn = easeOutCubic(range(t, 850L, 1500L)) * (1.0F - out);
        float x = (w - detailWidth(font, s.details)) / 2.0F;
        float y = titleY + titleH + 2.0F;
        for (int i = 0; i < s.details.size(); i++) {
            if (i > 0) {
                drawTracked(canvas, font, DETAIL_SEPARATOR, x, y, DETAIL_SCALE, DETAIL_TRACKING,
                        UiColor.withAlpha(DETAIL_COLOR, 0.5F * detailIn), 0.3F * detailIn);
                x += trackedWidth(font, DETAIL_SEPARATOR, DETAIL_SCALE, DETAIL_TRACKING) + DETAIL_TRACKING;
            }
            String part = s.details.get(i);
            int color = i == 0 && s.first ? FIRST_COLOR : DETAIL_COLOR;
            haloTracked(canvas, font, part, x, y, DETAIL_SCALE, DETAIL_TRACKING, 0.32F * detailIn);
            drawTracked(canvas, font, part, x, y, DETAIL_SCALE, DETAIL_TRACKING, UiColor.withAlpha(color, 0.95F * detailIn),
                    0.55F * detailIn);
            x += trackedWidth(font, part, DETAIL_SCALE, DETAIL_TRACKING);
        }
    }

    /** 小字在亮背景（白天的天空、中景）上的暗描边：上下左右各偏半个像素画一层半透明黑字。 */
    private static void haloTracked(UiCanvas canvas, Font font, String text, float x, float y, float scale,
                                    float tracking, float alpha) {
        int color = UiColor.withAlpha(0xFF000000, alpha);
        drawTracked(canvas, font, text, x - 0.5F, y, scale, tracking, color, 0.0F);
        drawTracked(canvas, font, text, x + 0.5F, y, scale, tracking, color, 0.0F);
        drawTracked(canvas, font, text, x, y - 0.5F, scale, tracking, color, 0.0F);
        drawTracked(canvas, font, text, x, y + 0.5F, scale, tracking, color, 0.0F);
    }

    /** 说明行（各段加间隔点）的总宽度。 */
    private static float detailWidth(Font font, List<String> details) {
        float total = 0.0F;
        for (int i = 0; i < details.size(); i++) {
            total += trackedWidth(font, details.get(i), DETAIL_SCALE, DETAIL_TRACKING);
            if (i > 0) {
                total += trackedWidth(font, DETAIL_SEPARATOR, DETAIL_SCALE, DETAIL_TRACKING) + DETAIL_TRACKING;
            }
        }
        return total;
    }

    // ==================== 工具 ====================

    private static float trackedWidth(Font font, String text, float scale, float tracking) {
        int count = text.codePointCount(0, text.length());
        return font.width(text) * scale + tracking * Math.max(0, count - 1);
    }

    /** 逐字绘制以加大字距；{@code shadow} 为向右下偏移一像素的黑色阴影的不透明度。 */
    private static void drawTracked(UiCanvas canvas, Font font, String text, float x, float y, float scale,
                                    float tracking, int color, float shadow) {
        float cursor = x;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            String glyph = new String(Character.toChars(codePoint));
            if (shadow > 0.01F) {
                canvas.text(glyph, cursor + 0.8F, y + 0.9F, UiColor.withAlpha(0xFF000000, Math.min(1.0F, shadow)), scale, false);
            }
            canvas.text(glyph, cursor, y, color, scale, false);
            cursor += font.width(glyph) * scale + tracking;
            index += Character.charCount(codePoint);
        }
    }

    /** 由种子与两个序号得到 0~1 的稳定伪随机数。 */
    private static float hash(long seed, int a, int b) {
        long h = seed + a * 0xBF58476D1CE4E5B9L + b * 0x94D049BB133111EBL;
        h ^= h >>> 31;
        h *= 0x7FB5D329728EA185L;
        h ^= h >>> 27;
        h *= 0x81DADEF4BC2DD44DL;
        h ^= h >>> 33;
        return (h >>> 40) / (float) (1L << 24);
    }

    private static float range(long t, long start, long end) {
        return Math.max(0.0F, Math.min(1.0F, (t - start) / (float) Math.max(1L, end - start)));
    }

    private static float easeOutCubic(float v) {
        float inv = 1.0F - v;
        return 1.0F - inv * inv * inv;
    }

    private static float easeInCubic(float v) {
        return v * v * v;
    }

    private static float easeOutBack(float v) {
        float c1 = 1.4F;
        float c3 = c1 + 1.0F;
        float x = v - 1.0F;
        return 1.0F + c3 * x * x * x + c1 * x * x;
    }

    private static float easeInBack(float v) {
        float c1 = 1.4F;
        float c3 = c1 + 1.0F;
        return c3 * v * v * v - c1 * v * v;
    }
}
