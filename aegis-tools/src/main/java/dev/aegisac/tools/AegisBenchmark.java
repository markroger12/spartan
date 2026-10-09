package dev.aegisac.tools;

import dev.aegisac.common.physics.*;
import dev.aegisac.common.collision.*;
import dev.aegisac.common.world.WorldSnapshot;
import org.openjdk.jmh.annotations.*;
import java.util.concurrent.TimeUnit;

/** Fixed representative physics workload; return values are consumed by JMH. */
@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput,Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations=2,time=1)
@Measurement(iterations=3,time=1)
@Fork(value=1,jvmArgsAppend={"-Xms256m","-Xmx256m"})
public class AegisBenchmark {
    @Param({"walking","water","piston"}) public String scenario;
    private PhysicsEngine engine;
    private PhysicsEngine.State state;
    private PhysicsEngine.Input input;
    private WorldSnapshot world;
    @Setup public void setup() {
        engine=new PhysicsEngine();state=new PhysicsEngine.State(new Vec3(0,1,0),Vec3.ZERO,true,1.8);
        input=new PhysicsEngine.Input(1,0,0,false,false,false);world=FixtureCorpus.world(scenario,0,0);
    }
    @Benchmark public PhysicsEngine.Result physicsStep() { return engine.step(PhysicsProfile.JAVA_1_21_11,state,input,world); }
}
