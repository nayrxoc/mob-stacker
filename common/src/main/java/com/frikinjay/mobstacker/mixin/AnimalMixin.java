package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Animal.class)
public class AnimalMixin {

    @Inject(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/Animal;setInLove(Lnet/minecraft/world/entity/player/Player;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void mobstacker$onBreedBatch(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Animal self = (Animal) (Object) this;

        if (!(self.level() instanceof ServerLevel serverLevel)) return;

        int stackSize = MobStacker.getStackSize((Mob) self);
        if (stackSize <= 1) return;

        int y = Math.min(MobStacker.getBreedBatchSize(), stackSize);
        int babies = y / 2;
        if (babies <= 0) return;

        // Cancel love mode — we handle breeding directly
        self.resetLove();
        self.setAge(MobStacker.getBreedCooldown());

        for (int i = 0; i < babies; i++) {
            AgeableMob baby = self.getBreedOffspring(serverLevel, self);
            if (baby != null) {
                baby.setBaby(true);
                baby.setPos(self.position());
                serverLevel.addFreshEntity(baby);
            }
        }

        // Award breeding XP (vanilla gives 1-7 per breed event)
        ExperienceOrb.award(serverLevel, self.position(), babies * (1 + self.getRandom().nextInt(7)));
    }
}