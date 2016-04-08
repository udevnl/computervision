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
     * Helper method to create an 1D Allocation of the given elementType.
     *
     * @param rs             Boss script
     * @param length         Length of buffer to create
     * @param elementType    Element type of buffer to create
     * @return  A new 1D Allocation buffer with the given specs
     */
    public static Allocation create1d(RenderScript rs, int length, Element elementType) {
        Type.Builder vectorBufferBuilder = new Type.Builder(rs, elementType);
        vectorBufferBuilder.setX(length);
        return Allocation.createTyped(rs, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
    }

    /**
     * Helper method to create an 2D Allocation of the given elementType.
     *
     * @param rs             Boss script
     * @param width          Width
     * @param height         Height
     * @param elementType    Element type of buffer to create
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
     * @param rs             Boss script
     * @param width          Width
     * @param height         Height
     * @param count          Count or Z
     * @param elementType    Element type of buffer to create
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
