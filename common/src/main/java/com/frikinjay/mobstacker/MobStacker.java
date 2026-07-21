package com.frikinjay.mobstacker;

import com.frikinjay.almanac.Almanac;
import com.frikinjay.mobstacker.api.MobStackerAPI;
import com.frikinjay.mobstacker.command.MobStackerCommands;
import com.frikinjay.mobstacker.config.MobStackerConfig;
import com.frikinjay.mobstacker.mixin.mobs.AxolotlMixin;
import com.frikinjay.mobstacker.mixin.mobs.CatMixin;
import com.frikinjay.mobstacker.mixin.mobs.FoxMixin;
import com.frikinjay.mobstacker.mixin.mobs.FrogMixin;
import com.frikinjay.mobstacker.mixin.mobs.MushroomCowMixin;
import com.frikinjay.mobstacker.mixin.mobs.WolfMixin;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.entity.animal.cow.MushroomCow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.slf4j.Logger;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;

public final class MobStacker {
    public static final String MOD_ID = "mobstacker";

    public static final Logger logger = LogUtils.getLogger();
    public static final String STACK_DATA_KEY = "StackData";
    public static final String STACK_SIZE_KEY = "StackSize";
    public static final String CAN_STACK_KEY = "CanStack";
    public static MobStackerConfig config;
    public static final File CONFIG_FILE = new File("config/mobstacker.json");

    private static final WeakHashMap<Class<?>, Boolean> bossEntityCache = new WeakHashMap<>();
    private static final WeakHashMap<Class<?>, Field> bossFieldCache = new WeakHashMap<>();

    private static final Pattern STACKED_NAME_PATTERN = Pattern.compile(" x\\d+$");

    private static final Map<Class<? extends Mob>, BiPredicate<Mob, Mob>> VARIANT_CHECKERS = Map.of(
            Sheep.class, (self, other) -> ((Sheep)self).getColor() == ((Sheep)other).getColor()
                    && ((Sheep)self).isSheared() == ((Sheep)other).isSheared(),
            Villager.class, (self, other) -> checkVillagerMatch((Villager)self, (Villager)other),
            ZombieVillager.class, (self, other) -> checkZombieVillagerMatch((ZombieVillager)self, (ZombieVillager)other),
            Slime.class, (self, other) -> ((Slime)self).getSize() == ((Slime)other).getSize(),
            Frog.class, (self, other) -> ((Frog)self).getVariant() == ((Frog)other).getVariant(),
            Axolotl.class, (self, other) -> ((Axolotl)self).getVariant() == ((Axolotl)other).getVariant(),
            Cat.class, (self, other) -> ((Cat)self).getVariant() == ((Cat)other).getVariant(),
            Fox.class, (self, other) -> ((Fox)self).getVariant() == ((Fox)other).getVariant(),
            MushroomCow.class, (self, other) -> ((MushroomCow)self).getVariant() == ((MushroomCow)other).getVariant(),
            Wolf.class, (self, other) -> checkWolfMatch((Wolf)self, (Wolf)other)
    );

    public static void init() {
        config = MobStackerConfig.load();
        Almanac.addConfigChangeListener(CONFIG_FILE, newConfig -> {
            config = (MobStackerConfig) newConfig;
            logger.info("MobStacker config reloaded");
        });
        config.save();
        Almanac.addCommandRegistration(MobStackerCommands::register);
    }

    public static boolean canStack(Mob entity) {
        if (!(entity instanceof Mob) || entity.isBaby()) {
            return false;
        }

        if (entity instanceof Wolf wolf && wolf.isTame()) {
            return false;
        }

        Identifier entityId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        if (config.getIgnoredEntities().contains(entityId.toString()) ||
                config.getIgnoredMods().contains(entityId.getNamespace())) {
            return false;
        }

        return hasValidCustomNameForStacking(entity) && getStackSize(entity) < getMaxMobStackSize();
    }

    public static boolean canMerge(Mob self, Mob nearby) {
        if (self.getClass() != nearby.getClass() || !getCanStack(nearby)) {
            return false;
        }

        if ((getStackSize(self) + getStackSize(nearby)) > getMaxMobStackSize()) {
            return false;
        }

        BiPredicate<Mob, Mob> variantChecker = VARIANT_CHECKERS.get(self.getClass());
        if (variantChecker != null && !variantChecker.test(self, nearby)) {
            return false;
        }

        return MobStackerAPI.checkCustomMergingConditions(self, nearby);
    }

