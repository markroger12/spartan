package dev.aegisac.paper.output;
import dev.aegisac.api.check.SafePositionSnapshot;
import dev.aegisac.common.config.OutputSettings;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class VerifiedSetbackTest {
    final UUID worldId=UUID.randomUUID(); Server server; Player player; World world; Block air;
    final OutputSettings.Setbacks policy=new OutputSettings.Setbacks(true,"LAST_VALID_POSITION",true,true,1,0,1000,500,8,Map.of("SpeedA",true));
    SafePositionSnapshot safe(boolean verified) { return new SafePositionSnapshot(worldId,1,2,1000,1,1,1,verified,4,Set.of()); }
    @BeforeEach void setup() {
        server=mock(Server.class); player=mock(Player.class); world=mock(World.class); air=mock(Block.class);
        when(server.isPrimaryThread()).thenReturn(true); when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world); when(world.getUID()).thenReturn(worldId);
        when(player.getLocation()).thenReturn(new Location(world,0,1,0)); when(player.getBoundingBox()).thenReturn(new BoundingBox(-.3,1,-.3,.3,2.8,.3));
        var border=mock(WorldBorder.class); when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320); when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(air); when(air.getType()).thenReturn(Material.AIR);
        var shape=mock(VoxelShape.class); when(shape.getBoundingBoxes()).thenReturn(List.of()); when(air.getCollisionShape()).thenReturn(shape);
        when(player.teleport(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(true);
    }
    @Test void verifiedFreshSameSessionPositionIsRecheckedAndMovedOnOwnerThread() {
        assertTrue(VerifiedSetback.move(server,player,safe(true),1,2,1000,policy));
        verify(player).teleport(argThat((Location l)->l.getX()==1&&l.getY()==1&&l.getZ()==1),eq(PlayerTeleportEvent.TeleportCause.PLUGIN));
    }
    @Test void unverifiedStaleForeignOrInvalidCandidateNeverTeleports() {
        assertFalse(VerifiedSetback.move(server,player,safe(false),1,2,1000,policy));
        assertFalse(VerifiedSetback.move(server,player,safe(true),2,2,1000,policy));
        assertFalse(VerifiedSetback.move(server,player,safe(true),1,3,1000,policy));
        assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,600_001_000L,policy));
        assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,999,policy));
        assertFalse(VerifiedSetback.eligible(new SafePositionSnapshot(worldId,1,2,1000,Double.MAX_VALUE,1,1,true,4,Set.of()),worldId,1,2,1000,500));
        verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
    }
    @Test void unloadedGeometryDoesNotLoadChunksAndSolidCollisionRejects() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false); assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,1000,policy)); verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        var stone=mock(Block.class); var shape=mock(VoxelShape.class); when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,0,0,1,1,1))); when(stone.getCollisionShape()).thenReturn(shape);
        when(world.getBlockAt(1,1,1)).thenReturn(stone); assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,1000,policy));
        verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
    }
    @Test void distanceAndThreadAreStrictGates() {
        when(player.getLocation()).thenReturn(new Location(world,100,1,0)); assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,1000,policy));
        when(server.isPrimaryThread()).thenReturn(false); assertThrows(IllegalStateException.class,()->VerifiedSetback.move(server,player,safe(true),1,2,1000,policy));
    }
    @Test void foreignDestinationAreaRejectsBeforeBlockScan() {
        var access=mock(dev.aegisac.paper.world.WorldAccess.class); when(access.entity(player)).thenReturn(true); when(access.synchronousTeleports()).thenReturn(true);
        assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,1000,policy,access));
        verify(world,never()).isChunkLoaded(anyInt(),anyInt()); verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
        verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
    }
    @Test void foliaNeverFallsBackToSynchronousTeleportEvenWithOwnedSource() {
        var scheduler=mock(dev.aegisac.paper.scheduler.PlatformScheduler.class); when(scheduler.mode()).thenReturn(dev.aegisac.paper.scheduler.PlatformScheduler.Mode.FOLIA); when(scheduler.owns(player)).thenReturn(true);
        assertFalse(VerifiedSetback.move(server,player,safe(true),1,2,1000,policy,dev.aegisac.paper.world.WorldAccess.scheduled(scheduler)));
        verify(player,never()).getWorld(); verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
    }

    @Test void asyncCorrectionUsesNativeFutureWithoutWaitingOrSynchronousFallback() {
        var scheduler=new dev.aegisac.paper.scheduler.OwnershipHarness(); var completion=new java.util.concurrent.CompletableFuture<Boolean>();
        when(player.teleportAsync(any(Location.class),eq(PlayerTeleportEvent.TeleportCause.PLUGIN))).thenReturn(completion);
        scheduler.at(player,()->{
            var result=VerifiedSetback.moveAsync(player,safe(true),1,2,1000,policy,dev.aegisac.paper.world.WorldAccess.scheduled(scheduler),()->true);
            assertSame(completion,result); assertFalse(result.isDone());
        });
        completion.complete(true); assertTrue(completion.join()); verify(player,never()).teleport(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
    }
    @Test void asyncCorrectionRejectsUnownedGeometryAndRevokedAuthorization() {
        var scheduler=new dev.aegisac.paper.scheduler.OwnershipHarness(); var access=dev.aegisac.paper.world.WorldAccess.scheduled(scheduler);
        scheduler.at(player,()->{
            scheduler.area=false; assertFalse(VerifiedSetback.moveAsync(player,safe(true),1,2,1000,policy,access,()->true).join());
            verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
            scheduler.area=true; assertFalse(VerifiedSetback.moveAsync(player,safe(true),1,2,1000,policy,access,()->false).join());
        });
        verify(player,never()).teleportAsync(any(Location.class),any(PlayerTeleportEvent.TeleportCause.class));
        assertThrows(IllegalStateException.class,()->VerifiedSetback.moveAsync(player,safe(true),1,2,1000,policy,access,()->true));
    }

}
