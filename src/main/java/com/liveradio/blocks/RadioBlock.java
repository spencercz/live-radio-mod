package com.liveradio.blocks;

import com.liveradio.LiveRadioConstants;
import com.liveradio.blockentity.RadioBlockEntity;
import com.liveradio.network.OpenRadioScreenPayload;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * Right-click to set the station URL; the radio plays while it receives redstone power.
 */
public class RadioBlock extends Block implements BlockEntityProvider {
    /** The direction the radio's front faces. */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    /** True while the radio is powered by redstone, which is what makes it play. */
    public static final BooleanProperty ACTIVATED = BooleanProperty.of("activated");

    public RadioBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(ACTIVATED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVATED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState()
                .with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(ACTIVATED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) {
            return;
        }
        boolean powered = world.isReceivingRedstonePower(pos);
        if (powered != state.get(ACTIVATED)) {
            world.setBlockState(pos, state.with(ACTIVATED, powered), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!player.canModifyBlocks()) {
            return ActionResult.PASS;
        }
        if (world.isClient) {
            // Play a click sound to emphasise the interaction.
            world.playSound(player, pos, SoundEvents.BLOCK_COMPARATOR_CLICK, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else if (player instanceof ServerPlayerEntity serverPlayer
                && world.getBlockEntity(pos) instanceof RadioBlockEntity radioBlockEntity) {
            ServerPlayNetworking.send(serverPlayer, new OpenRadioScreenPayload(pos, radioBlockEntity.getStationUrl()));
        }
        return ActionResult.success(world.isClient);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new RadioBlockEntity(pos, state);
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
