package dev.aegisac.common.world;
import dev.aegisac.common.collision.Aabb;
import dev.aegisac.common.physics.MotionContext;
import dev.aegisac.common.physics.Uncertainty;
import java.util.*;
/** Immutable, bounded owner-thread capture. No Bukkit objects or acknowledgement claims. */
public record WorldSnapshot(UUID world, long revision, long capturedNanos, Aabb coverage,
                            List<BlockSample> blocks, List<Aabb> entities, MotionContext context,
                            Set<Uncertainty> uncertainty, List<Aabb> shapes) {
    public WorldSnapshot(UUID world, long revision, long time, Aabb coverage, List<BlockSample> blocks,
                         List<Aabb> entities, MotionContext context, Set<Uncertainty> uncertainty) {
        this(world,revision,time,coverage,blocks,entities,context,uncertainty,
                List.of());
    }
    public WorldSnapshot {
        Objects.requireNonNull(world); Objects.requireNonNull(coverage); Objects.requireNonNull(context);
        blocks=List.copyOf(blocks); entities=List.copyOf(entities); uncertainty=Set.copyOf(uncertainty); shapes=blocks.stream().flatMap(b->b.shapes().stream()).toList();
        if (blocks.size()>4096 || shapes.size()>4096 || entities.size()>64) throw new IllegalArgumentException("World budget exceeded");
    }
}
