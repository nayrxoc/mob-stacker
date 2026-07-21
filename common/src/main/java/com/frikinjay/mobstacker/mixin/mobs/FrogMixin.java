package com.frikinjay.mobstacker.mixin.mobs;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.frog.FrogVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Frog.class)
public interface FrogMixin {
    @Invoker("setVariant")
    void mobstacker$setVariant(Holder<FrogVariant> variant);
}