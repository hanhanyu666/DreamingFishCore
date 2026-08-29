package com.hhy.dreamingfishcore.datagen;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.item.DreamingFishCore_Items;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.armortrim.TrimMaterial;
import net.minecraft.world.item.armortrim.TrimMaterials;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.LinkedHashMap;

public class ModItemModelProvider extends ItemModelProvider {
    private static LinkedHashMap<ResourceKey<TrimMaterial>, Float> trimMaterials = new LinkedHashMap<>();
    static {
        trimMaterials.put(TrimMaterials.QUARTZ, 0.1f);
        trimMaterials.put(TrimMaterials.IRON, 0.1f);
        trimMaterials.put(TrimMaterials.NETHERITE, 0.1f);
        trimMaterials.put(TrimMaterials.REDSTONE, 0.1f);
        trimMaterials.put(TrimMaterials.COPPER, 0.1f);
        trimMaterials.put(TrimMaterials.GOLD, 0.1f);
        trimMaterials.put(TrimMaterials.EMERALD, 0.1f);
        trimMaterials.put(TrimMaterials.DIAMOND, 0.1f);
        trimMaterials.put(TrimMaterials.LAPIS, 0.1f);
        trimMaterials.put(TrimMaterials.AMETHYST, 0.1f);
    }

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, DreamingFishCore.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        simpleItem(DreamingFishCore_Items.GUITAR);
        simpleItem(DreamingFishCore_Items.BLUEPRINT_ITEM);
        withExistingParent(DreamingFishCore_Items.BLANK_BLUEPRINT.getId().getPath(), mcLoc("item/generated"))
                .texture("layer0", modLoc("item/blueprint"));
        simpleItem(DreamingFishCore_Items.FRAGMENT_PAGE);
        simpleItem(DreamingFishCore_Items.STORY_BOOK);
        simpleItem(DreamingFishCore_Items.EASY_AID_KIT);
        simpleItem(DreamingFishCore_Items.ADVANCED_AID_KIT);
        simpleItem(DreamingFishCore_Items.PROFESSIONAL_AID_KIT);
        simpleItem(DreamingFishCore_Items.DREAMINGFISH);
        // 重生锦鲤使用原版不死图腾纹理
        simpleItem(DreamingFishCore_Items.REVIVAL_CHARM);
        // 基因复苏药剂
        simpleItem(DreamingFishCore_Items.GENE_RESURGENCE_POTION);
        withExistingParent("siege_zombie_spawn_egg", mcLoc("item/template_spawn_egg"));
    }

    private ItemModelBuilder simpleItem(DeferredHolder<Item, ? extends Item> itemRegistryObject) {
        return withExistingParent(itemRegistryObject.getId().getPath(),
                mcLoc("item/generated")).texture("layer0",
                ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "item/" + itemRegistryObject.getId().getPath()));
    }

    private ItemModelBuilder simpleTool(DeferredHolder<Item, ? extends Item> itemRegistryObject) {
        return withExistingParent(itemRegistryObject.getId().getPath(),
                mcLoc("item/handheld")).texture("layer0",
                ResourceLocation.fromNamespaceAndPath(DreamingFishCore.MODID, "item/" + itemRegistryObject.getId().getPath()));
    }

    private void trimmedArmorItem(DeferredHolder<Item, ? extends Item> itemRegistryObject) {
        final String MOD_ID = DreamingFishCore.MODID;

        if (itemRegistryObject.get() instanceof ArmorItem armorItem) {
            trimMaterials.entrySet().forEach(entry -> {
                ResourceKey<TrimMaterial> trimMaterialResourceKey = entry.getKey();
                float trimValue = entry.getValue();

                String armorType = switch (armorItem.getEquipmentSlot()) {
                    case HEAD -> "helmet";
                    case CHEST -> "chestplate";
                    case LEGS -> "leggings";
                    case FEET -> "boots";
                    default -> "";
                };

                String armorItemPath = "item/" + armorItem;
                String trimPath = "trims/items/" + armorType + "_trim_" + trimMaterialResourceKey.location().getPath();
                String currentTrimName = armorItemPath + "_" + trimMaterialResourceKey.location().getPath() + "_trim";
                ResourceLocation armorItemResLoc = ResourceLocation.fromNamespaceAndPath(MOD_ID, armorItemPath);
                ResourceLocation trimResLoc = ResourceLocation.parse(trimPath);
                ResourceLocation trimNameResLoc = ResourceLocation.fromNamespaceAndPath(MOD_ID, currentTrimName);

                existingFileHelper.trackGenerated(trimResLoc, PackType.CLIENT_RESOURCES, ".png", "textures");

                getBuilder(currentTrimName)
                        .parent(new ModelFile.UncheckedModelFile("item/generated"))
                        .texture("layer0", armorItemResLoc)
                        .texture("layer1", trimResLoc);

                this.withExistingParent(itemRegistryObject.getId().getPath(), mcLoc("item/generated"))
                        .override()
                        .model(new ModelFile.UncheckedModelFile(trimNameResLoc))
                        .predicate(mcLoc("trim_type"), trimValue).end()
                        .texture("layer0", ResourceLocation.fromNamespaceAndPath(MOD_ID, "item/" + itemRegistryObject.getId().getPath()));
            });
        }
    }
}
