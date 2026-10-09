package dev.aegisac.paper.world;
import dev.aegisac.common.world.BlockSample.Surface;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import java.util.EnumSet;
import java.util.Set;
/** Material traits supplement Bukkit's exact server collision shapes; they do not replace them. */
public final class BlockClassifier {
    private BlockClassifier() { }
    public static Set<Surface> surfaces(Material material, BlockData data) {
        EnumSet<Surface> result=EnumSet.noneOf(Surface.class);
        String name=material.name();
        if(material.isSolid()) result.add(Surface.SOLID);
        if(name.equals("WATER") || data instanceof Waterlogged water && water.isWaterlogged()) result.add(Surface.WATER);
        if(name.equals("LAVA")) result.add(Surface.LAVA);
        if(name.equals("ICE") || name.equals("PACKED_ICE") || name.equals("FROSTED_ICE")) result.add(Surface.ICE);
        if(name.equals("BLUE_ICE")) result.add(Surface.BLUE_ICE);
        if(name.equals("SLIME_BLOCK")) result.add(Surface.SLIME);
        if(name.equals("HONEY_BLOCK")) result.add(Surface.HONEY);
        if(name.equals("SOUL_SAND")) result.add(Surface.SOUL_SAND);
        if(name.equals("LADDER") || name.contains("VINE")) result.add(Surface.CLIMBABLE);
        if(name.equals("COBWEB")) result.add(Surface.COBWEB);
        if(name.contains("PISTON")) result.add(Surface.PISTON);
        if(name.equals("BUBBLE_COLUMN")) { result.add(Surface.WATER); result.add(Surface.BUBBLE_COLUMN); }
        if(name.equals("SCAFFOLDING")) result.add(Surface.SCAFFOLDING);
        if(name.endsWith("_BED") || name.equals("POWDER_SNOW") || name.equals("SWEET_BERRY_BUSH")) result.add(Surface.SPECIAL);
        return result;
    }
}
