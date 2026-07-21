package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.MobStacker;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.apache.commons.lang3.Conversion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {

    /**
     * 1.21.11 signature (per your crash): convertTo(EntityType, ConversionParams, EntitySpawnReason, ConversionParams$Inner)
     *
     * IMPORTANT:
     * - The 4th parameter type is an inner type of ConversionParams.
     *   In your environment it maps to class_10179$class_10180.
     *   Open ConversionParams in IDE and use the exact inner type name.
     */
    @Inject(method = "convertTo*", at = @At("RETURN"))
    private void mobstacker$convertTo(
            EntityType<?> entityType,
            ConversionParams conversionParams,
            EntitySpawnReason spawnReason,
            /* ConversionParams$Inner */ ConversionParams.AfterConversion<?> afterConversion,
            CallbackInfoReturnable<Mob> cir
    ) {
        Mob self = (Mob) (Object) this;
        Mob out = cir.getReturnValue();
        if (out == null) return;

        // Copy stack info to the converted entity
        MobStacker.setStackSize(out, MobStacker.getStackSize(self));

        // Optional: keep your old behavior (clear name + update display)
        if (out.hasCustomName()) out.setCustomName(null);
        MobStacker.updateStackDisplay(out);
    }
}
