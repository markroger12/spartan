package dev.aegisac.common.check;
/** Evaluation is pure; the dispatcher owns configuration, gating, buffering and evidence retention. */
public interface Check<T> {
    CheckId id();
    Evaluation evaluate(T input);
    record Evaluation(boolean applicable,double observed,double expected,double excess) {
        public Evaluation {
            if(!Double.isFinite(observed)||!Double.isFinite(expected)||!Double.isFinite(excess)||excess<0)
                throw new IllegalArgumentException("Invalid evaluation");
        }
        public static Evaluation absent() { return new Evaluation(false,0,0,0); }
        public static Evaluation above(double observed,double limit) { return new Evaluation(true,observed,limit,Math.max(0,observed-limit)); }
    }
}
