package com.frierenmod.magic;

import com.frierenmod.magic.SpellCaster.StaffSpell;
import com.frierenmod.projectile.ZoltraakProjectile;
import com.frierenmod.spell.UselessSpells;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Two learned spells fused into one, discovered in the spellbook's fusion circle. Order doesn't matter.
 */
public enum Fusion {
    HOMING_BLAST("Homing Blast", StaffSpell.ZOLTRAAK_T2, StaffSpell.ZOLTRAAK_T3, 60,
            "A Zoltraak that hunts its target down, then explodes."),
    GOLDEN_ZOLTRAAK("Golden Zoltraak", StaffSpell.MIDAS, StaffSpell.ZOLTRAAK_T1, 200,
            "Whatever it hits turns to gold for a while. Mobs it kills drop gold."),
    BUTTERFLY_BOMBS("Butterfly Bombs", StaffSpell.GOLDEN_BUTTERFLIES, StaffSpell.ZOLTRAAK_T1, 300,
            "Golden butterflies that seek out monsters and burst."),
    SPRING_BLOOM("Spring Bloom", StaffSpell.FLOWER_FIELD, StaffSpell.GROW_GRASS, 200,
            "Grass and flowers spread far around you."),
    SEARING_ZOLTRAAK("Searing Zoltraak", StaffSpell.CREATE_TORCH, StaffSpell.ZOLTRAAK_T1, 30,
            "A Zoltraak that sets its target ablaze.");

    public final String displayName;
    public final StaffSpell a, b;
    public final int cooldownTicks;
    public final String description;

    Fusion(String displayName, StaffSpell a, StaffSpell b, int cooldownTicks, String description) {
        this.displayName = displayName;
        this.a = a;
        this.b = b;
        this.cooldownTicks = cooldownTicks;
        this.description = description;
    }

    @Nullable
    public static Fusion of(StaffSpell x, StaffSpell y) {
        for (Fusion f : values()) {
            if ((f.a == x && f.b == y) || (f.a == y && f.b == x)) return f;
        }
        return null;
    }

    public boolean isDiscovered(int fusionMask) {
        return (fusionMask & (1 << ordinal())) != 0;
    }

    /** Server side. True if it fired. */
    public boolean cast(Player player) {
        Level level = player.level();
        return switch (this) {
            case HOMING_BLAST -> {
                ZoltraakProjectile p = beam(level, player, 12.0F, 0.8F);
                p.setHoming(true);
                p.setExplosion(PlayerMagic.t3ExplosionRadius(player) - 0.5F, false);
                yield launch(level, player, p, 1.6F);
            }
            case GOLDEN_ZOLTRAAK -> {
                ZoltraakProjectile p = beam(level, player, 10.0F, 1.4F);
                p.setEffect(ZoltraakProjectile.Effect.GOLDEN);
                yield launch(level, player, p, 2.5F);
            }
            case SEARING_ZOLTRAAK -> {
                ZoltraakProjectile p = beam(level, player, 14.0F, 1.0F);
                p.setEffect(ZoltraakProjectile.Effect.SEARING);
                yield launch(level, player, p, 3.0F);
            }
            case BUTTERFLY_BOMBS -> UselessSpells.butterflies(player, 8, true);
            case SPRING_BLOOM -> UselessSpells.springBloom(player, 10);
        };
    }

    private static ZoltraakProjectile beam(Level level, Player player, float damage, float pitch) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, pitch);
        ZoltraakProjectile p = new ZoltraakProjectile(level, player);
        p.setDamage(damage * PlayerMagic.damageMultiplier(player));
        return p;
    }

    private static boolean launch(Level level, Player player, ZoltraakProjectile p, float velocity) {
        p.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F,
                velocity * PlayerMagic.velocityMultiplier(player), 0.4F);
        level.addFreshEntity(p);
        return true;
    }
}
