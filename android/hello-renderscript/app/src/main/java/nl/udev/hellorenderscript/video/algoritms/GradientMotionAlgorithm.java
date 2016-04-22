package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.text.Html;
import android.util.Size;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_gradientmotion;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.EdgeDetection;
import nl.udev.hellorenderscript.video.algoritms.common.Kernels;

/**
 * Motion detection algorithm to detect motion based on extrapolating the gradients.
 *
 * See #getDescription
 *
 * Created by ben on 11-4-16.
 */
public class GradientMotionAlgorithm extends AbstractAlgorithm {

    private static final String TAG = "GradMotionAlg";

    public static final int DEFAULT_BLOCK_COUNT = 15;

    private EdgeDetection edgeDetection;

    private ScriptC_gradientmotion rsGradientMotion;
    private ScriptC_utils rsUtils;

    private Allocation intensityBuffer;
    private Allocation motionBlocks, brightnessBlocksCurrent, brightnessBlocksPrevious, kernelVectorsBuffer;

    private int activeBlockSize;

    // Parameters
    private int blockSize;
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
        View_MotionOverlay
    }


    public GradientMotionAlgorithm() {
        addParameter(new IntegerParameter("blocks", 3, 99, 15, new BlockSizeParameterMonitor()));
        addParameter(new IntegerParameter("inputAmplification", 0, 100, 10, new InputAmplificationParameterMonitor()));
        addParameter(new IntegerParameter("motionAmplification", 0, 100, 10, new MotionAmplificationParameterMonitor()));
        addParameter(new LimitedSettingsParameter<>("input", InputType.values(), InputType.Input_Brightness, new InputTypeParameterMonitor()));
        addParameter(new LimitedSettingsParameter<>("view", ViewType.values(), ViewType.View_MotionOverlay, new ViewTypeParameterMonitor()));

        this.blockSize = DEFAULT_BLOCK_COUNT;
        this.viewType = ViewType.View_MotionOverlay;
        this.inputType = InputType.Input_Brightness;
        this.motionAmplification = 1.0f;
        this.inputAmplification = 1.0f;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public CharSequence getDescription() {
        return Html.fromHtml(
                "Detect motion vectors based on determining the motion of the gradient at each NxN position."
        );
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
        rsGradientMotion = new ScriptC_gradientmotion(getRenderScript());

        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsUtils.destroy();
        edgeDetection.destroy();
        rsGradientMotion.destroy();

        // Destroy buffers
        intensityBuffer.destroy();
        if(motionBlocks != null) {
            motionBlocks.destroy();
            motionBlocks = null;
            brightnessBlocksCurrent.destroy();
            brightnessBlocksPrevious.destroy();
            kernelVectorsBuffer.destroy();
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
        rsGradientMotion.set_sourceImage(input);

        // ----- ----- ----- ----- Detect the brightness center ----- ----- ----- -----
        swapCurrentPreviousBrightnessBuffers();
        rsGradientMotion.forEach_calcGradientBlocks(brightnessBlocksCurrent);

        // ----- ----- ----- ----- Calculate motion vectors ----- ----- ----- -----
        rsGradientMotion.set_currentBrightnessCenterBlocks(brightnessBlocksCurrent);
        rsGradientMotion.set_previousBrightnessCenterBlocks(brightnessBlocksPrevious);
        rsGradientMotion.set_motionAmplification(motionAmplification);
        rsGradientMotion.forEach_calcMotionBlocks(motionBlocks);

        // ----- ----- ----- ----- Render the view ----- ----- ----- -----
        switch (viewType) {
            case View_Source:
                rsUtils.forEach_calcRgbaIntensity(input, displayBufferRgba);
                break;
            case View_MotionOverlay:
                rsGradientMotion.set_plotDestination(displayBufferRgba);
                rsGradientMotion.set_plotWidth(getVideoResolution().getWidth());
                rsGradientMotion.set_plotHeight(getVideoResolution().getHeight());
                displayBufferRgba.copyFrom(captureBufferRgba);
                rsGradientMotion.forEach_calcOverlayMotionBlocks(motionBlocks);

                rsGradientMotion.set_motionBlocks(motionBlocks);
                // rsGradientMotion.invoke_plotGlobalMotion();
                break;
        }
    }

    private void swapCurrentPreviousBrightnessBuffers() {
        Allocation temp = brightnessBlocksPrevious;
        brightnessBlocksPrevious = brightnessBlocksCurrent;
        brightnessBlocksCurrent = temp;
    }

    /**
     * Change the blockSize if needed
     */
    private void dynamicCreateBlocksBufferBasedOnBlockCount() {
        if(activeBlockSize != blockSize || motionBlocks == null) {

            // Calculate the block size for the image
            // If it does not fit perfectly it will still work but we will not use the full image.
            Size imageSize = getVideoResolution();

            if(motionBlocks != null) {
                motionBlocks.destroy();
                brightnessBlocksCurrent.destroy();
                brightnessBlocksPrevious.destroy();
                kernelVectorsBuffer.destroy();
            }

            int blockCountX = imageSize.getWidth() / blockSize;
            int blockCountY = imageSize.getHeight() / blockSize;

            motionBlocks = create2d(blockCountX, blockCountY, Element.F32_4(getRenderScript()));
            brightnessBlocksCurrent = create2d(blockCountX, blockCountY, Element.F32_4(getRenderScript()));
            brightnessBlocksPrevious = create2d(blockCountX, blockCountY, Element.F32_4(getRenderScript()));

            rsGradientMotion.set_blockCountX(blockCountX);
            rsGradientMotion.set_blockCountY(blockCountY);
            rsGradientMotion.set_blockSize(blockSize);

            kernelVectorsBuffer = create2d(blockSize, blockSize, Element.F32_4(getRenderScript()));
            float kernelBuffer[] = Kernels.createWeightedAngularVectorKernel(blockSize);
            kernelVectorsBuffer.copyFrom(kernelBuffer);
            rsGradientMotion.set_kernelVectorBuffer(kernelVectorsBuffer);
            rsGradientMotion.set_totalKernelWeight(Kernels.calculateTotalKernelWeight(kernelBuffer));

            activeBlockSize = blockSize;
        }
    }

    private class BlockSizeParameterMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%dx%d", blockSize, blockSize);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            blockSize = 1 + newValue * 2;
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