package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_pyramid;
import nl.udev.hellorenderscript.video.ScriptC_utils;

/**
 * Created by ben on 26-2-16.
 */
public class ImagePyramidAlgorithm extends AbstractAlgorithm {

    private static final String TAG = "ImagePyramid";
    private ScriptC_pyramid rsPyramid;
    private ScriptC_utils rsUtils;

    private Allocation intensityBuffer;
    private Allocation scratchPadBuffer;

    // Dynamically created / changed
    private final List<PyramidLevel> pyramidLevels = new ArrayList<>();

    private int pyramidCount;
    private boolean pyramidCountChanged;
    private int viewLevel;
    private ViewType viewType;

    private enum ViewType {
        LEVEL,
        LEVEL_EXPANDED,
        LAPLACIAN
    }

    public ImagePyramidAlgorithm() {
        addParameter(new IntegerParameter("Pyramid count", 1, 10, 3, new PyramidCountMonitor()));
        addParameter(new IntegerParameter("ViewLevel", 1, 10, 1, new ViewLevelMonitor()));
        addParameter(new LimitedSettingsParameter<>("ViewType", ViewType.values(), ViewType.LAPLACIAN, new ViewTypeMonitor()));

        this.pyramidCount = 3;
        this.viewLevel = 1;
        this.viewType = ViewType.LAPLACIAN;
    }

    @Override
    protected String getName() {
        return "ImagePyramid";
    }

