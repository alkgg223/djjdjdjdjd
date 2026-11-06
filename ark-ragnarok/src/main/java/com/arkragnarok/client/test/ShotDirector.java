package com.arkragnarok.client.test;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.registry.ModEntities;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.registries.BuiltInRegistries;
import com.arkragnarok.registry.ModItems;
import com.arkragnarok.species.Kibble;
import com.arkragnarok.entity.ResourceNode;
import com.arkragnarok.world.NodeKind;
import com.arkragnarok.building.PieceBlock;
import com.arkragnarok.building.PieceType;
import com.arkragnarok.building.Placement;
import com.arkragnarok.building.Tier;
import com.arkragnarok.registry.ModBuilding;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-game screenshot run for CI (-Dark.shots=true with --quickPlaySingleplayer): every species at real size next to a
 * villager for scale plus a close-up of its face, head-tracking checks (looking up and sideways for 2 s), then the
 * T-Rex from all sides, saddled with a rider, and frames of its animations; then the game closes.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ShotDirector {
    private static final Logger LOG = LoggerFactory.getLogger("ARK-SHOTS");
    private static final boolean ENABLED = Boolean.getBoolean("ark.shots");

    /**
     * angle: camera around the creature (0 = in front); dist/eye/lookY in blocks; target: "body", "head" or "rex"
     * (fixed numbers for the Rex suite); pitch/headYaw: forced look; forced: forced animation id.
     */
    private record Shot(String name, String species, float angle, float dist, float eye, float lookY, boolean saddled, int forced,
                        String trigger, int delay, boolean rider, float headYaw, float pitch, String target) {}

    private static final List<Shot> SHOTS = new ArrayList<>();

    private static void rex(String name, float angle, float dist, float eye, float lookY, boolean saddled, int forced, String trigger,
                            int delay, boolean rider, float headYaw) {
        SHOTS.add(new Shot(name, "tyrannosaurus", angle, dist, eye, lookY, saddled, forced, trigger, delay, rider, headYaw, 0, "rex"));
    }

    static {
        for (Species s : SpeciesList.all()) {
            // whole animal from the side, a little from the front, villager in front of it for scale
            SHOTS.add(new Shot("a_" + s.id, s.id, 70, 0, 0, 0, false, 0, null, 30, false, 0, 0, "body"));
            // the face, three-quarter front, at eye level
            SHOTS.add(new Shot("h_" + s.id, s.id, 38, 0, 0, 0, false, 0, null, 20, false, 0, 0, "head"));
        }
        // head tracking: looking up and to the side for 2 s - the neck must stay smooth (no twisting / winding up)
        for (String id : new String[] {"dodo", "therizinosaurus", "gallimimus", "utahraptor", "pachycephalosaurus", "triceratops",
                "brontosaurus", "tyrannosaurus"}) {
            SHOTS.add(new Shot("look_" + id, id, 60, 0, 0, 0, false, 0, null, 45, false, 45, -35, "body"));
        }
        SHOTS.add(new Shot("look_walk_therizinosaurus", "therizinosaurus", 300, 0, 0, 0, false, 1, null, 45, false, -40, -30, "body"));
        SHOTS.add(new Shot("look_walk_dodo", "dodo", 300, 0, 0, 0, false, 1, null, 45, false, -40, 30, "body"));
        rex("rex_front", 0, 16, 3.6f, 3.0f, false, 0, null, 30, false, 0);
        rex("rex_left", 90, 17, 3.0f, 2.6f, false, 0, null, 25, false, 0);
        rex("rex_back", 180, 16, 4.0f, 2.8f, false, 0, null, 25, false, 0);
        rex("rex_top", 60, 5, 18.0f, 1.8f, false, 0, null, 30, false, 0);
        rex("rex_look_left", 20, 12, 3.4f, 3.4f, false, 0, null, 25, false, 45);
        rex("rex_look_right", 340, 12, 3.4f, 3.4f, false, 0, null, 25, false, -45);
        rex("rex_saddle", 70, 11, 7.0f, 4.0f, true, 0, null, 30, false, 0);
        rex("rex_rider", 60, 13, 5.2f, 4.2f, true, 0, null, 35, true, 0);
        rex("rex_walk", 90, 17, 2.8f, 2.6f, false, 1, null, 23, false, 0);
        rex("rex_run", 90, 17, 2.8f, 2.6f, false, 2, null, 25, false, 0);
        rex("rex_roar", 40, 15, 3.6f, 3.6f, false, 0, "roar", 26, false, 0);
        rex("rex_bite", 40, 13, 3.0f, 3.0f, false, 0, "bite", 6, false, 0);
        rex("rex_attack", 40, 13, 3.0f, 3.0f, false, 0, "attack", 12, false, 0);
        rex("rex_death", 60, 15, 3.6f, 1.2f, false, 6, null, 75, false, 0);
        SHOTS.add(new Shot("trike_attack", "triceratops", 50, 0, 0, 0, false, 0, "attack", 12, false, 0, 0, "body"));
        SHOTS.add(new Shot("stego_attack", "stegosaurus", 300, 0, 0, 0, false, 0, "attack", 15, false, 0, 0, "body"));
        SHOTS.add(new Shot("bronto_walk", "brontosaurus", 80, 0, 0, 0, false, 1, null, 25, false, 0, 0, "body"));
        SHOTS.add(new Shot("raptor_pounce", "utahraptor", 70, 0, 0, 0, false, 0, "attack", 13, false, 0, 0, "body"));
    }

    /** item checks: every mod item in the hand (first and third person), in the inventory and on the ground */
    private record ItemShot(String name, int slot, int camera, boolean inventory) {}

    private static final List<ItemShot> ITEM_SHOTS = new ArrayList<>();
    private static final List<Item> ITEMS = new ArrayList<>();

    private static void itemList() {
        if (!ITEMS.isEmpty()) return;
        ITEMS.add(ModItems.SUPER_SPYGLASS.get());
        ITEMS.add(ModItems.TRANQ_ARROW.get());
        ITEMS.add(ModItems.NARCOTIC.get());
        ITEMS.add(ModItems.MEJOBERRY.get());
        ITEMS.add(ModItems.NARCOBERRY.get());
        ITEMS.add(ModItems.KIBBLE.get(Kibble.EXCEPTIONAL).get());
        ITEMS.add(ModItems.RAW_PRIME_MEAT.get());
        ITEMS.add(ModItems.COOKED_PRIME_MEAT.get());
        ITEMS.add(ModItems.DINO_SADDLE.get());
        for (Kibble k : Kibble.values()) if (k != Kibble.EXCEPTIONAL) ITEMS.add(ModItems.KIBBLE.get(k).get());
        for (var egg : ModItems.SPAWN_EGGS.values()) ITEMS.add(egg.get());
        for (int i = 0; i < 9; i++) {
            String n = BuiltInRegistries.ITEM.getKey(ITEMS.get(i)).getPath();
            ITEM_SHOTS.add(new ItemShot("item_fp_" + n, i, 0, false));
            ITEM_SHOTS.add(new ItemShot("item_tp_" + n, i, 2, false));
        }
        ITEM_SHOTS.add(new ItemShot("item_ground", 0, 0, false));
        ITEM_SHOTS.add(new ItemShot("item_inventory", 0, 0, true));
        ITEM_SHOTS.add(new ItemShot("nodes_overview", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("nodes_close", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("houses_front", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("house_close", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("houses_doors_open", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("house_inside", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("house_stations", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("economy_inventory", 0, 0, true));
        ITEM_SHOTS.add(new ItemShot("survivor_base", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("survivor_top", 8, 0, false));
        ITEM_SHOTS.add(new ItemShot("survivor_close", 8, 0, false));
    }

    private static int itemIndex;
    private static int phase = -1;
    private static int wait;
    private static int index;
    private static UUID creatureId;
    private static String creatureSpecies;
    private static UUID riderId;
    private static UUID scaleId;
    private static double ox, oy, oz;

    private ShotDirector() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer srv = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || srv == null) return;
        if (phase == -1) {
            phase = 0;
            wait = 120;
            mc.options.hideGui = true;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            LOG.info("ARK-SHOTS world loaded, preparing");
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            if (phase == 0) {
                srv.execute(() -> setup(srv, mc.player.getUUID()));
                phase = 1;
                wait = 40;
                return;
            }
            if (phase == 5) {
                if (itemIndex >= ITEM_SHOTS.size()) {
                    mc.setScreen(null);
                    LOG.info("ARK-SHOTS done ({} shots + {} item shots)", SHOTS.size(), ITEM_SHOTS.size());
                    phase = 3;
                    wait = 20;
                    return;
                }
                ItemShot it = ITEM_SHOTS.get(itemIndex);
                mc.player.getInventory().selected = it.slot();
                mc.options.setCameraType(it.camera() == 2 ? CameraType.THIRD_PERSON_FRONT : CameraType.FIRST_PERSON);
                if (it.name().equals("economy_inventory")) srv.execute(() -> economyInventory(srv, mc.player.getUUID()));
                if (it.inventory()) mc.setScreen(new InventoryScreen(mc.player));
                if (it.name().startsWith("house") || it.name().startsWith("survivor")) {
                    mc.options.hideGui = true;
                    String n = it.name();
                    srv.execute(() -> houseView(srv, mc.player.getUUID(), n));
                }
                if (it.name().startsWith("nodes")) {
                    mc.options.hideGui = true;
                    boolean close = it.name().endsWith("close");
                    srv.execute(() -> nodeView(srv, mc.player.getUUID(), close));
                }
                if (it.name().equals("item_ground")) {
                    srv.execute(() -> groundItems(srv, mc.player.getUUID()));
                    mc.player.getInventory().selected = 8;
                }
                phase = 6;
                wait = it.name().equals("item_ground") ? 30 : it.name().startsWith("survivor") ? 80
                        : it.name().startsWith("nodes") || it.name().startsWith("house") ? 40 : 12;
                return;
            }
            if (phase == 6) {
                ItemShot it = ITEM_SHOTS.get(itemIndex);
                Screenshot.grab(mc.gameDirectory, it.name() + ".png", mc.getMainRenderTarget(), msg -> LOG.info("ARK-SHOTS {}", msg.getString()));
                LOG.info("ARK-SHOTS captured {}", it.name());
                if (it.inventory()) mc.setScreen(null);
                itemIndex++;
                phase = 5;
                wait = 2;
                return;
            }
            if (phase == 1) {
                if (index >= SHOTS.size()) {
                    itemList();
                    srv.execute(() -> itemSetup(srv, mc.player.getUUID()));
                    mc.options.hideGui = false;
                    phase = 5;
                    wait = 40;
                    return;
                }
                Shot s = SHOTS.get(index);
                srv.execute(() -> stage(srv, mc.player.getUUID(), s));
                phase = 2;
                wait = s.delay();
                return;
            }
            if (phase == 2) {
                Shot s = SHOTS.get(index);
                Screenshot.grab(mc.gameDirectory, s.name() + ".png", mc.getMainRenderTarget(), msg -> LOG.info("ARK-SHOTS {}", msg.getString()));
                LOG.info("ARK-SHOTS captured {}", s.name());
                index++;
                phase = 1;
                wait = 2;
                return;
            }
            if (phase == 3) {
                phase = 4;
                mc.stop();
            }
        } catch (Throwable t) {
            LOG.error("ARK-SHOTS failed", t);
            phase = 3;
        }
    }

    private static void setup(IntegratedServer srv, UUID playerId) {
        ServerLevel level = srv.overworld();
        level.setDayTime(6000);
        level.setWeatherParameters(12000, 0, false, false);
        srv.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, srv);
        srv.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, srv);
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        BlockPos base = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp != null ? sp.blockPosition() : level.getSharedSpawnPos());
        ox = base.getX() + 0.5;
        oy = base.getY();
        oz = base.getZ() + 0.5;
        if (sp != null) {
            sp.setGameMode(GameType.SPECTATOR);   // no collisions with the creatures, nothing of the player in the frame
            // a tiny camera holder: its eye can go down to the eye level of a Compy or a Dodo
            AttributeInstance scale = sp.getAttribute(Attributes.SCALE);
            if (scale != null) scale.setBaseValue(0.0625);
        }
        // a villager standing beside the creature shows the real size
        Villager v = EntityType.VILLAGER.create(level);
        if (v != null) {
            v.moveTo(ox + 4, oy, oz + 3, 200f, 0f);
            v.setNoAi(true);
            v.setInvulnerable(true);
            level.addFreshEntity(v);
            scaleId = v.getUUID();
        }
    }

    private static void itemSetup(IntegratedServer srv, UUID playerId) {
        ServerLevel level = srv.overworld();
        Entity c = creatureId != null ? level.getEntity(creatureId) : null;
        if (c != null) c.discard();
        Entity v = scaleId != null ? level.getEntity(scaleId) : null;
        if (v != null) v.discard();
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp == null) return;
        sp.setGameMode(GameType.CREATIVE);
        AttributeInstance scale = sp.getAttribute(Attributes.SCALE);
        if (scale != null) scale.setBaseValue(1.0);
        sp.getAbilities().flying = false;
        sp.onUpdateAbilities();
        sp.teleportTo(level, ox + 0.5, oy, oz + 0.5, 0f, 18f);
        sp.setYHeadRot(0f);
        spawnNodes(level);
        buildHouses(level);
        placeStations(level);
        sp.getInventory().clearContent();
        for (int i = 0; i < ITEMS.size() && i < 36; i++) sp.getInventory().setItem(i, new ItemStack(ITEMS.get(i), i == 1 ? 16 : 1));
        sp.inventoryMenu.broadcastChanges();
    }

    /** a row of every resource node kind (rows) in every size (columns), north of the item spot */
    private static void spawnNodes(ServerLevel level) {
        for (NodeKind k : NodeKind.values()) {
            for (int size = 0; size < 3; size++) {
                ResourceNode n = ModEntities.RESOURCE_NODE.get().create(level);
                if (n == null) continue;
                double x = ox - 6 + size * 6;
                double z = oz - 10 - k.ordinal() * 6;
                n.moveTo(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z)) - 0.15, z,
                        40f * size + 25f * k.ordinal(), 0f);
                n.setup(k, size, 40f * size + 25f * k.ordinal());
                level.addFreshEntity(n);
            }
        }
    }

    private static final List<BlockPos> DOORFRAMES = new ArrayList<>();

    /** three small houses (thatch, wood, stone): foundation, three walls, a doorframe with its door, a ceiling */
    private static void buildHouses(ServerLevel level) {
        Tier[] tiers = Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            PieceBlock b = ModBuilding.PIECE_BLOCKS.get(tiers[i]).get();
            BlockPos c = BlockPos.containing(ox - 24 + i * 12, oy, oz + 20);
            Placement.place(level, b, PieceType.FOUNDATION, new Placement.Plan(c, Direction.NORTH));
            for (Direction f : new Direction[] {Direction.EAST, Direction.WEST, Direction.SOUTH}) {
                Placement.place(level, b, PieceType.WALL, new Placement.Plan(c.relative(f, 2).above(), f));
            }
            BlockPos df = c.relative(Direction.NORTH, 2).above();
            Placement.place(level, b, PieceType.DOORFRAME, new Placement.Plan(df, Direction.NORTH));
            Placement.installDoor(level, df, tiers[i]);
            DOORFRAMES.add(df);
            Placement.place(level, b, PieceType.CEILING, new Placement.Plan(c.above(3), Direction.NORTH));
        }
    }

    /** every station, storage box, the bed and crop plots in all stages in a row */
    private static void placeStations(ServerLevel level) {
        java.util.List<BlockState> row = new java.util.ArrayList<>();
        for (var b : new net.minecraft.world.level.block.Block[]{com.arkragnarok.economy.ModEconomy.CAMPFIRE.get(),
                com.arkragnarok.economy.ModEconomy.MORTAR.get(), com.arkragnarok.economy.ModEconomy.SMITHY.get(),
                com.arkragnarok.economy.ModEconomy.REFINING_FORGE.get(), com.arkragnarok.economy.ModEconomy.FABRICATOR.get(),
                com.arkragnarok.economy.ModEconomy.STORAGE_BOX.get(), com.arkragnarok.economy.ModEconomy.LARGE_STORAGE_BOX.get()}) {
            row.add(b.defaultBlockState().setValue(com.arkragnarok.economy.StationBlock.FACING, Direction.NORTH));
        }
        for (int a = 0; a < 3; a++) row.add(com.arkragnarok.economy.ModEconomy.CROP_PLOT.get().defaultBlockState().setValue(
                com.arkragnarok.economy.CropPlotBlock.AGE, a));
        int x0 = (int) Math.floor(ox) - row.size();
        int z = (int) Math.floor(oz) - 36;
        int y = (int) Math.floor(oy);
        for (int i = 0; i < row.size(); i++) level.setBlock(new BlockPos(x0 + i * 2, y, z), row.get(i), 3);
        // the bed (head + foot)
        BlockPos head = new BlockPos(x0 + row.size() * 2 + 1, y, z);
        var bed = com.arkragnarok.economy.ModEconomy.HIDE_BED.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.SOUTH);
        level.setBlock(head.north(), bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), 3);
        level.setBlock(head, bed.setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), 3);
    }

    private static void economyInventory(IntegratedServer srv, UUID playerId) {
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp == null) return;
        sp.setGameMode(GameType.SURVIVAL);
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        sp.getInventory().clearContent();
        int i = 0;
        for (var it : com.arkragnarok.economy.ModEconomy.ALL) {
            if (i >= 36) break;
            sp.getInventory().setItem(i++, new ItemStack(it.get(), 1 + (i % 5) * 3));
        }
        sp.inventoryMenu.broadcastChanges();
    }

    private static void houseView(IntegratedServer srv, UUID playerId, String name) {
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp == null) return;
        ServerLevel level = srv.overworld();
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        if (name.equals("houses_doors_open")) {
            for (BlockPos df : DOORFRAMES) {
                BlockState st = level.getBlockState(df);
                if (st.getBlock() instanceof PieceBlock pb) pb.toggleDoor(level, df);
            }
        }
        if (name.startsWith("survivor")) {
            // the house the survivor of the smoke test built by itself (and the survivor)
            var list = level.getEntities(ModEntities.SURVIVOR.get(), e -> true);
            if (list.isEmpty()) {
                LOG.info("ARK-SHOTS no survivor in the world");
                return;
            }
            com.arkragnarok.survivor.Survivor sv = list.get(0);
            BlockPos b = sv.base() != null ? sv.base() : sv.blockPosition();
            LOG.info("ARK-SHOTS survivor {} task {} base {} step {}", sv.getName().getString(), sv.task(), sv.base(), sv.buildStep());
            if (name.equals("survivor_base")) sp.teleportTo(level, b.getX() + 11.5, b.getY() + 7.0, b.getZ() - 11.5, 45f, 24f);
            else if (name.equals("survivor_top")) sp.teleportTo(level, b.getX() + 0.5, b.getY() + 26.0, b.getZ() + 6.5, 180f, 72f);
            else sp.teleportTo(level, sv.getX() + 2.5, sv.getY() + 1.6, sv.getZ() + 2.5, 135f, 10f);
            return;
        }
        if (name.equals("house_stations")) name = "stations";
        switch (name) {
            case "houses_front", "houses_doors_open" -> sp.teleportTo(level, ox + 0.5, oy + 6.0, oz - 4.0, 0f, 14f);
            case "house_close" -> sp.teleportTo(level, ox + 0.5, oy + 2.6, oz + 13.0, 0f, 6f);
            case "stations" -> sp.teleportTo(level, ox + 0.5, oy + 3.0, oz - 42.0, 0f, 20f);
            default -> sp.teleportTo(level, ox + 0.5, oy + 2.4, oz + 21.5, 180f, -25f);
        }
    }

    private static void nodeView(IntegratedServer srv, UUID playerId, boolean close) {
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp == null) return;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        if (close) sp.teleportTo(srv.overworld(), ox + 4.5, oy + 2.2, oz - 4.0, 200f, 22f);
        else sp.teleportTo(srv.overworld(), ox, oy + 9.0, oz - 1.0, 180f, 38f);
    }

    private static void groundItems(IntegratedServer srv, UUID playerId) {
        ServerLevel level = srv.overworld();
        for (int i = 0; i < 9; i++) {
            double a = (i - 4) * 0.32;
            ItemEntity e = new ItemEntity(level, ox + 0.5 + Math.sin(a) * 2.6, oy + 0.05, oz + 0.5 + Math.cos(a) * 2.6, new ItemStack(ITEMS.get(i)));
            e.setDeltaMovement(0, 0, 0);
            e.setPickUpDelay(32767);
            level.addFreshEntity(e);
        }
    }

    private static ArkCreature creature(ServerLevel level, String id) {
        Entity ent = creatureId != null ? level.getEntity(creatureId) : null;
        if (ent instanceof ArkCreature c && id.equals(creatureSpecies)) return c;
        if (ent != null) ent.discard();
        ArkCreature c = ModEntities.type(id).create(level);
        if (c == null) throw new IllegalStateException("could not create " + id);
        c.setLevel(1);
        c.moveTo(ox, oy, oz, 0f, 0f);
        c.setNoAi(true);
        c.setPersistenceRequired();
        c.setInvulnerable(true);
        level.addFreshEntity(c);
        creatureId = c.getUUID();
        creatureSpecies = id;
        return c;
    }

    private static void stage(IntegratedServer srv, UUID playerId, Shot s) {
        ServerLevel level = srv.overworld();
        ArkCreature c = creature(level, s.species());
        c.moveTo(ox, oy, oz, 0f, 0f);
        c.setYBodyRot(0f);
        c.setYHeadRot(s.headYaw());
        c.setXRot(s.pitch());
        c.xRotO = s.pitch();
        c.setSaddled(s.saddled());
        c.setForcedAnim(s.forced());
        if (s.trigger() != null) c.triggerAnim("action", s.trigger());
        Entity scale = scaleId != null ? level.getEntity(scaleId) : null;
        if (scale != null) {
            // in front of the nose and a little to the side: never between the camera and the creature
            Species sv = SpeciesList.get(s.species());
            double side = Math.sin(Math.toRadians(s.angle())) >= 0 ? -1 : 1;     // the side away from the camera
            scale.moveTo(ox + side * Math.max(0.9, sv.width * 0.6), oy, oz + sv.headZ * 1.15 + Math.max(0.9, sv.length * 0.12), 200f, 0f);
            if (scale instanceof Villager vv) vv.setInvisible(s.target().equals("head"));
        }
        if (s.rider() && riderId == null) {
            Villager v = EntityType.VILLAGER.create(level);
            if (v != null) {
                v.moveTo(ox, oy + 6, oz, 0f, 0f);
                v.setNoAi(true);
                v.setInvulnerable(true);
                level.addFreshEntity(v);
                v.startRiding(c, true);
                riderId = v.getUUID();
            }
        } else if (!s.rider() && riderId != null) {
            Entity v = level.getEntity(riderId);
            if (v != null) v.discard();
            riderId = null;
        }
        Species sp_ = SpeciesList.get(s.species());
        // what the camera looks at, and from how far
        double tx = ox, ty, tz = oz, dist, camY;
        if (s.target().equals("rex")) {
            ty = oy + s.lookY();
            dist = s.dist();
            camY = oy + s.eye();
        } else if (s.target().equals("head")) {
            ty = oy + sp_.eye * 0.97;
            tz = oz + sp_.headZ * 1.08;
            dist = Math.max(0.45, sp_.length * 0.11 + 0.35);
            camY = ty + dist * 0.12;
        } else {
            ty = oy + sp_.height * 0.5;
            tz = oz + sp_.headZ * 1.1 - sp_.length * 0.5;
            dist = Math.max(1.5, (sp_.length * 0.5 + 1.2) / 1.05);
            camY = ty + dist * 0.16;
        }
        double a = Math.toRadians(s.angle());
        double cx = tx + Math.sin(a) * dist;
        double cz = tz + Math.cos(a) * dist;
        double cy = camY;
        double dx = tx - cx, dy = ty - cy, dz = tz - cz;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        ServerPlayer sp = srv.getPlayerList().getPlayer(playerId);
        if (sp != null) {
            sp.teleportTo(level, cx, cy - sp.getEyeHeight(), cz, yaw, pitch);
            sp.setYHeadRot(yaw);
        }
    }
}
