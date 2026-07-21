package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import com.frikinjay.mobstacker.api.MobStackerAPI;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    @Unique
    private LivingEntity mobstacker$thisEntity;
    @Unique
    private Mob mobstacker$self;
    @Unique
    private int mobstacker$killBatchK;

    public LivingEntityMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "onChangedBlock", at = @At("HEAD"))
    private void mobstacker$onChangedBlock(CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!mobstacker$thisEntity.level().isClientSide() && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            if (MobStacker.getCanStack(mobstacker$self) && MobStacker.canStack(mobstacker$self)) {
                mobstacker$self.level().getEntities(mobstacker$self, mobstacker$self.getBoundingBox().inflate(MobStacker.getStackRadius()),
                                e -> e instanceof Mob && MobStacker.canStack((Mob) e))
                        .stream()
                        .filter(nearby -> MobStacker.canMerge(mobstacker$self, (Mob) nearby))
                        .findFirst()
                        .ifPresent(nearby -> MobStacker.mergeEntities((Mob) nearby, mobstacker$self));
            }
        }
    }

    @Inject(method = "die", at = @At("HEAD"))
    private void mobstacker$onDie(DamageSource damageSource, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        mobstacker$killBatchK = 0;
        if (mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            if (damageSource.is(DamageTypes.GENERIC_KILL)) {
                MobStacker.setStackSize(mobstacker$self, 1);
            }
            int stackSize = MobStacker.getStackSize(mobstacker$self);
            if (MobStacker.getKillWholeStackOnDeath()) {
                mobstacker$killBatchK = stackSize;
            } else {
                mobstacker$killBatchK = Math.min(MobStacker.getKillBatchSize(), stackSize);
            }
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void mobstacker$onRemoveHead(RemovalReason reason, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            int stackSize = MobStacker.getStackSize(mobstacker$self);
            int k = mobstacker$killBatchK > 0 ? mobstacker$killBatchK : 1;
            int remaining = stackSize - k;

            if (MobStacker.shouldSpawnNewEntity(mobstacker$self, reason) && remaining >= 1 && mobstacker$self.level() instanceof ServerLevel serverLevel) {
                MobStackerAPI.executeCustomDeathHandlers(mobstacker$self, mobstacker$self.getLastDamageSource());
                MobStacker.spawnNewEntity(serverLevel, mobstacker$self, remaining + 1);
            }
        }
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;awardKillScore(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$onDieAllScore(DamageSource damageSource, CallbackInfo ci) {
        if (mobstacker$killBatchK > 1 && mobstacker$self != null) {
            LivingEntity livingEntity = mobstacker$self.getKillCredit();
            if (livingEntity != null) {
                for (int i = 1; i < mobstacker$killBatchK; i++) {
                    livingEntity.awardKillScore(mobstacker$self, damageSource);
                }
            }
        }
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$onDieAllDropLoot(DamageSource damageSource, CallbackInfo ci) {
        if (mobstacker$killBatchK > 1 && mobstacker$self != null) {
            for (int i = 1; i < mobstacker$killBatchK; i++) {
                if (!mobstacker$self.level().isClientSide()) {
                    mobstacker$self.dropAllDeathLoot((ServerLevel) mobstacker$self.level(), damageSource);
                }
            }
        }
    }

    @Inject(method = "die", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;dropAllDeathLoot(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;)V", shift = At.Shift.AFTER))
    private void mobstacker$onDieAllCreateWRose(DamageSource damageSource, CallbackInfo ci) {
        if (mobstacker$killBatchK > 1 && mobstacker$self != null) {
            LivingEntity livingEntity = mobstacker$self.getKillCredit();
            for (int i = 1; i < mobstacker$killBatchK; i++) {
                mobstacker$self.createWitherRose(livingEntity);
            }
        }
    }

    @Inject(method = "die", at = @At("TAIL"))
    private void mobstacker$onDieExtraDurability(DamageSource damageSource, CallbackInfo ci) {
        if (mobstacker$killBatchK > 1 && mobstacker$self != null) {
            if (damageSource.getDirectEntity() instanceof ServerPlayer player) {
                ItemStack weapon = player.getMainHandItem();
                if (weapon.isDamageableItem()) {
                    weapon.hurtAndBreak(mobstacker$killBatchK - 1, player, EquipmentSlot.MAINHAND);
                }
            }
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void mobstacker$onConstructed(EntityType<?> entityType, Level level, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!level.isClientSide() && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            if (MobStacker.getStackSize(mobstacker$self) == 1) {
                MobStacker.setStackSize(mobstacker$self, 1);
            }
            if (MobStacker.getCanStack(mobstacker$self)) {
                MobStacker.setCanStack(mobstacker$self, true);
            }
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void mobstacker$onReadAdditionalSaveData(ValueInput valueInput, CallbackInfo ci) {
        mobstacker$thisEntity = (LivingEntity) (Object) this;
        if (!mobstacker$thisEntity.level().isClientSide() && mobstacker$thisEntity instanceof Mob) {
            mobstacker$self = (Mob) mobstacker$thisEntity;
            MobStacker.updateStackDisplay(mobstacker$self);
        }
    }

}