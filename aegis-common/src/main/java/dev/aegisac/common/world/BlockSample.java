package dev.aegisac.common.world;
import dev.aegisac.common.collision.Aabb;
import java.util.List;
import java.util.Set;
/** Shapes are absolute world coordinates. The volume also describes non-solid media. */
public record BlockSample(Aabb volume, List<Aabb> shapes, Set<Surface> surfaces) {
    public enum Surface { SOLID, ICE, BLUE_ICE, SLIME, HONEY, SOUL_SAND, WATER, LAVA, CLIMBABLE, COBWEB,
        PISTON, BUBBLE_COLUMN, SCAFFOLDING, SPECIAL }
    public BlockSample { shapes=List.copyOf(shapes); surfaces=Set.copyOf(surfaces); }
}
