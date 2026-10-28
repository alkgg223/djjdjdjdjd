package com.gunsmod.block;

import com.gunsmod.Reg;
import com.gunsmod.entity.BotEntity;
import com.gunsmod.entity.BotTeam;
import com.gunsmod.entity.ai.CoverFinder;
import com.gunsmod.gun.GunType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Runs team death-match rounds inside a generated battlefield. */
public class ArenaBlockEntity extends BlockEntity {
    public static final int MODE_PLAYER = 0, MODE_AI = 1;
    public static final int IDLE = 0, RUNNING = 1, ENDED = 2;
    public static final String[] LOADOUTS = {"mixed", "rifles", "snipers", "shotguns", "pistols", "heavy", "explosives"};

    public int greens = 5, reds = 5, mode = MODE_PLAYER, loadout = 0, state = IDLE, scoreGreen, scoreRed, round;
    public BlockPos greenBase, redBase, min, max;
    private final List<UUID> bots = new ArrayList<>();
    private final Set<UUID> players = new HashSet<>();
    private int aliveGreen, aliveRed;
    private int endTimer;

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> greens;
                case 1 -> reds;
                case 2 -> mode;
                case 3 -> loadout;
                case 4 -> state;
                case 5 -> scoreGreen;
                case 6 -> scoreRed;
                case 7 -> aliveGreen;
                case 8 -> aliveRed;
                case 9 -> round;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    public ArenaBlockEntity(BlockPos pos, BlockState state) {
        super(Reg.ARENA_BE.get(), pos, state);
    }

    public void configure(BlockPos greenBase, BlockPos redBase, BlockPos min, BlockPos max) {
        this.greenBase = greenBase;
        this.redBase = redBase;
        this.min = min;
        this.max = max;
        setChanged();
    }

    public void button(int id, Player player) {
        switch (id) {
            case 0 -> greens = Math.max(mode == MODE_PLAYER ? 0 : 1, greens - 1);
            case 1 -> greens = Math.min(20, greens + 1);
            case 2 -> reds = Math.max(1, reds - 1);
            case 3 -> reds = Math.min(20, reds + 1);
            case 4 -> {
                mode = 1 - mode;
                if (mode == MODE_AI) greens = Math.max(1, greens);
            }
            case 5 -> loadout = (loadout + 1) % LOADOUTS.length;
            case 6 -> start(player);
            case 7 -> stop();
            default -> { }
        }
        setChanged();
    }

    private GunType pick(BotTeam team, int i) {
        var r = level.random;
        GunType[] pool = switch (loadout) {
            case 1 -> team == BotTeam.GREEN ? new GunType[]{GunType.M4A1, GunType.M16A4, GunType.BR17, GunType.TACTICAL_RIFLE} : new GunType[]{GunType.AK47, GunType.BR77, GunType.BR17};
            case 2 -> new GunType[]{GunType.M24, GunType.SVD, GunType.M107};
            case 3 -> new GunType[]{GunType.PUMP_SHOTGUN, GunType.AUTO_SHOTGUN, GunType.DOUBLE_BARREL};
            case 4 -> new GunType[]{GunType.M1911, GunType.P9, GunType.MAGNUM50, GunType.REVOLVER};
            case 5 -> new GunType[]{GunType.M249, GunType.MINIGUN, GunType.M249};
            case 6 -> new GunType[]{GunType.RPG7, GunType.M79, GunType.FLAMETHROWER};
            default -> null;
        };
        return pool == null ? null : pool[r.nextInt(pool.length)];
    }

    public void start(Player starter) {
        if (!(level instanceof ServerLevel sl) || greenBase == null) {
            if (starter != null) starter.displayClientMessage(Component.translatable("gunsmod.msg.arena_missing"), true);
            return;
        }
        clearBots(sl);
        round++;
        players.clear();
        for (int i = 0; i < greens; i++) spawn(sl, BotTeam.GREEN, greenBase, i);
        for (int i = 0; i < reds; i++) spawn(sl, BotTeam.RED, redBase, i);
        if (mode == MODE_PLAYER && starter instanceof ServerPlayer sp) {
            players.add(sp.getUUID());
            sp.teleportTo(greenBase.getX() + 0.5, greenBase.getY(), greenBase.getZ() + 0.5);
        }
        state = RUNNING;
        endTimer = 0;
        count(sl);
        broadcastTitle(sl, Component.translatable("gunsmod.arena.round", round), Component.translatable("gunsmod.arena.vs", greens + (mode == MODE_PLAYER ? 1 : 0), reds));
        sl.playSound(null, worldPosition, Reg.sound("missile_alarm"), SoundSource.BLOCKS, 3f, 1.2f);
        setChanged();
    }

