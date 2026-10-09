package dev.aegisac.paper.world;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import dev.aegisac.common.config.PhysicsSettings;
import dev.aegisac.common.physics.*;
import dev.aegisac.common.world.BlockSample.Surface;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.*;
import org.bukkit.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class WorldCaptureTest {
    private Player player;
    private World world;
    private Block block;
    private VoxelShape shape;
    @BeforeEach void setup() {
        player=mock(Player.class);
        Server server=mock(Server.class); when(server.getBukkitVersion()).thenReturn("1.21.11-R0.1-SNAPSHOT"); when(player.getServer()).thenReturn(server); world=mock(World.class); block=mock(Block.class); shape=mock(VoxelShape.class);
        when(player.getWorld()).thenReturn(world); when(world.getUID()).thenReturn(new UUID(0,1));
        when(player.getLocation()).thenReturn(new Location(world,-.2,64,-.2));
        when(world.getMinHeight()).thenReturn(-64); when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
        when(world.getBlockAt(anyInt(),anyInt(),anyInt())).thenReturn(block);
        when(block.getCollisionShape()).thenReturn(shape);
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,0,0,1,.5,1)));
        when(block.getType()).thenReturn(Material.STONE_SLAB); when(block.getBlockData()).thenReturn(mock(BlockData.class));
        when(player.getPose()).thenReturn(Pose.STANDING);
        when(player.getAttribute(any())).thenAnswer(call->{
            Attribute type=call.getArgument(0); AttributeInstance instance=mock(AttributeInstance.class);
            double value=type==Attribute.MOVEMENT_SPEED?.1:type==Attribute.JUMP_STRENGTH?.42:
                    type==Attribute.GRAVITY?.08:type==Attribute.SCALE?1:type==Attribute.STEP_HEIGHT?.6:0;
            when(instance.getValue()).thenReturn(value); return instance;
        });
    }
    @Test void copiesLocalShapesToNegativeWorldCoordinatesAndDoesNotLoadChunks() {
        var result=new WorldCapture(PhysicsSettings.DEFAULT,()->true).capture(player,7,100);
        assertEquals(7,result.revision()); assertEquals(150,result.blocks().size());
        var box=result.shapes().getFirst();
        assertEquals(-3,box.minX()); assertEquals(62,box.minY()); assertEquals(62.5,box.maxY()); assertEquals(-3,box.minZ());
        verify(world,never()).getChunkAt(anyInt(),anyInt());
        assertEquals(.42,result.context().jumpStrength()); assertTrue(result.uncertainty().contains(Uncertainty.UNACKNOWLEDGED_WORLD));
    }
    @Test void rejectsOffThreadCaptureBeforeAnyWorldAccess() {
        assertThrows(IllegalStateException.class,()->new WorldCapture(PhysicsSettings.DEFAULT,()->false).capture(player,0,0));
        verify(player,never()).getLocation(); verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void unloadedChunksAreExplicitlyIncompleteAndNeverRead() {
        when(world.isChunkLoaded(anyInt(),anyInt())).thenReturn(false);
        var result=new WorldCapture(PhysicsSettings.DEFAULT,()->true).capture(player,0,0);
        assertTrue(result.uncertainty().contains(Uncertainty.UNLOADED_CHUNK)); assertTrue(result.shapes().isEmpty());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void blockBoxAndEntityBudgetsTruncateWithUncertainty() {
        var settings=new PhysicsSettings(true,1,4,32,32,1,250);
        Entity a=mock(Entity.class),b=mock(Entity.class);
        when(a.getBoundingBox()).thenReturn(new BoundingBox(0,64,0,1,65,1)); when(player.getNearbyEntities(4,3,4)).thenReturn(List.of(a,b));
        var result=new WorldCapture(settings,()->true).capture(player,0,0);
        assertEquals(32,result.blocks().size()); assertEquals(1,result.entities().size());
        assertTrue(result.uncertainty().contains(Uncertainty.SCAN_BUDGET));
        verify(world,times(32)).getBlockAt(anyInt(),anyInt(),anyInt()); verify(b,never()).getBoundingBox();
    }
    @Test void complexShapesAlsoObeyBoxBudget() {
        when(shape.getBoundingBoxes()).thenReturn(Collections.nCopies(33,new BoundingBox(0,0,0,1,1,1)));
        var settings=new PhysicsSettings(true,1,2,512,32,16,250);
        var result=new WorldCapture(settings,()->true).capture(player,0,0);
        assertTrue(result.shapes().size()<=32); assertTrue(result.uncertainty().contains(Uncertainty.SCAN_BUDGET));
        verify(world,times(1)).getBlockAt(anyInt(),anyInt(),anyInt());
    }
    @Test void waterloggedPartialBlocksAndSpecialMovementRemainExplicit() {
        var data=mock(Waterlogged.class); when(data.isWaterlogged()).thenReturn(true);
        when(block.getBlockData()).thenReturn(data); when(player.isGliding()).thenReturn(true); when(player.isInsideVehicle()).thenReturn(true);
        var result=new WorldCapture(PhysicsSettings.DEFAULT,()->true).capture(player,0,0);
        assertTrue(result.blocks().getFirst().surfaces().contains(Surface.WATER));
        assertTrue(result.context().uncertainty().contains(Uncertainty.ELYTRA)); assertTrue(result.context().uncertainty().contains(Uncertainty.VEHICLE));
    }
    @Test void exactProtocolBaselinesMatchOfficialLibraryIds() {
        assertEquals(PhysicsProfile.JAVA_1_8,PhysicsProfile.forProtocol(ClientVersion.V_1_8.getProtocolVersion()));
        assertEquals(PhysicsProfile.JAVA_1_16_5,PhysicsProfile.forProtocol(ClientVersion.V_1_16_4.getProtocolVersion()));
        assertEquals(PhysicsProfile.JAVA_1_21_11,PhysicsProfile.forProtocol(ClientVersion.V_1_21_11.getProtocolVersion()));
        assertNull(PhysicsProfile.forProtocol(ClientVersion.V_1_17.getProtocolVersion()));
    }
    @Test void aDifferentServerBaselineIsNotSilentlyTreatedAsCurrentGeometry() {
        when(player.getServer().getBukkitVersion()).thenReturn("1.22-R0.1-SNAPSHOT");
        assertTrue(new WorldCapture(PhysicsSettings.DEFAULT,()->true).capture(player,0,0)
                .uncertainty().contains(Uncertainty.UNSUPPORTED_VERSION));
    }
    @Test void allCollisionPiecesAreRetainedWithoutFullCubeSubstitution() {
        when(shape.getBoundingBoxes()).thenReturn(List.of(new BoundingBox(0,0,0,1,.5,1),new BoundingBox(0,.5,.5,1,1,1)));
        var result=new WorldCapture(PhysicsSettings.DEFAULT,()->true).capture(player,0,0);
        assertEquals(2,result.blocks().getFirst().shapes().size()); assertEquals(300,result.shapes().size());
        assertEquals(.5,result.shapes().get(0).maxY()-result.shapes().get(0).minY());
    }
    @Test void crossRegionAreaStopsBlockAndNearbyEntityReadsBeforeTheyHappen() {
        var access=mock(WorldAccess.class); when(access.entity(player)).thenReturn(true);
        var snapshot=new WorldCapture(PhysicsSettings.DEFAULT,access).capture(player,7,100);
        assertTrue(snapshot.uncertainty().contains(Uncertainty.REGION_NOT_OWNED)); assertTrue(snapshot.blocks().isEmpty());
        verify(world,never()).getBlockAt(anyInt(),anyInt(),anyInt()); verify(world,never()).isChunkLoaded(anyInt(),anyInt());
        verify(player,never()).getNearbyEntities(anyDouble(),anyDouble(),anyDouble());
        // Negative coordinates must floor into negative chunks, including the collision-neighbour border.
        verify(access).area(world,-1,-1,0,0);
    }
    @Test void foreignEntityBoundsAreNeverReadAndIterationRemainsBounded() {
        var access=mock(WorldAccess.class); when(access.entity(player)).thenReturn(true);
        when(access.area(eq(world),anyInt(),anyInt(),anyInt(),anyInt())).thenReturn(true);
        var foreign=mock(Entity.class); when(player.getNearbyEntities(2,3,2)).thenReturn(Collections.nCopies(100,foreign));
        var snapshot=new WorldCapture(PhysicsSettings.DEFAULT,access).capture(player,0,0);
        assertTrue(snapshot.uncertainty().contains(Uncertainty.REGION_NOT_OWNED)); assertTrue(snapshot.uncertainty().contains(Uncertainty.SCAN_BUDGET));
        assertTrue(snapshot.entities().isEmpty()); verify(foreign,never()).getBoundingBox();
        verify(access,times(PhysicsSettings.DEFAULT.maximumEntities())).entity(foreign);
    }

}
