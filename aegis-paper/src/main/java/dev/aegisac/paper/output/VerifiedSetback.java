package dev.aegisac.paper.output;
import dev.aegisac.api.check.SafePositionSnapshot;
import dev.aegisac.common.config.OutputSettings;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.BoundingBox;
/** The only implemented correction is an independently revalidated verified safe position. */
public final class VerifiedSetback {
    private VerifiedSetback() { }
    public static boolean eligible(SafePositionSnapshot safe,java.util.UUID world,long session,long revision,long now,int maxAgeMillis) {
        return safe!=null&&safe.verified()&&safe.reasons().isEmpty()&&safe.sessionId()==session&&safe.worldRevision()==revision&&safe.world().equals(world)
                &&now>=safe.observedNanos()&&now-safe.observedNanos()<=maxAgeMillis*1_000_000L
                &&Double.isFinite(safe.x())&&Double.isFinite(safe.y())&&Double.isFinite(safe.z())
                &&Math.abs(safe.x())<=30_000_000&&Math.abs(safe.y())<=30_000_000&&Math.abs(safe.z())<=30_000_000;
    }
    public static boolean move(Server server,Player player,SafePositionSnapshot safe,long session,long revision,long now,OutputSettings.Setbacks policy) {
        return move(server,player,safe,session,revision,now,policy,dev.aegisac.paper.world.WorldAccess.primary(server));
    }
    public static boolean move(Server server,Player player,SafePositionSnapshot safe,long session,long revision,long now,OutputSettings.Setbacks policy,dev.aegisac.paper.world.WorldAccess access) {
        if(!access.entity(player)) throw new IllegalStateException("Setbacks require the owning server thread");
        // Folia requires an independently validated async teleport pipeline; never fall back to synchronous teleport.
        if(!access.synchronousTeleports()) return false;
        var target=destination(player,safe,session,revision,now,policy,access);
        try { return target!=null&&player.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN); } catch(RuntimeException unavailable) { return false; }
    }
    /** Only already-owned, loaded destination geometry is accepted; native teleport handles migration. */
    public static java.util.concurrent.CompletableFuture<Boolean> moveAsync(Player player,SafePositionSnapshot safe,long session,long revision,long now,
            OutputSettings.Setbacks policy,dev.aegisac.paper.world.WorldAccess access,java.util.function.BooleanSupplier current) {
        if(!access.entity(player)) throw new IllegalStateException("Setbacks require the entity owner");
        var target=destination(player,safe,session,revision,now,policy,access);
        if(target==null||!current.getAsBoolean()) return java.util.concurrent.CompletableFuture.completedFuture(false);
        try { return player.teleportAsync(target,PlayerTeleportEvent.TeleportCause.PLUGIN); }
        catch(RuntimeException unavailable) { return java.util.concurrent.CompletableFuture.completedFuture(false); }
    }
    private static Location destination(Player player,SafePositionSnapshot safe,long session,long revision,long now,
            OutputSettings.Setbacks policy,dev.aegisac.paper.world.WorldAccess access) {
        if(player==null||!player.isOnline()||!eligible(safe,player.getWorld().getUID(),session,revision,now,policy.maximumAgeMillis())) return null;
        try {
            World world=player.getWorld(); var target=player.getLocation().clone();
            if(!Double.isFinite(target.getX()+target.getY()+target.getZ())||!Float.isFinite(target.getYaw())||!Float.isFinite(target.getPitch())) return null;
            if(Math.hypot(Math.hypot(target.getX()-safe.x(),target.getZ()-safe.z()),target.getY()-safe.y())>policy.maximumDistance()) return null;
            target.setX(safe.x()); target.setY(safe.y()); target.setZ(safe.z());
            if(world.getWorldBorder()==null||!world.getWorldBorder().isInside(target)) return null;
            var current=player.getBoundingBox(); double width=current.getWidthX(),depth=current.getWidthZ(),height=current.getHeight();
            if(!Double.isFinite(width+depth+height)||width<=0||depth<=0||height<=0||width>2||depth>2||height>4) return null;
            var box=new BoundingBox(safe.x()-width/2+1e-7,safe.y()+1e-7,safe.z()-depth/2+1e-7,safe.x()+width/2-1e-7,safe.y()+height-1e-7,safe.z()+depth/2-1e-7);
            int minX=(int)Math.floor(box.getMinX())-1,maxX=(int)Math.floor(box.getMaxX())+1,minZ=(int)Math.floor(box.getMinZ())-1,maxZ=(int)Math.floor(box.getMaxZ())+1;
            int minY=(int)Math.floor(box.getMinY())-1,maxY=(int)Math.floor(box.getMaxY())+1;
            if(minY<world.getMinHeight()||maxY>=world.getMaxHeight()) return null;
            if(!access.area(world,(minX-1)>>4,(minZ-1)>>4,(maxX+1)>>4,(maxZ+1)>>4)) return null;
            for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) if(!world.isChunkLoaded(x>>4,z>>4)) return null;
            int scanned=0,shapes=0;
            for(int x=minX;x<=maxX;x++) for(int z=minZ;z<=maxZ;z++) for(int y=minY;y<=maxY;y++) {
                if(++scanned>128) return null; var block=world.getBlockAt(x,y,z);
                var cell=new BoundingBox(x,y,z,x+1,y+1,z+1);
                if(cell.overlaps(box)&&(block.isLiquid()||block.getType()==Material.FIRE||block.getType()==Material.SOUL_FIRE)) return null;
                for(var shape:block.getCollisionShape().getBoundingBoxes()) { if(++shapes>256||shape.clone().shift(x,y,z).overlaps(box)) return null; }
            }
            return target;
        } catch(RuntimeException unavailable) { return null; }
    }
}
