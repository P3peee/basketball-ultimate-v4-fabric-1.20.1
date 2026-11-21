package com.example.basketball.entity;

import com.example.basketball.api.BodyApi;
import com.example.basketball.block.HoopBlock;
import com.example.basketball.item.ModItems;
import com.example.basketball.logic.BallHandler;
import com.example.basketball.logic.ScoreKeeper;
import com.example.basketball.pose.PoseState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class BasketballEntity extends ThrownItemEntity {
    private static final TrackedData<Boolean> DRIBBLING =
            DataTracker.registerData(BasketballEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    /** id игрока, который ведёт мяч (синхронизируется на клиенты). */
    private static final TrackedData<Integer> HANDLER =
            DataTracker.registerData(BasketballEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** +1 - мяч в правой руке, -1 - в левой. */
    private static final TrackedData<Integer> HAND =
            DataTracker.registerData(BasketballEntity.class, TrackedDataHandlerRegistry.INTEGER);

    /** Мяч высоко над головой (дэнк). */
    private static final TrackedData<Boolean> CARRY =
            DataTracker.registerData(BasketballEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    /** 0 - обычный кроссовер, 1 - между ног, 2 - за спиной. */
    private static final TrackedData<Integer> MODE =
            DataTracker.registerData(BasketballEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private long modeUntil = 0;

    private float handLerp = 1f;
    private static final double RIM_Y = 9.0 / 16.0;
    private static final double BOUNCE = 0.78;

    private int bounces = 0;
    private boolean scored = false;
    private Vec3d shotFrom = Vec3d.ZERO;
    private UUID graceUuid = null;
    private long graceUntil = 0;
    private long deflectedAt = -100;

    public BasketballEntity(EntityType<? extends BasketballEntity> type, World world) {
        super(type, world);
    }

    public BasketballEntity(World world, LivingEntity owner) {
        super(ModEntities.BASKETBALL, owner, world);
        this.shotFrom = this.getPos();
        this.graceUuid = owner.getUuid();
        this.graceUntil = world.getTime() + 12;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(DRIBBLING, false);
        this.dataTracker.startTracking(HANDLER, -1);
        this.dataTracker.startTracking(HAND, 1);
        this.dataTracker.startTracking(CARRY, false);
        this.dataTracker.startTracking(MODE, 0);
    }

    // ----- состояние -----
    public boolean isDribbling() { return this.dataTracker.get(DRIBBLING); }
    public void setDribbling(boolean v) { this.dataTracker.set(DRIBBLING, v); }
    public void setHandlerId(int id) { this.dataTracker.set(HANDLER, id); }
    public void setMode(int mode, long until) { this.dataTracker.set(MODE, mode); this.modeUntil = until; }
    public boolean isCarry() { return this.dataTracker.get(CARRY); }
    public void setCarry(boolean v) { this.dataTracker.set(CARRY, v); }
    public int getHandSide() { return this.dataTracker.get(HAND); }
    public void setHandSide(int side) { this.dataTracker.set(HAND, side >= 0 ? 1 : -1); }

    @Nullable
    public Entity getHandler() {
        int id = this.dataTracker.get(HANDLER);
        return id < 0 ? null : this.getWorld().getEntityById(id);
    }

    public void setGrace(UUID who, long until) { this.graceUuid = who; this.graceUntil = until; }
    public void setScored(boolean v) { this.scored = v; }
    public void setShotFrom(Vec3d v) { this.shotFrom = v; }
    public long getDeflectedAt() { return deflectedAt; }
    public void markDeflected(long t) { this.deflectedAt = t; this.scored = false; this.bounces++; }

    @Override
    protected Item getDefaultItem() { return ModItems.BASKETBALL; }

    @Override
    protected float getGravity() { return 0.05f; }

    @Override
    protected boolean canHit(Entity entity) { return false; }

    // ----- тик -----
    @Override
    public void tick() {
        if (isDribbling()) {
            tickDribble();
            return;
        }
        Vec3d before = this.getPos();
        super.tick();
        if (this.getWorld().isClient || this.isRemoved()) return;
        checkScore(before, this.getPos());
        tryCatch();
    }

    private void tickDribble() {
        World w = this.getWorld();
        Entity h = getHandler();

        if (!(h instanceof PlayerEntity p) || !p.isAlive() || p.isSpectator()) {
            if (!w.isClient && h != null) {
                BallHandler.forget(this);
                this.dropStack(new ItemStack(ModItems.BASKETBALL));
                this.discard();
            } else if (!w.isClient && this.age > 5) {
                // игрок вышел / мяч остался после перезапуска
                BallHandler.forget(this);
                this.dropStack(new ItemStack(ModItems.BASKETBALL));
                this.discard();
            }
            return;
        }
        if (!w.isClient && !BallHandler.owns(p.getUuid(), this)) {
            this.dropStack(new ItemStack(ModItems.BASKETBALL));
            this.discard();
            return;
        }

        double yaw = Math.toRadians(p.getYaw());
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        long t = w.getTime();
        double b = PoseState.bounce(t);

        double s = BodyApi.scale(p);
        // плавный переход между руками (кроссовер): мяч проходит низко перед игроком
        handLerp += MathHelper.clamp(getHandSide() - handLerp, -0.34f, 0.34f);
        double cross = 1.0 - Math.abs(handLerp);
        // при беге мяч выносится вперёд
        double speed = Math.min(0.35, Math.hypot(p.getX() - p.prevX, p.getZ() - p.prevZ));
        int mode = this.dataTracker.get(MODE);
        if (!w.isClient && mode != 0 && t >= modeUntil) {
            setMode(0, 0);
            mode = 0;
        }
        double fwd = 0.62 + 0.12 * cross + speed * 1.4;
        double ky = 0.9 - 0.45 * cross;
        if (mode == 1) {            // между ног: мяч проходит низко под корпусом
            fwd = 0.62 - 0.62 * cross + speed * 1.4;
            ky = 0.9 - 0.7 * cross;
        } else if (mode == 2) {     // за спиной
            fwd = 0.62 - 1.05 * cross;
            ky = 0.9 - 0.3 * cross;
        }
        double x = p.getX() + (rx * 0.42 * handLerp + fx * fwd) * s;
        double y = p.getY() + (0.02 + ky * b) * s;
        double z = p.getZ() + (rz * 0.42 * handLerp + fz * fwd) * s;
        if (isCarry()) {
            x = p.getX() + (rx * 0.18 * handLerp + fx * 0.25) * s;
            y = p.getY() + 2.15 * s;
            z = p.getZ() + (rz * 0.18 * handLerp + fz * 0.25) * s;
        }
        this.setPosition(x, y, z);
        this.setVelocity(Vec3d.ZERO);

        if (w.isClient) {
            if (!isCarry()) PoseState.markDribble(p.getId(), t, handLerp);
        } else if (t % 10 == 5 && !isCarry()) {
            w.playSound(null, x, y, z, SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.PLAYERS, 0.5f, 1.7f);
        }
    }

    /** Свободный мяч сам «ловится» ближайшим игроком (пас, подбор). */
    private void tryCatch() {
        if (getVelocity().lengthSquared() > 1.5 * 1.5) return;
        long now = this.getWorld().getTime();
        Box box = this.getBoundingBox().expand(0.5, 0.9, 0.5);
        for (PlayerEntity p : this.getWorld().getEntitiesByClass(PlayerEntity.class, box, e -> e.isAlive() && !e.isSpectator())) {
            if (!(p instanceof ServerPlayerEntity sp)) continue;
            if (p.getUuid().equals(graceUuid) && now < graceUntil) continue;
            if (BallHandler.receive(sp, this)) return;
        }
    }

    // ----- попадание в кольцо -----
    private void checkScore(Vec3d from, Vec3d to) {
        if (scored || to.y >= from.y) return;
        int top = MathHelper.floor(from.y);
        int bottom = MathHelper.floor(to.y);
        for (int by = top; by >= bottom; by--) {
            double plane = by + RIM_Y;
            if (from.y >= plane && to.y < plane) {
                double t = (from.y - plane) / (from.y - to.y);
                double x = from.x + (to.x - from.x) * t;
                double z = from.z + (to.z - from.z) * t;
                BlockPos pos = BlockPos.ofFloored(x, plane, z);
                if (!(this.getWorld().getBlockState(pos).getBlock() instanceof HoopBlock)) continue;
                double lx = x - pos.getX();
                double lz = z - pos.getZ();
                if (lx > 0.2 && lx < 0.8 && lz > 0.2 && lz < 0.8) {
                    onScore(pos);
                    return;
                }
            }
        }
    }

    private void onScore(BlockPos hoop) {
        scored = true;
        double dist = Math.hypot(shotFrom.x - (hoop.getX() + 0.5), shotFrom.z - (hoop.getZ() + 0.5));
        int points = dist >= 7.0 ? 3 : 2;

        ServerWorld sw = (ServerWorld) this.getWorld();
        sw.playSound(null, hoop, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.6f, 1.6f);
        sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                hoop.getX() + 0.5, hoop.getY() + 0.4, hoop.getZ() + 0.5, 15, 0.2, 0.1, 0.2, 0.0);

        if (this.getOwner() instanceof ServerPlayerEntity player) {
            ScoreKeeper.award(player, points, bounces == 0 ? "message.basketball.swish" : "message.basketball.score");
        }
    }

    // ----- отскоки -----
    @Override
    protected void onBlockHit(BlockHitResult hit) {
        Vec3d v = this.getVelocity();
        Vec3d nv = switch (hit.getSide().getAxis()) {
            case X -> new Vec3d(-v.x * BOUNCE, v.y * BOUNCE, v.z * BOUNCE);
            case Y -> new Vec3d(v.x * 0.9, -v.y * BOUNCE, v.z * 0.9);
            case Z -> new Vec3d(v.x * BOUNCE, v.y * BOUNCE, -v.z * BOUNCE);
        };
        this.setVelocity(nv);
        bounces++;

        if (this.getWorld().isClient) return;
        this.velocityModified = true;
        float volume = (float) MathHelper.clamp(v.length(), 0.1, 1.0);
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.BLOCK_SLIME_BLOCK_FALL, SoundCategory.NEUTRAL, volume, 1.6f);

        boolean rested = hit.getSide() == Direction.UP && Math.abs(nv.y) < 0.12;
        if (rested || bounces > 25) {
            this.dropStack(new ItemStack(ModItems.BASKETBALL));
            this.discard();
        }
    }
}
