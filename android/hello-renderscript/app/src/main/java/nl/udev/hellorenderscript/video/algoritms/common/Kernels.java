package nl.udev.hellorenderscript.video.algoritms.common;

import android.util.Log;

/**
 * Utility class for generating kernels.
 *
 * Created by ben on 9-2-16.
 */
public class Kernels {

    private Kernels() {
        // Utility class, no instantiation
    }

    /**
     * Creates a weighted normalized angular vectors kernel buffer.
     *
     * The kernelSize should be an odd number >= 3.
     *
     * The resulting kernel buffer is a (kernelSize x kernelSize) grid.
     *
     * Each grid entry contains four bytes:
     * - vector.x
     * - vector.y
     * - vector.weight
     * - padding
     *
     * The vector.x and vector.y is a normalized vector that indicates the angle from the
     * center of the kernel.
     *
     * @param kernelSize    The desired size so that the kernel becomes a (kernelSize x kernelSize) grid
     * @return              The kernel buffer as a flat array of size (kernelSize x kernelSize x 4)
     */
    public static float[] createWeightedAngularVectorKernel(int kernelSize) {

        float[] kernelBuffer = new float[kernelSize * kernelSize * 4];
        float kernelCenter = (kernelSize - 1) / 2.0f;
        int offset = 0;

        StringBuilder bufferAsString = new StringBuilder(kernelBuffer.length * 5);

        for(int y = 0; y < kernelSize; y++) {
            float yp = y - kernelCenter;
            for(int x = 0; x < kernelSize; x++) {
                float xp = x - kernelCenter;

                double angleAtKernelPosition = Math.atan2(yp, xp);

                float vX = (float) Math.cos(angleAtKernelPosition);
                float vY = (float) Math.sin(angleAtKernelPosition);

                // Calculate the weight so that a circle is formed with a smooth gradient outside.
                float edgeSize = 1.0f;
                float edgeBoundaryRadius = (kernelSize / 2) - edgeSize;
                float currentRadius = (float) Math.sqrt(xp * xp + yp * yp);
                currentRadius += 0.5f; // Add 0.5 because to compensate for the kernel centering

                float vW;
                if(yp == 0 && xp == 0) {
                    // The center of the kernel is 0 because it has no valid angle
                    vW = 0.0f;
                } else if(currentRadius <= edgeBoundaryRadius) {
                    // Inside the circle
                    // Weight should be 1.0
                    vW = 1.0f;
                } else {
                    // Outside circle
                    // Weight should decay to 0.0 linearly depending on the edgeSize

                    float pixelsIntoEdge = currentRadius - edgeBoundaryRadius;

                    // Each pixel into the edge we should decay so that the pixel outside of the edge has weight 0.0.
                    // Examples:
                    //      EdgeSize=1 ==> 0.5, 0.0
                    //      EdgeSize=2 ==> 0.66, 0.33, 0.0
                    //      EdgeSize=3 ==> 0.75, 0.50, 0.25, 0.0
                    float edgeStepSize = 1.0f / (1.0f + edgeSize);

                    // Calculate the weight, but ensure it never goes below 0.0
                    float edgeFactor = 1.0f - pixelsIntoEdge * edgeStepSize;
                    vW = (float) Math.max(0.0, edgeFactor);
                }

                kernelBuffer[offset++] = vX;
                kernelBuffer[offset++] = vY;
                kernelBuffer[offset++] = vW;
                kernelBuffer[offset++] = 0; // Padding

                bufferAsString.append(String.format("(%1.2f, %1.2f, %1.2f) ", vX, vY, vW));
            }

            bufferAsString.append("\n");
        }

        Log.i("Kernels", "Created kernel buffer:\n" + bufferAsString.toString());

        return kernelBuffer;
    }

    /**
     * Calculates the total of all weight values in the given kernel.
     * Can be used to normalize the values after applying a kernel.
     *
     * @param kernelBuffer    Flat array of kernel entries of 4 elements where each 3rd entry is the weight
     * @return                The total kernel weight
     */
    public static float calculateTotalKernelWeight(float[] kernelBuffer) {
        float totalWeight = 0;

        for(int c = 0; c < kernelBuffer.length / 4; c++) {
            totalWeight += kernelBuffer[c * 4 + 2];
        }

        return totalWeight;
    }


    /**
     * Calculates the total of all weight values in the given kernel but takes
     * into account that the kernel will be applied only along the center X and Y axis.
     */
    public static float calculateTotalKernelWeight2N(float[] kernelBuffer) {

        int kernelEntries = kernelBuffer.length / 4;

        // This should be an odd number, 1, 3, 5, etc
        int kernelWidth = (int) Math.round(Math.sqrt(kernelEntries));
        if(kernelWidth%2 != 1) {
            throw new IllegalArgumentException("Unexpected kernel width: " + kernelWidth);
        }

        int widthCenter = kernelWidth / 2;

        float totalWeight = 0;

        for(int d = 0; d < kernelWidth; d++) {

            // Add all X values along the center of the Y axis
            totalWeight += kernelBuffer[(d + (widthCenter * kernelWidth)) * 4 + 2];

            // Add all Y values along the center of the X axis
            totalWeight += kernelBuffer[(widthCenter + (d * kernelWidth)) * 4 + 2];
        }

        return totalWeight;
    }

}
