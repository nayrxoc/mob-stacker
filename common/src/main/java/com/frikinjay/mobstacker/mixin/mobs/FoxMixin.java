package com.frikinjay.mobstacker.mixin.mobs;

import net.minecraft.world.entity.animal.fox.Fox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Fox.class)
public interface FoxMixin {
    @Invoker("setVariant")
    void mobstacker$setVariant(Fox.Variant variant);
}