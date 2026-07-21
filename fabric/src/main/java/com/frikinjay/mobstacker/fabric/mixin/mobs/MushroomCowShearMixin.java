package com.frikinjay.mobstacker.fabric.mixin.mobs;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MushroomCow.class)
public abstract class MushroomCowShearMixin extends Animal {

    protected MushroomCowShearMixin(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(
            method = "mobInteract",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/animal/cow/MushroomCow;shear(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/sounds/SoundSource;Lnet/minecraft/world/item/ItemStack;)V"
            )
    )
    private void mobstacker$onShearBatch(Player player, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        MushroomCow self = (MushroomCow) (Object) this;

        if (!(self.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        int stackSize = MobStacker.getStackSize(self);
        if (stackSize <= 1) {
            return;
        }

        int S = Math.min(MobStacker.getShearBatchSize(), stackSize);
        if (S <= 1) {
            return;
        }

        ItemStack itemStack = player.getItemInHand(interactionHand);
        EquipmentSlot slot = (interactionHand == InteractionHand.MAIN_HAND)
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;

        // Drop S-1 extra mushroom sets BEFORE vanilla's shear() handles 1 more + conversion.
        // Entity is still a valid mooshroom here so loot table resolves correctly.
        for (int i = 1; i < S; i++) {
            this.dropFromShearingLootTable(serverLevel, BuiltInLootTables.SHEAR_MOOSHROOM, itemStack, (sl, is) -> {
                for (int j = 0; j < is.getCount(); j++) {
                    sl.addFreshEntity(new ItemEntity(self.level(), self.getX(), self.getY(1.0), self.getZ(), is.copyWithCount(1)));
                }
            });
            itemStack.hurtAndBreak(1, player, slot);
        }
        // Vanilla's shear() then: drops 1 set, converts mooshroom→cow, MobMixin copies stackSize to cow.
        // Vanilla's hurtAndBreak(): 1 durability. Total: S drops, S durability, stack of N cows.
    }
}