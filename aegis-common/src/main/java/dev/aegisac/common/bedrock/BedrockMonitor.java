package dev.aegisac.common.bedrock;
import dev.aegisac.api.player.*;
import dev.aegisac.common.config.EditionSettings;
import dev.aegisac.common.packet.*;
import dev.aegisac.common.packet.NormalizedPacket.*;
import java.util.*;
/** Separate bounded histories of translated observations. No Java physics constants or native-input claims. */
public final class BedrockMonitor {
    private final Map<String,ArrayDeque<BedrockSnapshot.Observation>> lanes=new LinkedHashMap<>();
    private long generation,lastTime;
    private boolean seen;
    public BedrockMonitor() { for(String lane:List.of("movement","rotation","input","inventory","combat","placement")) lanes.put(lane,new ArrayDeque<>()); }
    public void clear() { lanes.values().forEach(ArrayDeque::clear); seen=false; }
    public void process(long generation,EditionSettings settings,EditionSnapshot identity,PacketFrame frame) {
        long now=frame.observedNanos();
        if(this.generation!=generation||seen&&now-lastTime<0||frame.packet().kind()==PacketKind.TELEPORT||frame.packet().kind()==PacketKind.WORLD_RESET) clear();
        this.generation=generation; lastTime=now; seen=true;
        if(identity.status()!=BedrockStatus.BEDROCK||!settings.historyEnabled()) { clear(); return; }
        prune(now,settings.historyAgeMillis());
        if(frame.direction()!=PacketDirection.INBOUND) return;
        switch(frame.packet()) {
            case Movement m -> {
                if(m.position()) add("movement",frame,"POSITION",Map.of("x",m.x(),"y",m.y(),"z",m.z(),"reported-ground",m.onGround()?1.0:0.0),settings);
                if(m.rotation()) add("rotation",frame,"ROTATION",Map.of("yaw",(double)m.yaw(),"pitch",(double)m.pitch()),settings);
            }
            case Input i -> add("input",frame,"INPUT",Map.of("forward",(double)i.forward(),"sideways",(double)i.sideways(),"jump",i.jump()?1.0:0.0,"sneak",i.sneak()?1.0:0.0,"sprint",i.sprint()?1.0:0.0),settings);
            case EntityAction a -> add("input",frame,a.action(),Map.of("jump-boost",(double)a.jumpBoost()),settings);
            case Inventory i -> add("inventory",frame,i.action().name(),Map.of("window",(double)i.window(),"slot",(double)i.slot(),"state",(double)i.stateId()),settings);
            case HeldItem h -> add("inventory",frame,"HELD_SLOT",Map.of("slot",(double)h.slot()),settings);
            case Interaction i -> add("combat",frame,i.action(),Map.of("target",(double)i.entityId()),settings);
            case Action a -> { if(a.kind()==PacketKind.SWING) add("combat",frame,"SWING",Map.of(),settings); }
            case BlockAction b -> add("placement",frame,b.action(),Map.of("x",(double)b.x(),"y",(double)b.y(),"z",(double)b.z(),"face",(double)b.face(),"sequence",(double)b.sequence()),settings);
            case UseItem u -> add("placement",frame,"USE_ITEM",Map.of("sequence",(double)u.sequence()),settings);
            default -> { }
        }
    }
    private void add(String lane,PacketFrame frame,String action,Map<String,Double> values,EditionSettings settings) {
        boolean finite=values.values().stream().allMatch(Double::isFinite);
        var ring=lanes.get(lane); while(ring.size()>=settings.historySize()) ring.removeFirst();
        // Action labels derive from protocol enums, but bound defensively at this common boundary.
        String label=action.matches("[A-Z_]{1,48}")?action:"UNKNOWN";
        ring.addLast(new BedrockSnapshot.Observation(frame.sequence(),frame.observedNanos(),label,finite?values:Map.of("finite",0.0)));
    }
    private void prune(long now,int maxAgeMillis) {
        lanes.values().forEach(ring->{ while(!ring.isEmpty()&&(now-ring.peekFirst().observedNanos()<0||now-ring.peekFirst().observedNanos()>maxAgeMillis*1_000_000L)) ring.removeFirst(); });
    }
    public BedrockSnapshot snapshot(long now,EditionSettings settings,EditionSnapshot identity) {
        if(identity.status()!=BedrockStatus.BEDROCK) return BedrockSnapshot.empty("IDENTITY_NOT_BEDROCK");
        if(!settings.historyEnabled()) return BedrockSnapshot.empty("HISTORY_DISABLED");
        prune(now,settings.historyAgeMillis());
        Map<String,List<BedrockSnapshot.Observation>> result=new LinkedHashMap<>(); lanes.forEach((k,v)->result.put(k,List.copyOf(v)));
        return new BedrockSnapshot(generation,"TRANSLATED_OBSERVATIONS",result,Set.of("NATIVE_INPUT_UNAVAILABLE","BEDROCK_PHYSICS_UNAVAILABLE","TRANSLATION_TIMING_UNCONFIRMED"));
    }
}
