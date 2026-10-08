package dev.aegisac.common.collision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class CollisionSolverTest {
    private final CollisionSolver solver=new CollisionSolver();
    private final Aabb player=Aabb.player(new Vec3(0,1,0),1.8);
    @Test void contactIsNotOverlapAndNegativeCoordinatesArePreserved() {
        Aabb a=new Aabb(-2,-1,-2,-1,0,-1), b=new Aabb(-1,-1,-2,0,0,-1);
        assertFalse(a.intersects(b)); assertTrue(a.expand(.001,0,0).intersects(b));
        assertTrue(a.sweep(new Vec3(-3,2,-4)).contains(a));
    }
    @Test void invalidGeometryCannotEnterSolver() {
        assertThrows(IllegalArgumentException.class,()->new Vec3(Double.NaN,0,0));
        assertThrows(IllegalArgumentException.class,()->new Aabb(2,0,0,1,1,1));
        assertThrows(IllegalArgumentException.class,()->new Aabb(0,0,0,1,Double.POSITIVE_INFINITY,1));
    }
    @Test void sweptMovementCannotTunnelThroughThinWall() {
        var wall=new Aabb(1,0,-2,1.01,4,2);
        var r=solver.move(player,new Vec3(3,0,0),List.of(wall),0,true,true);
        assertEquals(.7,r.movement().x(),1e-12); assertTrue(r.horizontalCollision());
        assertFalse(r.box().intersects(wall));
    }
    @Test void floorCeilingAndSlidingResolveEachAxis() {
        var floor=new Aabb(-10,0,-10,10,1,10); var ceiling=new Aabb(-10,3,-10,10,4,10);
        var down=solver.move(player,new Vec3(.2,-2,.3),List.of(floor),0,false,true);
        assertEquals(new Vec3(.2,0,.3),down.movement()); assertTrue(down.grounded());
        var up=solver.move(player,new Vec3(0,1,0),List.of(ceiling),0,false,true);
        assertEquals(.2,up.movement().y(),1e-12); assertFalse(up.grounded());
    }
    @ParameterizedTest @ValueSource(doubles={.0625,.125,.5})
    void stepsCarpetSnowAndSlabHeights(double height) {
        var floor=new Aabb(-4,0,-4,4,1,4); var partial=new Aabb(.5,1,-1,2,1+height,1);
        var r=solver.move(player,new Vec3(.8,-.08,0),List.of(floor,partial),.6,true,true);
        assertTrue(r.stepped()); assertEquals(.8,r.movement().x(),1e-12);
        assertEquals(height,r.movement().y(),1e-12); assertFalse(r.box().intersects(partial));
    }
    @Test void fullBlocksFencesAndLowCeilingsPreventStepping() {
        var floor=new Aabb(-4,0,-4,4,1,4); var tall=new Aabb(.5,1,-1,2,2.5,1);
        var r=solver.move(player,new Vec3(.8,-.08,0),List.of(floor,tall),.6,true,true);
        assertFalse(r.stepped()); assertEquals(.2,r.movement().x(),1e-12);
        var slab=new Aabb(.5,1,-1,2,1.5,1); var ceiling=new Aabb(-4,2.9,-4,4,4,4);
        assertFalse(solver.move(player,new Vec3(.8,-.08,0),List.of(floor,slab,ceiling),.6,true,true).stepped());
    }
    @Test void legacyAndModernAxisOrderingDifferAtCorner() {
        var corner=new Aabb(.5,1,.5,2,4,2);
        var legacy=solver.move(player,new Vec3(.5,0,1),List.of(corner),0,false,false);
        var modern=solver.move(player,new Vec3(.5,0,1),List.of(corner),0,false,true);
        assertEquals(.5,legacy.movement().x()); assertEquals(.2,legacy.movement().z(),1e-12);
        assertEquals(.2,modern.movement().x(),1e-12); assertEquals(1,modern.movement().z());
    }
}
