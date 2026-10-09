package dev.aegisac.paper.admin;
import dev.aegisac.paper.AegisPlugin;
import dev.aegisac.common.output.OutputRecord;
import java.util.*;
/** Bounded rendering from packet data and immutable owner-captured observations. */
@SuppressWarnings("deprecation")
public final class ProfileView {
    public static Map<String,String> values(AegisPlugin plugin,dev.aegisac.paper.player.PlayerDirectory.Handle target) {
        var s=target.data().snapshot(); var c=s.connection(); var live=target.observation();
        var score=plugin.outputs().violations(target.getUniqueId()); var values=new LinkedHashMap<String,String>();
        values.put("Player",s.name()); values.put("UUID",s.uuid().toString()); values.put("Edition",s.edition().status()+" / "+s.edition().source());
        values.put("Client",s.client().release()); values.put("Brand",OutputRecord.clean(s.client().brand(),128));
        values.put("Server ping",live==null?"unavailable":live.ping()+" ms");
        values.put("Transaction ping",c==null?"unknown":OutputRecord.number(c.timing().transactionRttMillis())+" ms");
        values.put("Jitter",c==null?"unknown":OutputRecord.number(c.timing().jitterMillis())+" ms");
        var health=target.health(); values.put("TPS",health.known()?OutputRecord.number(health.tps()):"unknown");
        values.put("Recent lag",c==null?"unknown":Boolean.toString(c.uncertain()));
        values.put("CPS",OutputRecord.number(s.combat().swingRate())); values.put("Total risk",OutputRecord.number(score.totalRisk()));
        values.put("Category VL",score.categories().toString()); values.put("Confidence",OutputRecord.number(score.confidence()));
        values.put("Effects",live==null?"unavailable":live.effects());
        values.put("Environment",live==null?"unavailable":live.environment());
        values.put("Profile",live==null?"unavailable":plugin.configuration().current().profileFor(live.world(),s.edition().status()).toString());
        var recent=plugin.outputs().recent(target.getUniqueId()); values.put("Last alert",recent.isEmpty()?"none":recent.getLast().check()+" "+recent.getLast().type());
        return Collections.unmodifiableMap(values);
    }
    private ProfileView() { }
}
