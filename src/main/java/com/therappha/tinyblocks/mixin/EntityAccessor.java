package com.therappha.tinyblocks.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** An entity made in a fake space belongs in the real level (FakeServerLevel#addFreshEntity). */
@Mixin(Entity.class)
public interface EntityAccessor {

    @Invoker("setLevel")
    void tinyblocks$setLevel(Level level);
}
