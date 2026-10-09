package dev.aegisac.paper.compatibility;
import org.bukkit.plugin.PluginManager;
/** Presence is reported independently from functional identity/version support. */
public record PlatformCapabilities(boolean folia, boolean floodgate, boolean geyser, boolean viaVersion) {
    public static PlatformCapabilities detect(PluginManager plugins) {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer", false,
                    PlatformCapabilities.class.getClassLoader());
            folia = true;
        } catch (ClassNotFoundException absent) { folia = false; }
        return new PlatformCapabilities(folia, plugins.isPluginEnabled("floodgate"),
                plugins.isPluginEnabled("Geyser-Spigot"), plugins.isPluginEnabled("ViaVersion"));
    }
}
