package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;

import java.util.Arrays;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.TemporalPyramid;

/**
 * Created by ben on 11-3-16.
 */
public class TemporalPyramidAlgorithm extends AbstractAlgorithm {

    private static final String TAG = "TemporalPyramid";

    private static final int MAX_PIRAMID_LEVELS = 5;

    private ScriptC_utils rsUtils;
    private TemporalPyramid pyramid;
    private Allocation intensityBuffer;

    // Parameters
    private int pyramidCount;
    private boolean pyramidCountChanged;
    private int viewLevel;
    private float[] levelAdjustments = new float[MAX_PIRAMID_LEVELS];
    private ViewType viewType;

    private enum ViewType {
        LEVEL,
        LEVEL_EXPANDED,
        LAPLACIAN,
        LAPLACIAN_COLLAPSED
    }

    public TemporalPyramidAlgorithm() {
        addParameter(new IntegerParameter("Pyramid count", 1, MAX_PIRAMID_LEVELS, 3, new PyramidCountMonitor()));
        addParameter(new IntegerParameter("ViewLevel", 0, MAX_PIRAMID_LEVELS, 1, new ViewLevelMonitor()));
        addParameter(new LimitedSettingsParameter<>("ViewType", ViewType.values(), ViewType.LAPLACIAN, new ViewTypeMonitor()));

        Arrays.fill(levelAdjustments, 1.0f);
        for(int i = 0; i < MAX_PIRAMID_LEVELS; i++) {
            addParameter(new IntegerParameter("L" + i + " adjust", 0, 110, 60, new LevelAdjustMonitor(i)));
        }

        this.pyramidCount = 3;
        this.viewLevel = 1;
        this.viewType = ViewType.LAPLACIAN;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public String getDescription() {
        return "Experiment what a temporal Laplacian pyramid would look like.";
    }

    @Override
    protected void initialize() {
        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());

        pyramid = new TemporalPyramid(
                getRenderScript(),
                getVideoResolution().getWidth(),
                getVideoResolution().getHeight(),
                pyramidCount
        );

        pyramidCountChanged = false;
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsUtils.destroy();

        // Destroy buffers
        intensityBuffer.destroy();
        pyramid.destroy();

        rsUtils = null;
        pyramid = null;
        intensityBuffer = null;
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Support synchronously changing the pyramid size
        if(pyramidCountChanged) {
            pyramid.resizePyramid(
                    getVideoResolution().getWidth(),
                    getVideoResolution().getHeight(),
                    pyramidCount
            );
            pyramidCountChanged = false;
        }

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        pyramid.calculate(intensityBuffer);

        // Do some magic to the individual levels...
        for(int level = 0; level < pyramid.getActualLevelCount(); level++) {
            // Adjust the intensity of the laplacian of each level
            TemporalPyramid.Level pyramidLevel = pyramid.getLevel(level);
            rsUtils.set_multiplyFactor(levelAdjustments[level]);
            for(int c=0; c<3; c++) {
                rsUtils.forEach_multiply(
                        pyramidLevel.getLevelLaplacianBuffers().get(c),
                        pyramidLevel.getLevelLaplacianBuffers().get(c)
                );
            }
        }

        if(pyramid.getActualLevelCount() > viewLevel) {

            TemporalPyramid.PlotType plotType;
            switch (viewType) {
                case LAPLACIAN:
                    plotType = TemporalPyramid.PlotType.LAPLACIAN;
                    break;
                case LEVEL_EXPANDED:
                    plotType = TemporalPyramid.PlotType.LEVEL_EXPANDED;
                    break;
                case LAPLACIAN_COLLAPSED:
                    pyramid.collapseLaplacian();
                    plotType = TemporalPyramid.PlotType.COLLAPSED_RESULT;
                    break;
                default: // fall through
                case LEVEL:
                    plotType = TemporalPyramid.PlotType.LEVEL;
                    break;
            }

            pyramid.plotLevel(
                    viewLevel,
                    plotType,
                    displayBufferRgba
            );
        }
    }

    //--------------------------------------------------------------------------------------------
    //region Parameter monitors
    //--------------------------------------------------------------------------------------------
    private class PyramidCountMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return "" + pyramidCount;
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            pyramidCount = newValue;
            pyramidCountChanged = true;
        }
    }

    private class ViewLevelMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return "" + viewLevel;
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            viewLevel = newValue;
        }
    }

    private class ViewTypeMonitor implements ParameterUser<ViewType> {

        @Override
        public String displayValue(ViewType value) {
            return value.toString();
        }

        @Override
        public void handleValueChanged(ViewType newValue) {
            viewType = newValue;
        }
    }

    private class LevelAdjustMonitor implements ParameterUser<Integer> {

        final int level;

        LevelAdjustMonitor(int level) {
            this.level = level;
        }

        @Override
        public String displayValue(Integer value) {
            return String.format("%2.2f", levelAdjustments[level]);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            levelAdjustments[level] = (newValue - 50) / 10.0f;
        }
    }
    //--------------------------------------------------------------------------------------------
    //endregion
    //--------------------------------------------------------------------------------------------
}
