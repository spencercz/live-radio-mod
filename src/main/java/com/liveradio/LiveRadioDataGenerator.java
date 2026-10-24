package com.liveradio;

import com.liveradio.blocks.RadioBlock;
import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootSubProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;

import java.util.concurrent.CompletableFuture;

public class LiveRadioDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
		pack.addProvider(BlockLootTables::new);
		pack.addProvider(BlockTagGenerator::new);
	}

	private static class BlockLootTables extends FabricBlockLootSubProvider {
		BlockLootTables(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(packOutput, registryLookup);
		}

		@Override
		public void generate() {
			dropSelf(RadioBlock.RADIO_BLOCK);
		}
	}

	private static class BlockTagGenerator extends FabricTagsProvider.BlockTagsProvider {
		BlockTagGenerator(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
			super(packOutput, registryLookup);
		}

		@Override
		protected void addTags(HolderLookup.Provider registries) {
			builder(BlockTags.MINEABLE_WITH_AXE).add(BuiltInRegistries.BLOCK.getResourceKey(RadioBlock.RADIO_BLOCK).orElseThrow());
		}
	}
}
