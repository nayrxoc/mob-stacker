package com.frikinjay.mobstacker.mixin;

import com.frikinjay.mobstacker.ICustomDataHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.frikinjay.mobstacker.MobStacker.*;

@Mixin(Entity.class)
public class EntityDataMixin implements ICustomDataHolder {

    @Unique private int mobstacker$stackSize = 1;
    @Unique private boolean mobstacker$canStack = true;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mobstacker$onConstruct(CallbackInfo ci) {
        // defaults already set in field initializers
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void mobstacker$onSaveWithoutId(ValueOutput valueOutput, CallbackInfo ci) {
        ValueOutput stack = valueOutput.child(STACK_DATA_KEY);
        stack.putInt(STACK_SIZE_KEY, this.mobstacker$stackSize);
        stack.putBoolean(CAN_STACK_KEY, this.mobstacker$canStack);
    }

    @Inject(method = "load", at = @At("RETURN"))
    private void mobstacker$onLoad(ValueInput valueInput, CallbackInfo ci) {
        ValueInput stack = valueInput.childOrEmpty(STACK_DATA_KEY);
        this.mobstacker$stackSize = stack.getIntOr(STACK_SIZE_KEY, 1);
        this.mobstacker$canStack = stack.getBooleanOr(CAN_STACK_KEY, true);
    }

    @Override
    public int mobstacker$getStackSize() {
        return this.mobstacker$stackSize;
    }

    @Override
    public void mobstacker$setStackSize(int size) {
        this.mobstacker$stackSize = Math.max(1, size);
    }

    @Override
    public boolean mobstacker$getCanStack() {
        return this.mobstacker$canStack;
    }

    @Override
    public void mobstacker$setCanStack(boolean canStack) {
        this.mobstacker$canStack = canStack;
    }
}
