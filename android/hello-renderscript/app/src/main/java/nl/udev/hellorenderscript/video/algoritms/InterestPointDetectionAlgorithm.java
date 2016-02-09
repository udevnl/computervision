package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;

import nl.udev.hellorenderscript.common.ColormapPolar2dRS;
import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_gradientvector;
import nl.udev.hellorenderscript.video.ScriptC_interestpoint;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.Kernels;

/**
 * Algorithm to shown and detect interest points in an image.
 *
 * Created by ben on 9-2-16.
 */
public class InterestPointDetectionAlgorithm extends AbstractAlgorithm {

    private ScriptC_interestpoint rsInterestPoint;
    private ScriptC_gradientvector rsGradientVector;
    private ScriptC_utils rsUtils;

    private ColormapPolar2dRS colormapPolar2dRS;

    private Allocation intensityBuffer;
    private Allocation gradientVectorsBuffer;
    private Allocation imageVectorsPolarBuffer;
    private Allocation polarBuffer1;

    // Dynamically created / changed
    private Allocation kernelVectorsBuffer;

    private int kernelSize;
    private int interestAreaSize;
    private float amplification;
    private float minAngle;
    private float maxAngle;
    private boolean kernelSizeChanged;


    public InterestPointDetectionAlgorithm() {
        addParameter(new IntegerParameter("Edge kernel", 1, 10, 1, new KernelSizeMonitor()));
        addParameter(new IntegerParameter("Edge amp", 1, 100, 1, new AmplificationMonitor()));
        addParameter(new IntegerParameter("InterestAreaSize", 1, 10, 5, new InterestAreaSizeMonitor()));
        addParameter(new IntegerParameter("MinAngle", 5, 180, 30, new MinAngleMonitor()));
        addParameter(new IntegerParameter("MaxAngle", 5, 180, 150, new MaxAngleMonitor()));
        this.kernelSize = 3;
        this.amplification = 2.0f;
        this.interestAreaSize = 5;
        this.minAngle = (float) Math.toRadians(30);
        this.maxAngle = (float) Math.toRadians(150);
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Support synchronously changing the kernel size
        updateKernelSizeIfChanged();

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // Calculate the gradients on the intensity buffer
        rsGradientVector.set_scale(amplification);
        rsGradientVector.forEach_calcGradientVectors(gradientVectorsBuffer);

        // Convert gradient vectors to polar vectors
        rsUtils.forEach_toPolar2D(gradientVectorsBuffer, imageVectorsPolarBuffer);

        // Plot polar vectors
        colormapPolar2dRS.plotVectorAngles(imageVectorsPolarBuffer, displayBufferRgba);

        // Calculate the amount of edge in a certain area
        rsInterestPoint.set_areaSize(interestAreaSize);
        rsInterestPoint.set_polarEdgeBuffer(imageVectorsPolarBuffer);
        rsInterestPoint.forEach_calcInterestPoints(imageVectorsPolarBuffer, polarBuffer1);

        // Plot the interest points
        rsInterestPoint.set_minAngle(minAngle / 2.0f);
        rsInterestPoint.set_maxAngle(maxAngle / 2.0f);
        rsInterestPoint.set_interestPointsBuffer(polarBuffer1);
        rsInterestPoint.set_plotImageBuffer(displayBufferRgba);
        rsInterestPoint.forEach_plotInterestPoints(imageVectorsPolarBuffer);
    }

    @Override
    protected String getName() {
        return "InterestPoint detection";
    }

    @Override
    protected void initialize() {
        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
        gradientVectorsBuffer = create2d(Element.F32_2(getRenderScript()));
        imageVectorsPolarBuffer = create2d(Element.F32_2(getRenderScript()));
        polarBuffer1 = create2d(Element.F32_2(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());
        rsGradientVector = new ScriptC_gradientvector(getRenderScript());
        rsInterestPoint = new ScriptC_interestpoint(getRenderScript());

        // Initialize scriptlets
        rsGradientVector.set_sourceWidth(getVideoResolution().getWidth());
        rsGradientVector.set_sourceHeight(getVideoResolution().getHeight());
        rsGradientVector.set_intensityBuffer(intensityBuffer);

        rsInterestPoint.set_sourceWidth(getVideoResolution().getWidth());
        rsInterestPoint.set_sourceHeight(getVideoResolution().getHeight());

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
        rsInterestPoint.destroy();
        colormapPolar2dRS.cleanup();

        // Destroy buffers
        intensityBuffer.destroy();
        gradientVectorsBuffer.destroy();
        imageVectorsPolarBuffer.destroy();
        if(kernelVectorsBuffer != null) {
            kernelVectorsBuffer.destroy();
        }
        polarBuffer1.destroy();

        rsUtils = null;
        rsGradientVector = null;
        colormapPolar2dRS = null;
        intensityBuffer = null;
        gradientVectorsBuffer = null;
        imageVectorsPolarBuffer = null;
        kernelVectorsBuffer = null;

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

    private class InterestAreaSizeMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return interestAreaSize + "x" + interestAreaSize;
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            interestAreaSize = newValue;
        }
    }

    private class MinAngleMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f deg", Math.toDegrees(minAngle));
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            minAngle = (float)Math.toRadians(newValue);
        }
    }

    private class MaxAngleMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f deg", Math.toDegrees(maxAngle));
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            maxAngle = (float)Math.toRadians(newValue);
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

            kernelSizeChanged = false;
        }
    }

}
