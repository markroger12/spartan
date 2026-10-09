package dev.aegisac.common.combat;
import dev.aegisac.common.collision.*;
import java.util.List;
/** Pure bounded geometry. Ray origin/target boxes must describe the same coherent observation space. */
public final class CombatGeometry {
    private CombatGeometry() { }
    public static Vec3 direction(float yaw,float pitch) {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)) throw new IllegalArgumentException("Non-finite rotation");
        double y=Math.toRadians(yaw),p=Math.toRadians(pitch),cos=Math.cos(p);
        return new Vec3(-Math.sin(y)*cos,-Math.sin(p),Math.cos(y)*cos);
    }
    public static double wrappedDelta(double from,double to) {
        double delta=(to-from)%360; if(delta>180) delta-=360; if(delta<-180) delta+=360; return delta;
    }
    public static double distance(Vec3 eye,Aabb box) {
        double x=Math.max(box.minX()-eye.x(),Math.max(0,eye.x()-box.maxX()));
        double y=Math.max(box.minY()-eye.y(),Math.max(0,eye.y()-box.maxY()));
        double z=Math.max(box.minZ()-eye.z(),Math.max(0,eye.z()-box.maxZ()));
        return Math.sqrt(x*x+y*y+z*z);
    }
    /** Slab intersection returns distance along a unit ray, or +infinity for a miss/behind-origin box. */
    public static double ray(Vec3 eye,Vec3 direction,Aabb box) {
        double[] origin={eye.x(),eye.y(),eye.z()},delta={direction.x(),direction.y(),direction.z()};
        double[] min={box.minX(),box.minY(),box.minZ()},max={box.maxX(),box.maxY(),box.maxZ()};
        double near=0,far=Double.POSITIVE_INFINITY;
        for(int axis=0;axis<3;axis++) {
            if(Math.abs(delta[axis])<1e-12) { if(origin[axis]<min[axis]||origin[axis]>max[axis]) return Double.POSITIVE_INFINITY; continue; }
            double a=(min[axis]-origin[axis])/delta[axis],b=(max[axis]-origin[axis])/delta[axis];
            near=Math.max(near,Math.min(a,b)); far=Math.min(far,Math.max(a,b));
            if(far<near) return Double.POSITIVE_INFINITY;
        }
        return near;
    }
    public record Result(double minimumDistance,double rayDistance,boolean hit,double alignmentDegrees) { }
    public static Result evaluate(Vec3 eye,float yaw,float pitch,List<Aabb> boxes) {
        Vec3 direction=direction(yaw,pitch); double distance=Double.POSITIVE_INFINITY,ray=Double.POSITIVE_INFINITY,alignment=180;
        for(Aabb box:boxes) {
            distance=Math.min(distance,distance(eye,box)); ray=Math.min(ray,ray(eye,direction,box));
            Vec3 center=new Vec3((box.minX()+box.maxX())*.5,(box.minY()+box.maxY())*.5,(box.minZ()+box.maxZ())*.5).subtract(eye);
            double length=Math.sqrt(center.lengthSquared());
            if(length>0) alignment=Math.min(alignment,Math.toDegrees(Math.acos(Math.max(-1,Math.min(1,
                    (direction.x()*center.x()+direction.y()*center.y()+direction.z()*center.z())/length)))));
        }
        return new Result(distance,ray,Double.isFinite(ray),alignment);
    }
}
