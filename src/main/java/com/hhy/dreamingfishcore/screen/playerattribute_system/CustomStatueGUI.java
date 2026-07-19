package com.hhy.dreamingfishcore.screen.playerattribute_system;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.core.playerattributes_system.courage.PlayerCourageManager;
import com.hhy.dreamingfishcore.core.playerattributes_system.infection.PlayerInfectionManager;
import com.hhy.dreamingfishcore.core.playerattributes_system.limb_health_system.LimbClientInjurySync;
import com.hhy.dreamingfishcore.core.playerattributes_system.limb_health_system.LimbType;
import com.hhy.dreamingfishcore.core.playerattributes_system.strength.PlayerStrengthClientSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, value = Dist.CLIENT)
public class CustomStatueGUI {
    // 左下角玩家贴图布局
    private static final int PLAYER_MODEL_ANCHOR_HALF_WIDTH = 14;
    private static final int PLAYER_ICON_WIDTH = 27;
    private static final int PLAYER_MODEL_HEIGHT = 59;
    private static final int PLAYER_UV_X_NORMAL = 0;
    private static final int PLAYER_UV_X_INJURED = 27;
    private static final int PLAYER_UV_Y = 0;
    private static final int PLAYER_TEXTURE_TOTAL_WIDTH = 256;
    private static final int PLAYER_TEXTURE_TOTAL_HEIGHT = 256;
    private static final ResourceLocation PLAYER_HEALTH_TEXTURE =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/health/health.png");
    private static final int EQUIPMENT_DURABILITY_BAR_WIDTH = 4;
    private static final int EQUIPMENT_DURABILITY_BAR_GAP = 3;
    private static final int EQUIPMENT_ICON_SIZE = 8;
    private static final int EQUIPMENT_ICON_TO_BAR_GAP = 2;
    private static final int EQUIPMENT_DURABILITY_WIDTH =
            EQUIPMENT_ICON_SIZE + EQUIPMENT_ICON_TO_BAR_GAP + EQUIPMENT_DURABILITY_BAR_WIDTH;
    private static final int EQUIPMENT_DURABILITY_GAP = 2;
    private static final int EQUIPMENT_DURABILITY_VERTICAL_PADDING = 4;

    // 受伤闪烁相关
    private static long lastDamageTime = 0;              // 上次受伤时间（毫秒）
    private static final long DAMAGE_FLASH_DURATION = 400; // 受伤闪烁持续时间（毫秒）
    private static final int FLASH_CYCLE = 100;          // 闪烁周期（毫秒）- 每100ms切换一次
    private static float lastHealth = 0;                 // 上次记录的血量
    //控制小人与屏幕右侧的距离
//    private static final int RIGHT_OFFSET = 5;
    private static final int LEFT_OFFSET = 2;
    //样式常量
    // 基础样式：低透明、低饱和，警告时才抬高存在感
    private static final int LOW_COLOR = 0xFFA85048;
    private static final int TRACK_COLOR = 0xFF101314;

    private static final int STATUS_SIGNAL_WIDTH = 54;
    private static final int STATUS_BAR_HEIGHT = 6;
    private static final int HUD_ICON_SIZE = 10;
    private static final int HUD_ICON_TEXTURE_SIZE = 16;
    private static final int STATUS_ICON_TO_BAR_SPACING = 4;
    private static final int STATUS_BAR_TO_PLAYER_SPACING = 2;
    private static final int STATUS_BAR_SPACING = 6;
    private static final int STATUS_ROW_COUNT = 4;
    private static final int STATUS_STACK_HEIGHT =
            STATUS_ROW_COUNT * STATUS_BAR_HEIGHT + (STATUS_ROW_COUNT - 1) * STATUS_BAR_SPACING;
    private static final int LEFT_STATUS_BOTTOM_OFFSET = 12;
    private static final long STATUS_BAR_REVEAL_DURATION = 5000L;
    private static final long STATUS_BAR_ANIMATION_DURATION = 260L;
    private static final int SPLIT_EXPERIENCE_BAR_HEIGHT = 5;
    private static final int SPLIT_EXPERIENCE_HOTBAR_GAP = 4;
    private static final int SPLIT_EXPERIENCE_SIDE_WIDTH = 68;
    private static final int SPLIT_EXPERIENCE_CENTER_WIDTH = 82;
    private static final int SPLIT_EXPERIENCE_SEGMENT_GAP = 4;
    private static final int SPLIT_EXPERIENCE_BAR_WIDTH =
            SPLIT_EXPERIENCE_SIDE_WIDTH * 2
                    + SPLIT_EXPERIENCE_CENTER_WIDTH
                    + SPLIT_EXPERIENCE_SEGMENT_GAP * 2;
    private static final long BOTTOM_VALUE_REVEAL_DURATION = 1800;

    private static final int STRENGTH_BAR_COLOR = 0xFFB99A57;
    private static final int FOOD_BAR_COLOR = 0xFFB9824B;
    private static final int ARMOR_BAR_COLOR = 0xFF7F95A1;
    private static final int EXPERIENCE_BAR_COLOR = 0xFF6E9A70;
    private static final int COURAGE_BAR_COLOR = 0xFF8170A7;
    private static final float COURAGE_DANGER_THRESHOLD = 0.25f;
    private static final float INFECTION_DANGER_THRESHOLD = 0.45f;
    private static final ResourceLocation HEALTH_ICON =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/hud_icons/health.png");
    private static final ResourceLocation FOOD_ICON =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/hud_icons/food.png");
    private static final ResourceLocation ARMOR_ICON =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/hud_icons/armor.png");
    private static final ResourceLocation INFECTION_ICON =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/hud_icons/infection.png");
    private static final ResourceLocation STAMINA_ICON =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/hud_icons/stamina.png");
    private static final ResourceLocation COURAGE_ICON =
            new ResourceLocation(DreamingFishCore.MODID, "textures/gui/hud_icons/courage.png");

