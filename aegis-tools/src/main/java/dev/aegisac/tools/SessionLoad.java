package dev.aegisac.tools;

import dev.aegisac.common.config.ConfigurationLoader;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.player.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Synthetic queue/lifecycle load. No network, collision captures, Bukkit or population guarantee. */
public final class SessionLoad {
    private SessionLoad() { }
    public record Result(int sessions,long packets,long processed,long dropped,long failures,long remainingQueues,double elapsedMillis) { }
    public static Result run(int sessions,int packets)throws Exception {
        if(sessions<1||sessions>5000||packets<1||packets>200)throw new IllegalArgumentException("Budgets: sessions 1..5000, packets 1..200");
        Path directory=Files.createTempDirectory("aegis-load-");
        try { return run(sessions,packets,new ConfigurationLoader(directory).load(1)); }
        finally { try(var paths=Files.walk(directory)) { for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path); } }
    }
    private static Result run(int sessions,int packets,dev.aegisac.common.config.ConfigSnapshot config)throws Exception {
        var metrics=new PacketMetrics();long start=System.nanoTime();
        try(var players=new PlayerRegistry();var router=new PacketRouter<Object>(metrics,()->true,config.pipeline(),error->{})) {
            var uuids=new UUID[sessions];var connections=new Object[sessions];
            for(int i=0;i<sessions;i++) {
                uuids[i]=new UUID(0,i+1);connections[i]=new Object();var data=players.join(uuids[i],"Synthetic",start);
                data.configureChecks(()->config,dev.aegisac.common.check.ServerTickHealth.Snapshot::unknown);
                router.attach(uuids[i],connections[i],data);
            }
            var protocol=new ClientProtocol(774,"1.21.11",true,true,true,true);
            for(int packet=0;packet<packets;packet++)for(int i=0;i<sessions;i++)router.observe(uuids[i],connections[i],PacketDirection.INBOUND,System.nanoTime(),protocol,
                    new NormalizedPacket.Movement(true,true,0,1,packet*.02,0,0,true,false));
            long expected=(long)sessions*packets,deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
            while(metrics.pipeline().processed()+metrics.pipeline().dropped()<expected&&System.nanoTime()<deadline)Thread.sleep(10);
            var result=metrics.pipeline();
            if(result.processed()+result.dropped()!=expected||result.failures()!=0||result.queued()!=0)throw new IllegalStateException("Load failed or timed out: "+result);
            for(UUID uuid:uuids) { router.detach(uuid);players.quit(uuid); }
            if(metrics.pipeline().activeQueues()!=0||players.size()!=0)throw new IllegalStateException("Session cleanup leak");
            return new Result(sessions,expected,result.processed(),result.dropped(),result.failures(),metrics.pipeline().activeQueues(),(System.nanoTime()-start)/1e6);
        }
    }
}
