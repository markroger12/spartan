package dev.aegisac.common.check;
import dev.aegisac.common.collision.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.Set;
import static dev.aegisac.common.check.CheckFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
class MovementEvaluatorTest {
    @ParameterizedTest @EnumSource(value=CheckId.class,names={"SpeedA","FlyA","GroundA","NoFallA","StepA","ClimbA","LiquidA","PhaseA","AccelerationA","GravityA","AirJumpA","SprintA","SneakA"})
    void implementedGeometryChecksDetectTheirSpecificInjectedMismatch(CheckId id) {
        boolean step=id==CheckId.StepA;
        var f=new MovementFrame(1,50_000_000,new Vec3(0,5,0),new Vec3(1,6,0),new Vec3(0,-.2,0),
                .2,-.2,-.1,.2,step,step,true,true,true,true,true,true,.8,5,world(0),Set.of());
        var value=new MovementCheck(id).evaluate(f);
        assertTrue(value.applicable(),id.name()); assertTrue(value.excess()>.03,id+" "+value);
    }
    @ParameterizedTest @EnumSource(value=CheckId.class,names={"SpeedA","FlyA","GroundA","NoFallA","StepA","ClimbA","LiquidA","PhaseA","AccelerationA","GravityA","AirJumpA","SprintA","SneakA"})
    void legitimateSupportedWalkingDoesNotProduceAnExcess(CheckId id) {
        var result=new MovementCheck(id).evaluate(frame(1,50_000_000,false,Set.of()));
        assertEquals(0,result.excess(),id.name());
    }
    @Test void noFallDoesNotAccuseAnActualLandingAndAirJumpAllowsJumpAlternatives() {
        var landing=frame(1,50_000_000,false,Set.of());
        assertFalse(new MovementCheck(CheckId.NoFallA).evaluate(landing).applicable());
        var jump=new MovementFrame(1,50_000_000,new Vec3(0,1,0),new Vec3(0,1.42,0),Vec3.ZERO,.2,-.08,.42,.2,
                true,false,true,false,false,false,false,false,0,0,world(0),Set.of());
        assertEquals(0,new MovementCheck(CheckId.GravityA).evaluate(jump).excess());
        assertFalse(new MovementCheck(CheckId.AirJumpA).evaluate(jump).applicable());
    }
    @Test void unavailableModelsCannotBeInstantiatedAsWorkingChecks() {
        for(CheckId id:new CheckId[]{CheckId.NoSlowA,CheckId.ElytraA,CheckId.VehicleA})
            assertThrows(IllegalArgumentException.class,()->new MovementCheck(id));
    }
}
