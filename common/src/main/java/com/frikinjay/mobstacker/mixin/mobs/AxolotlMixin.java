package com.frikinjay.mobstacker.mixin.mobs;

import net.minecraft.world.entity.animal.axolotl.Axolotl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Axolotl.class)
public interface AxolotlMixin {
    @Invoker("setVariant")
    void mobstacker$setVariant(Axolotl.Variant variant);
}