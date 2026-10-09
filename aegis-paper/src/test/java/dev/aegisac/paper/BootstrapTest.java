package dev.aegisac.paper;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.protocol.player.User;
import dev.aegisac.api.AntiCheatApi;
import dev.aegisac.common.config.ConfigurationLoader;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import java.nio.file.Files;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BootstrapTest {
    private ServerMock server;
    private PacketEventsAPI<?> packetApi;
    @BeforeEach void setup() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("packetevents", "2.14.0");
        packetApi = mock(PacketEventsAPI.class, RETURNS_DEEP_STUBS);
        when(packetApi.isInitialized()).thenReturn(true);
        when(packetApi.getPlayerManager().getUser(any(Player.class))).thenAnswer(call -> {
            Player player = call.getArgument(0);
            User user = mock(User.class);
            when(user.getUUID()).thenReturn(player.getUniqueId());
            return user;
        });
        PacketEvents.setAPI(packetApi);
    }
    @AfterEach void cleanup() {
        MockBukkit.unmock();
        PacketEvents.setAPI(null);
    }
    @Test void enablesGeneratesFilesRegistersApiAndCleansUp() {
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        assertTrue(plugin.isEnabled());
        for (String file : ConfigurationLoader.FILES) assertTrue(Files.exists(plugin.getDataFolder().toPath().resolve(file)), file);
        AntiCheatApi api = server.getServicesManager().load(AntiCheatApi.class);
        assertNotNull(api);
        PlayerMock player = server.addPlayer("Alice");
        assertEquals(1, api.onlinePlayers());
        UUID uuid = player.getUniqueId();
        assertEquals("Alice", api.player(uuid).orElseThrow().name());
        server.getPluginManager().callEvent(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.text("quit")));
        assertTrue(api.player(uuid).isEmpty());
        verify(packetApi.getEventManager()).registerListener(any(PacketListenerCommon.class));
        server.getPluginManager().disablePlugin(plugin);
        assertEquals(0, api.onlinePlayers());
        assertNull(server.getServicesManager().load(AntiCheatApi.class));
        verify(packetApi.getEventManager()).unregisterListener(any(PacketListenerCommon.class));
        verify(packetApi, never()).terminate();
    }
    @Test void commandPermissionsAndCompletionAreEnforced() {
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(false);
        assertTrue(server.dispatchCommand(player, "ac performance"));
        assertTrue(player.nextMessage().contains("permission"));
        assertEquals(java.util.List.of(), plugin.getCommand("ac").tabComplete(player, "ac", new String[]{""}));
        player.addAttachment(plugin, "aegisac.performance", true);
        server.dispatchCommand(player, "ac performance");
        assertTrue(player.nextMessage().contains("Players=1"));
        assertEquals(java.util.List.of("performance"), plugin.getCommand("ac").tabComplete(player, "ac", new String[]{"p"}));
    }
    @Test void editionAndBedrockDiagnosticsAreIndependentlyPermissionGated() {
        AegisPlugin plugin=MockBukkit.load(AegisPlugin.class); PlayerMock player=server.addPlayer("Alice"); player.setOp(false);
        for(String action:java.util.List.of("edition","bedrock")) {
            server.dispatchCommand(player,"ac "+action+" Alice"); assertTrue(player.nextMessage().contains("permission"));
            player.addAttachment(plugin,"aegisac."+action,true);
            server.dispatchCommand(player,"ac "+action+" Alice"); String message=player.nextMessage();
            assertTrue(message.contains(action.equals("edition")?"edition=UNKNOWN":"ANALYSIS_INVALIDATED"),message);
            assertEquals(java.util.List.of("Alice"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{action,"A"}));
        }
    }
    @Test void phaseEightCommandsArePermissionGatedAndPanicIsVisible() {
        AegisPlugin plugin=MockBukkit.load(AegisPlugin.class); PlayerMock player=server.addPlayer("Alice"); player.setOp(false);
        for(String action:java.util.List.of("alerts","verbose","violations","logs","panic","output")) {
            server.dispatchCommand(player,"ac "+action+" Alice"); assertTrue(player.nextMessage().contains("permission"));
        }
        player.addAttachment(plugin,"aegisac.panic",true); server.dispatchCommand(player,"ac panic"); assertTrue(player.nextMessage().contains("Panic=true")); assertTrue(plugin.outputs().panic());
        player.addAttachment(plugin,"aegisac.alerts",true); server.dispatchCommand(player,"ac alerts"); assertTrue(player.nextMessage().contains("subscription=true"));
        server.dispatchCommand(player,"ac alerts"); assertTrue(player.nextMessage().contains("subscription=false"));
        player.addAttachment(plugin,"aegisac.violations",true); server.dispatchCommand(player,"ac violations Alice"); assertTrue(player.nextMessage().contains("findings=0"));
        assertEquals(java.util.List.of("Alice"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{"violations","A"}));
        var api=server.getServicesManager().load(AntiCheatApi.class); assertEquals(0,api.violations(player.getUniqueId()).findings());
    }
    @Test void malformedReloadRetainsGenerationAndReportsFailureOnServerThread() throws Exception {
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "config-version: 1\ndefault-profile: invalid\n");
        var before = plugin.configuration().current();
        plugin.reload(server.getConsoleSender());
        assertTrue(server.getConsoleSender().nextMessage().contains("Validating"));
        String result = awaitReply();
        assertTrue(result.contains("Reload rejected"), result);
        assertSame(before, plugin.configuration().current());
    }
    @Test void successfulReloadPublishesAndResponds() throws Exception {
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        Files.writeString(plugin.getDataFolder().toPath().resolve("config.yml"), "config-version: 1\ndefault-profile: strict\n");
        plugin.reload(server.getConsoleSender());
        server.getConsoleSender().nextMessage();
        assertTrue(awaitReply().contains("generation 2"));
        assertEquals("strict", plugin.configuration().current().defaultProfile());
    }
    @Test void connectionCommandIsPermissionGatedAndClientBrandRemainsLiteralText() {
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        PlayerMock player = server.addPlayer("Alice"); player.setOp(false);
        server.dispatchCommand(player, "ac connection Alice");
        assertTrue(player.nextMessage().contains("permission"));
        player.addAttachment(plugin, "aegisac.connection", true);
        server.dispatchCommand(player, "ac connection Alice");
        String response = player.nextMessage();
        assertTrue(response.contains("transaction=-1.0ms"), response);
        assertTrue(response.contains("uncertain=true"), response);
        plugin.reply(player, "connection", java.util.Map.of("player", "Alice", "brand", "&c%player%"));
        assertTrue(player.nextMessage().contains("brand=&c%player%"));
    }
    @Test void unavailablePacketEngineDisablesWithoutRegisteringListenersOrServices() {
        when(packetApi.isInitialized()).thenReturn(false);
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        assertFalse(plugin.isEnabled());
        assertNull(server.getServicesManager().load(AntiCheatApi.class));
        verify(packetApi.getEventManager(), never()).registerListener(any(PacketListenerCommon.class));
        verify(packetApi, never()).terminate();
    }
    @Test void physicsCommandIsPermissionGatedAndReportsUnavailableBaseline() {
        AegisPlugin plugin = MockBukkit.load(AegisPlugin.class);
        PlayerMock player = server.addPlayer("Alice"); player.setOp(false);
        server.dispatchCommand(player, "ac physics Alice");
        assertTrue(player.nextMessage().contains("permission"));
        player.addAttachment(plugin, "aegisac.physics", true);
        server.dispatchCommand(player, "ac physics Alice");
        String response=player.nextMessage();
        assertTrue(response.contains("BASELINE_MISSING"),response); assertTrue(response.contains("diagnostic only"),response);
        assertEquals(java.util.List.of("physics"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{"ph"}));
    }
    @Test void movementCommandsRequirePermissionsAndUnavailableModelsAreVisible() {
        AegisPlugin plugin=MockBukkit.load(AegisPlugin.class);
        PlayerMock player=server.addPlayer("Alice"); player.setOp(false);
        server.dispatchCommand(player,"ac checks"); assertTrue(player.nextMessage().contains("permission"));
        player.addAttachment(plugin,"aegisac.checks",true);
        server.dispatchCommand(player,"ac checks");
        boolean unavailable=false; int rows=0; String line;
        while((line=player.nextMessage())!=null) { rows++; if(line.contains("NoSlowA") && line.contains("UNAVAILABLE")) unavailable=true; }
        assertEquals(72,rows); assertTrue(unavailable);
        player.addAttachment(plugin,"aegisac.movement",true); player.addAttachment(plugin,"aegisac.safeposition",true);
        server.dispatchCommand(player,"ac movement Alice"); assertTrue(player.nextMessage().contains("experimental-findings=0"));
        server.dispatchCommand(player,"ac safeposition Alice"); assertTrue(player.nextMessage().contains("no current"));
        assertEquals(java.util.List.of("movement"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{"m"}));
        player.setOp(true); assertFalse(player.hasPermission("aegisac.bypass"));
    }
    @Test void combatCommandsRequirePermissionsAndDoNotGrantBypass() {
        AegisPlugin plugin=MockBukkit.load(AegisPlugin.class);
        PlayerMock player=server.addPlayer("Alice"); player.setOp(false);
        server.dispatchCommand(player,"ac combat Alice"); assertTrue(player.nextMessage().contains("permission"));
        player.addAttachment(plugin,"aegisac.combat",true); player.addAttachment(plugin,"aegisac.cps",true);
        server.dispatchCommand(player,"ac combat Alice"); assertTrue(player.nextMessage().contains("diagnostic only"));
        server.dispatchCommand(player,"ac cps Alice"); assertTrue(player.nextMessage().contains("not physical clicks"));
        assertEquals(java.util.List.of("combat"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{"com"}));
        player.setOp(true); assertFalse(player.hasPermission("aegisac.bypass.reacha"));
    }
    @Test void inspectRequiresPermissionAndValidatesCategoryWithCompletion() {
        AegisPlugin plugin=MockBukkit.load(AegisPlugin.class);
        PlayerMock player=server.addPlayer("Alice"); player.setOp(false);
        server.dispatchCommand(player,"ac inspect Alice protocol"); assertTrue(player.nextMessage().contains("permission"));
        player.addAttachment(plugin,"aegisac.inspect",true);
        server.dispatchCommand(player,"ac inspect Alice protocol"); assertTrue(player.nextMessage().contains("diagnostic only"));
        server.dispatchCommand(player,"ac inspect Alice invalid"); assertTrue(player.nextMessage().contains("Usage:"));
        assertEquals(java.util.List.of("inspect"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{"ins"}));
        assertEquals(java.util.List.of("protocol"),plugin.getCommand("ac").tabComplete(player,"ac",new String[]{"inspect","Alice","pro"}));
        player.setOp(true); assertFalse(player.hasPermission("aegisac.bypass.badpacketsa"));
    }

    @Test void foliaReleaseGateRejectsBeforeAnyConventionalSchedulerOrPacketListenerIsInstalled() {
        try(var capabilities=mockStatic(dev.aegisac.paper.compatibility.PlatformCapabilities.class)) {
            capabilities.when(()->dev.aegisac.paper.compatibility.PlatformCapabilities.detect(any())).thenReturn(new dev.aegisac.paper.compatibility.PlatformCapabilities(true,false,false,false));
            var plugin=MockBukkit.load(AegisPlugin.class); assertFalse(plugin.isEnabled()); assertNull(plugin.scheduler());
            assertNull(server.getServicesManager().load(AntiCheatApi.class));
            verify(packetApi.getEventManager(),never()).registerListener(any(PacketListenerCommon.class));
        }
    }
    private String awaitReply() throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            server.getScheduler().performOneTick();
            String response = server.getConsoleSender().nextMessage();
            if (response != null) return response;
            // Test thread only: production server threads never sleep.
            Thread.sleep(5);
        }
        fail("Reload worker did not complete within 10 seconds");
        throw new AssertionError();
    }
}