    private static boolean checkVillagerMatch(Villager self, Villager other) {
        return self.getVillagerData().type() == other.getVillagerData().type()
                && self.getVillagerData().profession().is(VillagerProfession.NONE)
                && other.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    private static boolean checkZombieVillagerMatch(ZombieVillager self, ZombieVillager other) {
        return self.getVillagerData().type() == other.getVillagerData().type()
                && self.getVillagerData().profession().is(VillagerProfession.NONE)
                && other.getVillagerData().profession().is(VillagerProfession.NONE);
    }

    private static boolean checkWolfMatch(Wolf self, Wolf other) {
        if (self.isTame() || other.isTame()) return false;
        return ((WolfMixin) self).mobstacker$getVariant() == ((WolfMixin) other).mobstacker$getVariant();
    }

    public static void spawnNewEntity(ServerLevel serverLevel, Mob self, int stackSize) {
        EntityType<?> entityType = self.getType();
        Mob newEntity = (Mob) entityType.create(serverLevel, EntitySpawnReason.NATURAL);
        if (newEntity == null) return;

        copyEntityData(self, newEntity, serverLevel);
        MobStacker.setStackSize(newEntity, stackSize - 1);
        serverLevel.addFreshEntity(newEntity);
    }

    private static void copyEntityData(Mob source, Mob target, ServerLevel serverLevel) {
        target.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(source.blockPosition()),
                EntitySpawnReason.NATURAL, null);
//        target.moveTo(source.position().x, source.position().y, source.position().z,
//                source.getYRot(), source.getXRot());
        target.setPos(source.position());
        target.setYRot(source.getYRot());
        target.setXRot(source.getXRot());
        target.yBodyRot = source.yBodyRot;

        if (source.hasCustomName()) {
            target.setCustomName(source.getCustomName());
        }

        copyVariantData(source, target);
        MobStackerAPI.applyEntityDataModifiers(source, target);
    }

    private static void copyVariantData(Mob source, Mob target) {
        if (source instanceof Sheep sourceSheep && target instanceof Sheep targetSheep) {
            targetSheep.setSheared(sourceSheep.isSheared());
            targetSheep.setColor(sourceSheep.getColor());
        } else if (source instanceof Villager sourceVillager && target instanceof Villager targetVillager) {
            targetVillager.setVillagerData(sourceVillager.getVillagerData());
        } else if (source instanceof ZombieVillager sourceZombie && target instanceof ZombieVillager targetZombie) {
            targetZombie.setVillagerData(sourceZombie.getVillagerData());
        } else if (source instanceof Slime sourceSlime && target instanceof Slime targetSlime) {
            targetSlime.setSize(sourceSlime.getSize(), true);
        } else if (source instanceof Frog sourceFrog && target instanceof Frog targetFrog) {
            ((FrogMixin) targetFrog).mobstacker$setVariant(sourceFrog.getVariant());
        } else if (source instanceof Axolotl sourceAxolotl && target instanceof Axolotl targetAxolotl) {
            ((AxolotlMixin) targetAxolotl).mobstacker$setVariant(sourceAxolotl.getVariant());
        } else if (source instanceof Cat sourceCat && target instanceof Cat targetCat) {
            ((CatMixin) targetCat).mobstacker$setVariant(sourceCat.getVariant());
        } else if (source instanceof Fox sourceFox && target instanceof Fox targetFox) {
            ((FoxMixin) targetFox).mobstacker$setVariant(sourceFox.getVariant());
        } else if (source instanceof MushroomCow sourceCow && target instanceof MushroomCow targetCow) {
            ((MushroomCowMixin) targetCow).mobstacker$setVariant(sourceCow.getVariant());
        } else if (source instanceof Wolf sourceWolf && target instanceof Wolf targetWolf) {
            ((WolfMixin) targetWolf).mobstacker$setVariant(((WolfMixin) sourceWolf).mobstacker$getVariant());
        }
    }

    public static void separateEntity(Mob entity) {
        if (entity.level().isClientSide()) return;

        try {
            ServerLevel serverLevel = (ServerLevel) entity.level();
            EntityType<?> entityType = entity.getType();
//            Mob newEntity = (Mob) entityType.create(entity.level());
            Mob newEntity = (Mob) entityType.create(entity.level(), EntitySpawnReason.NATURAL);
            if (newEntity == null) return;
            newEntity.setPos(entity.position());
            newEntity.setYRot(entity.getYRot());
            newEntity.setXRot(entity.getXRot());

            setStackSize(entity, getStackSize(entity) - 1);

            copyEntityDataForSeparation(entity, newEntity, serverLevel);
            handleHealthOnSeparation(entity, newEntity);

            // Apply custom entity data
            MobStackerAPI.applyEntityDataModifiersOnSeparation(entity, newEntity);
            entity.level().addFreshEntity(newEntity);

        } catch (Exception e) {
            setStackSize(entity, getStackSize(entity) + 1);
            logger.error("Error occurred while separating entity: {}", e.getMessage());
        }
    }

