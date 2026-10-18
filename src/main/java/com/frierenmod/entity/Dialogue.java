package com.frierenmod.entity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.Random;

/**
 * Holds Frieren's dialogue lines for each relationship stage and prints them as chat bubbles.
 */
public final class Dialogue {
    private static final Random RANDOM = new Random();

    private static final String[] STRANGER = {
            "...Grimoires are my weakness. Do you have any?",
            "Do you know of any rare spell books nearby?",
            "I've been traveling a very long time.",
            "...You're staring. Is there something you need?",
            "Magic is simply a tool. Nothing more.",
            "I collect spells. Even useless ones."
    };

    private static final String[] FRIEND = {
            "I'll stay close. Just in case.",
            "You remind me of Himmel, a little.",
            "Traveling alone gets tiresome. This is... fine.",
            "If monsters come, leave them to me.",
            "Here, I can heal you if you're hurt. Just ask.",
            "Ten years is nothing. You'll understand someday."
    };

    private static final String[] ALLY = {
            "Zoltraak. ...Leave the fighting to me.",
            "I've grown... fond of traveling with you.",
            "I'll teach you what I can. Magic takes patience.",
            "Stay behind me when it gets dangerous.",
            "You've become someone I can rely on.",
            "I'll shield you. Don't do anything reckless."
    };

    private static final String[] PARTNER = {
            "...Don't make me say it again.",
            "I suppose you're my reason to return now.",
            "A human lifetime is short. I'll treasure it.",
            "Stay by my side. That's all I ask.",
            "...I love you. There. Are you satisfied?",
            "Wherever you go, I'll follow."
    };

    public static void say(Player player, FrierenEntity.Stage stage, boolean special) {
        String line = special ? pickSpecial(stage) : pick(stage);
        ChatFormatting color = switch (stage) {
            case STRANGER -> ChatFormatting.GRAY;
            case FRIEND -> ChatFormatting.AQUA;
            case ALLY -> ChatFormatting.GREEN;
            case PARTNER -> ChatFormatting.LIGHT_PURPLE;
        };
        player.sendSystemMessage(Component.literal("Frieren: " + line).withStyle(color));
    }

    private static String pick(FrierenEntity.Stage stage) {
        String[] lines = linesFor(stage);
        return lines[RANDOM.nextInt(lines.length)];
    }

    private static String pickSpecial(FrierenEntity.Stage stage) {
        String[] lines = linesFor(stage);
        // Special lines are the last two entries per stage.
        return lines[lines.length - 1 - RANDOM.nextInt(2)];
    }

    private static String[] linesFor(FrierenEntity.Stage stage) {
        return switch (stage) {
            case STRANGER -> STRANGER;
            case FRIEND -> FRIEND;
            case ALLY -> ALLY;
            case PARTNER -> PARTNER;
        };
    }

    private Dialogue() {}
}
