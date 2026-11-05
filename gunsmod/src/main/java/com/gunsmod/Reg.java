package com.gunsmod;

import com.gunsmod.block.AmmoCrateBlock;
import com.gunsmod.block.ArenaBlockEntity;
import com.gunsmod.block.ArenaControllerBlock;
import com.gunsmod.block.BarbedWireBlock;
import com.gunsmod.block.CoverBlock;
import com.gunsmod.block.ExplosiveBarrelBlock;
import com.gunsmod.block.HeavyLauncherBlock;
import com.gunsmod.block.HeavyLauncherBlockEntity;
import com.gunsmod.block.HeavyLauncherPartBlock;
import com.gunsmod.block.LauncherBlock;
import com.gunsmod.block.LauncherBlockEntity;
import com.gunsmod.block.ModTableBlock;
import com.gunsmod.block.SpawnerBlock;
import com.gunsmod.block.SpawnerBlockEntity;
import com.gunsmod.block.VehicleSpawnerBlock;
import com.gunsmod.block.VehicleSpawnerBlockEntity;
import com.gunsmod.block.WorkbenchBlock;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.ArmoredCarEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.CarType;
import com.gunsmod.entity.FlareEntity;
import com.gunsmod.entity.MineEntity;
import com.gunsmod.entity.GuidedMissileEntity;
import com.gunsmod.entity.GrenadeEntity;
import com.gunsmod.entity.HelicopterEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.JetEntity;
import com.gunsmod.entity.MissileEntity;
import com.gunsmod.entity.RocketEntity;
import com.gunsmod.entity.HeliType;
import com.gunsmod.entity.TankEntity;
import com.gunsmod.entity.TankType;
import com.gunsmod.entity.VehicleKind;
import com.gunsmod.gun.AmmoType;
import com.gunsmod.gun.Attachment;
import com.gunsmod.gun.GunAttachments;
import com.gunsmod.gun.GunType;
import com.gunsmod.item.AmmoItem;
import com.gunsmod.item.ArenaToolItem;
import com.gunsmod.item.AttachmentItem;
import com.gunsmod.item.DogTagItem;
import com.gunsmod.item.GrenadeItem;
import com.gunsmod.item.GunItem;
import com.gunsmod.item.JeepItem;
import com.gunsmod.item.MissileItem;
import com.gunsmod.item.OrchardToolItem;
import com.gunsmod.item.RouteToolItem;
import com.gunsmod.item.RemoteItem;
import com.gunsmod.item.RemoteLinks;
import com.gunsmod.item.SoldierItem;
import com.gunsmod.item.VehicleItem;
import com.gunsmod.menu.ArenaMenu;
import com.gunsmod.menu.BotMenu;
import com.gunsmod.menu.ModTableMenu;
import com.gunsmod.menu.SpawnerMenu;
import com.gunsmod.menu.WorkbenchMenu;
import com.mojang.serialization.Codec;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Every registry entry of the mod. */
public final class Reg {
    private Reg() {}

