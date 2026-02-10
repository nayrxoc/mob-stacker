package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies stack-scaled outgoing melee damage when stackOutgoingDamage is enabled.
 *
 * After a successful vanilla doHurtTarget hit, applies (N-1) additional hurtServer()
 * calls to simulate N mobs attacking simultaneously. Each extra hit goes through the
 * full vanilla damage pipeline (armor reduction, enchantment protection, durability wear).
 *
 * Mitigations:
 * - Re-entrancy guard: @Unique boolean prevents extra hits from recursively triggering
 *   more extra hits (e.g. if thorns or a mod re-invokes doHurtTarget).
 * - Invulnerability reset: invulnerableTime is zeroed before each extra hit so vanilla
 *   i-frames don't nullify them; restored to the post-first-hit value afterward so the
 *   target still gets normal protection against subsequent attacks from other sources.
 * - Velocity restore: target delta movement is saved after the first hit and restored
 *   after each extra hit so knockback doesn't accumulate to absurd levels.
 * - Performance cap: effective stack size for damage is hard-capped at 16 to prevent
 *   server lag from enormous stacks. This cap is intentionally conservative.
 */
@Mixin(Mob.class)
public abstract class MobAttackMixin {

    /** Max additional hits to apply regardless of stack size, to bound tick cost. */
    @Unique
    private static final int MOBSTACKER$MAX_EXTRA_HITS = 15; // cap effective N at 16

    @Unique
    private boolean mobstacker$applyingExtraHits = false;

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void mobstacker$stackedOutgoingDamage(
            ServerLevel serverLevel, Entity target, CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue() || mobstacker$applyingExtraHits
                || !MobStacker.getStackOutgoingDamage()) {
            return;
        }

        Mob attacker = (Mob) (Object) this;
        if (!(target instanceof LivingEntity livingTarget)) return;

        int stackSize = MobStacker.getStackSize(attacker);
        if (stackSize <= 1) return;

        int extraHits = Math.min(stackSize - 1, MOBSTACKER$MAX_EXTRA_HITS);

        AttributeInstance attackAttr = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr == null) return;
        float damage = (float) attackAttr.getValue();

        DamageSource damageSource = attacker.damageSources().mobAttack(attacker);

        // Snapshot post-first-hit state
        Vec3 savedVelocity = livingTarget.getDeltaMovement();
        int savedInvulTime = livingTarget.invulnerableTime;

        mobstacker$applyingExtraHits = true;
        try {
            for (int i = 0; i < extraHits; i++) {
                livingTarget.invulnerableTime = 0;
                livingTarget.hurtServer(serverLevel, damageSource, damage);
                livingTarget.setDeltaMovement(savedVelocity);
            }
        } finally {
            mobstacker$applyingExtraHits = false;
            livingTarget.invulnerableTime = savedInvulTime;
        }
    }
}
