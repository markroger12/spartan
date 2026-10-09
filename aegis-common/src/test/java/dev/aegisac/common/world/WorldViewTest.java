package dev.aegisac.common.world;
import dev.aegisac.common.physics.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.aegisac.common.physics.PhysicsFixtures.*;
class WorldViewTest {
    @Test void invalidationFencesInFlightCaptureAndClosePreventsResurrection() {
        var view=new WorldView(); var start=view.read();
        var capture=world(BlockSample.Surface.SOLID,MotionContext.NORMAL);
        view.invalidate(); assertFalse(view.publish(start,capture)); assertNull(view.read().snapshot());
        var fresh=view.read();
        var next=new WorldSnapshot(capture.world(),fresh.revision(),0,capture.coverage(),capture.blocks(),capture.entities(),capture.context(),capture.uncertainty());
        assertTrue(view.publish(fresh,next)); assertSame(next,view.read().snapshot());
        var beforeClose=view.read(); view.close(); assertFalse(view.publish(beforeClose,next));
        assertTrue(view.read().closed()); assertNull(view.read().snapshot());
    }
    @Test void snapshotsDefensivelyCopyShapesAndTraits() {
        var w=world(BlockSample.Surface.ICE,MotionContext.NORMAL);
        assertThrows(UnsupportedOperationException.class,()->w.blocks().clear());
        assertThrows(UnsupportedOperationException.class,()->w.shapes().clear());
        assertThrows(UnsupportedOperationException.class,()->w.blocks().getFirst().surfaces().clear());
    }
}