    @Override
    protected void initialize() {
        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
        scratchPadBuffer = create2d(Element.F32(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());
        rsPyramid = new ScriptC_pyramid(getRenderScript());

        pyramidCountChanged = true;
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsUtils.destroy();
        rsPyramid.destroy();

        // Destroy buffers
        intensityBuffer.destroy();
        scratchPadBuffer.destroy();
        destroyPyramid();

        rsUtils = null;
        rsPyramid = null;
        intensityBuffer = null;
        scratchPadBuffer = null;
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Support synchronously changing the pyramid size
        updatePyramidStructureIfChanged();

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // Calculate all pyramids, starting at the top
        rsPyramid.set_compressSource(intensityBuffer);
        for(int level = 1; level < pyramidLevels.size(); level++) {
            PyramidLevel pyramidLevel = pyramidLevels.get(level);

            // Calculate the pyramid level
            rsPyramid.set_compressTargetWidth(pyramidLevel.width);
            rsPyramid.set_compressTargetHeight(pyramidLevel.height);
            rsPyramid.forEach_compressStep1(pyramidLevel.scratchBuffer);
            rsPyramid.set_compressSource(pyramidLevel.scratchBuffer);
            rsPyramid.forEach_compressStep2(pyramidLevel.levelGaussianBuffer);

            // Calculate the expansion of the level
            rsPyramid.set_expandTargetWidth(pyramidLevel.width * 2);
            rsPyramid.set_expandTargetHeight(pyramidLevel.height * 2);
            rsPyramid.set_expandSource(pyramidLevel.levelGaussianBuffer);
            rsPyramid.forEach_expandStep1(pyramidLevel.scratchBuffer);
            rsPyramid.set_expandSource(pyramidLevel.scratchBuffer);
            rsPyramid.forEach_expandStep2(pyramidLevel.expandedBuffer);

            // Prepare for the next level (if any)
            rsPyramid.set_compressSource(pyramidLevel.levelGaussianBuffer);
        }

        // Calculate the Laplacians...

        // For the lowest level this is simply the gaussian buffer
        int lowestLevelIndex = pyramidLevels.size() - 1;
        PyramidLevel lowestLevel = pyramidLevels.get(lowestLevelIndex);
        lowestLevel.levelLaplacianBuffer.copyFrom(lowestLevel.levelGaussianBuffer);

        // For all other levels it stacks, working from smallest to biggest
        for(int level = lowestLevelIndex - 1; level >= 0; level--) {
            PyramidLevel targetLevel = pyramidLevels.get(level);
            PyramidLevel smallerLevel = pyramidLevels.get(level + 1);
            rsPyramid.set_laplacianLowerLevel(smallerLevel.expandedBuffer);
            rsPyramid.forEach_laplacian(targetLevel.levelGaussianBuffer, targetLevel.levelLaplacianBuffer);
        }

        if(pyramidLevels.size() > viewLevel) {

            PyramidLevel plotLevel = pyramidLevels.get(viewLevel);

            rsPyramid.set_plotWidth(getVideoResolution().getWidth());
            rsPyramid.set_plotHeight(getVideoResolution().getHeight());

            switch (viewType) {
                case LAPLACIAN:
                    rsPyramid.set_pyramidImage(plotLevel.levelLaplacianBuffer);
                    rsPyramid.set_pyramidWidth(plotLevel.width);
                    rsPyramid.set_pyramidHeight(plotLevel.height);
                    break;
                case LEVEL:
                    rsPyramid.set_pyramidImage(plotLevel.levelGaussianBuffer);
                    rsPyramid.set_pyramidWidth(plotLevel.width);
                    rsPyramid.set_pyramidHeight(plotLevel.height);
                    break;
                case LEVEL_EXPANDED:
                    rsPyramid.set_pyramidImage(plotLevel.expandedBuffer);
                    rsPyramid.set_pyramidWidth(plotLevel.width * 2);
                    rsPyramid.set_pyramidHeight(plotLevel.height * 2);
                    break;
            }

            rsPyramid.forEach_plotPyramidLevel(displayBufferRgba);
        }
    }

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
    private void updatePyramidStructureIfChanged() {

        if(pyramidCountChanged) {

            destroyPyramid();
            createPyramid(pyramidCount);

            pyramidCountChanged = false;
        }
    }

    private void createPyramid(int pyramidCount) {
        // Try to build a pyramid with the given number of levels.
        // Note that this will only succeed if the resolution divides in whole numbers all the way.

        int width = getVideoResolution().getWidth();
        int height = getVideoResolution().getHeight();

        // Insert level 0...
        pyramidLevels.add(
                new PyramidLevel(
                        0,
                        width,
                        height,
                        intensityBuffer,
                        create2d(width, height, Element.F32(getRenderScript())),
                        null,
                        null
                )
        );

        int levels = 0;
        for(int level = 1; level <= pyramidCount; level++) {
            if(width%2 == 0 && height %2 == 0) {
                width /= 2;
                height /= 2;
                pyramidLevels.add(
                        new PyramidLevel(
                                level,
                                width,
                                height,
                                create2d(width, height, Element.F32(getRenderScript())),
                                create2d(width, height, Element.F32(getRenderScript())),
                                create2d(width * 2, height * 2, Element.F32(getRenderScript())),
                                create2d(width, height * 2, Element.F32(getRenderScript()))
                        )
                );
                Log.d(TAG, "Created level " + level + ", size " + width + "x" + height + ".");
                levels++;
            } else {
                // Unable to create all pyramids
                Log.w(TAG, "Cannot finish pyramid at level " + level + ", size now " + width + "x" + height + ".");
                break;
            }
        }

        this.pyramidCount = levels;
    }

    private void destroyPyramid() {
        for(PyramidLevel level : pyramidLevels) {
            // Only destroy the buffers of the sub-levels as level 0 is special
            if(level.level == 0) {
                level.levelLaplacianBuffer.destroy();
            } else {
                level.levelGaussianBuffer.destroy();
                level.levelLaplacianBuffer.destroy();
                level.expandedBuffer.destroy();
                level.scratchBuffer.destroy();
            }
        }
        pyramidLevels.clear();
    }

    private static class PyramidLevel {

        final int level;
        final int width;
        final int height;
        final Allocation levelGaussianBuffer;
        final Allocation levelLaplacianBuffer;
        final Allocation expandedBuffer;
        final Allocation scratchBuffer;

        PyramidLevel(int level,
                     int width,
                     int height,
                     Allocation levelGaussianBuffer,
                     Allocation levelLaplacianBuffer, Allocation expandedBuffer,
                     Allocation scratchBuffer) {
            this.level = level;
            this.width = width;
            this.height = height;
            this.levelGaussianBuffer = levelGaussianBuffer;
            this.levelLaplacianBuffer = levelLaplacianBuffer;
            this.expandedBuffer = expandedBuffer;
            this.scratchBuffer = scratchBuffer;
        }
    }
}
