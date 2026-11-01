package com.therappha.tinyblocks.mixin;

import com.therappha.tinyblocks.v2.FakeSpace;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.vibrations.VibrationSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A sensor takes a vibration only with the chunks around it loaded and ticking. A tiny one's position is virtual, in a
 * chunk that never loads, and it held the vibration forever; a fake space is always resident.
 */
@Mixin(VibrationSystem.Ticker.class)
public interface VibrationTickerMixin {

    @Inject(method = "areAdjacentChunksTicking", at = @At("HEAD"), cancellable = true)
    private static void tinyblocks$fakeSpaceIsResident(Level level, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof FakeSpace) cir.setReturnValue(true);
    }
}
