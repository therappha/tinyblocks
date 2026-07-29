package com.therappha.tinyblocks.subgrid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class PieceDefinition {

    private static final Map<ResourceLocation, PieceDefinition> REGISTRY = new LinkedHashMap<>();

    private final ResourceLocation id;
    private final Vec3i baseFootprint;

    protected PieceDefinition(ResourceLocation id, Vec3i baseFootprint) {
        this.id = id;
        this.baseFootprint = baseFootprint;
        REGISTRY.put(id, this);
    }

    public ResourceLocation id() { return id; }

    /** Size in grid cells, defined along the Z axis. Rotated at placement time. */
    public Vec3i baseFootprint() { return baseFootprint; }

    /** BlockState used by the BER to render this piece. */
    public abstract BlockState renderState(Direction facing);

    /**
     * Per-instance variant. Defaults to renderState(piece.facing); override this instead when
     * appearance depends on piece.extraData/runtimeState (e.g. an on/off indicator).
     */
    public BlockState renderState(PlacedPiece piece) {
        return renderState(piece.facing);
    }

    /** Mining hardness. Negative = unbreakable. Defaults to stone (1.5f). */
    public float destroyTime() { return 1.5f; }

    /** Per-instance variant. Override when hardness depends on piece.extraData/runtimeState. */
    public float destroyTime(PlacedPiece piece) { return destroyTime(); }

    /** If true, wrong tool gives no drops and mines 3x slower. */
    public boolean requiresCorrectTool() { return false; }

    /** Items dropped when this piece is broken with the correct tool. */
    public List<ItemStack> drops(PlacedPiece piece) { return List.of(); }

    /** Called after a piece is loaded from NBT. Initialize runtimeState here from extraData. */
    public void onLoaded(PlacedPiece piece, HolderLookup.Provider registries) {}

    /** Called before a piece is saved. Flush runtimeState into extraData here. */
    public void onSaving(PlacedPiece piece, HolderLookup.Provider registries) {}

    /** True if this piece needs a server-side tick every game tick. */
    public boolean requiresTick() { return false; }

    /**
     * Called each server tick when requiresTick() is true.
     * Return true if state changed and the BE should mark itself dirty.
     */
    public boolean tick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) { return false; }

    /** True if this piece needs a client-side tick every game tick, for local animation state. */
    public boolean requiresClientTick() { return false; }

    /**
     * Called each client tick when requiresClientTick() is true. Client-only: use it to ease
     * piece.runtimeState toward a target driven by extraData (e.g. a lid angle animating toward
     * open/closed). Never mutate extraData here — it's server-authoritative and gets overwritten
     * by the next sync packet; keep purely local animation state in runtimeState instead.
     */
    public void clientTick(PlacedPiece piece, Level level, BlockPos subgridPos, SubgridBlockEntity be) {}

    /**
     * Called on the server when a piece adjacent to this one (within the same subgrid) is
     * placed, removed, or reports a state change from tick(). Mirrors vanilla neighborChanged.
     * Interactive pieces that change state in onUse() can trigger this themselves by calling
     * be.notifyNeighbors(piece) after mutating state.
     */
    public void onNeighborChanged(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be, PlacedPiece changedNeighbor) {}

    /**
     * Called when a player right-clicks this piece.
     * On client side: return SUCCESS to consume without side effects.
     * On server side: open menus, change state, etc.
     */
    public InteractionResult onUse(PlacedPiece piece, Level level, BlockPos subgridPos, SubgridBlockEntity be, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /**
     * Called when a scheduled tick this piece requested (v2: via a real vanilla scheduleTick
     * call, e.g. a redstone lamp asking to turn off in 4 ticks) comes due. See
     * SubgridBlockEntity.scheduleTick.
     */
    public void scheduledTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {}

    /**
     * Called on a small random sample of pieces each server tick, mirroring vanilla's random
     * tick (crop growth, leaf decay, fire spread). Most pieces should no-op; v2 pieces check
     * their own BlockState.isRandomlyTicking() before doing anything.
     */
    public void randomTick(PlacedPiece piece, ServerLevel level, BlockPos subgridPos, SubgridBlockEntity be) {}

    public static PieceDefinition get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static PieceDefinition getOrThrow(ResourceLocation id) {
        PieceDefinition def = REGISTRY.get(id);
        if (def == null) throw new IllegalArgumentException("Unknown PieceDefinition: " + id);
        return def;
    }

    public static Collection<PieceDefinition> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }
}
