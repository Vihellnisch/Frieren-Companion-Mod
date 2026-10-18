package com.frierenmod.spell;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.projectile.ZoltraakProjectile;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Zoltraak - offensive magic blast. At ALLY+ fires a three-shot spread.
 */
public class ZoltraakSpell extends SpellBase {
    public static final ZoltraakSpell INSTANCE = new ZoltraakSpell();

    public ZoltraakSpell() {
        super("zoltraak", 40);
    }

    public void cast(FrierenEntity frieren, LivingEntity target, boolean spread) {
        Level level = frieren.level();
        if (level.isClientSide) {
            return;
        }

        frieren.triggerCastAnimation();
        level.playSound(null, frieren.getX(), frieren.getY(), frieren.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.0F, 1.1F);

        Vec3 start = frieren.getEyePosition();
        Vec3 toTarget = target.getEyePosition().subtract(start).normalize();

        if (spread) {
            fireBeam(frieren, level, toTarget.yRot(0.0F));
            fireBeam(frieren, level, toTarget.yRot((float) Math.toRadians(12.0)));
            fireBeam(frieren, level, toTarget.yRot((float) Math.toRadians(-12.0)));
        } else {
            fireBeam(frieren, level, toTarget);
        }
    }

    private void fireBeam(FrierenEntity frieren, Level level, Vec3 direction) {
        ZoltraakProjectile projectile = new ZoltraakProjectile(level, frieren);
        projectile.setDamage(8.0F);
        projectile.setPos(frieren.getX(), frieren.getEyeY() - 0.1D, frieren.getZ());
        projectile.shoot(direction.x, direction.y, direction.z, 2.5F, 0.5F);
        level.addFreshEntity(projectile);
    }
}
