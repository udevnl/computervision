package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_interest2;
import nl.udev.hellorenderscript.video.ScriptC_utils;
import nl.udev.hellorenderscript.video.algoritms.common.EdgeDetection;
import nl.udev.hellorenderscript.video.algoritms.common.Plotting;

/**
 * Another attempt at detecting interest points.
 *
 * See interest2.rs for the algorithm.
 *
 * Created by ben on 9-4-16.
 */
public class InterestPoint2Algorithm extends AbstractAlgorithm {

    private ScriptC_interest2 rsInterestPoint;
    private ScriptC_utils rsUtils;

    private Plotting plotting;
    private EdgeDetection edgeDetection;

    private Allocation intensityBuffer;
    private Allocation polarBuffer1;

    private int kernelSize;
    private int areaSize;
    private float start;
    private float end;
    private float minLength;
    private float amplification;
    private ViewType viewType;

    enum ViewType {
        ViewEdgesOverlay,
        ViewSourceOverlay
    }

    public InterestPoint2Algorithm() {
        addParameter(new IntegerParameter("Edge kernel", 1, 10, 1, new KernelSizeMonitor()));
        addParameter(new IntegerParameter("Edge amp", 1, 100, 1, new AmplificationMonitor()));
        addParameter(new IntegerParameter("AreaSize", 1, 8, 1, new AreaSizeMonitor()));
        addParameter(new IntegerParameter("Start", 0, 100, 40, new StartMonitor()));
        addParameter(new IntegerParameter("End", 0, 100, 60, new EndMonitor()));
        addParameter(new IntegerParameter("MinLength", 0, 100, 0, new MinLengthMonitor()));
        addParameter(new LimitedSettingsParameter<>("Viewtype", ViewType.values(), ViewType.ViewEdgesOverlay, new ViewTypeMonitor()));
        this.kernelSize = 5;
        this.amplification = 7.0f;
        this.areaSize = 1;
        this.start = 0.4f;
        this.end = 0.6f;
        this.minLength = 0;
        this.viewType = ViewType.ViewEdgesOverlay;
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Convert RGB image to intensity (black/white) image
        rsUtils.forEach_calcGreyscaleIntensity(captureBufferRgba, intensityBuffer);

        // Calculate the gradients on the intensity buffer
        edgeDetection.setKernelSize(kernelSize);
        edgeDetection.setAmplification(amplification);
        Allocation edgePolarVectors = edgeDetection.calcEdgePolarVectors(intensityBuffer);

        // Calculate the amount of edge in a certain area
        rsInterestPoint.set_minLength(minLength);
        rsInterestPoint.set_areaSize(areaSize);
        rsInterestPoint.set_startFraction(start);
        rsInterestPoint.set_endFraction(end);
        rsInterestPoint.set_sourcePolarEdgeVectorBuffer(edgePolarVectors);
        rsInterestPoint.set_sourceEdgeVectorBuffer(edgeDetection.getEdgeVectorsBuffer());
        rsInterestPoint.forEach_calcInterestPoints(edgePolarVectors, polarBuffer1);

        // Plot the interest points
        switch (viewType) {
            case ViewEdgesOverlay:
                plotting.plotColormapPolar2d(edgePolarVectors, displayBufferRgba);
                rsInterestPoint.set_overlaySourceBuffer(displayBufferRgba);
                break;
            case ViewSourceOverlay:
                rsInterestPoint.set_overlaySourceBuffer(captureBufferRgba);
                break;
        }

        rsInterestPoint.forEach_plotInterestPoints(polarBuffer1, displayBufferRgba);
    }

    @Override
    protected String getName() {
        return "IntPtDet v2";
    }

    @Override
    protected void initialize() {
        // Create buffers
        intensityBuffer = create2d(Element.F32(getRenderScript()));
        polarBuffer1 = create2d(Element.F32_2(getRenderScript()));

        // Create scriptlets
        rsUtils = new ScriptC_utils(getRenderScript());
        rsInterestPoint = new ScriptC_interest2(getRenderScript());
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

    private class ViewTypeMonitor implements ParameterUser<ViewType> {

        @Override
        public String displayValue(ViewType value) {
            return viewType.toString();
        }

        @Override
        public void handleValueChanged(ViewType newValue) {
            viewType = newValue;
        }
    }

    private class StartMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f", start * 100.0f);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            start = newValue / 100.0f;
        }
    }

    private class EndMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f", end * 100.0f);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            end = newValue / 100.0f;
        }
    }

    private class AreaSizeMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%dx%d", areaSize * 2 + 1, areaSize * 2 + 1);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            areaSize = newValue;
        }
    }

    private class MinLengthMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%8.2f", minLength);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            minLength = (float)(Math.pow(1.05, newValue) - 1);
        }
    }
}
