package dev.aegisac.paper.world;

import dev.aegisac.common.collision.*;
import dev.aegisac.common.config.PhysicsSettings;
import dev.aegisac.common.physics.*;
import dev.aegisac.common.world.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Live reads require entity/area ownership. Cross-region geometry remains uncertain; never loads chunks. */
public final class WorldCapture {
    private final PhysicsSettings settings;
    private final WorldAccess access;
    public WorldCapture(PhysicsSettings settings, BooleanSupplier ownerThread) { this(settings,WorldAccess.primary(ownerThread)); }
    public WorldCapture(PhysicsSettings settings,WorldAccess access) { this.settings=settings; this.access=access; }
    public WorldSnapshot capture(Player player, long revision, long now) {
        if(!access.entity(player)) throw new IllegalStateException("World capture requires the owning thread");
        Location location=player.getLocation(); World world=player.getWorld();
        int x=location.getBlockX(),y=location.getBlockY(),z=location.getBlockZ(),r=settings.radius();
        if(Math.abs((long)x)>30_000_000 || Math.abs((long)y)>30_000_000 || Math.abs((long)z)>30_000_000)
            throw new IllegalArgumentException("Capture outside coordinate budget");
        int minY=Math.max(y-2,world.getMinHeight()), maxY=Math.min(y+4,world.getMaxHeight());
        if(minY>=maxY) throw new IllegalArgumentException("Capture outside world height");
        // Shapes can protrude into adjacent cells. A one-block border is scanned but excluded from usable coverage.
        Aabb coverage=new Aabb(x-r+1,minY+1,z-r+1,x+r,maxY-1,z+r);
        List<BlockSample> blocks=new ArrayList<>(); List<Aabb> entities=new ArrayList<>();
        EnumSet<Uncertainty> reasons=EnumSet.of(Uncertainty.UNACKNOWLEDGED_WORLD);
        if (!player.getServer().getBukkitVersion().startsWith("1.21.11-")) reasons.add(Uncertainty.UNSUPPORTED_VERSION);
        if(!access.area(world,(x-r-1)>>4,(z-r-1)>>4,(x+r+1)>>4,(z+r+1)>>4)) {
            reasons.add(Uncertainty.REGION_NOT_OWNED);
            return new WorldSnapshot(world.getUID(),revision,now,coverage,List.of(),List.of(),context(player),reasons);
        }
        int scanned=0,boxes=0;
        outer: for(int bx=x-r;bx<=x+r;bx++) for(int bz=z-r;bz<=z+r;bz++) {
            if(!world.isChunkLoaded((bx-1)>>4,(bz-1)>>4) || !world.isChunkLoaded((bx+1)>>4,(bz-1)>>4)
                    || !world.isChunkLoaded((bx-1)>>4,(bz+1)>>4) || !world.isChunkLoaded((bx+1)>>4,(bz+1)>>4)) { reasons.add(Uncertainty.UNLOADED_CHUNK); continue; }
            for(int by=minY;by<maxY;by++) {
                if(scanned++>=settings.maximumBlocks()) { reasons.add(Uncertainty.SCAN_BUDGET); break outer; }
                var block=world.getBlockAt(bx,by,bz);
                List<Aabb> shapes=new ArrayList<>();
                // Bukkit VoxelShape boxes are relative to the block, unlike Block#getBoundingBox.
                for(BoundingBox shape:block.getCollisionShape().getBoundingBoxes()) {
                    if(boxes++>=settings.maximumBoxes()) { reasons.add(Uncertainty.SCAN_BUDGET); break outer; }
                    shapes.add(box(shape).move(new Vec3(bx,by,bz)));
                }
                var traits=BlockClassifier.surfaces(block.getType(),block.getBlockData());
                if(!shapes.isEmpty() || !traits.isEmpty()) blocks.add(new BlockSample(
                        new Aabb(bx,by,bz,bx+1,by+1,bz+1),shapes,traits));
            }
        }
        // Bukkit bounds the spatial query; our retained records and iteration are capped independently.
        var nearby=player.getNearbyEntities(r,3,r);
        int entitiesScanned=0;
        for(var entity:nearby) {
            if(entitiesScanned++>=settings.maximumEntities()) { reasons.add(Uncertainty.SCAN_BUDGET); break; }
            if(!access.entity(entity)) { reasons.add(Uncertainty.REGION_NOT_OWNED); continue; }
            entities.add(box(entity.getBoundingBox()));
        }
        return new WorldSnapshot(world.getUID(),revision,now,coverage,blocks,entities,context(player),reasons);
    }
    private MotionContext context(Player p) {
        EnumSet<Uncertainty> reasons=EnumSet.noneOf(Uncertainty.class);
        if(p.isGliding()) reasons.add(Uncertainty.ELYTRA);
        if(p.isInsideVehicle()) reasons.add(Uncertainty.VEHICLE);
        if(p.isRiptiding()) reasons.add(Uncertainty.RIPTIDE);
        if(p.isFlying() || p.getAllowFlight()) reasons.add(Uncertainty.FLIGHT);
        if(p.getPose()!=org.bukkit.entity.Pose.STANDING) reasons.add(Uncertainty.UNSUPPORTED_POSE);
        double speed=p.getAttribute(Attribute.MOVEMENT_SPEED).getValue();
        // The authoritative attribute already includes sprint/effect/plugin modifiers.
        if(p.isSprinting()) speed/=1.3;
        double jump=p.getAttribute(Attribute.JUMP_STRENGTH).getValue();
        if(p.getAttribute(Attribute.GRAVITY).getValue()!=.08 || p.getAttribute(Attribute.SCALE).getValue()!=1
                || p.getAttribute(Attribute.STEP_HEIGHT).getValue()!=.6) reasons.add(Uncertainty.UNSUPPORTED_ATTRIBUTE);
        for(var effect:p.getActivePotionEffects()) {
            var type=effect.getType();
            if(type!=PotionEffectType.SPEED && type!=PotionEffectType.SLOWNESS && type!=PotionEffectType.JUMP_BOOST
                    && type!=PotionEffectType.LEVITATION && type!=PotionEffectType.SLOW_FALLING)
                reasons.add(Uncertainty.UNSUPPORTED_EFFECT);
        }
        return new MotionContext(speed,jump,amplifier(p,PotionEffectType.JUMP_BOOST),amplifier(p,PotionEffectType.LEVITATION),
                p.hasPotionEffect(PotionEffectType.SLOW_FALLING),reasons);
    }
    private int amplifier(Player p,PotionEffectType type) { var effect=p.getPotionEffect(type); return effect==null?-1:effect.getAmplifier(); }
    private static Aabb box(BoundingBox b) { return new Aabb(b.getMinX(),b.getMinY(),b.getMinZ(),b.getMaxX(),b.getMaxY(),b.getMaxZ()); }
}
