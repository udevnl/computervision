package nl.udev.hellorenderscript.common.algoritm;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.List;

import nl.udev.hellorenderscript.common.algoritm.parameter.AbstractParameter;
import nl.udev.hellorenderscript.common.algoritm.parts.RsUtils;

/**
 * Base class which contains common video processing algorithm functionality.
 *
 * Created by ben on 9-2-16.
 */
public abstract class AbstractAlgorithm {

    private static final String TAG = "Algorithm";

    private final List<AbstractParameter> parameters = new ArrayList<>();
    private Size resolution;
    private RenderScript rs;

    /**
     * Initialize the algorithm.
     *
     * @param videoResolution    the resolution of the video
     * @param rs                 the main renderscript
     */
    public void initialize(Size videoResolution, RenderScript rs) {
        Log.i(TAG, "Initialize algorithm: " + toString());
        this.resolution = videoResolution;
        this.rs = rs;
        initialize();
    }

    /**
     * Cleanup
     */
    public void cleanup() {
        Log.i(TAG, "Cleanup algorithm: " + toString());
        unInitialize();
    }

    /**
     * @return  The parameters of this script
     */
    public List<AbstractParameter> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return getName();
    }

    // *********************************************************************************************
    // Interface between child and this abstract class
    // *********************************************************************************************

    /**
     * @return  the display name of the algorithm
     */
    public abstract String getName();

    /**
     * @return description of what the algorithm does.
     */
    public abstract CharSequence getDescription();

    /**
     * Perform initialization of the algorithm.
     * Usually this involves creating buffers and setting up and initializing renderscripts.
     */
    protected abstract void initialize();

    /**
     * Cleanup everything created in the initialization.
     */
    protected abstract void unInitialize();


    // *********************************************************************************************
    // Interface between this abstract class and it's child
    // *********************************************************************************************

    /**
     * Helper method to create an 2D Allocation of the given elementType.
     *
     * This method assumes the 2D size is equal to the video resolution.
     *
     * @param elementType
     * @return
     */
    protected Allocation create2d(Element elementType) {
        return create2d(resolution.getWidth(), resolution.getHeight(), elementType);
    }

    /**
     * Helper method to create an 2D Allocation of the given elementType.
     *
     * @param size
     * @param elementType
     * @return
     */
    protected Allocation create2d(Size size, Element elementType) {
        return create2d(size.getWidth(), size.getHeight(), elementType);
    }

    /**
     * Helper method to create an 2D Allocation of the given elementType.
     * @param width
     * @param height
     * @param elementType
     * @return
     */
    protected Allocation create2d(int width, int height, Element elementType) {
        return RsUtils.create2d(rs, width, height, elementType);
    }

    /**
     * Add a new parameter of the algorithm to the list of parameters.
     * @param parameter
     */
    protected void addParameter(AbstractParameter parameter) {
        parameters.add(parameter);
    }

    /**
     * @return  The renderscript
     */
    protected RenderScript getRenderScript() {
        return rs;
    }

    /**
     * @return  The currently active resolution
     */
    protected Size getResolution() {
        return resolution;
    }
}
