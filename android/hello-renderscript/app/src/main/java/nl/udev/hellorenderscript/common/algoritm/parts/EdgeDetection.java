package nl.udev.hellorenderscript.common.algoritm.parts;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;

import nl.udev.hellorenderscript.video.ScriptC_edgedetection;
import nl.udev.hellorenderscript.video.ScriptC_utils;

/**
 * Wrapper class for the Edge detection RenderScript.
 *
 * Created by ben on 8-4-16.
 */
public class EdgeDetection {

    private final RenderScript rs;
    private final ScriptC_edgedetection rsEdge;
    private final ScriptC_utils rsUtils;

    private KernelMode kernelMode;
    private int kernelSize;

    private Allocation edgeVectorsBuffer;
    private Allocation edgeVectorsSeparationStep1Buffer;
    private Allocation edgePolarVectorsBuffer;
    private Allocation edgeMagnitudesBuffer;
    private Allocation kernelVectorsBuffer;

    /**
     * Create new edge detection sub-algorithm.
     *
     * @param rs                   Boss script
     * @param width                Width of the source/destination buffers
     * @param height               Height of the source/destination buffers
     * @param initialKernelSize    Initial size of kernel, advice odd numbers >= 1
     */
    public EdgeDetection(RenderScript rs, int width, int height, int initialKernelSize) {
        this.rs = rs;
        this.rsEdge = new ScriptC_edgedetection(rs);
        this.rsUtils = new ScriptC_utils(rs);

        rsEdge.set_sourceWidth(width);
        rsEdge.set_sourceHeight(height);

        edgeVectorsBuffer = RsUtils.create2d(rs, width, height, Element.F32_2(rs));
        edgeVectorsSeparationStep1Buffer = RsUtils.create2d(rs, width, height, Element.F32(rs));
        edgePolarVectorsBuffer = RsUtils.create2d(rs, width, height, Element.F32_2(rs));
        edgeMagnitudesBuffer = RsUtils.create2d(rs, width, height, Element.F32(rs));

        setKernelSize(initialKernelSize);
        setAmplification(1.0f);
        setKernelMode(KernelMode.KernelVector2D);
    }

    /**
     * Clean up
     */
    public void destroy() {

        rsEdge.destroy();
        rsUtils.destroy();

        edgeVectorsBuffer.destroy();
        edgeVectorsSeparationStep1Buffer.destroy();
        edgePolarVectorsBuffer.destroy();
        edgeMagnitudesBuffer.destroy();
        kernelVectorsBuffer.destroy();
    }

    public void setKernelMode(KernelMode kernelMode) {
        this.kernelMode = kernelMode;
    }

    /**
     * Set new kernel size (if changed)
     *
     * @param newSize    size of kernel, advice odd numbers >= 1
     */
    public void setKernelSize(int newSize) {

        if(kernelVectorsBuffer == null || kernelSize != newSize) {
            this.kernelSize = newSize;

            if(kernelVectorsBuffer != null) {
                kernelVectorsBuffer.destroy();
            }

            kernelVectorsBuffer = RsUtils.create2d(rs, newSize, newSize, Element.F32_4(rs));

            float kernelBuffer[] = Kernels.createWeightedAngularVectorKernel(newSize);
            kernelVectorsBuffer.copyFrom(kernelBuffer);
            rsEdge.set_kernelBuffer(kernelVectorsBuffer);
            rsEdge.set_kernelSquareRadius((newSize - 1) / 2);
            rsEdge.set_totalKernelWeight(Kernels.calculateTotalKernelWeight(kernelBuffer));
            rsEdge.set_totalKernelWeight2N(Kernels.calculateTotalKernelWeight2N(kernelBuffer));
        }
    }

    /**
     * Set amplification of detected edges.
     *
     * @param scale    Factor
     */
    public void setAmplification(float scale) {
        rsEdge.set_scale(scale);
    }

    /**
     * Calculate the 2D (X, Y) edge vectors.
     *
     * @param intensityBuffer    2D float intensity buffer to detect edges on
     * @return 2D (X, Y) edge vectors.
     */
    public Allocation calcEdgeVectors(Allocation intensityBuffer) {
        rsEdge.set_intensityBuffer(intensityBuffer);

        switch (kernelMode) {
            case KernelVector2D:
                rsEdge.forEach_applyVectorKernel(edgeVectorsBuffer);
                break;
            case KernelVector2dSeparable2N:
                rsEdge.forEach_applyVectorKernelPart1(edgeVectorsSeparationStep1Buffer);
                rsEdge.set_step1Buffer(edgeVectorsSeparationStep1Buffer);
                rsEdge.forEach_applyVectorKernelPart2(edgeVectorsBuffer);
                break;
        }

        return edgeVectorsBuffer;
    }

    /**
     * Calculate the edge magnitudes.
     *
     * @param intensityBuffer    2D float intensity buffer to detect edges on
     * @return 1D edge magnitudes
     */
    public Allocation calcEdgeMagnitudes(Allocation intensityBuffer) {
        calcEdgeVectors(intensityBuffer);
        rsUtils.forEach_magnitude2D(edgeVectorsBuffer, edgeMagnitudesBuffer);
        return edgeMagnitudesBuffer;
    }

    /**
     * Calculate the 2D (angle, magnitude) polar vectors of the edges.
     *
     * @param intensityBuffer    2D float intensity buffer to detect edges on
     * @return 2D (angle, magnitude) polar vectors of the edges.
     */
    public Allocation calcEdgePolarVectors(Allocation intensityBuffer) {
        calcEdgeVectors(intensityBuffer);
        rsUtils.forEach_toPolar2D(edgeVectorsBuffer, edgePolarVectorsBuffer);
        return edgePolarVectorsBuffer;
    }

    /**
     * @return  the intermediate edge vectors buffer containing the (x, y) edge vectors
     */
    public Allocation getEdgeVectorsBuffer() {
        return edgeVectorsBuffer;
    }

    public enum KernelMode {
        KernelVector2D,
        KernelVector2dSeparable2N
    }
}