    public static final String MODID = GunsMod.MODID;

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, MODID);
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MODID);

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    // ------------------------------------------------------------------------------ data components
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> AMMO = COMPONENTS.registerComponentType("ammo",
            b -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> RELOAD = COMPONENTS.registerComponentType("reload",
            b -> b.persistent(Codec.LONG).networkSynchronized(ByteBufCodecs.VAR_LONG));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GunAttachments>> ATTACHMENTS = COMPONENTS.registerComponentType("attachments",
            b -> b.persistent(GunAttachments.CODEC).networkSynchronized(GunAttachments.STREAM_CODEC));
    /** single linked station of 1.0 remotes; read once and converted to {@link #REMOTE_LINKS} */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockPos>> LINK = COMPONENTS.registerComponentType("link",
            b -> b.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RemoteLinks>> REMOTE_LINKS = COMPONENTS.registerComponentType("remote_links",
            b -> b.persistent(RemoteLinks.CODEC).networkSynchronized(RemoteLinks.STREAM_CODEC));

    // ------------------------------------------------------------------------------ sounds
    public static final Map<String, DeferredHolder<SoundEvent, SoundEvent>> SOUND = new LinkedHashMap<>();
    /** blocks the server sends a sound out to (the client fades it by its own distance) */
    public static final float SOUND_RANGE = 512f;

    static {
        for (String s : SoundNames.ALL) {
            // heard five times further than vanilla (attenuation_distance 80 in sounds.json): the server sends them that far too
            SOUND.put(s, SOUNDS.register(s, () -> s.startsWith("ui_") ? SoundEvent.createVariableRangeEvent(id(s)) : SoundEvent.createFixedRangeEvent(id(s), SOUND_RANGE)));
        }
    }

    public static SoundEvent sound(String name) {
        DeferredHolder<SoundEvent, SoundEvent> h = SOUND.get(name);
        return h == null ? SOUND.get("ui_beep").get() : h.get();
    }

    // ------------------------------------------------------------------------------ particles
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MUZZLE_FLASH = particle("muzzle_flash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> TRACER = particle("tracer");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE = particle("smoke");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> MISSILE_SMOKE = particle("missile_smoke");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLAME = particle("flame");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SPARK = particle("spark");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHELL = particle("shell");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> HOLO = particle("holo");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FIREBALL = particle("fireball");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DUST_CLOUD = particle("dust_cloud");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SHOCKWAVE = particle("shockwave");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> EMBER = particle("ember");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_FLASH = particle("blast_flash");
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> FLARE = particle("flare");

    private static DeferredHolder<ParticleType<?>, SimpleParticleType> particle(String name) {
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }

    // ------------------------------------------------------------------------------ blocks
    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops();
    }

    public static final DeferredBlock<WorkbenchBlock> WORKBENCH = BLOCKS.register("weapon_workbench", () -> new WorkbenchBlock(metal()));
    public static final DeferredBlock<ModTableBlock> MOD_TABLE = BLOCKS.register("modification_table", () -> new ModTableBlock(metal()));
    public static final DeferredBlock<AmmoCrateBlock> AMMO_CRATE = BLOCKS.register("ammo_crate",
            () -> new AmmoCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion()));
    public static final DeferredBlock<LauncherBlock> LAUNCHER = BLOCKS.register("missile_launcher", () -> new LauncherBlock(metal().strength(6f, 20f)));
    public static final DeferredBlock<HeavyLauncherBlock> HEAVY_LAUNCHER = BLOCKS.register("heavy_launcher",
            () -> new HeavyLauncherBlock(metal().strength(8f, 60f).pushReaction(PushReaction.BLOCK)));
    public static final DeferredBlock<HeavyLauncherPartBlock> HEAVY_LAUNCHER_PART = BLOCKS.register("heavy_launcher_part",
            () -> new HeavyLauncherPartBlock(metal().strength(8f, 60f).pushReaction(PushReaction.BLOCK).noLootTable()));
    public static final DeferredBlock<SpawnerBlock> SPAWNER = BLOCKS.register("bot_spawner", () -> new SpawnerBlock(metal().lightLevel(s -> 9)));
    public static final DeferredBlock<VehicleSpawnerBlock> VEHICLE_SPAWNER = BLOCKS.register("vehicle_spawner",
            () -> new VehicleSpawnerBlock(metal().strength(6f, 30f).lightLevel(s -> 7)));
    public static final DeferredBlock<ArenaControllerBlock> ARENA_CONTROLLER = BLOCKS.register("arena_controller",
            () -> new ArenaControllerBlock(metal().lightLevel(s -> 7)));
    public static final DeferredBlock<CoverBlock> SANDBAGS = BLOCKS.register("sandbags",
            () -> new CoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5f, 12f).sound(SoundType.SAND).noOcclusion(), 0));
    public static final DeferredBlock<CoverBlock> SANDBAGS_LOW = BLOCKS.register("sandbags_low",
            () -> new CoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5f, 12f).sound(SoundType.SAND).noOcclusion(), 1));
    public static final DeferredBlock<CoverBlock> CONCRETE_BARRIER = BLOCKS.register("concrete_barrier",
            () -> new CoverBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4f, 30f).sound(SoundType.STONE).noOcclusion().requiresCorrectToolForDrops(), 2));
    public static final DeferredBlock<BarbedWireBlock> BARBED_WIRE = BLOCKS.register("barbed_wire",
            () -> new BarbedWireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.CHAIN).noOcclusion().noCollission()));
    public static final DeferredBlock<ExplosiveBarrelBlock> EXPLOSIVE_BARREL = BLOCKS.register("explosive_barrel",
            () -> new ExplosiveBarrelBlock(metal().strength(1.5f, 2f)));

    // 3.0: battle objectives
    public static final DeferredBlock<com.gunsmod.game.ObjectiveBlock> CAPTURE_POINT = BLOCKS.register("capture_point",
            () -> new com.gunsmod.game.ObjectiveBlock(metal().strength(4f, 3600000f).noOcclusion(), com.gunsmod.game.ObjectiveBlock.Kind.POINT));
    public static final DeferredBlock<com.gunsmod.game.ObjectiveBlock> CTF_FLAG = BLOCKS.register("ctf_flag",
            () -> new com.gunsmod.game.ObjectiveBlock(metal().strength(4f, 3600000f).noOcclusion(), com.gunsmod.game.ObjectiveBlock.Kind.FLAG));
    public static final DeferredBlock<com.gunsmod.game.ObjectiveBlock> OBJECTIVE_TARGET = BLOCKS.register("objective_target",
            () -> new com.gunsmod.game.ObjectiveBlock(metal().strength(4f, 3600000f).noOcclusion(), com.gunsmod.game.ObjectiveBlock.Kind.TARGET));
    public static final DeferredBlock<com.gunsmod.game.ObjectiveBlock> CONVOY_DESTINATION = BLOCKS.register("convoy_destination",
            () -> new com.gunsmod.game.ObjectiveBlock(metal().strength(4f, 3600000f).noOcclusion(), com.gunsmod.game.ObjectiveBlock.Kind.DESTINATION));
    public static final DeferredBlock<com.gunsmod.game.ObjectiveBlock> MISSION_CONTROL = BLOCKS.register("mission_control",
            () -> new com.gunsmod.game.ObjectiveBlock(metal().strength(3f, 20f).noOcclusion().lightLevel(st -> 6), com.gunsmod.game.ObjectiveBlock.Kind.CONTROL));

    // ------------------------------------------------------------------------------ block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.gunsmod.game.ObjectiveBlockEntity>> OBJECTIVE_BE = BLOCK_ENTITIES.register("objective",
            () -> BlockEntityType.Builder.of(com.gunsmod.game.ObjectiveBlockEntity::new, CAPTURE_POINT.get(), CTF_FLAG.get(), OBJECTIVE_TARGET.get(),
                    CONVOY_DESTINATION.get(), MISSION_CONTROL.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherBlockEntity>> LAUNCHER_BE = BLOCK_ENTITIES.register("missile_launcher",
            () -> BlockEntityType.Builder.of(LauncherBlockEntity::new, LAUNCHER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeavyLauncherBlockEntity>> HEAVY_LAUNCHER_BE = BLOCK_ENTITIES.register("heavy_launcher",
            () -> BlockEntityType.Builder.of(HeavyLauncherBlockEntity::new, HEAVY_LAUNCHER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerBlockEntity>> SPAWNER_BE = BLOCK_ENTITIES.register("bot_spawner",
            () -> BlockEntityType.Builder.of(SpawnerBlockEntity::new, SPAWNER.get()).build(null));
    // ------------------------------------------------------------------------------ air defence: radars and launchers
    public static final Map<com.gunsmod.block.airdef.RadarType, DeferredBlock<com.gunsmod.block.airdef.AirDefenseBlock>> RADARS =
            new EnumMap<>(com.gunsmod.block.airdef.RadarType.class);
    public static final Map<com.gunsmod.block.airdef.LauncherType, DeferredBlock<com.gunsmod.block.airdef.AirDefenseBlock>> AD_LAUNCHERS =
            new EnumMap<>(com.gunsmod.block.airdef.LauncherType.class);

    static {
        for (com.gunsmod.block.airdef.RadarType t : com.gunsmod.block.airdef.RadarType.values()) {
            RADARS.put(t, BLOCKS.register(t.id, () -> new com.gunsmod.block.airdef.AirDefenseBlock(metal().strength(6f, 20f), t, null)));
        }
        for (com.gunsmod.block.airdef.LauncherType t : com.gunsmod.block.airdef.LauncherType.values()) {
            AD_LAUNCHERS.put(t, BLOCKS.register(t.id, () -> new com.gunsmod.block.airdef.AirDefenseBlock(metal().strength(6f, 20f), null, t)));
        }
    }

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.gunsmod.block.airdef.RadarBlockEntity>> RADAR_BE = BLOCK_ENTITIES.register("radar",
            () -> BlockEntityType.Builder.of(com.gunsmod.block.airdef.RadarBlockEntity::new,
                    RADARS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.gunsmod.block.airdef.DefenseBlockEntity>> DEFENSE_BE = BLOCK_ENTITIES.register("air_defense",
            () -> BlockEntityType.Builder.of(com.gunsmod.block.airdef.DefenseBlockEntity::new,
                    AD_LAUNCHERS.values().stream().map(DeferredBlock::get).toArray(Block[]::new)).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VehicleSpawnerBlockEntity>> VEHICLE_SPAWNER_BE = BLOCK_ENTITIES.register("vehicle_spawner",
            () -> BlockEntityType.Builder.of(VehicleSpawnerBlockEntity::new, VEHICLE_SPAWNER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArenaBlockEntity>> ARENA_BE = BLOCK_ENTITIES.register("arena_controller",
            () -> BlockEntityType.Builder.of(ArenaBlockEntity::new, ARENA_CONTROLLER.get()).build(null));

    // ------------------------------------------------------------------------------ entities
    public static final DeferredHolder<EntityType<?>, EntityType<BotEntity>> BOT = ENTITIES.register("soldier",
            () -> EntityType.Builder.of(BotEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("soldier"));
    public static final DeferredHolder<EntityType<?>, EntityType<JeepEntity>> JEEP = ENTITIES.register("jeep",
            () -> EntityType.Builder.<JeepEntity>of(JeepEntity::new, MobCategory.MISC).sized(2.6f, 2.0f).eyeHeight(1.4f).clientTrackingRange(12).updateInterval(1).build("jeep"));
    public static final DeferredHolder<EntityType<?>, EntityType<JeepEntity>> JEEP_TOW = ENTITIES.register("jeep_tow",
            () -> EntityType.Builder.<JeepEntity>of((t, l) -> new JeepEntity(t, l, true), MobCategory.MISC).sized(2.6f, 2.0f).eyeHeight(1.4f)
                    .clientTrackingRange(12).updateInterval(1).build("jeep_tow"));
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK = tank("tank", TankType.ABRAMS);
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK_LEOPARD = tank("tank_leopard", TankType.LEOPARD);
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK_T90 = tank("tank_t90", TankType.T90);
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK_CHALLENGER = tank("tank_challenger", TankType.CHALLENGER);
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK_MAUS = tank("tank_maus", TankType.MAUS);
    public static final DeferredHolder<EntityType<?>, EntityType<TankEntity>> TANK_OBJ279 = tank("tank_obj279", TankType.OBJ279);
    public static final DeferredHolder<EntityType<?>, EntityType<HelicopterEntity>> HELICOPTER = heli("helicopter", HeliType.COBRA);
    public static final DeferredHolder<EntityType<?>, EntityType<HelicopterEntity>> HELI_APACHE = heli("heli_apache", HeliType.APACHE);
    public static final DeferredHolder<EntityType<?>, EntityType<HelicopterEntity>> HELI_HIND = heli("heli_hind", HeliType.HIND);
    public static final DeferredHolder<EntityType<?>, EntityType<HelicopterEntity>> HELI_KA52 = heli("heli_ka52", HeliType.KA52);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> BTR = car("btr82", CarType.BTR);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> PANTSIR = car("pantsir", CarType.PANTSIR);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> HIMARS = car("himars", CarType.HIMARS);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> GRAD = car("grad", CarType.GRAD);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> CRAM = car("cram", CarType.CRAM);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> PICKUP = car("pickup", CarType.PICKUP);
    public static final DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> SUV = car("suv", CarType.SUV);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> F16 = jet("f16", com.gunsmod.entity.JetType.F16);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> B2 = jet("b2", com.gunsmod.entity.JetType.B2);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> F22 = jet("f22", com.gunsmod.entity.JetType.F22);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> F35 = jet("f35", com.gunsmod.entity.JetType.F35);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> SU35 = jet("su35", com.gunsmod.entity.JetType.SU35);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> MIG29 = jet("mig29", com.gunsmod.entity.JetType.MIG29);
    public static final DeferredHolder<EntityType<?>, EntityType<JetEntity>> A10 = jet("a10", com.gunsmod.entity.JetType.A10);

    private static DeferredHolder<EntityType<?>, EntityType<JetEntity>> jet(String id, com.gunsmod.entity.JetType k) {
        return ENTITIES.register(id, () -> EntityType.Builder.<JetEntity>of((t, l) -> new JetEntity(t, l, k), MobCategory.MISC)
                .sized((float) k.geo.size()[0], (float) k.geo.size()[1]).eyeHeight(1.8f).clientTrackingRange(k.rpgOnly() ? 32 : 24)
                .updateInterval(1).build(id));
    }

    private static DeferredHolder<EntityType<?>, EntityType<TankEntity>> tank(String id, TankType k) {
        return ENTITIES.register(id, () -> EntityType.Builder.<TankEntity>of((t, l) -> new TankEntity(t, l, k), MobCategory.MISC)
                .sized((float) k.geo.size()[0], (float) k.geo.size()[1]).eyeHeight(2.0f).clientTrackingRange(12).updateInterval(1).build(id));
    }

    private static DeferredHolder<EntityType<?>, EntityType<HelicopterEntity>> heli(String id, HeliType k) {
        return ENTITIES.register(id, () -> EntityType.Builder.<HelicopterEntity>of((t, l) -> new HelicopterEntity(t, l, k), MobCategory.MISC)
                .sized((float) k.geo.size()[0], (float) k.geo.size()[1]).eyeHeight(1.8f).clientTrackingRange(16).updateInterval(1).build(id));
    }

    private static DeferredHolder<EntityType<?>, EntityType<ArmoredCarEntity>> car(String id, CarType k) {
        return ENTITIES.register(id, () -> EntityType.Builder.<ArmoredCarEntity>of((t, l) -> new ArmoredCarEntity(t, l, k), MobCategory.MISC)
                .sized((float) k.geo.size()[0], (float) k.geo.size()[1]).eyeHeight(2.0f).clientTrackingRange(14).updateInterval(1).build(id));
    }

    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("rocket"));
    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeEntity>> GRENADE = ENTITIES.register("grenade",
            () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(2).build("grenade"));
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE = ENTITIES.register("missile",
            () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC).sized(0.7f, 0.7f).clientTrackingRange(16).updateInterval(1).build("missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<GuidedMissileEntity>> GUIDED_MISSILE = ENTITIES.register("guided_missile",
            () -> EntityType.Builder.<GuidedMissileEntity>of(GuidedMissileEntity::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(16)
                    .updateInterval(1).build("guided_missile"));
    public static final DeferredHolder<EntityType<?>, EntityType<MineEntity>> MINE = ENTITIES.register("mine",
            () -> EntityType.Builder.<MineEntity>of(MineEntity::new, MobCategory.MISC).sized(0.6f, 0.2f).clientTrackingRange(8)
                    .updateInterval(20).build("mine"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.gunsmod.entity.WreckPieceEntity>> WRECK_PIECE = ENTITIES.register("wreck_piece",
            () -> EntityType.Builder.<com.gunsmod.entity.WreckPieceEntity>of(com.gunsmod.entity.WreckPieceEntity::new, MobCategory.MISC).sized(1.2f, 0.6f)
                    .clientTrackingRange(10).updateInterval(2).build("wreck_piece"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.gunsmod.entity.DroneEntity>> DRONE = ENTITIES.register("drone",
            () -> EntityType.Builder.<com.gunsmod.entity.DroneEntity>of(com.gunsmod.entity.DroneEntity::new, MobCategory.MISC).sized(0.9f, 0.45f)
                    .clientTrackingRange(16).updateInterval(1).build("drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<FlareEntity>> FLARE_ENTITY = ENTITIES.register("flare",
            () -> EntityType.Builder.<FlareEntity>of(FlareEntity::new, MobCategory.MISC).sized(0.25f, 0.25f).clientTrackingRange(12)
                    .updateInterval(1).build("flare"));

    // ------------------------------------------------------------------------------ menus
    public static final DeferredHolder<MenuType<?>, MenuType<WorkbenchMenu>> WORKBENCH_MENU = MENUS.register("weapon_workbench",
            () -> IMenuTypeExtension.create(WorkbenchMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ModTableMenu>> MOD_TABLE_MENU = MENUS.register("modification_table",
            () -> IMenuTypeExtension.create(ModTableMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<SpawnerMenu>> SPAWNER_MENU = MENUS.register("bot_spawner",
            () -> IMenuTypeExtension.create(SpawnerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ArenaMenu>> ARENA_MENU = MENUS.register("arena_controller",
            () -> IMenuTypeExtension.create(ArenaMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<BotMenu>> BOT_MENU = MENUS.register("soldier",
            () -> IMenuTypeExtension.create(BotMenu::new));

    // ------------------------------------------------------------------------------ items
    public static final Map<GunType, DeferredItem<GunItem>> GUNS = new EnumMap<>(GunType.class);
    public static final Map<AmmoType, DeferredItem<AmmoItem>> AMMO_ITEMS = new EnumMap<>(AmmoType.class);
    public static final Map<Attachment, DeferredItem<AttachmentItem>> ATTACHMENT_ITEMS = new EnumMap<>(Attachment.class);

    static {
        for (GunType g : GunType.values()) {
            Rarity r = switch (g.category) {
                case SNIPER, HEAVY, EXPLOSIVE, SPECIAL -> Rarity.EPIC;
                case RIFLE, SHOTGUN -> Rarity.RARE;
                default -> Rarity.UNCOMMON;
            };
            GUNS.put(g, ITEMS.register(g.id, () -> new GunItem(g, new Item.Properties().stacksTo(1).rarity(r))));
        }
        for (AmmoType a : AmmoType.values()) {
            AMMO_ITEMS.put(a, ITEMS.register(a.itemId, () -> new AmmoItem(a, a.isBox()
                    ? new Item.Properties().durability(a.perItem) : new Item.Properties().stacksTo(a == AmmoType.ROCKET ? 8 : 16))));
        }
        for (Attachment a : Attachment.values()) {
            ATTACHMENT_ITEMS.put(a, ITEMS.register(a.id, () -> new AttachmentItem(a, new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))));
        }
    }

    public static final DeferredItem<GrenadeItem> FRAG_GRENADE = ITEMS.register("frag_grenade", () -> new GrenadeItem(false, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<GrenadeItem> SMOKE_GRENADE = ITEMS.register("smoke_grenade", () -> new GrenadeItem(true, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<MissileItem> MISSILE_HE = missile("missile_he", MissileItem.HE);
    public static final DeferredItem<MissileItem> MISSILE_CLUSTER = missile("missile_cluster", MissileItem.CLUSTER);
    public static final DeferredItem<MissileItem> MISSILE_INCENDIARY = missile("missile_incendiary", MissileItem.INCENDIARY);
    public static final DeferredItem<MissileItem> MISSILE_THERMOBARIC = missile("missile_thermobaric", MissileItem.THERMOBARIC);
    public static final DeferredItem<MissileItem> MISSILE_BUNKER_BUSTER = missile("missile_bunker_buster", MissileItem.BUNKER_BUSTER);
    public static final DeferredItem<MissileItem> MISSILE_TITAN = missile("missile_titan", MissileItem.TITAN);

    private static DeferredItem<MissileItem> missile(String name, int kind) {
        return ITEMS.register(name, () -> new MissileItem(kind, new Item.Properties().stacksTo(MissileItem.isHeavy(kind) ? 2 : 4).rarity(Rarity.EPIC)));
    }
    public static final DeferredItem<RemoteItem> REMOTE = ITEMS.register("launch_remote", () -> new RemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<DogTagItem> DOG_TAG = ITEMS.register("dog_tag", () -> new DogTagItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ArenaToolItem> ARENA_TOOL = ITEMS.register("arena_tool", () -> new ArenaToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<OrchardToolItem> ORCHARD_TOOL = ITEMS.register("orchard_tool", () -> new OrchardToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<RouteToolItem> ROUTE_TOOL = ITEMS.register("route_planner", () -> new RouteToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<com.gunsmod.item.AirLinkToolItem> AD_LINK_TOOL = ITEMS.register("ad_link_tool",
            () -> new com.gunsmod.item.AirLinkToolItem(new Item.Properties().stacksTo(1)));
    /** the radar and launcher items, in order */
    public static final java.util.List<DeferredItem<com.gunsmod.item.AirDefenseItem>> AD_ITEMS = new java.util.ArrayList<>();

    static {
        for (var b : RADARS.values()) AD_ITEMS.add(ITEMS.register(b.getId().getPath(), () -> new com.gunsmod.item.AirDefenseItem(b.get(), new Item.Properties())));
        for (var b : AD_LAUNCHERS.values()) AD_ITEMS.add(ITEMS.register(b.getId().getPath(), () -> new com.gunsmod.item.AirDefenseItem(b.get(), new Item.Properties())));
    }

    public static final DeferredItem<com.gunsmod.item.DroneItem> FPV_DRONE = ITEMS.register("fpv_drone",
            () -> new com.gunsmod.item.DroneItem(com.gunsmod.entity.DroneEntity.Kind.FPV, new Item.Properties().stacksTo(8).rarity(Rarity.RARE)));
    public static final DeferredItem<com.gunsmod.item.DroneItem> SHAHED = ITEMS.register("shahed",
            () -> new com.gunsmod.item.DroneItem(com.gunsmod.entity.DroneEntity.Kind.SHAHED, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    // 3.0: drones flown from their camera
    public static final DeferredItem<com.gunsmod.item.DroneItem> RECON_DRONE = ITEMS.register("recon_drone",
            () -> new com.gunsmod.item.DroneItem(com.gunsmod.entity.DroneEntity.Kind.RECON, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<com.gunsmod.item.DroneItem> BOMBER_DRONE = ITEMS.register("bomber_drone",
            () -> new com.gunsmod.item.DroneItem(com.gunsmod.entity.DroneEntity.Kind.BOMBER, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<com.gunsmod.item.DroneItem> SURVEIL_DRONE = ITEMS.register("surveillance_drone",
            () -> new com.gunsmod.item.DroneItem(com.gunsmod.entity.DroneEntity.Kind.SURVEIL, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<com.gunsmod.item.DroneItem> LANCET = ITEMS.register("lancet",
            () -> new com.gunsmod.item.DroneItem(com.gunsmod.entity.DroneEntity.Kind.LANCET, new Item.Properties().stacksTo(4).rarity(Rarity.EPIC)));
    public static final DeferredItem<com.gunsmod.item.RepairKitItem> REPAIR_KIT = ITEMS.register("repair_kit",
            () -> new com.gunsmod.item.RepairKitItem(new Item.Properties().durability(64).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<com.gunsmod.item.CommandRadioItem> COMMAND_RADIO = ITEMS.register("command_radio",
            () -> new com.gunsmod.item.CommandRadioItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
    public static final DeferredItem<com.gunsmod.item.ConvoyToolItem> CONVOY_TOOL = ITEMS.register("convoy_planner",
            () -> new com.gunsmod.item.ConvoyToolItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<SoldierItem> GREEN_SOLDIER = ITEMS.register("green_soldier", () -> new SoldierItem(BotTeam.GREEN, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SoldierItem> RED_SOLDIER = ITEMS.register("red_soldier", () -> new SoldierItem(BotTeam.RED, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<JeepItem> GREEN_JEEP = ITEMS.register("green_jeep", () -> new JeepItem(BotTeam.GREEN, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<JeepItem> RED_JEEP = ITEMS.register("red_jeep", () -> new JeepItem(BotTeam.RED, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final Map<VehicleKind, DeferredItem<VehicleItem>> GREEN_VEHICLES = new EnumMap<>(VehicleKind.class);
    public static final Map<VehicleKind, DeferredItem<VehicleItem>> RED_VEHICLES = new EnumMap<>(VehicleKind.class);

    static {
        for (VehicleKind k : VehicleKind.values()) {
            GREEN_VEHICLES.put(k, vehicle("green_" + k.item, k, BotTeam.GREEN));
            RED_VEHICLES.put(k, vehicle("red_" + k.item, k, BotTeam.RED));
        }
    }

    private static DeferredItem<VehicleItem> vehicle(String name, VehicleKind kind, BotTeam team) {
        return ITEMS.register(name, () -> new VehicleItem(kind, team, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    }

    public static VehicleItem vehicleItem(VehicleKind kind, BotTeam team) {
        return (team == BotTeam.RED ? RED_VEHICLES : GREEN_VEHICLES).get(kind).get();
    }

    public static final DeferredItem<Item> STEEL_INGOT = ITEMS.register("steel_ingot", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> GUN_PARTS = ITEMS.register("gun_parts", () -> new Item(new Item.Properties()));

    public static final Map<String, DeferredItem<BlockItem>> BLOCK_ITEMS = new LinkedHashMap<>();

    static {
        blockItem("weapon_workbench", WORKBENCH);
        blockItem("modification_table", MOD_TABLE);
        blockItem("ammo_crate", AMMO_CRATE);
        blockItem("missile_launcher", LAUNCHER);
        blockItem("heavy_launcher", HEAVY_LAUNCHER);
        blockItem("bot_spawner", SPAWNER);
        blockItem("vehicle_spawner", VEHICLE_SPAWNER);
        blockItem("arena_controller", ARENA_CONTROLLER);
        blockItem("sandbags", SANDBAGS);
        blockItem("sandbags_low", SANDBAGS_LOW);
        blockItem("concrete_barrier", CONCRETE_BARRIER);
        blockItem("barbed_wire", BARBED_WIRE);
        blockItem("explosive_barrel", EXPLOSIVE_BARREL);
        blockItem("capture_point", CAPTURE_POINT);
        blockItem("ctf_flag", CTF_FLAG);
        blockItem("objective_target", OBJECTIVE_TARGET);
        blockItem("convoy_destination", CONVOY_DESTINATION);
        blockItem("mission_control", MISSION_CONTROL);
    }

    private static void blockItem(String name, Supplier<? extends Block> block) {
        BLOCK_ITEMS.put(name, ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties())));
    }

    public static GunItem gun(GunType t) {
        return GUNS.get(t).get();
    }

    public static ItemStack gunStack(GunType t) {
        return GunItem.create(t);
    }

    // ------------------------------------------------------------------------------ creative tab
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.gunsmod"))
            .icon(() -> GunItem.create(GunType.AK47))
            .displayItems((params, out) -> {
                for (GunType g : GunType.values()) out.accept(GunItem.create(g));
                for (DeferredItem<AmmoItem> a : AMMO_ITEMS.values()) out.accept(a.get());
                for (DeferredItem<AttachmentItem> a : ATTACHMENT_ITEMS.values()) out.accept(a.get());
                out.accept(FRAG_GRENADE.get());
                out.accept(SMOKE_GRENADE.get());
                out.accept(MISSILE_HE.get());
                out.accept(MISSILE_CLUSTER.get());
                out.accept(MISSILE_INCENDIARY.get());
                out.accept(MISSILE_THERMOBARIC.get());
                out.accept(MISSILE_BUNKER_BUSTER.get());
                out.accept(MISSILE_TITAN.get());
                out.accept(REMOTE.get());
                out.accept(COMMAND_RADIO.get());
                out.accept(REPAIR_KIT.get());
                out.accept(FPV_DRONE.get());
                out.accept(SHAHED.get());
                out.accept(RECON_DRONE.get());
                out.accept(BOMBER_DRONE.get());
                out.accept(SURVEIL_DRONE.get());
                out.accept(LANCET.get());
                out.accept(GREEN_SOLDIER.get());
                out.accept(RED_SOLDIER.get());
                out.accept(GREEN_JEEP.get());
                out.accept(RED_JEEP.get());
                for (VehicleKind k : VehicleKind.values()) {
                    out.accept(GREEN_VEHICLES.get(k).get());
                    out.accept(RED_VEHICLES.get(k).get());
                }
                out.accept(DOG_TAG.get());
                out.accept(ARENA_TOOL.get());
                out.accept(ORCHARD_TOOL.get());
                out.accept(ROUTE_TOOL.get());
                out.accept(CONVOY_TOOL.get());
                out.accept(AD_LINK_TOOL.get());
                for (var a : AD_ITEMS) out.accept(a.get());
                out.accept(STEEL_INGOT.get());
                out.accept(GUN_PARTS.get());
                for (DeferredItem<BlockItem> b : BLOCK_ITEMS.values()) out.accept(b.get());
            }).build());

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
        ITEMS.register(bus);
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        TABS.register(bus);
    }
}
