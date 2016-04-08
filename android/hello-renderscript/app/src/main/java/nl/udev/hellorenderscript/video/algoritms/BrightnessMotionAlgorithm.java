package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.util.Size;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_brightnessmotion;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.EdgeDetection;

/**
 * Simple algorithm that detects the motion of the center of brightness between two frames.
 *
 * Created by ben on 8-4-16.
 */
public class BrightnessMotionAlgorithm extends AbstractAlgorithm {

    private static final String TAG = "BrightMotionAlg";

    public static final int DEFAULT_BLOCK_COUNT = 16;

    private EdgeDetection edgeDetection;

    private ScriptC_brightnessmotion rsBrightnessMotion;
    private ScriptC_utils rsUtils;

    private Allocation intensityBuffer;
    private Allocation motionBlocks, brightnessBlocksCurrent, brightnessBlocksPrevious;

    private int blockSizeX, blockSizeY;
    private int activeBlockCount;

    // Parameters
    private int blockCount;
    private float inputAmplification;
    private float motionAmplification;
    private ViewType viewType;
    private InputType inputType;

    private enum InputType {
        Input_Brightness,
        Input_Edges
    }

    private enum ViewType {
        View_Source,
        View_BrightOverlay,
        View_MotionOverlay
    }


    public BrightnessMotionAlgorithm() {
        addParameter(new IntegerParameter("blocks", 2, 7, 4, new BlockCountParameterMonitor()));
        addParameter(new IntegerParameter("inputAmplification", 0, 100, 10, new InputAmplificationParameterMonitor()));
        addParameter(new IntegerParameter("motionAmplification", 0, 100, 10, new MotionAmplificationParameterMonitor()));
        addParameter(new LimitedSettingsParameter<>("input", InputType.values(), InputType.Input_Brightness, new InputTypeParameterMonitor()));
        addParameter(new LimitedSettingsParameter<>("view", ViewType.values(), ViewType.View_BrightOverlay, new ViewTypeParameterMonitor()));

        this.blockCount = DEFAULT_BLOCK_COUNT;
        this.viewType = ViewType.View_BrightOverlay;
        this.inputType = InputType.Input_Brightness;
        this.motionAmplification = 1.0f;
        this.inputAmplification = 1.0f;
    }

    @Override
    protected String getName() {
        return TAG;
    }

    @Override
    protected void initialize() {

        edgeDetection = new EdgeDetection(
                getRenderScript(),
                getVideoResolution().getWidth(),
                getVideoResolution().getHeight(),
                3
        );

        // Create scriptlets (RenderScript)
        rsUtils = new ScriptC_utils(getRenderScript());
        rsBrightnessMotion = new ScriptC_brightnessmotion(getRenderScript());

        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsUtils.destroy();
        edgeDetection.destroy();
        rsBrightnessMotion.destroy();

        // Destroy buffers
        intensityBuffer.destroy();
        if(motionBlocks != null) {
            motionBlocks.destroy();
            motionBlocks = null;
            brightnessBlocksCurrent.destroy();
            brightnessBlocksPrevious.destroy();
        }
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        dynamicCreateBlocksBufferBasedOnBlockCount();

        // Convert captured frame to gray-scale for processing
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // ----- ----- ----- ----- Generate the input ----- ----- ----- -----
        Allocation input;
        switch (inputType) {
            default:
            case Input_Brightness:
                input = intensityBuffer;
                break;
            case Input_Edges:
                edgeDetection.setAmplification(inputAmplification);
                input = edgeDetection.calcEdgeMagnitudes(intensityBuffer);
                break;
        }
        rsBrightnessMotion.set_sourceImage(input);

        // ----- ----- ----- ----- Detect the brightness center ----- ----- ----- -----
        swapCurrentPreviousBrightnessBuffers();
        rsBrightnessMotion.forEach_calcBrightnessCenterBlocks(brightnessBlocksCurrent);

        // ----- ----- ----- ----- Calculate motion vectors ----- ----- ----- -----
        rsBrightnessMotion.set_currentBrightnessCenterBlocks(brightnessBlocksCurrent);
        rsBrightnessMotion.set_previousBrightnessCenterBlocks(brightnessBlocksPrevious);
        rsBrightnessMotion.set_motionAmplification(motionAmplification);
        rsBrightnessMotion.forEach_calcMotionBlocks(motionBlocks);

        // ----- ----- ----- ----- Render the view ----- ----- ----- -----
        switch (viewType) {
            case View_Source:
                rsUtils.forEach_calcRgbaIntensity(input, displayBufferRgba);
                break;
            case View_BrightOverlay:
                rsBrightnessMotion.set_overlayBrightnessBlocks(brightnessBlocksCurrent);
                rsBrightnessMotion.forEach_calcOverlayBrightnessBlocks(captureBufferRgba, displayBufferRgba);
                break;
            case View_MotionOverlay:
                rsBrightnessMotion.set_plotDestination(displayBufferRgba);
                rsBrightnessMotion.set_plotWidth(getVideoResolution().getWidth());
                rsBrightnessMotion.set_plotHeight(getVideoResolution().getHeight());
                displayBufferRgba.copyFrom(captureBufferRgba);
                rsBrightnessMotion.forEach_calcOverlayMotionBlocks(motionBlocks);

                rsBrightnessMotion.set_motionBlocks(motionBlocks);
                rsBrightnessMotion.invoke_plotGlobalMotion();
                break;
        }
    }

