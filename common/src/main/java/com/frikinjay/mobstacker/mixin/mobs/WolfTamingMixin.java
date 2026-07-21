package com.frikinjay.mobstacker.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Wolf.class)
public abstract class WolfTamingMixin {

    @Unique
    private int mobstacker$preTameStackSize = 0;

    @Inject(method = "tryToTame", at = @At("HEAD"))
    private void mobstacker$preTame(Player player, CallbackInfo ci) {
        this.mobstacker$preTameStackSize = MobStacker.getStackSize((Mob) (Object) this);
    }

    @Inject(method = "tryToTame", at = @At("TAIL"))
    private void mobstacker$postTame(Player player, CallbackInfo ci) {
        Wolf wolf = (Wolf) (Object) this;
        if (wolf.isTame() && this.mobstacker$preTameStackSize > 1) {
            int oldSize = this.mobstacker$preTameStackSize;
            MobStacker.setStackSize(wolf, 1);

            ServerLevel serverLevel = (ServerLevel) wolf.level();
            MobStacker.spawnNewEntity(serverLevel, wolf, oldSize);
        }
    }
}