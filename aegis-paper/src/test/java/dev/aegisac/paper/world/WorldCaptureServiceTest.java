package dev.aegisac.paper.world;
import dev.aegisac.common.config.PhysicsSettings;
import dev.aegisac.common.player.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class WorldCaptureServiceTest {
    @Test void roundRobinLimitsWorkAndInvalidationAndCloseRemoveRetainedSessions() {
        Server server=mock(Server.class); when(server.isPrimaryThread()).thenReturn(true);
        Player player=mock(Player.class); when(player.isOnline()).thenReturn(true);
        // A deliberate capture failure exercises the service's graceful error path.
        when(player.getLocation()).thenThrow(new IllegalStateException("fixture failure"));
        when(server.getPlayer(any(UUID.class))).thenReturn(player);
        var failures=new ArrayList<RuntimeException>();
        var service=new WorldCaptureService(server,PhysicsSettings.DEFAULT,failures::add);
        PlayerRegistry registry=new PlayerRegistry(); UUID world=UUID.randomUUID(); var ids=new ArrayList<UUID>();
        for(int i=0;i<5;i++) { UUID id=UUID.randomUUID(); ids.add(id); service.attach(id,world,registry.join(id,"P"+i,0)); }
        service.tick(); verify(server,times(2)).getPlayer(any(UUID.class)); assertEquals(2,service.failed());
        service.tick(); service.tick(); verify(server,times(6)).getPlayer(any(UUID.class)); assertEquals(1,failures.size());
        for(UUID id:ids) verify(server,atLeastOnce()).getPlayer(id);
        long revision=registry.find(ids.getFirst()).world().read().revision();
        var block=mock(Block.class); var bukkitWorld=mock(World.class); when(block.getWorld()).thenReturn(bukkitWorld); when(bukkitWorld.getUID()).thenReturn(world);
        new WorldInvalidationListener(service).broken(new BlockBreakEvent(block,player));
        assertEquals(revision+1,registry.find(ids.getFirst()).world().read().revision());
        service.resetPlayer(ids.getFirst(),UUID.randomUUID()); assertEquals(1,registry.find(ids.getFirst()).lossEpoch());
        ids.forEach(service::detach); service.tick(); verify(server,times(6)).getPlayer(any(UUID.class));
        service.close(); assertThrows(IllegalStateException.class,()->service.attach(ids.getFirst(),world,registry.find(ids.getFirst())));
    }
    @Test void disabledCaptureStillSamplesOwnerMembershipAndChecksThread() {
        Server server=mock(Server.class); when(server.isPrimaryThread()).thenReturn(true);
        var service=new WorldCaptureService(server,new PhysicsSettings(false,2,2,512,512,16,250),error->fail(error));
        var registry=new PlayerRegistry(); var id=UUID.randomUUID(); service.attach(id,UUID.randomUUID(),registry.join(id,"P",0));
        service.tick(); verify(server,times(1)).getPlayer(any(UUID.class));
        assertEquals(0,service.captured());
        when(server.isPrimaryThread()).thenReturn(false); assertThrows(IllegalStateException.class,service::tick);
    }
    @Test void ownerThreadBlockRangeFeedsGuardWithoutWorkerBukkitReads(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var configuration=new dev.aegisac.common.config.ConfigService(new dev.aegisac.common.config.ConfigurationLoader(directory));
        configuration.reload();
        java.nio.file.Files.writeString(directory.resolve("checks/world.yml"),"config-version: 1\ngrace-ms: 0\n"); configuration.reload();
        Server server=mock(Server.class); when(server.isPrimaryThread()).thenReturn(true);
        Player player=mock(Player.class); when(player.isOnline()).thenReturn(true);
        World world=mock(World.class); when(world.getName()).thenReturn("world"); when(world.getUID()).thenReturn(new UUID(0,1));
        when(player.getWorld()).thenReturn(world); when(player.getGameMode()).thenReturn(GameMode.SURVIVAL); when(player.getEyeHeight()).thenReturn(1.62);
        var range=mock(org.bukkit.attribute.AttributeInstance.class); when(range.getValue()).thenReturn(12.0);
        when(player.getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE)).thenReturn(range);
        UUID id=UUID.randomUUID(); when(server.getPlayer(id)).thenReturn(player);
        var service=new WorldCaptureService(server,new PhysicsSettings(false,2,2,512,512,16,250),error->fail(error));
        var registry=new PlayerRegistry(); var data=registry.join(id,"Alice",0); data.configure(configuration.current().pipeline());
        data.configureChecks(configuration::current,service::health); service.attach(id,world.getUID(),data); service.tick();
        long now=System.nanoTime();
        data.identityObservation(new dev.aegisac.common.bedrock.IdentityObservation(configuration.current().generation(),0,new dev.aegisac.api.player.EditionSnapshot(dev.aegisac.api.player.BedrockStatus.JAVA,"FIXTURE","UNKNOWN","UNKNOWN","unknown",now,Set.of())));
        var protocol=new dev.aegisac.common.packet.ClientProtocol(767,"fixture",true,true,true,true);
        data.process(new dev.aegisac.common.packet.PacketFrame(1,0,now,dev.aegisac.common.packet.PacketDirection.INBOUND,protocol,
                new dev.aegisac.common.packet.NormalizedPacket.Movement(true,true,.5,0,0,0,0,true,false)),now);
        data.process(new dev.aegisac.common.packet.PacketFrame(2,0,now,dev.aegisac.common.packet.PacketDirection.INBOUND,protocol,
                new dev.aegisac.common.packet.NormalizedPacket.BlockAction(true,0,1,10,2,1,"PLACE","MAIN_HAND",.5f,.5f,.5f)),now);
        var result=data.snapshot(now).guard().checks().get("BlockReachA"); assertEquals(1,result.evaluated()); assertEquals(0,result.diagnostics());
        when(range.getValue()).thenReturn(4.5); service.tick(); now=System.nanoTime();
        data.process(new dev.aegisac.common.packet.PacketFrame(3,0,now,dev.aegisac.common.packet.PacketDirection.INBOUND,protocol,
                new dev.aegisac.common.packet.NormalizedPacket.BlockAction(true,0,1,10,2,2,"PLACE","MAIN_HAND",.5f,.5f,.5f)),now);
        assertEquals(1,data.snapshot(now).guard().checks().get("BlockReachA").diagnostics());
        verify(player,times(2)).getAttribute(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE);
        service.close(); registry.close();
    }

}
