package com.hhy.dreamingfishcore.item;

import net.minecraft.core.registries.BuiltInRegistries;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.items.*;
import com.hhy.dreamingfishcore.item.items.medicine.Easy_Aid_Kit;
import com.hhy.dreamingfishcore.item.items.Potion_RestoreUnInfected;
import com.hhy.dreamingfishcore.item.items.Item_RevivalCharm;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.crafting.Ingredient;

public class DreamingFishCore_Items {

    // ArmorMaterial 在 1.21.1 仍是注册表对象；面具使用零护甲值，避免把“防感染”意外变成额外
    // 的战斗护甲，同时保留原版头部装备、右键穿戴和玩家装备渲染行为。
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, DreamingFishCore.MODID);

    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> PROTECTIVE_MASK_MATERIAL =
            ARMOR_MATERIALS.register("protective_mask", () -> new ArmorMaterial(
                    zeroDefenseValues(),
                    0,
                    SoundEvents.ARMOR_EQUIP_GENERIC,
                    () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "protective_mask"))),
                    0.0F,
                    0.0F
            ));

    // 创建物品的 DeferredRegister
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, DreamingFishCore.MODID);

    // 注册吉他物品
    public static final DeferredHolder<Item, ? extends Item> GUITAR = ITEMS.register("guitar",
            () -> new Item_Guitar(new Item.Properties()
                    .stacksTo(1) // 堆叠限制为 1
                    .fireResistant() // 可选，防火
            ));

    // 注册启程锦鲤
    public static final DeferredHolder<Item, ? extends Item> DREAMINGFISH = ITEMS.register(
            "dreamingfish",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON))  //金色品质
            {
                // 加自定义描述tooltips
                @Override
                public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context,
                                            List<Component> tooltip, TooltipFlag flag) {
                    super.appendHoverText(stack, context, tooltip, flag);
                    // 读语言文件里的tooltip
                    tooltip.add(Component.translatable("item.dreamingfishcore.dreamingfish.tooltip"));
                }
            }
    );

    // 自定义蓝图物品（可选，如果不想用地图）
    public static final DeferredHolder<Item, ? extends Item> BLUEPRINT_ITEM = ITEMS.register("blueprint",
            () -> new Item_Blueprint(new Item.Properties()
                    .stacksTo(1)  // 蓝图只能堆叠1个
                    .fireResistant()  // 防火（重要物品）
            ));

    public static final DeferredHolder<Item, ? extends Item> FRAGMENT_PAGE = ITEMS.register("fragment_page",
            () -> new Item_FragmentPage(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)
            ));

    public static final DeferredHolder<Item, ? extends Item> STORY_BOOK = ITEMS.register("story_book",
            () -> new Item_StoryBook(new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.RARE)
            ));

    // 空白蓝图（用于制作特定蓝图）
    public static final DeferredHolder<Item, ? extends Item> BLANK_BLUEPRINT = ITEMS.register("blank_blueprint",
            () -> new Item(new Item.Properties()
                    .stacksTo(64)
            ));

    //药品注册————————————————————————————————————————————————————————————————————————
    // 简易急救包（初级）
    public static final DeferredHolder<Item, ? extends Item> EASY_AID_KIT = ITEMS.register("easy_aid_kit",
            () -> new Item_AidKit(
                    20,      // healInterval: 20刻 = 1秒
                    1.0,     // perHealAmount: 每次治疗1点生命值
                    20,      // durabilityConsumeInterval: 每20刻消耗1耐久
                    1000,    // cooldown: 1000刻 = 50秒冷却
                    100,     // startDelay: 100刻 = 5秒启动延迟
                    "简易急救包" // displayName: 显示名称
            )
    );

    // 高级急救包（中级）
    public static final DeferredHolder<Item, ? extends Item> ADVANCED_AID_KIT = ITEMS.register("advanced_aid_kit",
            () -> new Item_AidKit(
                    15,      // 更短的治疗间隔
                    2.0,     // 每次治疗2点生命值
                    30,      // 更耐用的耐久消耗间隔
                    800,     // 更短的冷却时间
                    80,      // 更短的启动延迟
                    "高级急救包"
            )
    );

    // 专业急救包（高级）
    public static final DeferredHolder<Item, ? extends Item> PROFESSIONAL_AID_KIT = ITEMS.register("professional_aid_kit",
            () -> new Item_AidKit(
                    10,      // 非常短的治疗间隔
                    3.0,     // 每次治疗3点生命值
                    40,      // 非常耐用的耐久消耗间隔
                    600,     // 很短的冷却时间
                    60,      // 很短的启动延迟
                    "专业急救包"
            )
    );

    // 复活护符
    public static final DeferredHolder<Item, ? extends Item> REVIVAL_CHARM = ITEMS.register("revival_charm",
            () -> new Item_RevivalCharm(new Item.Properties()
                    .stacksTo(1)  // 只能堆叠1个
                    .rarity(Rarity.RARE)  // 稀有品质
            ));

    // 基因复苏药剂
    public static final DeferredHolder<Item, ? extends Item> GENE_RESURGENCE_POTION = ITEMS.register("restore_uninfected_potion",
            () -> new Potion_RestoreUnInfected(new Item.Properties()
                    .stacksTo(1)  // 只能堆叠1个
                    .rarity(Rarity.EPIC)  // 史诗品质
            ));

    // 防护面具：可佩戴在头部；防护效果由感染系统根据佩戴状态判定。
    public static final DeferredHolder<Item, ProtectiveMaskItem> PROTECTIVE_MASK = ITEMS.register("protective_mask",
            () -> new ProtectiveMaskItem(PROTECTIVE_MASK_MATERIAL, new Item.Properties()
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)  // 史诗品质
            ));

    // 聚居地过滤装置：方块物品，绑定组织后在领地内阻断被动接触暴露（ADR 0017）。
    public static final DeferredHolder<Item, BlockItem> SETTLEMENT_FILTER = ITEMS.register("settlement_filter",
            () -> new BlockItem(
                    com.hhy.dreamingfishcore.block.DreamingFishCore_Blocks.SETTLEMENT_FILTER.get(),
                    new Item.Properties().rarity(Rarity.RARE)));

    private static Map<ArmorItem.Type, Integer> zeroDefenseValues() {
        EnumMap<ArmorItem.Type, Integer> values = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type type : ArmorItem.Type.values()) {
            values.put(type, 0);
        }
        return values;
    }

    public static void register(IEventBus eventBus) {
        ARMOR_MATERIALS.register(eventBus);
        ITEMS.register(eventBus); // 注册物品
    }
}
