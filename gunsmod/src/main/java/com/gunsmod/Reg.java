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
import com.gunsmod.block.WorkbenchBlock;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.GrenadeEntity;
import com.gunsmod.entity.JeepEntity;
import com.gunsmod.entity.MissileEntity;
import com.gunsmod.entity.RocketEntity;
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
import com.gunsmod.item.RemoteItem;
import com.gunsmod.item.RemoteLinks;
import com.gunsmod.item.SoldierItem;
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

    static {
        for (String s : new String[]{"shot_pistol", "shot_magnum", "shot_smg", "shot_rifle", "shot_carbine", "shot_shotgun", "shot_sniper",
                "shot_heavy", "shot_lmg", "shot_minigun", "shot_turret", "shot_suppressed", "shot_grenade_launcher", "rocket_launch",
                "flamethrower_loop", "dry_fire", "mag_out", "mag_in", "bolt", "shell_insert", "pump", "break_open", "jeep_idle",
                "jeep_drive", "jeep_start", "jeep_horn", "missile_launch", "missile_alarm", "cover_open", "arm_switch", "ui_beep",
                "ui_confirm", "hitmarker", "grenade_bounce", "bullet_whiz", "turret_overheat", "heavy_launch", "explosion_big",
                "explosion_far", "missile_incoming"}) {
            SOUND.put(s, SOUNDS.register(s, () -> SoundEvent.createVariableRangeEvent(id(s))));
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

    // ------------------------------------------------------------------------------ block entities
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LauncherBlockEntity>> LAUNCHER_BE = BLOCK_ENTITIES.register("missile_launcher",
            () -> BlockEntityType.Builder.of(LauncherBlockEntity::new, LAUNCHER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HeavyLauncherBlockEntity>> HEAVY_LAUNCHER_BE = BLOCK_ENTITIES.register("heavy_launcher",
            () -> BlockEntityType.Builder.of(HeavyLauncherBlockEntity::new, HEAVY_LAUNCHER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerBlockEntity>> SPAWNER_BE = BLOCK_ENTITIES.register("bot_spawner",
            () -> BlockEntityType.Builder.of(SpawnerBlockEntity::new, SPAWNER.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArenaBlockEntity>> ARENA_BE = BLOCK_ENTITIES.register("arena_controller",
            () -> BlockEntityType.Builder.of(ArenaBlockEntity::new, ARENA_CONTROLLER.get()).build(null));

    // ------------------------------------------------------------------------------ entities
    public static final DeferredHolder<EntityType<?>, EntityType<BotEntity>> BOT = ENTITIES.register("soldier",
            () -> EntityType.Builder.of(BotEntity::new, MobCategory.MISC).sized(0.6f, 1.8f).eyeHeight(1.62f).clientTrackingRange(10).build("soldier"));
    public static final DeferredHolder<EntityType<?>, EntityType<JeepEntity>> JEEP = ENTITIES.register("jeep",
            () -> EntityType.Builder.of(JeepEntity::new, MobCategory.MISC).sized(2.6f, 2.0f).eyeHeight(1.4f).clientTrackingRange(12).updateInterval(1).build("jeep"));
    public static final DeferredHolder<EntityType<?>, EntityType<RocketEntity>> ROCKET = ENTITIES.register("rocket",
            () -> EntityType.Builder.<RocketEntity>of(RocketEntity::new, MobCategory.MISC).sized(0.4f, 0.4f).clientTrackingRange(8).updateInterval(1).build("rocket"));
    public static final DeferredHolder<EntityType<?>, EntityType<GrenadeEntity>> GRENADE = ENTITIES.register("grenade",
            () -> EntityType.Builder.<GrenadeEntity>of(GrenadeEntity::new, MobCategory.MISC).sized(0.3f, 0.3f).clientTrackingRange(6).updateInterval(2).build("grenade"));
    public static final DeferredHolder<EntityType<?>, EntityType<MissileEntity>> MISSILE = ENTITIES.register("missile",
            () -> EntityType.Builder.<MissileEntity>of(MissileEntity::new, MobCategory.MISC).sized(0.7f, 0.7f).clientTrackingRange(16).updateInterval(1).build("missile"));

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
    public static final DeferredItem<SoldierItem> GREEN_SOLDIER = ITEMS.register("green_soldier", () -> new SoldierItem(BotTeam.GREEN, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<SoldierItem> RED_SOLDIER = ITEMS.register("red_soldier", () -> new SoldierItem(BotTeam.RED, new Item.Properties().stacksTo(16)));
    public static final DeferredItem<JeepItem> GREEN_JEEP = ITEMS.register("green_jeep", () -> new JeepItem(BotTeam.GREEN, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final DeferredItem<JeepItem> RED_JEEP = ITEMS.register("red_jeep", () -> new JeepItem(BotTeam.RED, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
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
        blockItem("arena_controller", ARENA_CONTROLLER);
        blockItem("sandbags", SANDBAGS);
        blockItem("sandbags_low", SANDBAGS_LOW);
        blockItem("concrete_barrier", CONCRETE_BARRIER);
        blockItem("barbed_wire", BARBED_WIRE);
        blockItem("explosive_barrel", EXPLOSIVE_BARREL);
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
                out.accept(GREEN_SOLDIER.get());
                out.accept(RED_SOLDIER.get());
                out.accept(GREEN_JEEP.get());
                out.accept(RED_JEEP.get());
                out.accept(DOG_TAG.get());
                out.accept(ARENA_TOOL.get());
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