    private static void copyEntityDataForSeparation(Mob source, Mob target, ServerLevel serverLevel) {
        target.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(source.blockPosition()),
                EntitySpawnReason.NATURAL, null);
//        target.moveTo(source.position().x, source.position().y, source.position().z,
//                source.getYRot(), source.getXRot());
//        target.yBodyRot = source.yBodyRot;
        target.setPos(source.position());
        target.setYRot(source.getYRot());
        target.setXRot(source.getXRot());
        target.yBodyRot = source.yBodyRot;

        Component newName = Component.literal("Lone " + Almanac.getLocalizedEntityName(source.getType()).getString());
        target.setCustomName(newName);

        copyVariantData(source, target);
    }

    private static void handleHealthOnSeparation(Mob source, Mob target) {
        if (getStackHealth() && source.getHealth() > target.getMaxHealth()) {
            source.setHealth(source.getHealth() - target.getMaxHealth());
        }
    }

    public static void mergeEntities(Mob target, Mob source) {
        int newStackSize = Math.min(getStackSize(target) + getStackSize(source), getMaxMobStackSize());

        CompoundTag targetNbt = saveEntityToNbt(target);

        Almanac.dropEquipmentOnDiscard(source);
        Almanac.dropEquipmentOnDiscard(target);

        copyRelevantNbtData(source, targetNbt);

        updateStackDataInNbt(targetNbt, newStackSize);

        loadEntityFromNbt(target, targetNbt);

        updateHealth(target, source);

        source.discard();

        updateStackDisplay(target);
    }

    private static CompoundTag saveEntityToNbt(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    private static void loadEntityFromNbt(Entity entity, CompoundTag nbt) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), nbt));
    }

    private static void copyRelevantNbtData(Mob source, CompoundTag targetNbt) {
        CompoundTag sourceNbt = saveEntityToNbt(source);

//        sourceNbt.getAllKeys().stream()
//                .filter(key -> !isExcludedNbtKey(key))
//                .forEach(key -> targetNbt.put(key, sourceNbt.get(key)));
        sourceNbt.keySet().forEach(key -> targetNbt.put(key, sourceNbt.get(key)));
    }

    private static boolean isExcludedNbtKey(String key) {
        return key.equals("Pos") || key.equals("UUID") ||
                key.equals("Motion") || key.equals("Health");
    }

    private static void updateStackDataInNbt(CompoundTag nbt, int stackSize) {
//        CompoundTag stackData = nbt.contains(STACK_DATA_KEY, 10) ?
//                nbt.getCompound(STACK_DATA_KEY) : new CompoundTag();
        CompoundTag stackData = nbt.getCompoundOrEmpty(STACK_DATA_KEY);
        stackData.putInt(STACK_SIZE_KEY, stackSize);
        nbt.put(STACK_DATA_KEY, stackData);
    }

    public static void updateStackDisplay(Mob entity) {
        int stackSize = getStackSize(entity);
        boolean isBoss = isBossEntity(entity);

        Component newName = generateNewDisplayName(entity, stackSize);
        updateEntityName(entity, newName, isBoss);
    }

    private static Component generateNewDisplayName(Mob entity, int stackSize) {
        if (entity.hasCustomName() && !Almanac.matchesStackedName(entity.getCustomName().getString(), entity)) {
            String baseName = STACKED_NAME_PATTERN.matcher(entity.getCustomName().getString())
                    .replaceFirst("");
            return Component.literal(baseName + (stackSize > 1 ? " x" + stackSize : ""))
                    .withStyle(entity.getCustomName().getStyle());
        }
        return stackSize > 1 ?
                Component.literal(Almanac.getLocalizedEntityName(entity.getType()).getString() + " x" + stackSize) :
                null;
    }

    private static void updateEntityName(Mob entity, Component newName, boolean isBoss) {
        if (newName != null) {
            entity.setCustomName(newName);
            if (isBoss) {
                ServerBossEvent bossEvent = getBossField(entity);
                if (bossEvent != null) {
                    bossEvent.setName(newName);
                }
            }
        } else if (isBoss) {
            handleBossNameReset(entity);
        } else if (entity.hasCustomName()) {
            entity.setCustomName(null);
        }

        if (!isBoss && Almanac.hasNonCustomName(entity)) {
            entity.setCustomNameVisible(false);
        }
    }

    private static void handleBossNameReset(Mob entity) {
        delayExecution((ServerLevel) entity.level(), 1, () -> {
            ServerBossEvent bossEvent = getBossField(entity);
            if (bossEvent != null) {
                bossEvent.setName(Component.literal(entity.getDisplayName().getString()));
                entity.setCustomName(null);
            }
        });
    }

    public static void updateHealth(Mob target, Mob source) {
        double maxHealth = target.getMaxHealth();
        float newHealth = target.getHealth() + source.getHealth();

        if (getStackHealth() && getKillWholeStackOnDeath()) {
            maxHealth += source.getMaxHealth();
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(maxHealth);
        }

        target.setHealth(Math.min(newHealth, (float) maxHealth));
    }

    public static void delayExecution(ServerLevel serverLevel, int ticksDelay, Runnable task) {
        serverLevel.getServer().execute(new DelayedTask(serverLevel, ticksDelay, task));
    }

    private static class DelayedTask implements Runnable {
        private final ServerLevel serverLevel;
        private final Runnable task;
        private int ticksLeft;

        DelayedTask(ServerLevel serverLevel, int ticksDelay, Runnable task) {
            this.serverLevel = serverLevel;
            this.ticksLeft = ticksDelay;
            this.task = task;
        }

        @Override
        public void run() {
            if (ticksLeft > 0) {
                ticksLeft--;
                serverLevel.getServer().execute(this);
            } else {
                task.run();
            }
        }
    }

    public static boolean hasValidCustomNameForStacking(Mob entity) {
        return !entity.hasCustomName() ||
                matchesStackedName(entity.getCustomName().getString(), entity);
    }

    public static boolean matchesStackedName(String customName, Entity entity) {
        return Pattern.compile(Pattern.quote(Almanac.getLocalizedEntityName(entity.getType()).getString())
                + " x\\d+").matcher(customName).find();
    }

    public static boolean shouldSpawnNewEntity(Mob entity, Entity.RemovalReason reason) {
        return (entity instanceof Creeper creeper && creeper.isIgnited()) ||
                reason == Entity.RemovalReason.KILLED;
    }

    public static boolean isBossEntity(Entity entity) {
        if (!(entity instanceof Mob)) return false;

        return bossEntityCache.computeIfAbsent(entity.getClass(), clazz -> {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.getType().equals(ServerBossEvent.class)) {
                    bossFieldCache.put(clazz, field);
                    return true;
                }
            }
            return false;
        });
    }

    public static ServerBossEvent getBossField(Entity entity) {
        Field field = bossFieldCache.get(entity.getClass());
        if (field == null) return null;

        try {
            field.setAccessible(true);
            return (ServerBossEvent) field.get(entity);
        } catch (IllegalAccessException e) {
            logger.error("Could not access ServerBossEvent field for entity: {} ", entity.getType(), e);
            return null;
        }
    }

    public static int getStackSize(Mob entity) {
        if (entity instanceof ICustomDataHolder holder) {
            return holder.mobstacker$getStackSize();
        }
        return 1;
    }

    public static void setStackSize(Mob entity, int size) {
        if (entity instanceof ICustomDataHolder holder) {
            holder.mobstacker$setStackSize(size);
            updateStackDisplay(entity);
        }
    }

    public static boolean getCanStack(Mob entity) {
        if (entity instanceof ICustomDataHolder holder) {
            return holder.mobstacker$getCanStack();
        }
        return true;
    }

    public static void setCanStack(Mob entity, boolean canStack) {
        if (entity instanceof ICustomDataHolder holder) {
            holder.mobstacker$setCanStack(canStack);
        }
    }


    public static double getStackRadius() {return config.getStackRadius();}

    public static int getMaxMobStackSize() {return config.getMaxMobStackSize();}

    public static boolean getKillWholeStackOnDeath() {return config.getKillWholeStackOnDeath();}

    public static boolean getStackHealth() {return config.getStackHealth();}

    public static boolean getEnableSeparator() {return config.getEnableSeparator();}

    public static boolean getConsumeSeparator() {return config.getConsumeSeparator();}

    public static String getSeparatorItem() {return config.getSeparatorItem();}

    public static boolean getStackOutgoingDamage() {return config.getStackOutgoingDamage();}

    public static int getKillBatchSize() {return config.getKillBatchSize();}
    public static int getBreedBatchSize() {return config.getBreedBatchSize();}
    public static int getShearBatchSize() {return config.getShearBatchSize();}
    public static int getBreedCooldown() {return config.getBreedCooldown();}

    public static int getMonsterMobCap() {return config.getMonsterMobCap();}
    public static int getCreatureMobCap() {return config.getCreatureMobCap();}
    public static int getAmbientMobCap() {return config.getAmbientMobCap();}
    public static int getAxolotlsMobCap() {return config.getAxolotlsMobCap();}
    public static int getUndergroundWaterCreatureMobCap() {return config.getUndergroundWaterCreatureMobCap();}
    public static int getWaterCreatureMobCap() {return config.getWaterCreatureMobCap();}
    public static int getWaterAmbientMobCap() {return config.getWaterAmbientMobCap();}

}
