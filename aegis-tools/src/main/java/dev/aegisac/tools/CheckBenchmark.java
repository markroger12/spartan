package dev.aegisac.tools;

import dev.aegisac.common.check.*;
import dev.aegisac.common.collision.Vec3;
import org.openjdk.jmh.annotations.*;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode({Mode.Throughput,Mode.SampleTime})
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations=2,time=1)
@Measurement(iterations=3,time=1)
@Fork(value=1,jvmArgsAppend={"-Xms256m","-Xmx256m"})
public class CheckBenchmark {
    @Param({"SpeedA","AccelerationA"}) public String check;
    private MovementCheck evaluator;
    private MovementFrame frame;
    @Setup public void setup() {
        evaluator=new MovementCheck(CheckId.valueOf(check));
        frame=new MovementFrame(1,50_000_000,new Vec3(0,1,0),new Vec3(.1,1,.1),new Vec3(.1,0,0),
                .2,-.08,.42,.15,true,true,true,true,true,false,false,false,0,0,FixtureCorpus.world("walking",0,0),Set.of());
    }
    @Benchmark public Check.Evaluation evaluate() { return evaluator.evaluate(frame); }
}