    private void spawn(ServerLevel sl, BotTeam team, BlockPos base, int i) {
        BlockPos spot = null;
        for (int k = 0; k < 12 && spot == null; k++) {
            spot = CoverFinder.ground(sl, base.offset(sl.random.nextInt(9) - 4, 0, sl.random.nextInt(7) - 3));
        }
        if (spot == null) spot = base;
        BotEntity b = new BotEntity(Reg.BOT.get(), sl);
        b.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, 0, 0);
        b.setup(team, sl.random, pick(team, i));
        b.setOrder(BotEntity.ORDER_PATROL);
        b.home = new BlockPos((greenBase.getX() + redBase.getX()) / 2, spot.getY(), (greenBase.getZ() + redBase.getZ()) / 2);
        b.arenaPos = worldPosition;
        b.ignorePlayers = mode == MODE_AI;
        b.lastKnownEnemyPos = Vec3.atBottomCenterOf(team == BotTeam.GREEN ? redBase : greenBase);
        sl.addFreshEntity(b);
        bots.add(b.getUUID());
    }

    public void stop() {
        if (level instanceof ServerLevel sl) clearBots(sl);
        state = IDLE;
        setChanged();
    }

    private void clearBots(ServerLevel sl) {
        for (UUID u : bots) {
            Entity e = sl.getEntity(u);
            if (e != null) e.discard();
        }
        bots.clear();
        if (min != null) {
            for (BotEntity b : sl.getEntitiesOfClass(BotEntity.class, new AABB(Vec3.atLowerCornerOf(min), Vec3.atLowerCornerOf(max).add(1, 1, 1)).inflate(4),
                    b -> worldPosition.equals(b.arenaPos))) {
                b.discard();
            }
        }
    }

    public void onBotDied(BotEntity bot) {
        if (level instanceof ServerLevel sl && state == RUNNING) count(sl);
    }

    public void onPlayerDied(ServerPlayer p) {
        if (players.remove(p.getUUID()) && level instanceof ServerLevel sl) count(sl);
    }

    public boolean hasPlayer(UUID id) {
        return players.contains(id);
    }

    private void count(ServerLevel sl) {
        int g = 0, r = 0;
        for (UUID u : bots) {
            Entity e = sl.getEntity(u);
            if (e instanceof BotEntity b && b.isAlive()) {
                if (b.getBotTeam() == BotTeam.GREEN) g++;
                else r++;
            }
        }
        aliveGreen = g;
        aliveRed = r;
        if (state != RUNNING) return;
        boolean playersAlive = false;
        for (UUID u : players) {
            Player p = sl.getPlayerByUUID(u);
            if (p != null && p.isAlive() && min != null && p.blockPosition().closerThan(worldPosition, 120)) playersAlive = true;
        }
        if (r == 0) end(sl, BotTeam.GREEN);
        else if (g == 0 && !(mode == MODE_PLAYER && playersAlive)) end(sl, BotTeam.RED);
    }

    private void end(ServerLevel sl, BotTeam winner) {
        state = ENDED;
        endTimer = 0;
        if (winner == BotTeam.GREEN) scoreGreen++;
        else scoreRed++;
        broadcastTitle(sl, Component.translatable("gunsmod.arena.win." + winner.name().toLowerCase()).withStyle(winner.chat),
                Component.translatable("gunsmod.arena.score", scoreGreen, scoreRed));
        sl.playSound(null, worldPosition, net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 2f, 1f);
        for (int i = 0; i < 4; i++) {
            net.minecraft.world.entity.projectile.FireworkRocketEntity fw = new net.minecraft.world.entity.projectile.FireworkRocketEntity(
                    sl, worldPosition.getX() + 0.5 + (i - 1.5) * 2, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIREWORK_ROCKET));
            sl.addFreshEntity(fw);
        }
        setChanged();
    }

    private void broadcastTitle(ServerLevel sl, Component title, Component sub) {
        for (ServerPlayer p : sl.players()) {
            if (p.blockPosition().closerThan(worldPosition, 100)) {
                p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
                p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
                p.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }

    public void tick() {
        if (!(level instanceof ServerLevel sl)) return;
        if (state == RUNNING && sl.getGameTime() % 20 == 0) {
            count(sl);
            // keep the fight going: idle soldiers advance towards the enemy side
            if (sl.getGameTime() % 100 == 0) {
                for (UUID u : bots) {
                    if (sl.getEntity(u) instanceof BotEntity b && b.getTarget() == null && b.lastKnownEnemyPos == null) {
                        BlockPos goal = b.getBotTeam() == BotTeam.GREEN ? redBase : greenBase;
                        b.lastKnownEnemyPos = Vec3.atBottomCenterOf(goal).add(sl.random.nextInt(11) - 5, 0, sl.random.nextInt(11) - 5);
                    }
                }
            }
        }
        if (state == ENDED && ++endTimer > 200) {
            clearBots(sl);
            state = IDLE;
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.saveAdditional(tag, reg);
        tag.putInt("Greens", greens);
        tag.putInt("Reds", reds);
        tag.putInt("Mode", mode);
        tag.putInt("Loadout", loadout);
        tag.putInt("State", state);
        tag.putInt("ScoreG", scoreGreen);
        tag.putInt("ScoreR", scoreRed);
        tag.putInt("Round", round);
        if (greenBase != null) {
            tag.put("GreenBase", NbtUtils.writeBlockPos(greenBase));
            tag.put("RedBase", NbtUtils.writeBlockPos(redBase));
            tag.put("Min", NbtUtils.writeBlockPos(min));
            tag.put("Max", NbtUtils.writeBlockPos(max));
        }
        ListTag list = new ListTag();
        for (UUID u : bots) list.add(NbtUtils.createUUID(u));
        tag.put("Bots", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) {
        super.loadAdditional(tag, reg);
        greens = tag.getInt("Greens");
        reds = Math.max(1, tag.getInt("Reds"));
        mode = tag.getInt("Mode");
        loadout = tag.getInt("Loadout");
        state = tag.getInt("State");
        scoreGreen = tag.getInt("ScoreG");
        scoreRed = tag.getInt("ScoreR");
        round = tag.getInt("Round");
        greenBase = NbtUtils.readBlockPos(tag, "GreenBase").orElse(null);
        redBase = NbtUtils.readBlockPos(tag, "RedBase").orElse(null);
        min = NbtUtils.readBlockPos(tag, "Min").orElse(null);
        max = NbtUtils.readBlockPos(tag, "Max").orElse(null);
        bots.clear();
        for (Tag t : tag.getList("Bots", Tag.TAG_INT_ARRAY)) bots.add(NbtUtils.loadUUID(t));
    }
}