    // 肢体受伤标记配置（老贴图上的红色脉冲点）
    private static final int INJURY_DOT_COLOR = 0x90D64038;
    private static final int INJURY_DOT_BASE_SIZE = 3;
    private static final int INJURY_DOT_PULSE_SIZE = 2;
    private static final long PULSE_CYCLE = 500;
    private static final int HEAD_OFFSET_X = 13;
    private static final int HEAD_OFFSET_Y = 5;
    private static final int CHEST_OFFSET_X = 13;
    private static final int CHEST_OFFSET_Y = 22;
    private static final int LEGS_OFFSET_X = 13;
    private static final int LEGS_OFFSET_Y = 38;
    private static final int FEET_OFFSET_X = 13;
    private static final int FEET_OFFSET_Y = 52;

    //缓存坐标，优化
    // 缓存上一次的屏幕宽高和GUI缩放（用于判断是否需要重新计算）
    private static int CACHED_SCREEN_WIDTH = 0;
    private static int CACHED_SCREEN_HEIGHT = 0;
    private static double CACHED_GUI_SCALE = 0.0D;
    private static boolean bottomValuesInitialized = false;
    private static long lastBottomValueRevealTime = 0L;
    private static int lastStrengthValue = 0;
    private static int lastMaxStrengthValue = 0;
    private static float lastCourageValue = 0.0f;
    private static float lastMaxCourageValue = 0.0f;
    private static final long STATUS_FLASH_DURATION = 420L;
    private static boolean leftValuesInitialized = false;
    private static float lastLeftHealthValue = 0.0f;
    private static float lastLeftMaxHealthValue = 0.0f;
    private static int lastLeftFoodValue = 0;
    private static int lastLeftArmorValue = 0;
    private static float lastLeftInfectionValue = 0.0f;
    private static long lastHealthFlashTime = 0L;
    private static long lastFoodFlashTime = 0L;
    private static long lastArmorFlashTime = 0L;
    private static long lastInfectionFlashTime = 0L;
    private static long healthRevealStartTime = 0L;
    private static long healthRevealLastChangeTime = 0L;
    private static long foodRevealStartTime = 0L;
    private static long foodRevealLastChangeTime = 0L;
    private static long armorRevealStartTime = 0L;
    private static long armorRevealLastChangeTime = 0L;
    private static long infectionRevealStartTime = 0L;
    private static long infectionRevealLastChangeTime = 0L;

