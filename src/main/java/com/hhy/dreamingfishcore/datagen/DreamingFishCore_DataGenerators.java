package com.hhy.dreamingfishcore.datagen;

import com.hhy.dreamingfishcore.DreamingFishCore;
import com.hhy.dreamingfishcore.datagen.lang.EnUsLanguageProvider;
import com.hhy.dreamingfishcore.datagen.lang.ZhCnLanguageProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.concurrent.CompletableFuture;

@Mod.EventBusSubscriber(modid = DreamingFishCore.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DreamingFishCore_DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator dataGenerator = event.getGenerator();
        PackOutput packOutput = dataGenerator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> providerCompletableFuture = event.getLookupProvider();

        dataGenerator.addProvider(event.includeServer(), new ModItemModelProvider(packOutput, existingFileHelper));
        dataGenerator.addProvider(event.includeServer(), new ZhCnLanguageProvider(dataGenerator, DreamingFishCore.MODID));
        dataGenerator.addProvider(event.includeServer(), new EnUsLanguageProvider(dataGenerator, DreamingFishCore.MODID));
    }
}
