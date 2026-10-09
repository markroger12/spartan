package dev.aegisac.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.packettype.*;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.*;
import dev.aegisac.common.packet.NormalizedPacket;
import dev.aegisac.common.packet.NormalizedPacket.*;
import dev.aegisac.paper.packet.*;
import io.github.retrooper.packetevents.netty.buffer.ByteBufOperatorModernImpl;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real PacketEvents wrappers and actual Netty buffers; no mock decoder outputs. */
class PacketNormalizerTest {
    private User user;
    private final PacketEventsNormalizer normalizer = new PacketEventsNormalizer(8192);
    @BeforeEach void setup() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class, withSettings().stubOnly().defaultAnswer(RETURNS_DEEP_STUBS));
        when(api.getServerManager().getVersion()).thenReturn(ServerVersion.V_1_21_11);
        var netty=mock(com.github.retrooper.packetevents.netty.NettyManager.class,withSettings().stubOnly());
        when(api.getNettyManager()).thenReturn(netty);
        when(netty.getByteBufOperator()).thenReturn(new ByteBufOperatorModernImpl());
        when(netty.getByteBufAllocationOperator()).thenReturn(new io.github.retrooper.packetevents.impl.netty.buffer.ByteBufAllocationOperatorImpl());
        when(api.getSettings().getResourceProvider()).thenReturn(path -> PacketEvents.class.getClassLoader().getResourceAsStream(path));
        PacketEvents.setAPI(api);
        user = mock(User.class);
        when(user.getClientVersion()).thenReturn(ClientVersion.V_1_21_11);
    }
    @AfterEach void cleanup() { PacketEvents.setAPI(null); }
    private PacketReceiveEvent inbound(PacketType.Play.Client type, ClientVersion version, ByteBuf buffer, PacketWrapper<?> cached) {
        var event = mock(PacketReceiveEvent.class);
        when(user.getClientVersion()).thenReturn(version);
        when(event.getUser()).thenReturn(user);when(event.getServerVersion()).thenReturn(version.toServerVersion());
        when(event.getPacketType()).thenReturn(type);when(event.getByteBuf()).thenReturn(buffer);
        doReturn(cached).when(event).getLastUsedWrapper();
        return event;
    }
    private PacketSendEvent outbound(PacketType.Play.Server type, ByteBuf buffer, PacketWrapper<?> cached) {
        var event = mock(PacketSendEvent.class);
        when(event.getUser()).thenReturn(user);when(event.getServerVersion()).thenReturn(ServerVersion.V_1_21_11);
        when(event.getPacketType()).thenReturn(type);when(event.getByteBuf()).thenReturn(buffer);
        doReturn(cached).when(event).getLastUsedWrapper();return event;
    }
    @Test void modernMovementDecodesRealWireBytesAndSurvivesBufferRelease() {
        ByteBuf buffer=Unpooled.buffer();
        buffer.writeDouble(1.25).writeDouble(64).writeDouble(-3.5).writeFloat(90).writeFloat(20).writeByte(3);
        Movement packet;
        try {
            var event=inbound(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,ClientVersion.V_1_21_11,buffer,null);
            packet=(Movement)normalizer.normalize(event);verify(event,never()).getPlayer();
        } finally {buffer.release();}
        assertTrue(packet.position());assertTrue(packet.rotation());assertEquals(1.25,packet.x());assertEquals(-3.5,packet.z());
        assertEquals(90,packet.yaw());assertTrue(packet.onGround());assertTrue(packet.horizontalCollision());
    }
    @ParameterizedTest @EnumSource(value=ClientVersion.class,names={"V_1_8","V_1_16_4","V_1_21_11"})
    void rotationOnlyPacketsPreservePresenceAndVersionSpecificGroundFlags(ClientVersion version) {
        ByteBuf buffer=Unpooled.buffer();buffer.writeFloat(30).writeFloat(-40).writeByte(1);
        try {
            Movement value=(Movement)normalizer.normalize(inbound(PacketType.Play.Client.PLAYER_ROTATION,version,buffer,null));
            assertFalse(value.position());assertTrue(value.rotation());assertEquals(-40,value.pitch());assertTrue(value.onGround());
        } finally {buffer.release();}
    }
    @Test void modernPongAndLegacyWindowIdsDecodeWithoutUnsignedOrChannelConfusion() {
        ByteBuf pong=Unpooled.buffer().writeInt(Integer.MIN_VALUE);
        try {assertEquals(new Timing(TimingKind.PING,Integer.MIN_VALUE,0,true),
                normalizer.normalize(inbound(PacketType.Play.Client.PONG,ClientVersion.V_1_21_11,pong,null)));}
        finally {pong.release();}
        ByteBuf window=Unpooled.buffer().writeByte(5).writeShort(Short.MIN_VALUE).writeBoolean(true);
        try {assertEquals(new Timing(TimingKind.WINDOW,Short.MIN_VALUE,5,true),
                normalizer.normalize(inbound(PacketType.Play.Client.WINDOW_CONFIRMATION,ClientVersion.V_1_16_4,window,null)));}
        finally {window.release();}
    }
    @Test void keepAliveRoundTripsLongIdsAndTruncatedPayloadFails() {
        ByteBuf bytes=Unpooled.buffer().writeLong(Long.MIN_VALUE);
        try {assertEquals(new Timing(TimingKind.KEEP_ALIVE,Long.MIN_VALUE,0,true),
                normalizer.normalize(outbound(PacketType.Play.Server.KEEP_ALIVE,bytes,null)));}
        finally {bytes.release();}
        ByteBuf truncated=Unpooled.buffer().writeByte(1);
        try {assertThrows(RuntimeException.class,()->normalizer.normalize(inbound(PacketType.Play.Client.KEEP_ALIVE,
                ClientVersion.V_1_21_11,truncated,null)));} finally {truncated.release();}
    }
    @Test void cachedWrapperChangesAreHonoredInsteadOfReReadingOldWireData() {
        ByteBuf bytes=Unpooled.buffer().writeDouble(999);
        try {
            var cached=new WrapperPlayClientPlayerFlying(true,true,true,new Location(2,65,3,40,50));
            var result=(Movement)normalizer.normalize(inbound(PacketType.Play.Client.PLAYER_POSITION_AND_ROTATION,
                    ClientVersion.V_1_21_11,bytes,cached));
            assertEquals(2,result.x());assertEquals(40,result.yaw());assertEquals(0,bytes.readerIndex());
        } finally {bytes.release();}
    }
    @Test void largeTrackedPacketsAreRejectedBeforeParsingButChunkTrafficIsNotDecoded() {
        ByteBuf bytes=Unpooled.buffer(9000).writeZero(9000);
        try {
            var event=inbound(PacketType.Play.Client.CLICK_WINDOW,ClientVersion.V_1_21_11,bytes,null);
            assertThrows(PacketDecodeException.class,()->normalizer.normalize(event));assertEquals(0,bytes.readerIndex());
            var other=outbound(PacketType.Play.Server.CHUNK_DATA,bytes,null);
            assertEquals(dev.aegisac.common.packet.PacketKind.WORLD_CHANGE,normalizer.normalize(other).kind());verify(other,never()).getByteBuf();
        } finally {bytes.release();}
    }
    @Test void teleportPreservesFullRelativeMaskAndDeltaForFuturePrediction() {
        ByteBuf bytes=Unpooled.buffer();
        try {
            var cached=new WrapperPlayServerPlayerPositionAndLook(8,new Vector3d(1,2,3),new Vector3d(4,5,6),30,40,
                    new RelativeFlag(0x101));
            var value=(Teleport)normalizer.normalize(outbound(PacketType.Play.Server.PLAYER_POSITION_AND_LOOK,bytes,cached));
            assertEquals(8,value.id());assertEquals(0x101,value.relativeFlags());assertEquals(4,value.deltaX());assertEquals(40,value.pitch());
        } finally {bytes.release();}
    }
    @Test void normalizedActionsAndInventoryRetainOnlySmallValues() {
        ByteBuf bytes=Unpooled.buffer();
        try {
            assertEquals(new HeldItem(4),normalizer.normalize(inbound(PacketType.Play.Client.HELD_ITEM_CHANGE,
                    ClientVersion.V_1_21_11,bytes,new WrapperPlayClientHeldItemChange(4))));
            assertEquals(new Inventory(InventoryAction.CLOSE,9,-1,-1,-1,""),normalizer.normalize(inbound(
                    PacketType.Play.Client.CLOSE_WINDOW,ClientVersion.V_1_21_11,bytes,new WrapperPlayClientCloseWindow(9))));
            var input=(Input)normalizer.normalize(inbound(PacketType.Play.Client.PLAYER_INPUT,ClientVersion.V_1_21_11,bytes,
                    new WrapperPlayClientPlayerInput(true,true,false,false,true,false,true)));
            assertEquals(0,input.forward());assertEquals(3,input.directionalMask());assertTrue(input.jump());
            var impulse=(Impulse)normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_VELOCITY,bytes,
                    new WrapperPlayServerEntityVelocity(42,new Vector3d(0.5,0.25,-0.5))));
            assertEquals(42,impulse.entityId());assertEquals(0.25,impulse.y());
        } finally {bytes.release();}
    }
    @Test void brandIsBoundedAndNoPayloadArrayIsRetained() {
        ByteBuf bytes=Unpooled.buffer();byte[] brand={7,'v','a','n','i','l','l','a'};
        Payload value;
        try {value=(Payload)normalizer.normalize(inbound(PacketType.Play.Client.PLUGIN_MESSAGE,ClientVersion.V_1_21_11,bytes,
                new WrapperPlayClientPluginMessage("minecraft:brand",brand)));}
        finally {bytes.release();}
        brand[1]='x';assertEquals("vanilla",value.brand());assertEquals("minecraft:brand",value.channel());
    }
    @Test void clientCapabilitiesHandleUnknownAndLegacyExplicitly() {
        var provider=new PacketEventsVersionProvider();
        when(user.getClientVersion()).thenReturn(ClientVersion.V_1_8);
        assertFalse(provider.protocol(user).pingPong());assertFalse(provider.protocol(user).teleportConfirmation());
        when(user.getClientVersion()).thenReturn(ClientVersion.V_1_21_11);
        assertTrue(provider.protocol(user).pingPong());assertTrue(provider.protocol(user).teleportConfirmation());
        when(user.getClientVersion()).thenReturn(ClientVersion.UNKNOWN);
        assertFalse(provider.protocol(user).known());assertFalse(provider.protocol(user).pingPong());
    }
    @Test void targetPacketsCopyScalarStateAndRemovalLists() {
        ByteBuf bytes=Unpooled.buffer();
        try {
            var uuid=new java.util.UUID(0,99);
            var spawn=new WrapperPlayServerSpawnEntity(9,java.util.Optional.of(uuid),
                    com.github.retrooper.packetevents.protocol.entity.type.EntityTypes.PLAYER,new Vector3d(1,2,3),0,0,0,0,java.util.Optional.empty());
            assertEquals(new EntitySpawn(9,uuid,true,1,2,3),normalizer.normalize(outbound(PacketType.Play.Server.SPAWN_ENTITY,bytes,spawn)));
            var legacy=new WrapperPlayServerSpawnPlayer(10,uuid,new Vector3d(2,3,4),0,0,java.util.List.of());
            assertEquals(new EntitySpawn(10,uuid,true,2,3,4),normalizer.normalize(outbound(PacketType.Play.Server.SPAWN_PLAYER,bytes,legacy)));
            assertEquals(new EntityMove(9,true,false,0,.5,.25,-.5),normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_RELATIVE_MOVE,
                    bytes,new WrapperPlayServerEntityRelativeMove(9,.5,.25,-.5,true))));
            assertEquals(new EntityMove(9,true,false,0,.5,.25,-.5),normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_RELATIVE_MOVE_AND_ROTATION,
                    bytes,new WrapperPlayServerEntityRelativeMoveAndRotation(9,.5,.25,-.5,0,0,true))));
            assertEquals(new EntityMove(9,false,true,1,4,5,6),normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_TELEPORT,bytes,
                    new WrapperPlayServerEntityTeleport(9,new Vector3d(4,5,6),new Vector3d(0,0,0),0,0,new RelativeFlag(1),true))));
            assertEquals(new EntityMove(9,false,false,0,4,5,6),normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_POSITION_SYNC,bytes,
                    new WrapperPlayServerEntityPositionSync(9,new com.github.retrooper.packetevents.protocol.entity.EntityPositionData(
                            new Vector3d(4,5,6),new Vector3d(0,0,0),0,0),true))));
            assertEquals(new EntityDimensionsUnknown(9),normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_METADATA,bytes,
                    new WrapperPlayServerEntityMetadata(9,java.util.List.of()))));
            assertEquals(new EntityDimensionsUnknown(9),normalizer.normalize(outbound(PacketType.Play.Server.UPDATE_ATTRIBUTES,bytes,
                    new WrapperPlayServerUpdateAttributes(9,java.util.List.of()))));
            int[] ids={9,10}; var removal=(EntityRemove)normalizer.normalize(outbound(PacketType.Play.Server.DESTROY_ENTITIES,bytes,new WrapperPlayServerDestroyEntities(ids)));
            ids[0]=200; assertEquals(java.util.List.of(9,10),removal.entityIds());
            assertThrows(UnsupportedOperationException.class,()->removal.entityIds().clear());
            assertThrows(PacketDecodeException.class,()->normalizer.normalize(outbound(PacketType.Play.Server.DESTROY_ENTITIES,bytes,new WrapperPlayServerDestroyEntities(new int[1025]))));
        } finally { bytes.release(); }
    }
    @Test void relativeMoveAndRemovalDecodeRealModernWireBytes() {
        ByteBuf bytes=Unpooled.buffer().writeByte(9).writeShort(2048).writeShort(-1024).writeShort(4096).writeBoolean(true);
        EntityMove move;
        try { move=(EntityMove)normalizer.normalize(outbound(PacketType.Play.Server.ENTITY_RELATIVE_MOVE,bytes,null)); }
        finally { bytes.release(); }
        assertEquals(new EntityMove(9,true,false,0,.5,-.25,1),move);
        ByteBuf removal=Unpooled.buffer().writeByte(2).writeByte(9).writeByte(10);
        try { assertEquals(new EntityRemove(java.util.List.of(9,10)),normalizer.normalize(outbound(PacketType.Play.Server.DESTROY_ENTITIES,removal,null))); }
        finally { removal.release(); }
    }

    @Test void blockAndMenuWrappersPreserveProtocolSentinelsAndSequence() {
        ByteBuf bytes=Unpooled.buffer();
        try {
            var pos=new com.github.retrooper.packetevents.util.Vector3i(2,64,-3);
            var digging=new WrapperPlayClientPlayerDigging(DiggingAction.FINISHED_DIGGING,pos,2,41);
            var dig=(BlockAction)normalizer.normalize(inbound(PacketType.Play.Client.PLAYER_DIGGING,ClientVersion.V_1_21_11,bytes,digging));
            assertEquals("FINISHED_DIGGING",dig.action()); assertEquals(41,dig.sequence()); assertEquals(-3,dig.z());
            var placement=new WrapperPlayClientPlayerBlockPlacement(InteractionHand.MAIN_HAND,pos,
                    com.github.retrooper.packetevents.protocol.world.BlockFace.UP,new com.github.retrooper.packetevents.util.Vector3f(.5f,1,.5f),null,false,42);
            var placed=(BlockAction)normalizer.normalize(inbound(PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,ClientVersion.V_1_21_11,bytes,placement));
            assertEquals(42,placed.sequence()); assertEquals(1,placed.face()); assertEquals(1,placed.cursorY());
            placement.setFaceId(255);
            assertEquals(255,((BlockAction)normalizer.normalize(inbound(PacketType.Play.Client.PLAYER_BLOCK_PLACEMENT,ClientVersion.V_1_8,bytes,placement))).face());
            var open=new WrapperPlayServerOpenWindow(4,0,net.kyori.adventure.text.Component.text("fixture"));
            assertEquals(4,((Inventory)normalizer.normalize(outbound(PacketType.Play.Server.OPEN_WINDOW,bytes,open))).window());
            var click=new WrapperPlayClientClickWindow(4,1,-999,0,WrapperPlayClientClickWindow.WindowClickType.PICKUP,java.util.Map.of(),java.util.Optional.empty());
            assertEquals(-999,((Inventory)normalizer.normalize(inbound(PacketType.Play.Client.CLICK_WINDOW,ClientVersion.V_1_21_11,bytes,click))).slot());
        } finally { bytes.release(); }
    }
    @Test void heldSlotWireRetainsInvalidValueForDiagnosticBeforeStateRejection() {
        ByteBuf bytes=Unpooled.buffer().writeShort(-1);
        try {
            assertEquals(new HeldItem(-1),normalizer.normalize(inbound(PacketType.Play.Client.HELD_ITEM_CHANGE,ClientVersion.V_1_21_11,bytes,null)));
        } finally { bytes.release(); }
    }

}
