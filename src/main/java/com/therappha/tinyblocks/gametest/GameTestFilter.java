package com.therappha.tinyblocks.gametest;

import com.therappha.tinyblocks.TinyBlocks;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** {@code -PgameTestFilter=regex} on runGameTestServer keeps only the tests whose name matches, for a quick run. */
@EventBusSubscriber(modid = TinyBlocks.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
final class GameTestFilter {

    private GameTestFilter() {}

    /** The server has its batches ready by now, each test already placed in one: they are what is left to filter. */
    @SuppressWarnings("unchecked")
    private static void keepOnly(MinecraftServer server, Pattern pattern) {
        try {
            Field field = GameTestServer.class.getDeclaredField("testBatches");
            field.setAccessible(true);
            List<GameTestBatch> kept = new ArrayList<>();
            for (GameTestBatch batch : (List<GameTestBatch>) field.get(server)) {
                List<GameTestInfo> tests = batch.gameTestInfos().stream().filter(t -> pattern.matcher(t.getTestName()).find()).toList();
                if (!tests.isEmpty()) kept.add(new GameTestBatch(batch.name(), tests, batch.beforeBatchFunction(), batch.afterBatchFunction()));
            }
            field.set(server, kept);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SubscribeEvent
    static void onStarted(ServerStartedEvent event) {
        String filter = System.getProperty("tinyblocks.gameTestFilter", "");
        if (!filter.isEmpty()) {
            keepOnly(event.getServer(), Pattern.compile(filter));
        }
        if (Boolean.getBoolean("tinyblocks.sprint")) {
            event.getServer().tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
        }
    }
}
