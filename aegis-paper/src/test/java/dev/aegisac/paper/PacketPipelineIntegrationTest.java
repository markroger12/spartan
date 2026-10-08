package dev.aegisac.paper;
import com.github.retrooper.packetevents.*;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPing;
import dev.aegisac.common.config.PipelineSettings;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.player.*;
import dev.aegisac.paper.packet.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PacketPipelineIntegrationTest {
    private Fixture fixture;
    @AfterEach void cleanup() {if(fixture!=null)fixture.close();PacketEvents.setAPI(null);}
    @Test void decodeFailureInvalidatesStateAndSubsequentPacketsRecoverThroughTheQueue() {
        AtomicBoolean fail=new AtomicBoolean(true);
        fixture=new Fixture(false,event->{if(fail.get())throw new PacketDecodeException("fixture");return NormalizedPacket.Other.INSTANCE;});
        fixture.listener.onPacketReceive(fixture.receive());
        assertEquals(1,fixture.metrics.pipeline().decodeRejected());assertEquals(1,fixture.data.lossEpoch());
        fail.set(false);fixture.listener.onPacketReceive(fixture.receive());fixture.executor.drain();
        assertEquals(1,fixture.data.snapshot().inboundPackets());assertEquals(1,fixture.data.snapshot().connection().processedEpoch());
    }
    @Test void shutdownDropsPendingFramesAndCannotRestartClosedWorkers() {
        fixture=new Fixture(false,event->NormalizedPacket.Other.INSTANCE);
        fixture.listener.onPacketReceive(fixture.receive());
        assertEquals(1,fixture.metrics.pipeline().queued());fixture.engine.close();fixture.executor.drain();
        assertEquals(0,fixture.data.snapshot().inboundPackets());assertEquals(0,fixture.metrics.pipeline().queued());
        assertEquals(0,fixture.metrics.pipeline().activeQueues());assertThrows(IllegalStateException.class,fixture.engine::start);
    }
    @Test void probesAreOptInAndOnlyUseKnownModernClientCapabilities() {
        fixture=new Fixture(false,event->NormalizedPacket.Other.INSTANCE);
        fixture.engine.probeConnections();verify(fixture.user,never()).sendPacket(any(WrapperPlayServerPing.class));fixture.close();
        fixture=new Fixture(true,event->NormalizedPacket.Other.INSTANCE);
        fixture.engine.probeConnections();verify(fixture.user).sendPacket(any(WrapperPlayServerPing.class));
        when(fixture.user.getClientVersion()).thenReturn(ClientVersion.V_1_16_4);fixture.engine.probeConnections();
        when(fixture.user.getClientVersion()).thenReturn(ClientVersion.UNKNOWN);fixture.engine.probeConnections();
        verify(fixture.user,times(1)).sendPacket(any(WrapperPlayServerPing.class));
        fixture.engine.close();fixture.engine.probeConnections();verify(fixture.user,times(1)).sendPacket(any(WrapperPlayServerPing.class));
    }
    @Test void unknownClientsAreCountedButNeverDecodedAsAnAssumedLatestProtocol() {
        fixture=new Fixture(false,event->{fail("Unknown protocol was decoded");return NormalizedPacket.Other.INSTANCE;});
        when(fixture.user.getClientVersion()).thenReturn(ClientVersion.UNKNOWN);
        fixture.listener.onPacketReceive(fixture.receive());fixture.executor.drain();
        assertEquals(1,fixture.data.snapshot().inboundPackets());assertEquals(-1,fixture.data.snapshot().client().protocolVersion());
        assertTrue(fixture.data.snapshot(10).connection().uncertain());
    }
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task){tasks.add(task);}
        void drain(){while(!tasks.isEmpty())tasks.removeFirst().run();}
    }
    private static final class Fixture implements AutoCloseable {
        final PacketEventsAPI<?> api=mock(PacketEventsAPI.class,RETURNS_DEEP_STUBS);
        final User user=mock(User.class);
        final PlayerRegistry registry=new PlayerRegistry();
        final PacketMetrics metrics=new PacketMetrics();
        final ManualExecutor executor=new ManualExecutor();
        final PlayerData data;
        final PacketEventsEngine engine;
        final PacketListenerAbstract listener;
        Fixture(boolean probes,PacketNormalizer normalizer) {
            when(api.isInitialized()).thenReturn(true);when(api.getServerManager().getVersion()).thenReturn(ServerVersion.V_1_21_11);
            PacketEvents.setAPI(api);
            UUID uuid=UUID.randomUUID();Player player=mock(Player.class);when(player.getUniqueId()).thenReturn(uuid);
            when(user.getUUID()).thenReturn(uuid);when(user.getEntityId()).thenReturn(42);
            when(user.getClientVersion()).thenReturn(ClientVersion.V_1_21_11);when(api.getPlayerManager().getUser(player)).thenReturn(user);
            var settings=new PipelineSettings(1,16,8,4,8192,8,8,1000,250,1000,0.5,probes,20);
            var router=new PacketRouter<User>(metrics,()->true,settings,executor,()->10,new PacketProcessor(()->10),problem->fail(problem));
            engine=new PacketEventsEngine(api,router,normalizer,new PacketEventsVersionProvider(),()->10);engine.start();
            data=registry.join(uuid,"Alice",1);assertTrue(engine.attach(player,data));
            var capture=ArgumentCaptor.forClass(PacketListenerCommon.class);verify(api.getEventManager()).registerListener(capture.capture());
            listener=(PacketListenerAbstract)capture.getValue();
        }
        PacketReceiveEvent receive(){
            var event=mock(PacketReceiveEvent.class);when(event.getUser()).thenReturn(user);
            when(event.getConnectionState()).thenReturn(ConnectionState.PLAY);return event;
        }
        public void close(){engine.close();registry.close();executor.drain();}
    }
}
