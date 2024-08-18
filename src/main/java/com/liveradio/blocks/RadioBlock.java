package com.liveradio.blocks;

import com.liveradio.LiveRadioConstants;
import com.liveradio.blockentity.RadioBlockEntity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.mob.PiglinBrain;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import net.minecraft.world.explosion.Explosion;

public class RadioBlock extends Block implements BlockEntityProvider {
    public static final BooleanProperty ACTIVATED = BooleanProperty.of("activated");

    public RadioBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(ACTIVATED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ACTIVATED);
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world.isClient) {
            // Play a click sound to emphasise the interaction.
            world.playSound(player, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 1.0F, 1.0F);
            return ActionResult.PASS;
        }
        if (!player.getAbilities().allowModifyWorld) {
            return ActionResult.PASS;
        }
        boolean activated = state.get(ACTIVATED);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RadioBlockEntity) {
            RadioBlockEntity radioBlockEntity = (RadioBlockEntity) blockEntity;
            if (!activated) {
                radioBlockEntity.buildMusicThread();
                player.sendMessage(Text.literal("Station is: " + radioBlockEntity.currStation), false);
            }
            else {
                try {
                    radioBlockEntity.closeMusicThread();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                player.sendMessage(Text.literal("Stopped"), false);
            }
            radioBlockEntity.markDirty();
        }
        world.setBlockState(pos, state.with(ACTIVATED, !activated));
        return ActionResult.SUCCESS;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RadioBlockEntity(pos, state);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        this.spawnBreakParticles(world, player, pos, state);
        if (state.isIn(BlockTags.GUARDED_BY_PIGLINS)) {
            PiglinBrain.onGuardedBlockInteracted(player, false);
        }
        world.emitGameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Emitter.of(player, state));
        world.setBlockState(pos, state.with(ACTIVATED, false));
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RadioBlockEntity) {
            RadioBlockEntity radioBlockEntity = (RadioBlockEntity) blockEntity;
            if (radioBlockEntity.audioPlayer.isPlaying()) {
                try {
                    radioBlockEntity.closeMusicThread();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            radioBlockEntity.markDirty();
        }
        return state;
    }

    @Override
    public void onDestroyedByExplosion(World world, BlockPos pos, Explosion explosion) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof RadioBlockEntity) {
            RadioBlockEntity radioBlockEntity = (RadioBlockEntity) blockEntity;
            if (radioBlockEntity.audioPlayer.isPlaying()) {
                try {
                    radioBlockEntity.closeMusicThread();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            radioBlockEntity.markDirty();
        }
    }

    public static Block register(Block block, String name, boolean shouldRegisterItem) {
        Identifier id = Identifier.of(LiveRadioConstants.MOD_ID, name);
        if (shouldRegisterItem) {
            BlockItem blockItem = new BlockItem(block, new Item.Settings());
            Registry.register(Registries.ITEM, id, blockItem);
        }
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static final Block RADIO_BLOCK = register(new RadioBlock(Settings.create()
            .sounds(BlockSoundGroup.WOOD)
            .hardness(0.7F)),
            "radio_block", true);

    public static final BlockEntityType<RadioBlockEntity> RADIO_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of("live-radio", "radio_block_entity"),
            BlockEntityType.Builder.create(RadioBlockEntity::new, RADIO_BLOCK).build()
    );

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.REDSTONE).register((itemGroup) -> {
            itemGroup.add(RADIO_BLOCK.asItem());
        });
    }
}
