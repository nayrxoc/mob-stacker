package com.frikinjay.mobstacker.mixin.mobs;

import net.minecraft.world.entity.animal.cow.MushroomCow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MushroomCow.class)
public interface MushroomCowMixin {
    @Invoker("setVariant")
    void mobstacker$setVariant(MushroomCow.Variant variant);
}