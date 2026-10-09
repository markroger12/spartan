package dev.aegisac.tools;

import dev.aegisac.common.config.ConfigurationLoader;
import dev.aegisac.common.trace.*;
import java.nio.file.Path;

/** Development entry point intentionally has no dependency on the platform adapter. */
public final class DevelopmentTools {
    private DevelopmentTools() { }
    public static void main(String[] args)throws Exception {
        if((args.length==2||args.length==3)&&args[0].equals("preflight")) {
            if(args.length==3&&!args[2].equals("--alert-only"))throw new IllegalArgumentException("Unknown preflight option");
            System.out.println(dev.aegisac.tools.release.ConfigurationPreflight.check(Path.of(args[1]),args.length==3));
            System.out.println("Configuration-only validation; no live server compatibility or client accuracy claim.");
        } else if(args.length==3&&args[0].equals("replay")) {
            System.out.println(TraceReplay.run(TraceFile.read(Path.of(args[1])),new ConfigurationLoader(Path.of(args[2])).load(1)));
        } else if(args.length==3&&args[0].equals("corpus")) {
            var configuration=new ConfigurationLoader(Path.of(args[2])).load(1);
            FixtureCorpus.write(Path.of(args[1]),configuration);
            System.out.println("Wrote synthetic fixtures; keep legitimate and suspicious scenarios separate.");
        } else if(args.length==3&&args[0].equals("load")) {
            System.out.println(SessionLoad.run(Integer.parseInt(args[1]),Integer.parseInt(args[2])));
        } else throw new IllegalArgumentException("Usage: preflight CONFIG_DIR [--alert-only] | replay TRACE CONFIG_DIR | corpus OUTPUT_DIR CONFIG_DIR | load SESSIONS PACKETS_PER_SESSION");
    }
}
