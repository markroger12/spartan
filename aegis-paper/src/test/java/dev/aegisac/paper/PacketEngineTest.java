package dev.aegisac.paper;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.ConnectionState;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import dev.aegisac.common.packet.PacketMetrics;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.config.PipelineSettings;
import java.util.ArrayDeque;
import java.util.concurrent.Executor;
import dev.aegisac.common.player.PlayerRegistry;
import dev.aegisac.paper.packet.PacketEventsEngine;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PacketEngineTest {
    private static PipelineSettings settings() {
        return new PipelineSettings(1,16,8,4,8192,8,8,1000,250,1000,0.5,false,20);
    }
    private static final class ManualExecutor implements Executor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        public void execute(Runnable task) { tasks.add(task); }
        void drain() { while(!tasks.isEmpty())tasks.removeFirst().run(); }
    }

    @Test void listenerLifecycleIsIdempotentAndDoesNotOwnSharedApi() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class, RETURNS_DEEP_STUBS);
        when(api.isInitialized()).thenReturn(true);
        var engine = new PacketEventsEngine(api, new PacketRouter<>(new PacketMetrics(), () -> true, settings(), problem -> fail(problem)));
        engine.start(); engine.start();
        assertTrue(engine.running());
        verify(api.getEventManager(), times(1)).registerListener(any(PacketListenerCommon.class));
        engine.close(); engine.close();
        assertFalse(engine.running());
        verify(api.getEventManager(), times(1)).unregisterListener(any(PacketListenerCommon.class));
        verify(api, never()).init(); verify(api, never()).terminate();
    }
    @Test void realAdapterRoutesBothDirectionsAndIgnoresCancelledOrPrePlayPackets() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class, RETURNS_DEEP_STUBS);
        when(api.isInitialized()).thenReturn(true);
        var registry = new PlayerRegistry();
        var metrics = new PacketMetrics();
        var executor = new ManualExecutor();
        var engine = new PacketEventsEngine(api, new PacketRouter<>(metrics, () -> true, settings(), executor, () -> 10,
                new PacketProcessor(() -> 10), problem -> fail(problem)));
        engine.start();
        var capture = ArgumentCaptor.forClass(PacketListenerCommon.class);
        verify(api.getEventManager()).registerListener(capture.capture());
        var listener = (PacketListenerAbstract) capture.getValue();
        UUID uuid = UUID.randomUUID();
        Player player = mock(Player.class); when(player.getUniqueId()).thenReturn(uuid);
        User user = mock(User.class); when(user.getUUID()).thenReturn(uuid);
        when(user.getClientVersion()).thenReturn(ClientVersion.V_1_21_11);
        when(api.getPlayerManager().getUser(player)).thenReturn(user);
        var data = registry.join(uuid, "Alice", 1);
        assertTrue(engine.attach(player, data));
        var receive = mock(PacketReceiveEvent.class);
        when(receive.getUser()).thenReturn(user);
        when(receive.getConnectionState()).thenReturn(ConnectionState.PLAY);
        listener.onPacketReceive(receive);
        var send = mock(PacketSendEvent.class);
        when(send.getUser()).thenReturn(user);
        when(send.getConnectionState()).thenReturn(ConnectionState.PLAY);
        listener.onPacketSend(send);
        when(receive.isCancelled()).thenReturn(true);
        listener.onPacketReceive(receive);
        when(receive.isCancelled()).thenReturn(false);
        when(receive.getConnectionState()).thenReturn(ConnectionState.LOGIN);
        listener.onPacketReceive(receive);
        executor.drain();
        assertEquals(1, data.snapshot().inboundPackets());
        assertEquals(1, data.snapshot().outboundPackets());
        assertEquals(ClientVersion.V_1_21_11.getProtocolVersion(), data.snapshot().client().protocolVersion());
        verify(receive, never()).getPlayer(); verify(send, never()).getPlayer();
        verify(receive, never()).getByteBuf(); verify(send, never()).getByteBuf();
        engine.close();
        listener.onPacketSend(send);
        assertEquals(1, data.snapshot().outboundPackets());
    }
}
