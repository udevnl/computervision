package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.text.Html;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.EdgeDetection;
import nl.udev.hellorenderscript.video.algoritms.common.EdgeDetection.KernelMode;
import nl.udev.hellorenderscript.video.algoritms.common.Plotting;

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

    private EdgeDetection edgeDetection;
    private Plotting plotting;

    private ScriptC_utils rsUtils;
    private Allocation intensityBuffer;

    private int kernelSize;
    private float amplification;
    private KernelMode mode;

    public VectorEdgeDetectionAlgorithm() {
        addParameter(new IntegerParameter("Kernel size", 1, 10, 1, new KernelSizeMonitor()));
        addParameter(new IntegerParameter("Amplification", 1, 100, 1, new AmplificationMonitor()));
        addParameter(new LimitedSettingsParameter<>("KernelMode", KernelMode.values(), KernelMode.KernelVector2D, new ModeMonitor()));
        this.kernelSize = 3;
        this.amplification = 2.0f;
        this.mode = KernelMode.KernelVector2D;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public CharSequence getDescription() {
        return Html.fromHtml(
                "Detect edges by converting the brightness at each kernel position to a vector:" +
                        "<br><b>A)</b> each kernel position is assigned a vector which matches the " +
                        "angle that position has from the center of the kernel." +
                        "<br><b>B)</b> when applying the kernel the brightness at the kernel position is the" +
                        " length and the vector in the kernel the direction." +
                        "<br><b>C)</b> all resulting vectors are summed resulting in the edge direction"
        );
    }

    @Override
    protected void initialize() {

        edgeDetection = new EdgeDetection(
                getRenderScript(),
                getVideoResolution().getWidth(),
                getVideoResolution().getHeight(),
                kernelSize
        );

        plotting = new Plotting(getRenderScript());

        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());
    }

    @Override
    protected void unInitialize() {

        edgeDetection.destroy();
        plotting.destroy();

        // Destroy scriptlets
        rsUtils.destroy();

        // Destroy buffers
        intensityBuffer.destroy();

        rsUtils = null;
        intensityBuffer = null;
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // Calculate the gradients on the intensity buffer
        edgeDetection.setAmplification(amplification);
        edgeDetection.setKernelMode(mode);
        edgeDetection.setKernelSize(kernelSize);
        Allocation polarVectors = edgeDetection.calcEdgePolarVectors(intensityBuffer);

        // Plot polar vectors
        plotting.plotColormapPolar2d(polarVectors, displayBufferRgba);
    }

    private class KernelSizeMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return kernelSize + "x" + kernelSize;
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            kernelSize = 1 + (newValue - 1) * 2;
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
}
