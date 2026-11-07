package com.arkragnarok.test;

import com.arkragnarok.ArkRagnarok;
import com.arkragnarok.entity.ArkCreature;
import com.arkragnarok.species.Species;
import com.arkragnarok.species.SpeciesList;
import com.arkragnarok.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dedicated-server self test, enabled with -Dark.smoketest=true (the CI "smoke" run).  Spawns the Rex, lets its
 * AI run, fires its triggered animations, saddles it, damages and kills it, then prints a result and stops.
 */
@EventBusSubscriber(modid = ArkRagnarok.MODID)
public final class SmokeTest {
    private static final Logger LOG = LoggerFactory.getLogger("ARK-SMOKE");
    private static final boolean ENABLED = Boolean.getBoolean("ark.smoketest");
    private static int tick = -1;
    private static ArkCreature rex;
    private static final java.util.List<ArkCreature> ALL = new java.util.ArrayList<>();
    private static int failures;

    private SmokeTest() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        if (!ENABLED) return;
        LOG.info("ARK-SMOKE start");
        tick = 0;
    }

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (!ENABLED || tick < 0) return;
        MinecraftServer server = e.getServer();
        ServerLevel level = server.overworld();
        try {
            tick++;
            if (tick == 20) {
                BlockPos spawn = level.getSharedSpawnPos();
                BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(8, 0, 8));
                rex = ModEntities.type("tyrannosaurus").spawn(level, at, MobSpawnType.COMMAND);
                check(rex != null, "rex spawned");
                if (rex != null) LOG.info("ARK-SMOKE rex at {} hp {} dmg {} level {}", rex.blockPosition(), rex.getHealth(),
                        rex.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), rex.getLevel());
                int i = 0;
                for (Species sp : SpeciesList.all()) {
                    BlockPos p = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(-40 + (i % 6) * 14, 0, 30 + (i / 6) * 22));
                    ArkCreature c = ModEntities.type(sp.id).spawn(level, p, MobSpawnType.COMMAND);
                    check(c != null, "spawned " + sp.id);
                    if (c != null) {
                        ALL.add(c);
                        LOG.info("ARK-SMOKE {} lv {} hp {} dmg {} size {}x{}", sp.id, c.getLevel(), Math.round(c.getMaxHealth()),
                                Math.round(c.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)),
                                c.getBbWidth(), c.getBbHeight());
                    }
                    i++;
                }
            }
            if (rex == null) {
                if (tick > 40) finish(server);
                return;
            }
            if (tick == 60) {
                rex.roar();
                rex.triggerAnim("action", "bite");
                rex.setSaddled(true);
                check(rex.isSaddled(), "saddle state");
            }
            if (tick == 120) {
                check(rex.isAlive(), "alive after AI ticks");
                int alive = 0;
                for (ArkCreature c : ALL) if (c.isAlive()) alive++;
                LOG.info("ARK-SMOKE {} of {} creatures alive after 100 AI ticks", alive, ALL.size());
                for (ArkCreature c : ALL) if (c != rex) c.discard();
                rex.hurt(level.damageSources().generic(), 30f);
                check(rex.getHealth() < rex.getMaxHealth(), "takes damage");
            }
            if (tick == 140) rex.kill();
            if (tick == 160) check(rex.isDeadOrDying(), "death animation phase");
            if (tick == 240) {
                check(rex.isRemoved(), "corpse removed after the death animation");
                startSystems(level);
            }
            if (tick > 240) systems(server, level);
        } catch (Throwable t) {
            failures++;
            LOG.error("ARK-SMOKE exception", t);
            finish(server);
        }
    }

    // ============================================================================================ taming, nodes, building, survivor
    private static ArkCreature dodo;
    private static net.minecraft.world.entity.player.Player fake;
    private static com.arkragnarok.entity.ResourceNode node;
    private static com.arkragnarok.survivor.Survivor survivor, rival;
    private static BlockPos house;

    private static void startSystems(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        // keep the whole test area simulated (a server without players only ticks entities right at spawn)
        int scx = spawn.getX() >> 4, scz = spawn.getZ() >> 4;
        for (int cx = -5; cx <= 5; cx++) for (int cz = -5; cz <= 5; cz++) level.setChunkForced(scx + cx, scz + cz, true);
        fake = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(level);
        dodo = ModEntities.type("dodo").spawn(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(20, 0, -20)),
                MobSpawnType.COMMAND);
        check(dodo != null, "dodo spawned");
        if (dodo != null) {
            dodo.setLevel(1);
            dodo.addTorpor(dodo.maxTorpor() * 1.5f, fake);
        }
        node = ModEntities.RESOURCE_NODE.get().create(level);
        if (node != null) {
            BlockPos np = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(-20, 0, -20));
            node.moveTo(np.getX() + 0.5, np.getY(), np.getZ() + 0.5, 0, 0);
            node.setup(com.arkragnarok.world.NodeKind.ROCK, 1, 0);
            level.addFreshEntity(node);
        }
        house = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(0, 0, -40));
        survivor = ModEntities.SURVIVOR.get().create(level);
        if (survivor != null) {
            BlockPos sp = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(30, 0, 30));
            survivor.moveTo(sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0, 0);
            survivor.init(3);
            // the mod's own resources only (what it would have gathered in its first hour)
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.WOOD.get(), 64));
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.THATCH.get(), 64));
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.THATCH.get(), 32));
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.FIBER.get(), 40));
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.STONE.get(), 24));
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.FLINT.get(), 8));
            survivor.inv.addItem(new net.minecraft.world.item.ItemStack(com.arkragnarok.economy.ModEconomy.HIDE.get(), 10));
            level.addFreshEntity(survivor);
        }
        // a rival with nothing: it has to look after itself
        rival = ModEntities.SURVIVOR.get().create(level);
        if (rival != null) {
            BlockPos rp = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.offset(-40, 0, 40));
            rival.moveTo(rp.getX() + 0.5, rp.getY(), rp.getZ() + 0.5, 0, 0);
            rival.init(8);
            level.addFreshEntity(rival);
        }
        int planned = 0;
        for (com.arkragnarok.survivor.BaseDesign d : com.arkragnarok.survivor.BaseDesign.all()) if (!d.plan().isEmpty()) planned++;
        check(planned == com.arkragnarok.survivor.BaseDesign.COUNT, "all " + com.arkragnarok.survivor.BaseDesign.COUNT + " base designs have a build plan");
    }

    private static void systems(MinecraftServer server, ServerLevel level) {
        int t = tick - 240;
        if (dodo != null && (t == 5 || t == 20 || t == 40)) {
            LOG.info("ARK-SMOKE dodo t={} torpor {} / {} unconscious {} alive {} ticking {}", t, dodo.getTorpor(), dodo.maxTorpor(), dodo.isUnconscious(),
                    dodo.isAlive(), level.isPositionEntityTicking(dodo.blockPosition()));
        }
        if (t == 80 && dodo != null) {
            check(dodo.isUnconscious(), "dodo knocked out by torpor");
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, com.arkragnarok.taming.Taming.preferredKibble(dodo.species()).copyWithCount(6));
            dodo.interact(fake, net.minecraft.world.InteractionHand.MAIN_HAND);
            LOG.info("ARK-SMOKE dodo torpor {} / {}, affinity needed {}", Math.round(dodo.getTorpor()), Math.round(dodo.maxTorpor()),
                    Math.round(dodo.requiredAffinity()));
        }
        if (t == 100 && node != null) {
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
            fake.getInventory().clearContent();
            fake.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_PICKAXE));
            int hits = 0;
            while (!node.depleted() && hits < 80) {
                node.hurt(level.damageSources().playerAttack(fake), 4f);
                hits++;
            }
            int stone = fake.getInventory().countItem(com.arkragnarok.economy.ModEconomy.STONE.get());
            int flint = fake.getInventory().countItem(com.arkragnarok.economy.ModEconomy.FLINT.get());
            LOG.info("ARK-SMOKE rock node: {} hits -> {} stone, {} flint", hits, stone, flint);
            check(node.depleted() && stone + flint > 0, "resource node gives stone/flint and crumbles");
        }
        if (t == 120) {
            var b = com.arkragnarok.registry.ModBuilding.PIECE_BLOCKS.get(com.arkragnarok.building.Tier.WOOD).get();
            boolean ok = com.arkragnarok.building.Placement.place(level, b, com.arkragnarok.building.PieceType.FOUNDATION,
                    new com.arkragnarok.building.Placement.Plan(house, net.minecraft.core.Direction.NORTH));
            BlockPos df = house.relative(net.minecraft.core.Direction.NORTH, 2).above();
            ok &= com.arkragnarok.building.Placement.place(level, b, com.arkragnarok.building.PieceType.DOORFRAME,
                    new com.arkragnarok.building.Placement.Plan(df, net.minecraft.core.Direction.NORTH));
            ok &= com.arkragnarok.building.Placement.installDoor(level, df, com.arkragnarok.building.Tier.WOOD);
            ok &= com.arkragnarok.building.Placement.place(level, b, com.arkragnarok.building.PieceType.WALL,
                    new com.arkragnarok.building.Placement.Plan(house.relative(net.minecraft.core.Direction.EAST, 2).above(), net.minecraft.core.Direction.EAST));
            check(ok, "foundation, doorframe, door and wall placed");
            b.toggleDoor(level, df);
            check(level.getBlockState(df).getValue(com.arkragnarok.building.PieceBlock.DOOR) == com.arkragnarok.building.DoorState.OPEN, "door opens");
            BlockPos wallPart = house.relative(net.minecraft.core.Direction.EAST, 2).above(2).north();
            level.destroyBlock(wallPart, false);
            check(level.isEmptyBlock(house.relative(net.minecraft.core.Direction.EAST, 2).above()), "breaking one block removes the whole wall");
        }
        if (survivor != null && t % 100 == 0 && t > 0) {
            LOG.info("ARK-SMOKE survivor-dbg t={} built={} {}", t, survivor.buildStep(), survivor.debugState());
        }
        if (t == 900 && dodo != null) {
            check(dodo.isTamed(), "dodo tamed with kibble (level now " + dodo.getLevel() + ")");
        }
        if (t == 1300) {
            if (survivor != null) {
                var tr = survivor.tribe();
                LOG.info("ARK-SMOKE survivor alive={} task={} base={} design=#{} {} stage={} built={} pieces={} wood={} thatch={}", survivor.isAlive(),
                        survivor.task(), survivor.base(), tr.design, tr.base == null ? "-" : com.arkragnarok.survivor.BaseDesign.get(tr.design).describe(),
                        tr.stage, survivor.buildStep(), tr.piecesBuilt, survivor.count(com.arkragnarok.economy.ModEconomy.WOOD.get()),
                        survivor.count(com.arkragnarok.economy.ModEconomy.THATCH.get()));
                check(survivor.isAlive(), "survivor alive");
                check(survivor.base() != null && survivor.buildStep() > 0, "survivor chose a site and a design and started building");
                check(tr.piecesBuilt > 0, "survivor placed structure pieces of its design");
                boolean foundation = false;
                if (tr.base != null) {
                    for (BlockPos q : BlockPos.betweenClosed(tr.base.offset(-12, -6, -12), tr.base.offset(12, 2, 12))) {
                        if (level.getBlockState(q).getBlock() instanceof com.arkragnarok.building.PieceBlock) {
                            foundation = true;
                            break;
                        }
                    }
                }
                check(foundation, "its pieces stand in the world");
                if (tr.base != null) {
                    // one connected house: foundations flush with the ground and edge to edge, no earth built up above the ground
                    int flush = 0, raisedEarth = 0, gaps = 0;
                    java.util.Set<BlockPos> fcells = new java.util.HashSet<>();
                    for (BlockPos q : BlockPos.betweenClosed(tr.base.offset(-30, -3, -30), tr.base.offset(30, 4, 30))) {
                        var st = level.getBlockState(q);
                        if (st.getBlock() instanceof com.arkragnarok.building.PieceBlock
                                && st.getValue(com.arkragnarok.building.PieceBlock.PIECE) == com.arkragnarok.building.PieceType.FOUNDATION) {
                            if (q.getY() == tr.base.getY() - 1) flush++;
                            fcells.add(q.immutable());
                        }
                        if (st.is(com.arkragnarok.economy.ModEconomy.RAMMED_EARTH.get()) && q.getY() >= tr.base.getY()) raisedEarth++;
                    }
                    // between two foundation blocks two apart on a line there must not be a hole
                    for (BlockPos q : fcells) {
                        for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.EAST, net.minecraft.core.Direction.SOUTH}) {
                            if (fcells.contains(q.relative(d, 2)) && !fcells.contains(q.relative(d))) gaps++;
                        }
                    }
                    LOG.info("ARK-SMOKE house: {} foundation blocks ({} flush with the ground), {} gaps between them, {} earth blocks above ground",
                            fcells.size(), flush, gaps, raisedEarth);
                    check(fcells.size() > 0 && flush == fcells.size(), "foundations lie flush with the ground");
                    check(gaps == 0, "the foundations of the house touch (one connected floor)");
                    check(raisedEarth == 0, "no rammed-earth plinth built up above the ground");
                }
                check(tr.personality != null, "survivor has a personality (" + tr.personality().temperament() + ")");
                var saved = com.arkragnarok.survivor.world.SurvivorWorld.get(level).save(new net.minecraft.nbt.CompoundTag(), level.registryAccess());
                var loaded = com.arkragnarok.survivor.world.SurvivorWorld.load(saved, level.registryAccess());
                var lt = loaded.find(survivor.getUUID());
                check(lt != null && lt.design == tr.design && java.util.Objects.equals(lt.base, tr.base) && lt.built.length == tr.built.length,
                        "tribe saves and loads with its base");
            }
            if (rival != null) {
                LOG.info("ARK-SMOKE rival alive={} task={} base={} food={}", rival.isAlive(), rival.task(), rival.base(), rival.foodItems());
                check(rival.isAlive(), "rival survivor alive");
            }
            finish(server);
        }
    }

    private static void check(boolean ok, String what) {
        if (ok) LOG.info("ARK-SMOKE ok   {}", what);
        else {
            failures++;
            LOG.error("ARK-SMOKE FAIL {}", what);
        }
    }

    private static void finish(MinecraftServer server) {
        LOG.info("ARK-SMOKE RESULT {} ({} failures)", failures == 0 ? "PASS" : "FAIL", failures);
        tick = -1;
        server.halt(false);
    }
}