    /**
     * 记录屏幕参数，避免 GUI 缩放变化时沿用旧布局状态
     */
    private static void calculateAndCachePlayerCoords() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getWindow() == null) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        double guiScale = mc.getWindow().getGuiScale();

        //更新参数缓存
        CACHED_SCREEN_WIDTH = screenWidth;
        CACHED_SCREEN_HEIGHT = screenHeight;
        CACHED_GUI_SCALE = guiScale;
    }

    /**
     * 渲染小人图片
     */
    @SubscribeEvent
    public static void renderCustomPlayerIcon(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.screen != null || player.isDeadOrDying()
                || mc.options.hideGui || mc.options.renderDebug
                || mc.gameMode != null && mc.gameMode.getPlayerMode() == GameType.CREATIVE) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        // 获取屏幕缩放后的宽高
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        double currentGuiScale = mc.getWindow().getGuiScale();
        //判断是否需要重新计算坐标
        // 首次渲染 或 屏幕宽高/GUI缩放变化时，重新计算坐标
        if (CACHED_SCREEN_WIDTH != screenWidth
                || CACHED_SCREEN_HEIGHT != screenHeight
                || CACHED_GUI_SCALE != currentGuiScale) {
            calculateAndCachePlayerCoords();
        }

        boolean showEquipmentDurability = hasDamageableArmor(player);
        int modelCenterX = LEFT_OFFSET + PLAYER_MODEL_ANCHOR_HALF_WIDTH;
        if (showEquipmentDurability) {
            modelCenterX += EQUIPMENT_DURABILITY_WIDTH + EQUIPMENT_DURABILITY_GAP;
        }
        int statusBarY = screenHeight - LEFT_STATUS_BOTTOM_OFFSET - STATUS_STACK_HEIGHT;
        int modelFootY = statusBarY + STATUS_STACK_HEIGHT
                + (PLAYER_MODEL_HEIGHT - STATUS_STACK_HEIGHT) / 2;
        int modelTopY = modelFootY - PLAYER_MODEL_HEIGHT;
        int statusBarX = modelCenterX + PLAYER_MODEL_ANCHOR_HALF_WIDTH + STATUS_BAR_TO_PLAYER_SPACING;

        int splitBarX = (screenWidth - SPLIT_EXPERIENCE_BAR_WIDTH) / 2;
        int splitBarY = CustomHotbarGUI.getAnimatedHotbarTopY(screenHeight)
                - SPLIT_EXPERIENCE_HOTBAR_GAP - SPLIT_EXPERIENCE_BAR_HEIGHT;
        int strengthBarX = splitBarX;
        int strengthBarY = splitBarY;
        int experienceBarX = splitBarX + SPLIT_EXPERIENCE_SIDE_WIDTH + SPLIT_EXPERIENCE_SEGMENT_GAP;
        int experienceBarY = splitBarY;
        int courageActionBarX = experienceBarX + SPLIT_EXPERIENCE_CENTER_WIDTH + SPLIT_EXPERIENCE_SEGMENT_GAP;
        int courageActionBarY = splitBarY;

        //获取玩家当前血量和最大血量
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        float healthPercent = maxHealth > 0 ? currentHealth / maxHealth : 0;
        int currentFood = player.getFoodData().getFoodLevel();
        int currentArmor = player.getArmorValue();
        int experienceLevel = player.experienceLevel;
        float experienceProgress = player.experienceProgress;

        // 检查是否在受伤闪烁时间内（闪烁两下）
        long currentTime = System.currentTimeMillis();
        long timeSinceDamage = currentTime - lastDamageTime;
        boolean isFlashing = false;
        if (timeSinceDamage < DAMAGE_FLASH_DURATION) {
            // 闪烁两下：0-100ms 显示，100-200ms 隐藏，200-300ms 显示，300-400ms 隐藏
            int cycle = (int) (timeSinceDamage / FLASH_CYCLE);
            isFlashing = (cycle == 0 || cycle == 2);  // 第1和第3个周期显示白色边框
        }

        int currentStrength = PlayerStrengthClientSync.getCurrentStrengthClient(player);
        int maxStrength = PlayerStrengthClientSync.getMaxStrengthClient(player);
        if (maxStrength <= 0) maxStrength = 100;

        float currentCourage = PlayerCourageManager.getCurrentCourageClient(player);
        float maxCourage = PlayerCourageManager.getMaxCourageClient(player);
        if (maxCourage <= 0) maxCourage = 100; // 避免除以0异常

        float currentInfection = PlayerInfectionManager.getCurrentInfectionClient(player);
        int maxInfection = 100;
        int infectionColor = getInfectionColor((int) currentInfection, maxInfection);
        int playerTintColor = getHealthColor(healthPercent);

        boolean courageDanger = currentCourage / maxCourage <= COURAGE_DANGER_THRESHOLD;
        boolean infectionDanger = currentInfection / maxInfection >= INFECTION_DANGER_THRESHOLD;
        float statusFade = 1.0f;

        if (showEquipmentDurability) {
            drawEquipmentDurability(guiGraphics, player, LEFT_OFFSET, modelTopY, modelFootY);
        }
        drawPlayerGroundShadow(guiGraphics, modelCenterX, modelFootY);
        drawPlayerIcon(guiGraphics, modelCenterX, modelTopY, playerTintColor, isFlashing);
        cleanupAndDrawLimbInjuryIcons(guiGraphics, player, modelCenterX, modelTopY);

        float strengthRatio = currentStrength / (float) maxStrength;
        float courageRatio = currentCourage / maxCourage;

        drawSplitExperienceSegment(guiGraphics, strengthBarX, strengthBarY, SPLIT_EXPERIENCE_SIDE_WIDTH,
                strengthRatio, STRENGTH_BAR_COLOR, false);
        drawSplitExperienceSegment(guiGraphics, experienceBarX, experienceBarY, SPLIT_EXPERIENCE_CENTER_WIDTH,
                experienceProgress, EXPERIENCE_BAR_COLOR, false);
        drawSplitExperienceSegment(guiGraphics, courageActionBarX, courageActionBarY, SPLIT_EXPERIENCE_SIDE_WIDTH,
                courageRatio, COURAGE_BAR_COLOR, courageDanger);
        drawHudIcon(guiGraphics, STAMINA_ICON, strengthBarX - HUD_ICON_SIZE - 4,
                strengthBarY - (HUD_ICON_SIZE - SPLIT_EXPERIENCE_BAR_HEIGHT) / 2, 210);
        drawHudIcon(guiGraphics, COURAGE_ICON, courageActionBarX + SPLIT_EXPERIENCE_SIDE_WIDTH + 4,
                courageActionBarY - (HUD_ICON_SIZE - SPLIT_EXPERIENCE_BAR_HEIGHT) / 2, 210);
        boolean revealSideValues = shouldRevealBottomSideValues(currentTime, currentStrength, maxStrength,
                currentCourage, maxCourage);
        if (revealSideValues) {
            drawSegmentValue(guiGraphics, mc, strengthBarX, strengthBarY, SPLIT_EXPERIENCE_SIDE_WIDTH,
                    currentStrength + "/" + maxStrength, STRENGTH_BAR_COLOR);
            drawSegmentValue(guiGraphics, mc, courageActionBarX, courageActionBarY, SPLIT_EXPERIENCE_SIDE_WIDTH,
                    Math.round(currentCourage) + "/" + Math.round(maxCourage), COURAGE_BAR_COLOR);
        }
        if (experienceLevel > 0) {
            drawSegmentValue(guiGraphics, mc, experienceBarX, experienceBarY, SPLIT_EXPERIENCE_CENTER_WIDTH,
                    Integer.toString(experienceLevel), EXPERIENCE_BAR_COLOR);
        }

        updateLeftStatusFlashTimes(currentTime, currentHealth, maxHealth, currentFood, currentArmor, currentInfection);
        drawStatusBars(guiGraphics, mc, statusBarX, statusBarY, healthPercent, currentHealth, maxHealth,
                currentFood / 20.0f, currentFood, currentArmor / 20.0f, currentArmor,
                currentInfection / maxInfection, infectionColor, currentInfection, statusFade, infectionDanger,
                currentTime);
    }

    /**
     * 根据血量百分比计算颜色（分段线性插值）
     * 确保关键血量点的颜色准确
     *
     * 100% → 纯绿色 (0, 255, 0)
     * 75% → 黄绿色 (128, 255, 0)
     * 50% → 纯黄色 (255, 255, 0)
     * 25% → 橙色 (255, 128, 0)
     * 0% → 纯红色 (255, 0, 0)
     *
     * @param percent 血量百分比 (0.0 ~ 1.0)
     * @return ARGB 颜色值
     */
    private static int getHealthColor(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));

        int r, g;
        float value = 1.0f;

        // 低血量时降低明度使其偏黑
        if (percent < 0.25f) {
            value = 0.6f + (percent / 0.25f) * 0.4f;
        }

        if (percent > 0.5f) {
            // 50% ~ 100%：黄色 → 绿色
            // 黄色(255, 255, 0) → 绿色(0, 255, 0)
            float t = (percent - 0.5f) * 2.0f;  // 0 ~ 1
            r = (int) ((1.0f - t) * 255 * value);  // 255 → 0
            g = (int) (255 * value);               // 始终255
        } else {
            // 0% ~ 50%：红色 → 黄色（包含50%）
            // 红色(255, 0, 0) → 黄色(255, 255, 0)
            float t = percent * 2.0f;  // 0 ~ 1
            r = (int) (255 * value);   // 始终255
            g = (int) (t * 255 * value);  // 0 → 255
        }

        int ri = Math.max(0, Math.min(255, r));
        int gi = Math.max(0, Math.min(255, g));

        int rawColor = (255 << 24) | (ri << 16) | (gi << 8);
        return blendColor(rawColor, 0xFF8D8F86, 0.28f);
    }

    private static boolean shouldRevealBottomSideValues(long currentTime, int currentStrength, int maxStrength,
                                                        float currentCourage, float maxCourage) {
        if (!bottomValuesInitialized) {
            bottomValuesInitialized = true;
            cacheBottomSideValues(currentStrength, maxStrength, currentCourage, maxCourage);
            return false;
        }

        boolean changed = currentStrength != lastStrengthValue
                || maxStrength != lastMaxStrengthValue
                || Math.abs(currentCourage - lastCourageValue) > 0.5f
                || Math.abs(maxCourage - lastMaxCourageValue) > 0.5f;

        if (changed) {
            lastBottomValueRevealTime = currentTime;
            cacheBottomSideValues(currentStrength, maxStrength, currentCourage, maxCourage);
        }

        return currentTime - lastBottomValueRevealTime <= BOTTOM_VALUE_REVEAL_DURATION;
    }

    private static void cacheBottomSideValues(int currentStrength, int maxStrength,
                                              float currentCourage, float maxCourage) {
        lastStrengthValue = currentStrength;
        lastMaxStrengthValue = maxStrength;
        lastCourageValue = currentCourage;
        lastMaxCourageValue = maxCourage;
    }

    private static void updateLeftStatusFlashTimes(long currentTime, float currentHealth, float maxHealth,
                                                   int currentFood, int currentArmor, float currentInfection) {
        if (!leftValuesInitialized) {
            leftValuesInitialized = true;
            cacheLeftStatusValues(currentHealth, maxHealth, currentFood, currentArmor, currentInfection);
            return;
        }

        if (Math.abs(currentHealth - lastLeftHealthValue) > 0.05f
                || Math.abs(maxHealth - lastLeftMaxHealthValue) > 0.05f) {
            lastHealthFlashTime = currentTime;
            if (!isStatusBarRevealVisible(currentTime, healthRevealStartTime, healthRevealLastChangeTime)) {
                healthRevealStartTime = currentTime;
            }
            healthRevealLastChangeTime = currentTime;
        }
        if (currentFood != lastLeftFoodValue) {
            lastFoodFlashTime = currentTime;
            if (!isStatusBarRevealVisible(currentTime, foodRevealStartTime, foodRevealLastChangeTime)) {
                foodRevealStartTime = currentTime;
            }
            foodRevealLastChangeTime = currentTime;
        }
        if (currentArmor != lastLeftArmorValue) {
            lastArmorFlashTime = currentTime;
            if (!isStatusBarRevealVisible(currentTime, armorRevealStartTime, armorRevealLastChangeTime)) {
                armorRevealStartTime = currentTime;
            }
            armorRevealLastChangeTime = currentTime;
        }
        if (Math.abs(currentInfection - lastLeftInfectionValue) > 0.5f) {
            lastInfectionFlashTime = currentTime;
            if (!isStatusBarRevealVisible(currentTime, infectionRevealStartTime, infectionRevealLastChangeTime)) {
                infectionRevealStartTime = currentTime;
            }
            infectionRevealLastChangeTime = currentTime;
        }

        cacheLeftStatusValues(currentHealth, maxHealth, currentFood, currentArmor, currentInfection);
    }

    private static void cacheLeftStatusValues(float currentHealth, float maxHealth,
                                              int currentFood, int currentArmor, float currentInfection) {
        lastLeftHealthValue = currentHealth;
        lastLeftMaxHealthValue = maxHealth;
        lastLeftFoodValue = currentFood;
        lastLeftArmorValue = currentArmor;
        lastLeftInfectionValue = currentInfection;
    }

    private static int getInfectionColor(int currentInfection, int maxInfection) {
        float t = (maxInfection <= 0) ? 0.0f : (float) currentInfection / maxInfection;
        t = Math.max(0.0f, Math.min(1.0f, t));
        float factor = t * t;

        int r = (int) (187 * (1.0f - factor));
        int g = (int) (255 - (255 - 51) * factor);
        int b = (int) (187 * (1.0f - factor));

        return (255 << 24) | (r << 16) | (g << 8) | b;
    }

    private static boolean hasDamageableArmor(Player player) {
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                return true;
            }
        }
        return false;
    }

    private static void drawEquipmentDurability(GuiGraphics guiGraphics, Player player,
                                                int x, int modelTopY, int modelFootY) {
        int[] armorOrder = {3, 2, 1, 0};
        int barTop = modelTopY + EQUIPMENT_DURABILITY_VERTICAL_PADDING;
        int totalHeight = Math.max(16, modelFootY - modelTopY - EQUIPMENT_DURABILITY_VERTICAL_PADDING * 2);
        int barHeight = Math.max(5, (totalHeight - EQUIPMENT_DURABILITY_BAR_GAP * 3) / 4);

        for (int i = 0; i < armorOrder.length; i++) {
            int armorSlot = armorOrder[i];
            ItemStack stack = player.getInventory().armor.get(armorSlot);
            if (stack.isEmpty() || !stack.isDamageableItem() || stack.getMaxDamage() <= 0) {
                continue;
            }

            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            float ratio = remaining / (float) stack.getMaxDamage();
            int color = getDurabilityColor(ratio);
            int barY = barTop + i * (barHeight + EQUIPMENT_DURABILITY_BAR_GAP);
            int iconY = barY + (barHeight - EQUIPMENT_ICON_SIZE) / 2;
            int barX = x + EQUIPMENT_ICON_SIZE + EQUIPMENT_ICON_TO_BAR_GAP;

            drawScaledItemIcon(guiGraphics, stack, x, iconY, EQUIPMENT_ICON_SIZE);
            drawVerticalDurabilityBar(guiGraphics, barX, barY,
                    EQUIPMENT_DURABILITY_BAR_WIDTH, barHeight, ratio, color);
        }
    }

    private static void drawScaledItemIcon(GuiGraphics guiGraphics, ItemStack stack, int x, int y, int size) {
        if (stack.isEmpty() || size <= 0) {
            return;
        }

        float scale = size / 16.0f;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.renderItem(stack, Math.round(x / scale), Math.round(y / scale));
        guiGraphics.pose().popPose();
    }

    private static int getDurabilityColor(float ratio) {
        float t = Math.max(0.0f, Math.min(1.0f, ratio));
        int low = 0xFFC65E58;
        int mid = 0xFFD0B35F;
        int high = 0xFF78A96D;

        if (t < 0.5f) {
            return blendColor(low, mid, t * 2.0f);
        }

        return blendColor(mid, high, (t - 0.5f) * 2.0f);
    }

    private static void drawVerticalDurabilityBar(GuiGraphics guiGraphics, int x, int y, int width, int height,
                                                  float ratio, int color) {
        float progress = Math.max(0.0f, Math.min(1.0f, ratio));
        int fillHeight = Math.max(1, Math.round((height - 2) * progress));
        int fillTop = y + height - 1 - fillHeight;

        drawPixelCutRect(guiGraphics, x, y, width, height, withAlpha(TRACK_COLOR, 128));
        drawPixelCutRect(guiGraphics, x + 1, y + 1, width - 2, height - 2, withAlpha(TRACK_COLOR, 70));
        drawPixelCutRect(guiGraphics, x + 1, fillTop, width - 2, fillHeight, withAlpha(color, 184));
        if (fillHeight > 2) {
            guiGraphics.fill(x + 1, fillTop, x + width - 1, fillTop + 1,
                    withAlpha(blendColor(color, 0xFFFFFFFF, 0.22f), 166));
        }
    }

    private static void drawScaledText(GuiGraphics guiGraphics, Minecraft mc, String text, int x, int y,
                                       int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        guiGraphics.drawString(mc.font, text, scaledX + 1, scaledY + 1, 0x65000000, false);
        guiGraphics.drawString(mc.font, text, scaledX, scaledY, color, false);
        guiGraphics.pose().popPose();
    }

    private static void drawPlayerIcon(GuiGraphics guiGraphics, int centerX, int topY,
                                       int healthColor, boolean flashing) {
        int iconX = centerX - PLAYER_ICON_WIDTH / 2;
        int uvX = flashing ? PLAYER_UV_X_INJURED : PLAYER_UV_X_NORMAL;

        guiGraphics.setColor(
                (healthColor >> 16 & 0xFF) / 255.0f,
                (healthColor >> 8 & 0xFF) / 255.0f,
                (healthColor & 0xFF) / 255.0f,
                1.0F
        );
        guiGraphics.blit(
                PLAYER_HEALTH_TEXTURE,
                iconX,
                topY,
                uvX,
                PLAYER_UV_Y,
                PLAYER_ICON_WIDTH,
                PLAYER_MODEL_HEIGHT,
                PLAYER_TEXTURE_TOTAL_WIDTH,
                PLAYER_TEXTURE_TOTAL_HEIGHT
        );
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void cleanupAndDrawLimbInjuryIcons(GuiGraphics guiGraphics, Player player,
                                                      int centerX, int topY) {
        LimbClientInjurySync.cleanupExpiredInjuries(player);

        int iconX = centerX - PLAYER_ICON_WIDTH / 2;
        for (LimbType limbType : LimbType.values()) {
            if (LimbClientInjurySync.isInjuryVisible(player, limbType)) {
                drawLimbInjuryIcon(guiGraphics, iconX, topY, limbType);
            }
        }
    }

    private static void drawLimbInjuryIcon(GuiGraphics guiGraphics, int playerIconX, int playerIconY,
                                           LimbType limbType) {
        int offsetX;
        int offsetY;
        switch (limbType) {
            case HEAD:
                offsetX = HEAD_OFFSET_X;
                offsetY = HEAD_OFFSET_Y;
                break;
            case CHEST:
                offsetX = CHEST_OFFSET_X;
                offsetY = CHEST_OFFSET_Y;
                break;
            case LEGS:
                offsetX = LEGS_OFFSET_X;
                offsetY = LEGS_OFFSET_Y;
                break;
            case FEET:
                offsetX = FEET_OFFSET_X;
                offsetY = FEET_OFFSET_Y;
                break;
            default:
                return;
        }

        long time = System.currentTimeMillis();
        float pulsePhase = (time % PULSE_CYCLE) / (float) PULSE_CYCLE;
        float pulseFactor = (float) Math.sin(pulsePhase * Math.PI);
        int currentSize = INJURY_DOT_BASE_SIZE + (int) (pulseFactor * INJURY_DOT_PULSE_SIZE);
        int halfSize = currentSize / 2;
        int centerX = playerIconX + offsetX;
        int centerY = playerIconY + offsetY;

        guiGraphics.fill(centerX - halfSize, centerY - halfSize,
                centerX + halfSize, centerY + halfSize, INJURY_DOT_COLOR);
    }

    private static void drawPlayerGroundShadow(GuiGraphics guiGraphics, int centerX, int footY) {
        int y = footY - 2;
        guiGraphics.fill(centerX - 13, y, centerX + 13, y + 1, 0x2A000000);
        guiGraphics.fill(centerX - 10, y - 1, centerX + 10, y, 0x20000000);
        guiGraphics.fill(centerX - 6, y - 2, centerX + 6, y - 1, 0x16000000);
    }

    private static void drawSplitExperienceSegment(GuiGraphics guiGraphics, int x, int y, int width, float ratio,
                                                   int fillColor, boolean warning) {
        float progress = Math.max(0.0f, Math.min(1.0f, ratio));
        int innerWidth = Math.max(0, width - 2);
        int fillWidth = (int) (innerWidth * progress);
        int activeColor = warning ? blendColor(fillColor, LOW_COLOR, 0.36f) : fillColor;

        drawPixelCutRect(guiGraphics, x + 1, y + 1, width, SPLIT_EXPERIENCE_BAR_HEIGHT, 0x26000000);
        drawPixelCutRect(guiGraphics, x, y, width, SPLIT_EXPERIENCE_BAR_HEIGHT, withAlpha(TRACK_COLOR, 168));
        drawPixelCutRect(guiGraphics, x + 1, y + 1, width - 2, SPLIT_EXPERIENCE_BAR_HEIGHT - 2,
                withAlpha(TRACK_COLOR, 88));
        if (fillWidth > 0) {
            int fillEnd = x + 1 + Math.min(innerWidth, Math.max(1, fillWidth));
            drawCleanGlow(guiGraphics, x, y, fillEnd, y + SPLIT_EXPERIENCE_BAR_HEIGHT, activeColor, warning ? 48 : 26);
            drawPixelCutRect(guiGraphics, x + 1, y + 1, fillEnd - (x + 1), SPLIT_EXPERIENCE_BAR_HEIGHT - 2,
                    withAlpha(activeColor, warning ? 222 : 196));
            if (fillEnd > x + 3) {
                guiGraphics.fill(x + 2, y + 1, fillEnd - 1, y + 2,
                        withAlpha(blendColor(activeColor, 0xFFFFFFFF, 0.18f), warning ? 232 : 176));
            }
        }
    }

    private static void drawSegmentValue(GuiGraphics guiGraphics, Minecraft mc, int x, int y, int width,
                                         String text, int color) {
        if (text == null || text.isEmpty()) {
            return;
        }

        float scale = 0.66f;
        int textWidth = (int) (mc.font.width(text) * scale);
        int textX = x + (width - textWidth) / 2;
        int textY = y - 8;
        int textColor = withAlpha(blendColor(color, 0xFFFFFFFF, 0.34f), 214);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledX = Math.round(textX / scale);
        int scaledY = Math.round(textY / scale);
        guiGraphics.drawString(mc.font, text, scaledX + 1, scaledY + 1, 0x70000000, false);
        guiGraphics.drawString(mc.font, text, scaledX, scaledY, textColor, false);
        guiGraphics.pose().popPose();
    }

    private static void drawStatusBars(GuiGraphics guiGraphics, Minecraft mc, int x, int y,
                                       float healthRatio, float currentHealth, float maxHealth,
                                       float foodRatio, int currentFood,
                                       float armorRatio, int currentArmor,
                                       float infectionRatio, int infectionColor, float currentInfection,
                                       float fade, boolean infectionDanger,
                                       long currentTime) {
        int healthColor = getHealthColor(healthRatio);
        boolean healthDanger = healthRatio <= 0.25f;
        boolean foodDanger = foodRatio <= 0.25f;
        drawStatusBar(guiGraphics, mc, x, y, healthRatio, healthColor, healthDanger, fade,
                STATUS_SIGNAL_WIDTH, HEALTH_ICON, formatStatNumber(currentHealth) + "/" + formatStatNumber(maxHealth),
                currentTime, lastHealthFlashTime, healthRevealStartTime, healthRevealLastChangeTime);
        drawStatusBar(guiGraphics, mc, x, y + statusBarStep(1), foodRatio, FOOD_BAR_COLOR, foodDanger, fade,
                STATUS_SIGNAL_WIDTH, FOOD_ICON, currentFood + "/20", currentTime, lastFoodFlashTime,
                foodRevealStartTime, foodRevealLastChangeTime);
        drawStatusBar(guiGraphics, mc, x, y + statusBarStep(2), armorRatio, ARMOR_BAR_COLOR, false, fade,
                STATUS_SIGNAL_WIDTH, ARMOR_ICON, Integer.toString(currentArmor), currentTime, lastArmorFlashTime,
                armorRevealStartTime, armorRevealLastChangeTime);
        drawStatusBar(guiGraphics, mc, x, y + statusBarStep(3), infectionRatio, infectionColor, infectionDanger, fade,
                STATUS_SIGNAL_WIDTH, INFECTION_ICON, Math.round(currentInfection) + "%", currentTime,
                lastInfectionFlashTime, infectionRevealStartTime, infectionRevealLastChangeTime);
    }

    private static int statusBarStep(int index) {
        return index * (STATUS_BAR_HEIGHT + STATUS_BAR_SPACING);
    }

    private static void drawStatusBar(GuiGraphics guiGraphics, Minecraft mc, int x, int y, float ratio, int normalColor,
                                      boolean warning, float fade, int signalWidth, ResourceLocation icon,
                                      String valueText, long currentTime, long flashTime, long revealStartTime,
                                      long revealLastChangeTime) {
        float progress = Math.max(0.0f, Math.min(1.0f, ratio));
        float expandProgress = getStatusBarExpandProgress(currentTime, revealStartTime, revealLastChangeTime);
        float warningPulse = getWarningPulse(currentTime, warning);
        int flashAlpha = getStatusFlashAlpha(currentTime, flashTime);
        int trackAlpha = (int) (108 + 60 * fade);
        int fillAlpha = Math.min(255, (int) (((warning ? 210 : 188) + warningPulse * 38) * fade));
        int activeColor = warning ? blendColor(normalColor, LOW_COLOR, 0.26f + warningPulse * 0.22f) : normalColor;
        int iconY = y - (HUD_ICON_SIZE - STATUS_BAR_HEIGHT) / 2;
        int animatedSignalWidth = Math.round(signalWidth * expandProgress);
        int iconX = x + Math.round((signalWidth + STATUS_ICON_TO_BAR_SPACING) * expandProgress);
        int valueX = iconX + HUD_ICON_SIZE + 3;

        if (animatedSignalWidth > 0) {
            drawStatusSegment(guiGraphics, x, y, animatedSignalWidth, progress, activeColor, trackAlpha, fillAlpha,
                    warning, warningPulse, flashAlpha);
        }
        int iconAlpha = Math.min(255, (int) (((warning ? 218 : 196) + warningPulse * 37 + flashAlpha / 3) * fade));
        drawHudIcon(guiGraphics, icon, iconX, iconY, iconAlpha);
        drawStatusValue(guiGraphics, mc, valueX, y - 1, valueText, activeColor, warning,
                Math.min(1.0f, fade * (0.72f + warningPulse * 0.22f + flashAlpha / 420.0f)));
    }

    private static boolean isStatusBarRevealVisible(long currentTime, long revealStartTime, long revealLastChangeTime) {
        return getStatusBarExpandProgress(currentTime, revealStartTime, revealLastChangeTime) > 0.0f;
    }

    private static float getStatusBarExpandProgress(long currentTime, long revealStartTime,
                                                    long revealLastChangeTime) {
        if (revealStartTime <= 0L || revealLastChangeTime <= 0L) {
            return 0.0f;
        }

        long elapsed = currentTime - revealStartTime;
        if (elapsed < 0L) {
            return 0.0f;
        }

        if (elapsed < STATUS_BAR_ANIMATION_DURATION) {
            return easeOutCubic(elapsed / (float) STATUS_BAR_ANIMATION_DURATION);
        }

        long idleElapsed = currentTime - revealLastChangeTime;
        if (idleElapsed <= STATUS_BAR_REVEAL_DURATION) {
            return 1.0f;
        }

        long collapseElapsed = idleElapsed - STATUS_BAR_REVEAL_DURATION;
        if (collapseElapsed < STATUS_BAR_ANIMATION_DURATION) {
            return 1.0f - easeOutCubic(collapseElapsed / (float) STATUS_BAR_ANIMATION_DURATION);
        }

        return 0.0f;
    }

    private static float easeOutCubic(float value) {
        float t = Math.max(0.0f, Math.min(1.0f, value));
        return 1.0f - (float) Math.pow(1.0f - t, 3.0D);
    }

    private static void drawStatusValue(GuiGraphics guiGraphics, Minecraft mc, int x, int y, String text,
                                        int color, boolean warning, float fade) {
        if (text == null || text.isEmpty()) {
            return;
        }

        float scale = 0.62f;
        int textColor = withAlpha(blendColor(color, 0xFFFFFFFF, warning ? 0.38f : 0.24f),
                (int) ((warning ? 232 : 176) * fade));

        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        int scaledX = Math.round(x / scale);
        int scaledY = Math.round(y / scale);
        guiGraphics.drawString(mc.font, text, scaledX + 1, scaledY + 1, 0x62000000, false);
        guiGraphics.drawString(mc.font, text, scaledX, scaledY, textColor, false);
        guiGraphics.pose().popPose();
    }

    private static String formatStatNumber(float value) {
        int rounded = Math.round(value);
        if (Math.abs(value - rounded) < 0.05f) {
            return Integer.toString(rounded);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static void drawHudIcon(GuiGraphics guiGraphics, ResourceLocation icon, int x, int y, int alpha) {
        if (alpha <= 0) {
            return;
        }

        guiGraphics.setColor(1.0F, 1.0F, 1.0F, Math.max(0, Math.min(255, alpha)) / 255.0F);
        guiGraphics.blit(icon, x, y, HUD_ICON_SIZE, HUD_ICON_SIZE, 0.0F, 0.0F,
                HUD_ICON_TEXTURE_SIZE, HUD_ICON_TEXTURE_SIZE,
                HUD_ICON_TEXTURE_SIZE, HUD_ICON_TEXTURE_SIZE);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void drawStatusSegment(GuiGraphics guiGraphics, int x, int y, int width, float progress,
                                          int fillColor, int trackAlpha, int fillAlpha, boolean warning,
                                          float warningPulse, int flashAlpha) {
        int innerWidth = Math.max(0, width - 2);
        int filledUntil = (int) (innerWidth * progress);
        int trackColor = withAlpha(TRACK_COLOR, trackAlpha);
        int trackInsetColor = withAlpha(TRACK_COLOR, trackAlpha / 2);
        int activeColor = withAlpha(fillColor, fillAlpha);
        int warningColor = withAlpha(blendColor(fillColor, 0xFFFFFFFF, 0.32f),
                warning ? (int) (102 + warningPulse * 118) : 0);

        drawPixelCutRect(guiGraphics, x + 1, y + 1, width, STATUS_BAR_HEIGHT, 0x2B000000);
        drawPixelCutRect(guiGraphics, x, y, width, STATUS_BAR_HEIGHT, trackColor);
        drawPixelCutRect(guiGraphics, x + 1, y + 1, width - 2, STATUS_BAR_HEIGHT - 2, trackInsetColor);
        if (filledUntil > 0) {
            int fillEnd = x + 1 + Math.min(innerWidth, Math.max(1, filledUntil));
            int fillWidth = fillEnd - (x + 1);
            drawPixelCutRect(guiGraphics, x + 1, y + 1, fillWidth, STATUS_BAR_HEIGHT - 2, activeColor);
            if (fillEnd > x + 3) {
                guiGraphics.fill(x + 2, y + 1, fillEnd - 1, y + 2,
                        withAlpha(blendColor(fillColor, 0xFFFFFFFF, 0.22f), Math.max(0, fillAlpha - 18)));
                guiGraphics.fill(x + 2, y + STATUS_BAR_HEIGHT - 2, fillEnd - 1, y + STATUS_BAR_HEIGHT - 1,
                        withAlpha(0xFF000000, 44));
            }
            if (warning) {
                guiGraphics.fill(x + 1, y - 1, fillEnd, y, warningColor);
            }
        }

        if (flashAlpha > 0) {
            int flashColor = withAlpha(0xFFFFFFFF, flashAlpha);
            int flashWidth = Math.max(2, (int) ((width - 2) * Math.max(0.12f, progress)));
            drawPixelCutRect(guiGraphics, x + 1, y + 1, flashWidth, STATUS_BAR_HEIGHT - 2, flashColor);
            guiGraphics.fill(x + 1, y - 1, x + width - 1, y, withAlpha(0xFFFFFFFF, flashAlpha / 2));
        }
    }

    private static float getWarningPulse(long currentTime, boolean warning) {
        if (!warning) {
            return 0.0f;
        }

        float phase = (currentTime % 720L) / 720.0f;
        return 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0f);
    }

    private static int getStatusFlashAlpha(long currentTime, long flashTime) {
        if (flashTime <= 0L) {
            return 0;
        }

        long elapsed = currentTime - flashTime;
        if (elapsed < 0L || elapsed > STATUS_FLASH_DURATION) {
            return 0;
        }

        float t = 1.0f - elapsed / (float) STATUS_FLASH_DURATION;
        return (int) (172 * t * t);
    }

    private static void drawPixelCutRect(GuiGraphics guiGraphics, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) {
            return;
        }

        if (width <= 2 || height <= 2) {
            guiGraphics.fill(x, y, x + width, y + height, color);
            return;
        }

        guiGraphics.fill(x + 1, y, x + width - 1, y + height, color);
        guiGraphics.fill(x, y + 1, x + width, y + height - 1, color);
    }

    private static void drawCleanGlow(GuiGraphics guiGraphics, int x1, int y1, int x2, int y2, int color, int alpha) {
        int glow = withAlpha(color, alpha);
        guiGraphics.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, withAlpha(color, alpha / 3));
        guiGraphics.fill(x1, y1 - 1, x2, y1, glow);
        guiGraphics.fill(x1, y2, x2, y2 + 1, glow);
        guiGraphics.fill(x1 - 1, y1, x1, y2, withAlpha(color, alpha / 2));
        guiGraphics.fill(x2, y1, x2 + 1, y2, withAlpha(color, alpha / 2));
    }

    private static int withAlpha(int color, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (color & 0x00FFFFFF);
    }

    private static int blendColor(int from, int to, float ratio) {
        float t = Math.max(0.0f, Math.min(1.0f, ratio));
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = (int) (fr + (tr - fr) * t);
        int g = (int) (fg + (tg - fg) * t);
        int b = (int) (fb + (tb - fb) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    //拦截原版UI：在UI渲染前取消原版血量和饱食度的渲染事件
    @SubscribeEvent
    public static void interceptVanillaHealthAndFoodUI(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        //拦截原版玩家血量UI
        if (event.getOverlay().id().equals(VanillaGuiOverlay.PLAYER_HEALTH.id())) {
            event.setCanceled(true);
        }
    }

    /**
     * 客户端tick事件 - 检测血量变化，触发受伤闪烁
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }

        Player player = mc.player;
        float currentHealth = player.getHealth();

        // 检测血量下降（受伤）
        if (currentHealth < lastHealth) {
            lastDamageTime = System.currentTimeMillis();
        }

        lastHealth = currentHealth;
    }

}
