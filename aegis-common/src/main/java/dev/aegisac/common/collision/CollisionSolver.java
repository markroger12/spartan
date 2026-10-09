package dev.aegisac.common.collision;
import java.util.List;
/** Fixed-pass swept AABB resolver; work is bounded by the supplied immutable obstacle list. */
public final class CollisionSolver {
    public record Result(Aabb box, Vec3 movement, boolean grounded, boolean horizontalCollision, boolean stepped) { }
    public Result move(Aabb box, Vec3 desired, List<Aabb> shapes, double stepHeight, boolean onGround, boolean modernOrder) {
        Vec3 direct = axes(box, desired, shapes, modernOrder);
        boolean horizontal = direct.x()!=desired.x() || direct.z()!=desired.z();
        Vec3 selected = direct;
        boolean stepped = false;
        if (stepHeight>0 && horizontal && (onGround || desired.y()<0 && direct.y()!=desired.y())) {
            // Try both headroom paths: ascending before lateral motion and under the swept footprint.
            for (int path=0; path<2; path++) {
                Aabb clearance = path==0 ? box : box.sweep(new Vec3(desired.x(),0,desired.z()));
                double up = clip(clearance, stepHeight, 1, shapes);
                Aabb raised = box.move(new Vec3(0,up,0));
                Vec3 horizontalMove = axes(raised, new Vec3(desired.x(),0,desired.z()), shapes, modernOrder);
                double down = clip(raised.move(horizontalMove), desired.y()-up, 1, shapes);
                Vec3 candidate = new Vec3(horizontalMove.x(),up+down,horizontalMove.z());
                if (candidate.horizontalSquared()>selected.horizontalSquared()) { selected=candidate; stepped=true; }
            }
        }
        return new Result(box.move(selected), selected, desired.y()<0 && selected.y()!=desired.y(),
                selected.x()!=desired.x() || selected.z()!=desired.z(), stepped);
    }
    private Vec3 axes(Aabb box, Vec3 d, List<Aabb> shapes, boolean modern) {
        double y=clip(box,d.y(),1,shapes); box=box.move(new Vec3(0,y,0));
        double x=d.x(), z=d.z();
        if (modern && Math.abs(x)<Math.abs(z)) {
            z=clip(box,z,2,shapes); box=box.move(new Vec3(0,0,z)); x=clip(box,x,0,shapes);
        } else {
            x=clip(box,x,0,shapes); box=box.move(new Vec3(x,0,0)); z=clip(box,z,2,shapes);
        }
        return new Vec3(x,y,z);
    }
    private double clip(Aabb box, double d, int axis, List<Aabb> shapes) {
        for (Aabb shape: shapes) d=shape.clip(box,d,axis);
        return d;
    }
}
