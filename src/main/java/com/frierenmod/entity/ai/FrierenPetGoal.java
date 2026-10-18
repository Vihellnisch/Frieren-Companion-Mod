package com.frierenmod.entity.ai;

import com.frierenmod.entity.FrierenEntity;
import com.frierenmod.network.PetAnimPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * Head pats: Frieren steps in front of the player, bows her head into their hand for a couple of
 * seconds, and enjoys it more the closer the two of you are.
 */
public class FrierenPetGoal extends Goal {
    public static final int BOW_TICKS = 60;
    private static final int APPROACH_TIMEOUT = 60;

    private static final String[] FRIEND_LINES = {
            "...I'm not a child, you know. ...You can keep going, though.",
            "Hm. ...That's not bad.",
            "Fern does this to me when I oversleep. It's different when you do it."
    };
    private static final String[] ALLY_LINES = {
            "Mm. Your hand is warm.",
            "...A little to the left. There.",
            "You're getting good at this."
    };
    private static final String[] PARTNER_LINES = {
            "...Himmel used to do this too. I don't mind that it's you now.",
            "*leans into your hand* ...A little longer.",
            "Eighty years from now, I'll still remember this. ...Keep going.",
            "...I like this. Don't tell anyone."
    };

    private final FrierenEntity frieren;
    @Nullable
    private Player petter;
    private boolean bowing;
    private int ticks;

    public FrierenPetGoal(FrierenEntity frieren) {
        this.frieren = frieren;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        petter = frieren.getPetRequester();
        return petter != null && !frieren.isInChest() && !frieren.isSleeping();
    }

    @Override
    public boolean canContinueToUse() {
        return petter != null && petter.isAlive() && ticks < (bowing ? BOW_TICKS : APPROACH_TIMEOUT)
                && frieren.distanceToSqr(petter) < 25.0D && !frieren.isInChest();
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        bowing = false;
        ticks = 0;
        Vec3 spot = spotInFront();
        frieren.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9D);
    }

    /** Just in front of the player, close enough to reach her head. */
    private Vec3 spotInFront() {
        Vec3 look = petter.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0D, look.z);
        flat = flat.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0.0F, petter.getYRot()) : flat.normalize();
        return petter.position().add(flat.scale(1.3D));
    }

    @Override
    public void tick() {
        ticks++;
        if (petter == null) return;
        frieren.getLookControl().setLookAt(petter, 30.0F, 30.0F);
        if (!bowing) {
            Vec3 spot = spotInFront();
            double dx = frieren.getX() - spot.x, dz = frieren.getZ() - spot.z;
            double distSq = dx * dx + dz * dz;
            if (distSq < 0.25D * 0.25D || ticks >= APPROACH_TIMEOUT - 1) {
                startBow();
            } else if (distSq < 3.0D * 3.0D) {
                // the last few steps straight in; the pathfinder stops a block short of the player
                frieren.getNavigation().stop();
                frieren.getMoveControl().setWantedPosition(spot.x, spot.y, spot.z, 0.6D);
            } else if (ticks % 10 == 0) {
                frieren.getNavigation().moveTo(spot.x, spot.y, spot.z, 0.9D);
            }
            return;
        }
        // Face the player squarely while bowing.
        double dx = petter.getX() - frieren.getX(), dz = petter.getZ() - frieren.getZ();
        float yaw = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        frieren.setYRot(yaw);
        frieren.setYBodyRot(yaw);
        frieren.setYHeadRot(yaw);
        if (ticks % 12 == 0 && frieren.level() instanceof ServerLevel sl) {
            boolean partner = frieren.getStage() == FrierenEntity.Stage.PARTNER;
            sl.sendParticles(ParticleTypes.HEART, frieren.getX(), frieren.getY() + 2.1D, frieren.getZ(),
                    partner ? 2 : 1, 0.25D, 0.1D, 0.25D, 0.0D);
        }
    }

    private void startBow() {
        bowing = true;
        ticks = 0;
        frieren.getNavigation().stop();
        frieren.getMoveControl().setWantedPosition(frieren.getX(), frieren.getY(), frieren.getZ(), 0.0D);
        frieren.setBowing(true);
        frieren.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.5F);
        if (petter instanceof ServerPlayer sp) PetAnimPacket.send(sp, frieren, BOW_TICKS);
        String[] lines = switch (frieren.getStage()) {
            case PARTNER -> PARTNER_LINES;
            case ALLY -> ALLY_LINES;
            default -> FRIEND_LINES;
        };
        if (frieren.getRandom().nextInt(3) != 0) {
            petter.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Frieren: " + lines[frieren.getRandom().nextInt(lines.length)]).withStyle(
                    frieren.getStage() == FrierenEntity.Stage.PARTNER ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA));
        }
        frieren.onPetted(petter);
    }

    @Override
    public void stop() {
        frieren.setBowing(false);
        frieren.clearPetRequest();
        frieren.getNavigation().stop();
        petter = null;
        bowing = false;
    }
}
