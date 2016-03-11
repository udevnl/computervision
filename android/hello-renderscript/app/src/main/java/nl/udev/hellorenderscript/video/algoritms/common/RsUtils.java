package nl.udev.hellorenderscript.video.algoritms.common;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;

/**
 * Common renderscript utilities
 *
 * Created by ben on 11-3-16.
 */
public class RsUtils {

    private RsUtils() {
        // No instantiation
    }

    /**
     * Helper method to create an 2D Allocation of the given elementType.
     *
     * @param rs
     * @param width
     * @param height
     * @param elementType
     * @return  A new 2D Allocation buffer with the given specs
     */
    public static Allocation create2d(RenderScript rs, int width, int height, Element elementType) {
        Type.Builder vectorBufferBuilder = new Type.Builder(rs, elementType);
        vectorBufferBuilder.setX(width);
        vectorBufferBuilder.setY(height);
        return Allocation.createTyped(rs, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
    }

    /**
     * Helper method to create an 3D Allocation of the given elementType.
     *
     * @param rs
     * @param width
     * @param height
     * @param count
     * @param elementType
     * @return  A new 2D Allocation buffer with the given specs
     */
    public static Allocation create3d(RenderScript rs, int width, int height, int count, Element elementType) {
        Type.Builder vectorBufferBuilder = new Type.Builder(rs, elementType);
        vectorBufferBuilder.setX(width);
        vectorBufferBuilder.setY(height);
        vectorBufferBuilder.setZ(count);
        return Allocation.createTyped(rs, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
    }

}
