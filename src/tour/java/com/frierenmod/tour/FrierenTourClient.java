package com.frierenmod.tour;

import com.frierenmod.FrierenMod;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Client half of {@link FrierenTour}: opens (or creates) the tour world from the title screen,
 * takes a screenshot near the end of each stop with the GUI hidden (like F1), then quits.
 */
@EventBusSubscriber(modid = FrierenMod.MODID, value = Dist.CLIENT)
public final class FrierenTourClient {
    private static final String WORLD = "frieren_test";
    private static boolean opened;
    private static int shot = -1;

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!FrierenTour.ENABLED || opened || !(event.getScreen() instanceof TitleScreen)) return;
        opened = true;
        Minecraft mc = Minecraft.getInstance();
        mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0D);
        mc.options.hideGui = true;
        mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
        if (FrierenTour.SHOWCASE && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        if (mc.getLevelSource().levelExists(WORLD)) {
            mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
        } else {
            LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                    new GameRules(), WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261016L, true, false),
                    WorldPresets::createNormalWorldDimensions, event.getScreen());
        }
    }

    /** Logs this mod's sounds as the client actually starts them, with the listener's distance. */
    @SubscribeEvent
    public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        if (!FrierenTour.ENABLED || event.getSound() == null) return;
        var id = event.getSound().getLocation();
        if (!FrierenMod.MODID.equals(id.getNamespace())) return;
        Minecraft mc = Minecraft.getInstance();
        double dist = mc.player == null ? -1 : Math.sqrt(mc.player.distanceToSqr(event.getSound().getX(), event.getSound().getY(), event.getSound().getZ()));
        com.mojang.logging.LogUtils.getLogger().info("TOUR sound {} resolved={} attenuation={} at {} blocks",
                id, event.getSound().getSound() != null ? event.getSound().getSound().getLocation() : "?",
                event.getSound().getAttenuation(), String.format("%.1f", dist));
    }

    /** F1 hides the first-person hand too, so hand stops keep the GUI on and drop the HUD layers instead. */
    private static boolean showsHand() {
        int since = FrierenTour.ticks - FrierenTour.FIRST_STOP;
        if (since < 0) return false;
        int stop = since / FrierenTour.STOP_LENGTH;
        if (stop >= FrierenTour.STOP_NAMES.size()) return false;
        String name = FrierenTour.STOP_NAMES.get(stop);
        return name.contains("hand") || name.contains("first_person");
    }

    @SubscribeEvent
    public static void onGuiLayer(net.neoforged.neoforge.client.event.RenderGuiLayerEvent.Pre event) {
        if (FrierenTour.ENABLED && showsHand()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!FrierenTour.ENABLED || mc.player == null || mc.level == null) return;
        int since = FrierenTour.ticks - FrierenTour.FIRST_STOP;
        mc.options.hideGui = !showsHand();
        if (since < 0) return;
        int stop = since / FrierenTour.STOP_LENGTH, within = since % FrierenTour.STOP_LENGTH;
        if (stop >= FrierenTour.STOP_NAMES.size()) {
            mc.stop();
            return;
        }
        String stopName = FrierenTour.STOP_NAMES.get(stop);
        mc.options.setCameraType(stopName.endsWith("_back") ? CameraType.THIRD_PERSON_BACK
                : stopName.endsWith("_front") ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
        if (stopName.endsWith("_screen") && within >= 40 && mc.screen == null && shot != stop) {
            mc.setScreen(new com.frierenmod.client.SpellbookScreen());
        }
        if (!stopName.endsWith("_screen") && mc.screen instanceof com.frierenmod.client.SpellbookScreen) {
            mc.setScreen(null);
        }
        if (shot != stop && within >= FrierenTour.SHOT_AT) {
            shot = stop;
            String name = "tour_" + String.format("%02d", stop) + "_" + FrierenTour.STOP_NAMES.get(stop) + ".png";
            Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> {});
        }
    }

    private FrierenTourClient() {}
}
