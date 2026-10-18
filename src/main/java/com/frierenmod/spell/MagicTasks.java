package com.frierenmod.spell;

import com.frierenmod.FrierenMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Spell effects that unfold over a few ticks, like a flower field spreading outward. Not saved. */
@EventBusSubscriber(modid = FrierenMod.MODID)
public final class MagicTasks {
    private record Task(ServerLevel level, long due, Runnable action) {}

    private static final List<Task> TASKS = new ArrayList<>();

    public static void schedule(ServerLevel level, int delayTicks, Runnable action) {
        TASKS.add(new Task(level, level.getGameTime() + delayTicks, action));
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || TASKS.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.level == level && t.due <= level.getGameTime()) {
                due.add(t);
                it.remove();
            }
        }
        due.forEach(t -> t.action.run());
    }

    private MagicTasks() {}
}
