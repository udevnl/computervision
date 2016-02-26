package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;

import nl.udev.hellorenderscript.common.ColormapPolar2dRS;
import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_gradientvector;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.Kernels;

/**
 * Algorithm that performs edge gradient detection using a new method I thought of:
 *
 * A 2D kernel is created which contains a grid of vectors with weights.
 *
 * For example the following kernel:
 *
 * v1 v2 v3
 * v4 v5 v5
 * v7 v8 v9
 *
 * Each vector in the kernel contains the direction that the vector has relative to the center v5.
 * So, if the vectors where written as angles it would be:
 *
 * 135 090 045
 * 180 --- 000
 * 225 270 315
 *
 * Additionally the vector contains the weight that it should apply.
 * In this algorithm the weight applied is 1.0 for all vectors that are within the radius of the
 * kernel. Vectors outside the radius of the kernel move to zero within a border region of 1.0.
 * (for full details see the {@code Kernels.createWeightedAngularVectorKernel})
 *
 * So the total vector has three elements:
 * 0) vector x-component
 * 1) vector y-component
 * 2) weight factor
 *
 * The kernel is applied by taking the magnitude of image pixels and given them a
 * direction from the kernel. All resulting vectors are summed resulting in an average
 * gradient vector.
 *
 * For details on how the kernel is applied, see the renderscript 'gradientvector.rs'.
 *
 * Created by ben on 9-2-16.
 */
public class VectorEdgeDetectionAlgorithm extends AbstractAlgorithm {

    private static final String TAG = "VectorEdgeAlg";
    private ScriptC_gradientvector rsGradientVector;
    private ScriptC_utils rsUtils;

    private ColormapPolar2dRS colormapPolar2dRS;

    private Allocation intensityBuffer;
    private Allocation gradientVectorsBuffer;
    private Allocation imageVectorsPolarBuffer;

    // Dynamically created / changed
    private Allocation kernelVectorsBuffer;

    private int kernelSize;
    private float amplification;
    private KernelMode mode;
    private boolean kernelSizeChanged;

    private enum KernelMode {
        FULL_2D,
        XY_SEPARABLE_2D
    }

    public VectorEdgeDetectionAlgorithm() {
        addParameter(new IntegerParameter("Kernel size", 1, 10, 1, new KernelSizeMonitor()));
        addParameter(new IntegerParameter("Amplification", 1, 100, 1, new AmplificationMonitor()));
        addParameter(new LimitedSettingsParameter<>("Mode", KernelMode.values(), KernelMode.FULL_2D, new ModeMonitor()));
        this.kernelSize = 3;
        this.amplification = 2.0f;
        this.mode = KernelMode.FULL_2D;
    }

    @Override
    protected String getName() {
        return "GradientVector edge detection";
    }

    @Override
    protected void initialize() {
        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
        gradientVectorsBuffer = create2d(Element.F32_2(getRenderScript()));
        imageVectorsPolarBuffer = create2d(Element.F32_2(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());
        rsGradientVector = new ScriptC_gradientvector(getRenderScript());

        // Initialize scriptlets
        rsGradientVector.set_sourceWidth(getVideoResolution().getWidth());
        rsGradientVector.set_sourceHeight(getVideoResolution().getHeight());
        rsGradientVector.set_intensityBuffer(intensityBuffer);

        colormapPolar2dRS = new ColormapPolar2dRS
                .Builder(getRenderScript())
                .addLinearGradient(0, 90,       1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f) // Red -> Yellow
                .addLinearGradient(90, 180,     1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f) // Yellow -> Green
                .addLinearGradient(180, 270,    0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f) // Green -> Cyan
                .addLinearGradient(270, 360,    0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f) // Cyan -> Red
                .build();

        kernelSizeChanged = true;
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsUtils.destroy();
        rsGradientVector.destroy();
        colormapPolar2dRS.cleanup();

        // Destroy buffers
        intensityBuffer.destroy();
        gradientVectorsBuffer.destroy();
        imageVectorsPolarBuffer.destroy();
        if(kernelVectorsBuffer != null) {
            kernelVectorsBuffer.destroy();
        }

        rsUtils = null;
        rsGradientVector = null;
        colormapPolar2dRS = null;
        intensityBuffer = null;
        gradientVectorsBuffer = null;
        imageVectorsPolarBuffer = null;
        kernelVectorsBuffer = null;
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Support synchronously changing the kernel size
        updateKernelSizeIfChanged();

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // Calculate the gradients on the intensity buffer
        rsGradientVector.set_scale(amplification);

        switch (mode) {
            case FULL_2D:
                rsGradientVector.forEach_calcGradientVectors(gradientVectorsBuffer);
                break;
            case XY_SEPARABLE_2D:
                rsGradientVector.forEach_calcGradientVectorsXYSeparable(gradientVectorsBuffer);
                break;
        }

        // Convert gradient vectors to polar vectors
        rsUtils.forEach_toPolar2D(gradientVectorsBuffer, imageVectorsPolarBuffer);

        // Plot polar vectors
        colormapPolar2dRS.plotVectorAngles(imageVectorsPolarBuffer, displayBufferRgba);
    }

    private class KernelSizeMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return kernelSize + "x" + kernelSize;
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            kernelSize = 1 + (newValue - 1) * 2;
            kernelSizeChanged = true;
        }
    }

    private class AmplificationMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%2.2f", amplification);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            amplification = (float) Math.pow(1.1, newValue);
        }
    }

    private class ModeMonitor implements ParameterUser<KernelMode> {

        @Override
        public String displayValue(KernelMode value) {
            return value.toString();
        }

        @Override
        public void handleValueChanged(KernelMode newValue) {
            mode = newValue;
        }
    }

    private void updateKernelSizeIfChanged() {

        if(kernelSizeChanged) {
            if(kernelVectorsBuffer != null) {
                kernelVectorsBuffer.destroy();
                kernelVectorsBuffer = null;
            }

            kernelVectorsBuffer = create2d(kernelSize, kernelSize, Element.F32_4(getRenderScript()));
            float kernelBuffer[] = Kernels.createWeightedAngularVectorKernel(kernelSize);
            kernelVectorsBuffer.copyFrom(kernelBuffer);

            rsGradientVector.set_kernelBuffer(kernelVectorsBuffer);
            rsGradientVector.set_kernelSquareRadius((kernelSize - 1) / 2);
            rsGradientVector.set_totalKernelWeight(Kernels.calculateTotalKernelWeight(kernelBuffer));
            rsGradientVector.set_totalKernelWeight2N(Kernels.calculateTotalKernelWeight2N(kernelBuffer));

            kernelSizeChanged = false;
        }
    }
}
