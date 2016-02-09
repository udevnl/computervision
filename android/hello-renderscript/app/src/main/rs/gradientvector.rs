#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

// ------------------------------------------------------------------------------------------------
// Calculate the local average gradient vector.
// The below algoritm uses a 'direction kernel' which contains 2D vectors for each position.
// The average gradient vector is obtained by:
// 1. For each kernel position determine a vector:
//          vector(x,y) = intensity(x,y) * kernel2DVector(x,y)
// 2. Sum all vectors for all kernel position to get the average vector.
// ------------------------------------------------------------------------------------------------

int sourceWidth;
int sourceHeight;
float scale;

rs_allocation kernelBuffer; // float4
int kernelSquareRadius;
float totalKernelWeight;

rs_allocation intensityBuffer; // float

float2 __attribute__((kernel)) calcGradientVectors(int32_t x, int32_t y) {

    float2 averageVector = 0;

    int xs = x - kernelSquareRadius;
    int xe = x + kernelSquareRadius;
    int ys = y - kernelSquareRadius;
    int ye = y + kernelSquareRadius;

    if( xs >= 0 && ys >= 0 && xe < sourceWidth && ye < sourceHeight) {

        float4 kernelPositionVector;
        int imageX, imageY, kernelX, kernelY;

        kernelY = 0;
        for(imageY = ys; imageY <= ye; imageY++) {
            kernelX = 0;
            for(imageX = xs; imageX <= xe; imageX++) {

                // Get the kernel vector at this position
                kernelPositionVector = rsGetElementAt_float4(kernelBuffer, kernelX, kernelY);

                // Apply the kernel vector to the image at this position
                // kernel.xy    - Contains the xy vector at the kernel position
                // kernel.z     - Contains the weight
                averageVector += kernelPositionVector.xy * rsGetElementAt_float(intensityBuffer, imageX, imageY) * kernelPositionVector.z;

                kernelX++;
            }
            kernelY++;
        }
    }

    return scale * averageVector / totalKernelWeight;
}

