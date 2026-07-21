package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "interactOn", at = @At("HEAD"))
    private void mobstacker$onInteract(Entity entity, InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        if (MobStacker.getEnableSeparator() && entity instanceof Mob && !entity.level().isClientSide()) {
            Player player = (Player) (Object) this;
            int stackSize = MobStacker.getStackSize((Mob) entity);
            ItemStack itemStack = player.getItemInHand(interactionHand);

            Identifier separatorResourceLocation = mobstacker$getResourceLocation();

            if (stackSize > 1) {
                BuiltInRegistries.ITEM.get(separatorResourceLocation).ifPresent(holder -> {
                    if (itemStack.is(holder)) {
                        MobStacker.separateEntity((Mob) entity);
                        if (!player.isCreative() && MobStacker.getConsumeSeparator()) {
                            itemStack.setCount(itemStack.getCount() - 1);
                        }
                    }
                });
            }
        }
    }


    @Unique
    private static @NotNull Identifier mobstacker$getResourceLocation() {
        String separatorItemId = MobStacker.getSeparatorItem();
        String[] parts = separatorItemId.split(":", 2);
        Identifier separatorResourceLocation;

        if (parts.length == 2) {
            separatorResourceLocation = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
        } else {
            separatorResourceLocation = Identifier.withDefaultNamespace(separatorItemId);
        }
        return separatorResourceLocation;
    }

}
