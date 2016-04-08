package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_interestpoint;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.EdgeDetection;
import nl.udev.hellorenderscript.video.algoritms.common.Plotting;

/**
 * Algorithm to show and detect interest points in an image using another custom algorithm.
 *
 *
 *
 * Created by ben on 9-2-16.
 */
public class InterestPointDetectionAlgorithm extends AbstractAlgorithm {

    private ScriptC_interestpoint rsInterestPoint;
    private ScriptC_utils rsUtils;

    private Plotting plotting;
    private EdgeDetection edgeDetection;

    private Allocation intensityBuffer;
    private Allocation polarBuffer1;

    private int kernelSize;
    private int interestAreaSize;
    private float amplification;
    private float minEdgeSize;
    private float binSizeRadians;
    private float maxWeightOutOfBinFactor;
    private float maxAngleBetweenBinsRadians;


    public InterestPointDetectionAlgorithm() {
        addParameter(new IntegerParameter("Edge kernel", 1, 10, 1, new KernelSizeMonitor()));
        addParameter(new IntegerParameter("Edge amp", 1, 100, 1, new AmplificationMonitor()));
        addParameter(new IntegerParameter("InterestAreaSize", 1, 10, 5, new InterestAreaSizeMonitor()));
        addParameter(new IntegerParameter("MinEdgeSize", 10, 100, 20, new MinEdgeSizeMonitor()));
        addParameter(new IntegerParameter("BinSize", 5, 180, 150, new BinSizeMonitor()));
        addParameter(new IntegerParameter("MaxNonBinFactor", 1, 100, 10, new MaxOutsideBinFactorMonitor()));
        addParameter(new IntegerParameter("MaxBinsAngle", 0, 360, 120, new MaxBinsAngleMonitor()));
        this.kernelSize = 5;
        this.amplification = 7.0f;
        this.interestAreaSize = 1;
        this.minEdgeSize = 0.5f;
        this.maxWeightOutOfBinFactor = 0.1f;
        this.binSizeRadians = (float) Math.toRadians(27);
        this.maxAngleBetweenBinsRadians = (float) Math.toRadians(120);
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // Calculate the gradients on the intensity buffer
        edgeDetection.setKernelSize(kernelSize);
        edgeDetection.setAmplification(amplification);
        Allocation edgePolarVectors = edgeDetection.calcEdgePolarVectors(intensityBuffer);

        // Plot polar vectors
        plotting.plotColormapPolar2d(edgePolarVectors, displayBufferRgba);

        // Calculate the amount of edge in a certain area
        rsInterestPoint.set_areaSize(interestAreaSize);
        rsInterestPoint.set_polarEdgeBuffer(edgePolarVectors);
        rsInterestPoint.set_binSizeRadians(binSizeRadians);
        rsInterestPoint.set_minEdgeSize(minEdgeSize);
        rsInterestPoint.set_maxOutOfBinsFactor(maxWeightOutOfBinFactor);
        rsInterestPoint.set_maxAngleBetweenBinsRadians(maxAngleBetweenBinsRadians);
        rsInterestPoint.forEach_calcInterestPoints(edgePolarVectors, polarBuffer1);

        // Plot the interest points
        rsInterestPoint.set_plotImageBuffer(displayBufferRgba);
        rsInterestPoint.forEach_plotInterestPoints(polarBuffer1);
    }

    @Override
    protected String getName() {
        return "InterestPoint detection";
    }

    @Override
    protected void initialize() {
        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
        polarBuffer1 = create2d(Element.F32_2(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());
        rsInterestPoint = new ScriptC_interestpoint(getRenderScript());
        rsInterestPoint.set_sourceWidth(getVideoResolution().getWidth());
        rsInterestPoint.set_sourceHeight(getVideoResolution().getHeight());

        plotting = new Plotting(getRenderScript());

        edgeDetection = new EdgeDetection(
                getRenderScript(),
                getVideoResolution().getWidth(),
                getVideoResolution().getHeight(),
                kernelSize
        );
    }

    @Override
    protected void unInitialize() {
        // Destroy scriptlets
        rsUtils.destroy();
        rsInterestPoint.destroy();
        plotting.destroy();
        edgeDetection.destroy();

        // Destroy buffers
        intensityBuffer.destroy();
        polarBuffer1.destroy();

        rsUtils = null;
        plotting = null;
        intensityBuffer = null;
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

    private class MinEdgeSizeMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%1.2f", minEdgeSize);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            minEdgeSize = (float) newValue / 100.0f;
        }
    }

    private class BinSizeMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.1f deg", Math.toDegrees(binSizeRadians));
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            binSizeRadians = (float)Math.toRadians(newValue);
        }
    }

    private class MaxOutsideBinFactorMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%1.1f", maxWeightOutOfBinFactor);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            maxWeightOutOfBinFactor = (float) newValue / 100.0f;
        }
    }

    private class MaxBinsAngleMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.1f deg", Math.toDegrees(maxAngleBetweenBinsRadians));
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            maxAngleBetweenBinsRadians = (float)Math.toRadians(newValue);
        }
    }
}