    private void swapCurrentPreviousBrightnessBuffers() {
        Allocation temp = brightnessBlocksPrevious;
        brightnessBlocksPrevious = brightnessBlocksCurrent;
        brightnessBlocksCurrent = temp;
    }

    /**
     * Change the blockCount if needed
     */
    private void dynamicCreateBlocksBufferBasedOnBlockCount() {
        if(activeBlockCount != blockCount || motionBlocks == null) {

            // Calculate the block size for the image
            // If it does not fit perfectly it will still work but we will not use the full image.
            Size imageSize = getVideoResolution();
            blockSizeX = imageSize.getWidth() / blockCount;
            blockSizeY = imageSize.getHeight() / blockCount;

            if(motionBlocks != null) {
                motionBlocks.destroy();
                brightnessBlocksCurrent.destroy();
                brightnessBlocksPrevious.destroy();
            }

            motionBlocks = create2d(blockCount, blockCount, Element.F32_4(getRenderScript()));
            brightnessBlocksCurrent = create2d(blockCount, blockCount, Element.F32_4(getRenderScript()));
            brightnessBlocksPrevious = create2d(blockCount, blockCount, Element.F32_4(getRenderScript()));

            rsBrightnessMotion.set_blockCount(blockCount);
            rsBrightnessMotion.set_blockSizeX(blockSizeX);
            rsBrightnessMotion.set_blockSizeY(blockSizeY);

            activeBlockCount = blockCount;
        }
    }

    private class BlockCountParameterMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%dx%d", blockCount, blockCount);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            blockCount = (int) Math.pow(2, newValue);
        }
    }

    private class MotionAmplificationParameterMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f%%", motionAmplification * 100.0f);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            motionAmplification = newValue / 10.0f;
        }
    }

    private class InputAmplificationParameterMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f%%", inputAmplification * 100.0f);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            inputAmplification = newValue / 10.0f;
        }
    }

    private class ViewTypeParameterMonitor implements ParameterUser<ViewType> {

        @Override
        public String displayValue(ViewType value) {
            return value.toString();
        }

        @Override
        public void handleValueChanged(ViewType newValue) {
            viewType = newValue;
        }
    }

    private class InputTypeParameterMonitor implements ParameterUser<InputType> {

        @Override
        public String displayValue(InputType value) {
            return value.toString();
        }

        @Override
        public void handleValueChanged(InputType newValue) {
            inputType = newValue;
        }
    }
}