package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.IdentityHashMap;
import java.util.Map;

/** Hands each entity that appears to the parity space it appeared in, for ParitySpace#entities. */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
final class ParityEntities {

    private ParityEntities() {}

    private record Watch(Level level, int[] size) {}

    private static final Map<ParitySpace, Watch> WATCHED = new IdentityHashMap<>();

    static void watch(Level level, ParitySpace space, int[] size) {
        WATCHED.put(space, new Watch(level, size));
    }

    static void stopWatching(ParitySpace space) {
        WATCHED.remove(space);
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        if (WATCHED.isEmpty() || entity instanceof Player || event.getLevel().isClientSide()) return;
        for (Map.Entry<ParitySpace, Watch> watched : WATCHED.entrySet()) {
            // Asked now, not when watching began: a grid that has moved has its box where it went.
            if (watched.getValue().level() == event.getLevel() && watched.getKey().bornIn(watched.getValue().size(), entity.position())) {
                watched.getKey().born().add(entity);
                return;
            }
        }
    }
}
