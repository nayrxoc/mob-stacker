package com.frikinjay.mobstacker.mixin.mobs;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Cat.class)
public interface CatMixin {
    @Invoker("setVariant")
    void mobstacker$setVariant(Holder<CatVariant> variant);
}