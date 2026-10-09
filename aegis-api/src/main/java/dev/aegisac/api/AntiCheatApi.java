package dev.aegisac.api;
import dev.aegisac.api.player.PlayerSnapshot;
import java.util.Optional;
import java.util.UUID;
/** Thread-safe, read-only Phase 8 API. Obtain through Bukkit's ServicesManager. */
public interface AntiCheatApi {
    Optional<PlayerSnapshot> player(UUID uuid);
    int onlinePlayers();
    long configurationGeneration();
    dev.aegisac.api.output.ViolationSnapshot violations(UUID uuid);
}
