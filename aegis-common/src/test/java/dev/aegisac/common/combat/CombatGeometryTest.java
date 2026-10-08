package dev.aegisac.common.combat;
import dev.aegisac.common.collision.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
class CombatGeometryTest {
    final Aabb box=new Aabb(-.3,0,3,.3,1.8,3.6);
    @Test void rayHitsFrontFaceAndMinimumDistanceUsesBoxRatherThanCenter() {
        var result=CombatGeometry.evaluate(new Vec3(0,1.62,0),0,0,List.of(box));
        assertTrue(result.hit()); assertEquals(3,result.rayDistance(),1e-9); assertEquals(3,result.minimumDistance(),1e-9);
    }
    @Test void parallelOutsideAndBehindBoxesMissButTangencyHits() {
        assertEquals(Double.POSITIVE_INFINITY,CombatGeometry.ray(new Vec3(.31,1,0),new Vec3(0,0,1),box));
        assertEquals(3,CombatGeometry.ray(new Vec3(.3,1,0),new Vec3(0,0,1),box));
        assertEquals(Double.POSITIVE_INFINITY,CombatGeometry.ray(new Vec3(0,1,4),new Vec3(0,0,1),box));
        assertEquals(0,CombatGeometry.ray(new Vec3(0,1,3.2),new Vec3(0,0,1),box));
    }
    @Test void anyInterpolationAlternativeCanMakeAttackPlausible() {
        var alternative=new Aabb(2,0,2,3,2,3);
        assertTrue(CombatGeometry.evaluate(new Vec3(0,1,0),0,0,List.of(alternative,box)).hit());
        assertFalse(CombatGeometry.evaluate(new Vec3(0,1,0),90,0,List.of(box)).hit());
    }
    @Test void rotationsWrapAndMinecraftDirectionIsRespected() {
        assertEquals(2,CombatGeometry.wrappedDelta(179,-179)); assertEquals(-2,CombatGeometry.wrappedDelta(-179,179));
        assertEquals(-1,CombatGeometry.direction(90,0).x(),1e-9); assertEquals(-1,CombatGeometry.direction(0,90).y(),1e-9);
        assertThrows(IllegalArgumentException.class,()->CombatGeometry.direction(Float.NaN,0));
    }
    @Test void negativeCoordinatesAndVerticalDistanceWork() {
        assertEquals(5,CombatGeometry.distance(new Vec3(-3,-4,0),new Aabb(0,0,0,1,1,1)));
        assertFalse(CombatGeometry.evaluate(new Vec3(0,0,0),0,0,List.of()).hit());
    }
}
