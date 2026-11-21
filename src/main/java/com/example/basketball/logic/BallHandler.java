package com.example.basketball.logic;

import com.example.basketball.api.BodyApi;
import com.example.basketball.block.HoopBlock;
import com.example.basketball.entity.BasketballEntity;
import com.example.basketball.item.ModItems;
import com.example.basketball.net.BallNet;
import com.example.basketball.pose.PoseState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/** Вся серверная логика: дриблинг, броски, пасы, блоки, перехваты, дэнки. */
public final class BallHandler {
    private static final Map<UUID, BasketballEntity> DRIBBLERS = new HashMap<>();
    private static final Map<UUID, Long> BLOCK_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> NEXT_BLOCK = new HashMap<>();
    private static final Map<UUID, Long> NEXT_STEAL = new HashMap<>();
    private static final Map<UUID, Long> NEXT_MOVE = new HashMap<>();
    private static final Map<UUID, Long> PROTECT_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> STUN_UNTIL = new HashMap<>();
    private static final Map<UUID, Dunk> DUNKS = new HashMap<>();
    private static final Map<UUID, Long> HESI_BURST = new HashMap<>();
    private static final Map<UUID, Long> LAST_LUNGE = new HashMap<>();

    public static void register() {
        BallNet.registerServer();
        ServerTickEvents.END_WORLD_TICK.register(BallHandler::tickWorld);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            UUID id = handler.player.getUuid();
            BasketballEntity b = DRIBBLERS.remove(id);
            if (b != null) {
                b.dropStack(new ItemStack(ModItems.BASKETBALL));
                b.discard();
            }
            BLOCK_UNTIL.remove(id);
            DUNKS.remove(id);
        });
    }

    // ---------- вспомогательное ----------
    public static boolean owns(UUID id, BasketballEntity e) { return DRIBBLERS.get(id) == e; }

    public static void forget(BasketballEntity e) { DRIBBLERS.values().removeIf(v -> v == e); }

    private static ItemStack findBallStack(ServerPlayerEntity p) {
        if (p.getMainHandStack().isOf(ModItems.BASKETBALL)) return p.getMainHandStack();
        if (p.getOffHandStack().isOf(ModItems.BASKETBALL)) return p.getOffHandStack();
        PlayerInventory inv = p.getInventory();
        for (ItemStack s : inv.main) if (s.isOf(ModItems.BASKETBALL)) return s;
        return null;
    }

    public static boolean hasBall(ServerPlayerEntity p) {
        return DRIBBLERS.containsKey(p.getUuid()) || findBallStack(p) != null;
    }

    /** Забирает мяч у игрока для броска/паса. */
    private static boolean takeBall(ServerPlayerEntity p) {
        BasketballEntity d = DRIBBLERS.remove(p.getUuid());
        if (d != null) {
            d.discard();
            return true;
        }
        ItemStack s = findBallStack(p);
        if (s == null) return false;
        s.decrement(1);
        return true;
    }

    /** Игрок получает свободный мяч и начинает вести его. */
    public static boolean receive(ServerPlayerEntity p, BasketballEntity ball) {
        if (DRIBBLERS.containsKey(p.getUuid())) return false;
        ball.setOwner(p);
        ball.setHandlerId(p.getId());
        ball.setDribbling(true);
        ball.setScored(false);
        ball.setHandSide(1);
        DRIBBLERS.put(p.getUuid(), ball);
        BallNet.sendPose(p, PoseState.CATCH);
        return true;
    }

    // ---------- вход от клиента ----------
    public static void handleAction(ServerPlayerEntity p, int action, int arg) {
        if (p.isSpectator() || !p.isAlive()) return;
        if (action >= BallNet.A_SHOOT_RELEASE && p.getServerWorld().getTime() < STUN_UNTIL.getOrDefault(p.getUuid(), 0L)) return;
        switch (action) {
            case BallNet.A_DRIBBLE -> toggleDribble(p);
            case BallNet.A_SHOOT_START -> {
                if (!hasBall(p)) p.sendMessage(Text.translatable("message.basketball.noball"), true);
            }
            case BallNet.A_SHOOT_RELEASE -> shoot(p, arg);
            case BallNet.A_PASS -> pass(p);
            case BallNet.A_BLOCK -> block(p);
            case BallNet.A_STEAL -> steal(p);
            case BallNet.A_CROSS -> move(p, 0, arg);
            case BallNet.A_SPIN -> move(p, 1, 0);
            case BallNet.A_STEPBACK -> move(p, 2, 0);
            case BallNet.A_MOVE -> move(p, arg / 4, (arg % 4) - 1);
            default -> { }
        }
    }

    public static void toggleDribble(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        BasketballEntity cur = DRIBBLERS.remove(id);
        if (cur != null) {
            cur.discard();
            p.getInventory().offerOrDrop(new ItemStack(ModItems.BASKETBALL));
            return;
        }
        ItemStack s = findBallStack(p);
        if (s == null) {
            p.sendMessage(Text.translatable("message.basketball.noball"), true);
            return;
        }
        s.decrement(1);
        ServerWorld w = p.getServerWorld();
        BasketballEntity b = new BasketballEntity(w, p);
        b.setDribbling(true);
        b.setHandlerId(p.getId());
        DRIBBLERS.put(id, b);
        w.spawnEntity(b);
    }

    // ---------- бросок ----------
    private static void shoot(ServerPlayerEntity p, int chargeTicks) {
        if (!hasBall(p)) {
            p.sendMessage(Text.translatable("message.basketball.noball"), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        int charge = MathHelper.clamp(chargeTicks, 0, 40);

        if (!p.isOnGround()) {
            BlockPos dunkHoop = findHoopForDunk(w, p);
            if (dunkHoop != null) {
                startDunk(p, dunkHoop);
                return;
            }
        }

        Vec3d start = new Vec3d(p.getX(), p.getEyeY() - 0.1, p.getZ());
        Vec3d look = p.getRotationVec(1.0f);
        BlockPos target = findTargetHoop(w, p, look);
        takeBall(p);

        Vec3d vel;
        String qualityKey;
        if (target != null) {
            Vec3d aim = new Vec3d(target.getX() + 0.5, target.getY() + 0.5625, target.getZ() + 0.5);
            double hd = Math.hypot(aim.x - start.x, aim.z - start.z);
            Vec3d ideal = ShotSolver.solve(start, aim);
            if (ideal == null) ideal = look.multiply(1.2);

            int tol = BodyApi.shotTolerance(p);
            int d = Math.abs(charge - 16);
            int tier = d <= 2 + tol ? 0 : d <= 5 + tol ? 1 : 2;
            if (hd > BodyApi.shotRange(p)) tier = Math.min(2, tier + 1);
            if (isContested(w, p)) tier = Math.min(2, tier + 1);

            vel = perturb(ideal, tier, w.random);
            qualityKey = tier == 0 ? "message.basketball.perfect" : tier == 1 ? "message.basketball.good" : "message.basketball.bad";
        } else {
            double speed = MathHelper.clamp(0.7 + charge * 0.03, 0.7, 1.6);
            vel = look.add(0, 0.15, 0).normalize().multiply(speed);
            qualityKey = null;
        }

        BasketballEntity b = launch(p, vel);
        b.setShotFrom(start);
        BallNet.sendPose(p, PoseState.SHOOT);
        if (qualityKey != null) p.sendMessage(Text.translatable(qualityKey), true);
    }

    private static boolean isContested(ServerWorld w, ServerPlayerEntity shooter) {
        long now = w.getTime();
        for (ServerPlayerEntity o : w.getPlayers()) {
            if (o == shooter) continue;
            Long until = BLOCK_UNTIL.get(o.getUuid());
            if (until != null && now <= until && o.squaredDistanceTo(shooter) < 2.6 * 2.6) return true;
        }
        return false;
    }

    private static Vec3d perturb(Vec3d v, int tier, Random r) {
        double[] yawDeg = {0, 1.3, 4.5};
        double[] pitDeg = {0, 1.8, 7.0};
        double[] spd = {0, 0.015, 0.06};
        double sp = Math.sqrt(v.x * v.x + v.z * v.z);
        double yaw = Math.atan2(v.z, v.x) + Math.toRadians(yawDeg[tier] * (r.nextDouble() * 2 - 1));
        double pit = Math.atan2(v.y, sp) + Math.toRadians(pitDeg[tier] * (r.nextDouble() * 2 - 1));
        double speed = v.length() * (1 + spd[tier] * (r.nextDouble() * 2 - 1));
        return new Vec3d(Math.cos(yaw) * Math.cos(pit) * speed, Math.sin(pit) * speed, Math.sin(yaw) * Math.cos(pit) * speed);
    }

    private static BasketballEntity launch(ServerPlayerEntity p, Vec3d vel) {
        ServerWorld w = p.getServerWorld();
        BasketballEntity b = new BasketballEntity(w, p);
        b.setVelocity(vel);
        b.velocityModified = true;
        w.spawnEntity(b);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 0.5f);
        return b;
    }

    private static BlockPos findTargetHoop(ServerWorld w, ServerPlayerEntity p, Vec3d look) {
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        if (flat.lengthSquared() < 1e-4) return null;
        flat = flat.normalize();
        BlockPos base = p.getBlockPos();
        BlockPos best = null;
        double bestScore = -1;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -18; dx <= 18; dx++) {
            for (int dz = -18; dz <= 18; dz++) {
                for (int dy = -6; dy <= 9; dy++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    BlockState st = w.getBlockState(m);
                    if (!(st.getBlock() instanceof HoopBlock)) continue;
                    double tx = m.getX() + 0.5 - p.getX();
                    double tz = m.getZ() + 0.5 - p.getZ();
                    double hd = Math.hypot(tx, tz);
                    if (hd < 1.5 || hd > 18) continue;
                    double cos = (tx * flat.x + tz * flat.z) / hd;
                    if (cos < 0.82) continue;
                    Direction f = st.get(HoopBlock.FACING);
                    double side = (tx / hd) * f.getOffsetX() + (tz / hd) * f.getOffsetZ();
                    if (side < 0.1) continue; // стреляем в сторону щита, а не с обратной стороны
                    double score = cos - hd * 0.002;
                    if (score > bestScore) {
                        bestScore = score;
                        best = m.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    // ---------- дэнк ----------
    private static BlockPos findHoopForDunk(ServerWorld w, ServerPlayerEntity p) {
        BlockPos base = p.getBlockPos();
        BlockPos.Mutable m = new BlockPos.Mutable();
        double reach = BodyApi.reach(p);
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = 0; dy <= 5; dy++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (!(w.getBlockState(m).getBlock() instanceof HoopBlock)) continue;
                    double hd = Math.hypot(m.getX() + 0.5 - p.getX(), m.getZ() + 0.5 - p.getZ());
                    double rel = m.getY() - p.getY();
                    if (hd <= 3.4 * reach && rel >= 0.3 && rel <= 3.5 * reach) return m.toImmutable();
                }
            }
        }
        return null;
    }

    // ---------- дэнк: подлёт -> замах -> удар -> повисание на кольце ----------
    private static final class Dunk {
        final BlockPos hoop;
        final Vec3d target;
        final long start;
        final BasketballEntity ball;
        boolean slammed = false;
        long slamTick = 0;

        Dunk(BlockPos hoop, Vec3d target, long start, BasketballEntity ball) {
            this.hoop = hoop; this.target = target; this.start = start; this.ball = ball;
        }
    }

    private static void startDunk(ServerPlayerEntity p, BlockPos hoop) {
        UUID id = p.getUuid();
        if (DUNKS.containsKey(id)) return;
        ServerWorld w = p.getServerWorld();

        BasketballEntity ball = DRIBBLERS.get(id);
        if (ball == null) {
            ItemStack s = findBallStack(p);
            if (s == null) return;
            s.decrement(1);
            ball = new BasketballEntity(w, p);
            ball.setDribbling(true);
            ball.setHandlerId(p.getId());
            DRIBBLERS.put(id, ball);
            w.spawnEntity(ball);
        }
        ball.setCarry(true);

        double cx = hoop.getX() + 0.5, cz = hoop.getZ() + 0.5;
        Vec3d away = new Vec3d(p.getX() - cx, 0, p.getZ() - cz);
        if (away.lengthSquared() < 0.04) {
            Direction f = w.getBlockState(hoop).get(HoopBlock.FACING);
            away = new Vec3d(-f.getOffsetX(), 0, -f.getOffsetZ());
        }
        away = away.normalize();
        // встаём перед кольцом со стороны игрока, руки над ободом
        double ty = hoop.getY() + 0.5625 - 1.7;
        Vec3d target = new Vec3d(cx + away.x * 0.85, ty, cz + away.z * 0.85);

        Vec3d v = p.getVelocity();
        boolean running = Math.hypot(v.x, v.z) > 0.12;
        float r = w.random.nextFloat();
        int pose = running ? (r < 0.6f ? PoseState.DUNK_WIND : PoseState.DUNK_TOMA)
                           : (r < 0.6f ? PoseState.DUNK : PoseState.DUNK_TOMA);

        DUNKS.put(id, new Dunk(hoop, target, w.getTime(), ball));
        BallNet.sendPose(p, pose);
    }

    private static void tickDunks(ServerWorld w) {
        if (DUNKS.isEmpty()) return;
        long now = w.getTime();
        Iterator<Map.Entry<UUID, Dunk>> it = DUNKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Dunk> en = it.next();
            Dunk d = en.getValue();
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) {
                if (p != null) p.setNoGravity(false);
                it.remove();
                continue;
            }
            if (p.getServerWorld() != w) continue;

            long t = now - d.start;
            p.fallDistance = 0;
            if (!d.slammed) {
                if (t >= 8) {
                    slam(p, d, now);
                } else {
                    // доводим игрока до точки у кольца ровно за 8 тиков
                    Vec3d v = d.target.subtract(p.getPos()).multiply(1.0 / Math.max(1, 8 - t));
                    double len = v.length();
                    if (len > 1.1) v = v.multiply(1.1 / len);
                    p.setVelocity(v);
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                }
            } else {
                p.setVelocity(d.target.subtract(p.getPos()).multiply(0.4));
                p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                if (now - d.slamTick >= 10) {
                    p.setNoGravity(false);
                    p.setVelocity(0, -0.1, 0);
                    p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                    it.remove();
                }
            }
        }
    }

    private static void slam(ServerPlayerEntity p, Dunk d, long now) {
        ServerWorld w = p.getServerWorld();
        BlockPos hoop = d.hoop;
        double cx = hoop.getX() + 0.5, cz = hoop.getZ() + 0.5;

        // постер: под кольцом стоит защитник
        ServerPlayerEntity victim = null;
        for (ServerPlayerEntity o : w.getPlayers()) {
            if (o == p || o.isSpectator()) continue;
            if (Math.hypot(o.getX() - cx, o.getZ() - cz) < 3.0 && o.getY() <= p.getY() + 0.3) {
                victim = o;
                break;
            }
        }
        ScoreKeeper.award(p, victim != null ? 3 : 2,
                victim != null ? "message.basketball.poster" : "message.basketball.dunk");

        // мяч вылетает из рук прямо в кольцо
        BasketballEntity b = d.ball;
        DRIBBLERS.remove(p.getUuid());
        b.setCarry(false);
        b.setDribbling(false);
        b.setHandlerId(-1);
        b.setScored(true);
        b.setOwner(p);
        b.setGrace(p.getUuid(), now + 25);
        b.refreshPositionAndAngles(cx, hoop.getY() + 0.35, cz, 0f, 0f);
        b.setVelocity(0, -0.4, 0);
        b.velocityModified = true;

        p.setNoGravity(true); // повисаем на кольце
        d.slammed = true;
        d.slamTick = now;

        w.playSound(null, hoop, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, victim != null ? 0.8f : 0.45f, 1.6f);
        w.playSound(null, hoop, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.25f, 1.9f);
        w.spawnParticles(ParticleTypes.CRIT, cx, hoop.getY() + 0.6, cz, 25, 0.3, 0.2, 0.3, 0.2);

        if (victim != null) {
            stumble(victim, new Vec3d(victim.getX() - cx, 0, victim.getZ() - cz));
            w.spawnParticles(ParticleTypes.EXPLOSION, victim.getX(), victim.getY() + 1.0, victim.getZ(), 2, 0.2, 0.2, 0.2, 0.0);
            for (ServerPlayerEntity o : w.getPlayers()) {
                if (o.squaredDistanceTo(p) < 64.0 * 64.0) {
                    o.sendMessage(Text.translatable("message.basketball.poster_broadcast", p.getDisplayName(), victim.getDisplayName()), false);
                }
            }
        }
    }

    /** Защитник падает: замедление, отбрасывание и короткое оглушение (нельзя блокировать/перехватывать). */
    private static void stumble(ServerPlayerEntity d, Vec3d dir) {
        long now = d.getServerWorld().getTime();
        STUN_UNTIL.put(d.getUuid(), now + 24);
        d.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 3));
        Vec3d n = dir.lengthSquared() < 1e-6 ? Vec3d.ZERO : dir.normalize();
        d.setVelocity(n.x * 0.5, 0.25, n.z * 0.5);
        d.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(d));
        BallNet.sendPose(d, PoseState.STUMBLE);
        d.getServerWorld().playSound(null, d.getX(), d.getY(), d.getZ(), SoundEvents.ENTITY_PLAYER_BIG_FALL, SoundCategory.PLAYERS, 0.7f, 1.0f);
    }

    // ---------- финты ----------
    // 0 кроссовер, 1 спин, 2 степбэк, 3 между ног, 4 за спиной, 5 гезитейшен, 6 финт броском, 7 евростеп
    private static void move(ServerPlayerEntity p, int type, int dir) {
        BasketballEntity ball = DRIBBLERS.get(p.getUuid());
        if (ball == null) {
            p.sendMessage(Text.translatable("message.basketball.nodribble"), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (now < NEXT_MOVE.getOrDefault(p.getUuid(), 0L)) return;
        NEXT_MOVE.put(p.getUuid(), now + 16);

        double yaw = Math.toRadians(p.getYaw());
        Vec3d f = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d r = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        int side = dir != 0 ? (dir > 0 ? 1 : -1) : -ball.getHandSide();

        Vec3d vel = Vec3d.ZERO;
        Vec3d dash = f;
        double hop = 0.0, chance = 0.0;
        int protect = 10;
        int pose = PoseState.CROSS_R;

        switch (type) {
            case 0 -> {
                vel = r.multiply(0.8 * side).add(f.multiply(0.12));
                dash = r.multiply(side);
                ball.setHandSide(side);
                pose = side > 0 ? PoseState.CROSS_R : PoseState.CROSS_L;
                chance = 0.75;
            }
            case 1 -> {
                vel = f.multiply(0.55);
                ball.setHandSide(-ball.getHandSide());
                pose = PoseState.SPIN;
                chance = 0.5;
                protect = 14;
            }
            case 2 -> {
                vel = f.multiply(-0.75);
                hop = 0.18;
                pose = PoseState.STEPBACK;
                chance = 0.35;
            }
            case 3 -> {
                vel = f.multiply(0.45);
                ball.setHandSide(-ball.getHandSide());
                ball.setMode(1, now + 7);
                pose = PoseState.BTL;
                chance = 0.7;
                protect = 12;
            }
            case 4 -> {
                vel = r.multiply(0.55 * side).add(f.multiply(0.25));
                dash = r.multiply(side);
                ball.setHandSide(side);
                ball.setMode(2, now + 7);
                pose = PoseState.BACK;
                chance = 0.55;
                protect = 12;
            }
            case 5 -> {
                vel = f.multiply(0.25);
                pose = PoseState.HESI;
                protect = 14;
                HESI_BURST.put(p.getUuid(), now + 6);
                fool(p, f, false);
            }
            case 6 -> {
                pose = PoseState.FAKE;
                protect = 8;
                fool(p, f, true);
            }
            default -> {
                vel = f.multiply(0.55).add(r.multiply(0.55 * side));
                dash = r.multiply(side);
                hop = 0.25;
                ball.setHandSide(side);
                pose = PoseState.EURO;
                chance = 0.6;
                protect = 14;
            }
        }

        if (vel.lengthSquared() > 0 || hop > 0) {
            p.setVelocity(vel.x, Math.max(p.getVelocity().y, hop), vel.z);
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        }
        PROTECT_UNTIL.put(p.getUuid(), now + protect);
        BallNet.sendPose(p, pose);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.5f, 1.6f);

        if (chance > 0) ankleBreak(p, chance, f, dash, type == 2);
    }

    /** Гезитейшен: короткий рывок вперёд через 6 тиков после замедления. */
    private static void tickHesi(ServerWorld w) {
        if (HESI_BURST.isEmpty()) return;
        long now = w.getTime();
        for (ServerPlayerEntity p : w.getPlayers()) {
            Long at = HESI_BURST.get(p.getUuid());
            if (at == null || now < at) continue;
            HESI_BURST.remove(p.getUuid());
            if (!DRIBBLERS.containsKey(p.getUuid())) continue;
            double yaw = Math.toRadians(p.getYaw());
            Vec3d f = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            p.setVelocity(f.x * 0.95, p.getVelocity().y, f.z * 0.95);
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
            PROTECT_UNTIL.put(p.getUuid(), now + 10);
            ankleBreak(p, 0.6, f, f, false);
        }
    }

    /**
     * Финт заставляет защитников купиться: гезитейшен ловит тех, кто только что прыгнул в блок или выпад
     * (всегда), финт броском (pump fake) подбрасывает защитника в прыжок с шансом 60%.
     */
    private static void fool(ServerPlayerEntity p, Vec3d f, boolean pumpFake) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        for (ServerPlayerEntity d : w.getPlayers()) {
            if (d == p || d.isSpectator()) continue;
            Vec3d to = d.getPos().subtract(p.getPos());
            double hd = Math.hypot(to.x, to.z);
            if (hd > 3.5 || hd < 1e-4 || (to.x * f.x + to.z * f.z) / hd < 0.2) continue;

            boolean bitten;
            if (pumpFake) {
                bitten = w.random.nextDouble() < 0.6;
                if (bitten) {
                    d.setVelocity(d.getVelocity().x, 0.5, d.getVelocity().z);
                    d.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(d));
                    STUN_UNTIL.put(d.getUuid(), now + 16);
                }
            } else {
                bitten = now - LAST_LUNGE.getOrDefault(d.getUuid(), -100L) <= 12;
                if (bitten) stumble(d, to);
            }
            if (bitten) {
                p.sendMessage(Text.translatable("message.basketball.fake_bitten"), true);
                d.sendMessage(Text.translatable("message.basketball.fooled"), true);
                return;
            }
        }
    }

    private static void ankleBreak(ServerPlayerEntity p, double chance, Vec3d forward, Vec3d dashDir, boolean anyDirection) {
        ServerWorld w = p.getServerWorld();
        for (ServerPlayerEntity d : w.getPlayers()) {
            if (d == p || d.isSpectator()) continue;
            Vec3d to = d.getPos().subtract(p.getPos());
            double hd = Math.hypot(to.x, to.z);
            if (hd > 2.6 || hd < 1e-4 || Math.abs(to.y) > 1.5) continue;
            double dot = (to.x * forward.x + to.z * forward.z) / hd;
            if (dot < 0.2 && !anyDirection) continue;
            if (w.random.nextDouble() >= chance) continue;

            stumble(d, dashDir.multiply(-1));
            p.sendMessage(Text.translatable("message.basketball.ankle"), true);
            d.sendMessage(Text.translatable("message.basketball.ankled"), true);
            for (ServerPlayerEntity o : w.getPlayers()) {
                if (o != p && o != d && o.squaredDistanceTo(p) < 48.0 * 48.0) {
                    o.sendMessage(Text.translatable("message.basketball.ankle_broadcast", p.getDisplayName(), d.getDisplayName()), false);
                }
            }
            break;
        }
    }

    // ---------- пас ----------
    private static void pass(ServerPlayerEntity p) {
        if (!hasBall(p)) {
            p.sendMessage(Text.translatable("message.basketball.noball"), true);
            return;
        }
        ServerWorld w = p.getServerWorld();
        Vec3d start = new Vec3d(p.getX(), p.getEyeY() - 0.1, p.getZ());
        Vec3d look = p.getRotationVec(1.0f);

        ServerPlayerEntity mate = null;
        double bestCos = Math.cos(Math.toRadians(18));
        for (ServerPlayerEntity o : w.getPlayers()) {
            if (o == p || o.isSpectator()) continue;
            Vec3d to = o.getPos().add(0, 1.2, 0).subtract(start);
            double d = to.length();
            if (d < 2 || d > 24) continue;
            double cos = to.normalize().dotProduct(look);
            if (cos > bestCos) {
                bestCos = cos;
                mate = o;
            }
        }

        Vec3d vel = null;
        if (mate != null) vel = ShotSolver.pass(start, mate.getPos().add(0, 1.2, 0), 1.35);
        if (vel == null) vel = look.multiply(1.3).add(0, 0.08, 0);

        takeBall(p);
        launch(p, vel);
        BallNet.sendPose(p, PoseState.PASS);
    }

    // ---------- блок ----------
    private static void block(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (now < NEXT_BLOCK.getOrDefault(p.getUuid(), 0L)) return;
        NEXT_BLOCK.put(p.getUuid(), now + 25);
        LAST_LUNGE.put(p.getUuid(), now);
        BLOCK_UNTIL.put(p.getUuid(), now + 12);

        if (p.isOnGround()) {
            Vec3d v = p.getVelocity();
            p.setVelocity(v.x, 0.45 * BodyApi.jump(p), v.z);
            p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
        }
        BallNet.sendPose(p, PoseState.BLOCK);
    }

    // ---------- перехват ----------
    private static void steal(ServerPlayerEntity p) {
        ServerWorld w = p.getServerWorld();
        long now = w.getTime();
        if (now < NEXT_STEAL.getOrDefault(p.getUuid(), 0L)) return;
        NEXT_STEAL.put(p.getUuid(), now + 20);
        LAST_LUNGE.put(p.getUuid(), now);
        BallNet.sendPose(p, PoseState.STEAL);

        Vec3d look = p.getRotationVec(1.0f);
        Box box = p.getBoundingBox().expand(1.6 * BodyApi.reach(p), 0.3, 1.6 * BodyApi.reach(p));
        for (BasketballEntity b : w.getEntitiesByClass(BasketballEntity.class, box, BasketballEntity::isDribbling)) {
            if (b.getHandler() == p) continue;
            if (!(b.getHandler() instanceof ServerPlayerEntity victim)) continue;
            Vec3d to = b.getPos().subtract(p.getPos());
            double hd = Math.hypot(to.x, to.z);
            if (hd > 1e-4 && (to.x * look.x + to.z * look.z) / hd < 0.15) continue;

            if (now < PROTECT_UNTIL.getOrDefault(victim.getUuid(), 0L)) {
                p.sendMessage(Text.translatable("message.basketball.stealfail"), true);
                return;
            }
            if (w.random.nextFloat() < 0.45f) {
                DRIBBLERS.remove(victim.getUuid());
                b.setDribbling(false);
                b.setHandlerId(-1);
                b.setGrace(victim.getUuid(), now + 10);
                victim.sendMessage(Text.translatable("message.basketball.stolen"), true);
                p.sendMessage(Text.translatable("message.basketball.steal"), true);
                w.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.8f, 0.8f);
                if (!receive(p, b)) {
                    b.setVelocity(look.x * 0.3, 0.3, look.z * 0.3);
                    b.velocityModified = true;
                }
            } else {
                p.sendMessage(Text.translatable("message.basketball.stealfail"), true);
            }
            return;
        }
    }

    // ---------- тик мира: отбитие мяча блокирующим ----------
    private static void tickWorld(ServerWorld w) {
        tickDunks(w);
        tickHesi(w);
        if (BLOCK_UNTIL.isEmpty()) return;
        long now = w.getTime();
        for (ServerPlayerEntity p : w.getPlayers()) {
            Long until = BLOCK_UNTIL.get(p.getUuid());
            if (until == null) continue;
            if (now > until) {
                BLOCK_UNTIL.remove(p.getUuid());
                continue;
            }
            Vec3d look = p.getRotationVec(1.0f);
            Box box = p.getBoundingBox().expand(0.9 * BodyApi.reach(p), 0.5, 0.9 * BodyApi.reach(p)).stretch(0, 1.0 * BodyApi.reach(p), 0).offset(look.x * 0.5, 0, look.z * 0.5);
            for (BasketballEntity b : w.getEntitiesByClass(BasketballEntity.class, box, e -> !e.isDribbling())) {
                if (b.getOwner() == p) continue;
                if (now - b.getDeflectedAt() < 8) continue;
                Vec3d v = new Vec3d(look.x * 0.9, 0.35, look.z * 0.9);
                b.setVelocity(v);
                b.velocityModified = true;
                b.markDeflected(now);
                b.setGrace(p.getUuid(), now + 8);
                w.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.PLAYERS, 0.9f, 1.2f);
                w.spawnParticles(ParticleTypes.CRIT, b.getX(), b.getY(), b.getZ(), 10, 0.2, 0.2, 0.2, 0.1);
                p.sendMessage(Text.translatable("message.basketball.block"), true);
                if (b.getOwner() instanceof ServerPlayerEntity shooter) {
                    shooter.sendMessage(Text.translatable("message.basketball.blocked"), true);
                }
            }
        }
    }

    private BallHandler() { }
}
