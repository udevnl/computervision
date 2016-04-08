#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

// ------------------------------------------------------------------------------------------------
// Generic vector kernel algorithm
//
// Uses a 'direction kernel' which contains 2D vectors + weights for each kernel position.
// Kernel.x = Vector X
// Kernel.y = Vector Y
// Kernel.z = weight
//
// When applying the kernel the average gradient vector is obtained by:
// 1. For each kernel position determine a vector:
//          vector(x,y) = intensity(x,y) * kernel2DVector(x,y)
// 2. Sum all vectors for all kernel position to get the average vector.
// ------------------------------------------------------------------------------------------------

// Global variables (always needed)
// ------------------------------------------------------------------------------------------------
int sourceWidth;
int sourceHeight;
rs_allocation kernelBuffer; // float4
rs_allocation intensityBuffer; // float
int kernelSquareRadius;
float scale;
// ------------------------------------------------------------------------------------------------


// ------------------------------------------------------------------------------------------------
// NxN version (most accurate) cost is N^2
// ------------------------------------------------------------------------------------------------
float totalKernelWeight;

float2 __attribute__((kernel)) applyVectorKernel(int32_t x, int32_t y) {

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


// ------------------------------------------------------------------------------------------------
// 2N version (uses kernel separability on X and Y axis from the kernel) cost is 2N
// ------------------------------------------------------------------------------------------------
float totalKernelWeight2N;

float2 __attribute__((kernel)) applyVectorKernelPart1(int32_t x, int32_t y) {

    float2 averageVector = 0;

    int xs = x - kernelSquareRadius;
    int xe = x + kernelSquareRadius;

    if(xs >= 0 && xe < sourceWidth) {

        float4 kernelPositionVector;
        int imageX, kernelX;

        kernelX = 0;
        for(imageX = xs; imageX <= xe; imageX++) {
            kernelPositionVector = rsGetElementAt_float4(kernelBuffer, kernelX, kernelSquareRadius);
            averageVector += kernelPositionVector.xy * rsGetElementAt_float(intensityBuffer, imageX, y) * kernelPositionVector.z;
            kernelX++;
        }
    }

    return averageVector;
}

rs_allocation step1Buffer; // float2
float2 __attribute__((kernel)) applyVectorKernelPart2(int32_t x, int32_t y) {

    float2 averageVector = 0;

    int ys = y - kernelSquareRadius;
    int ye = y + kernelSquareRadius;

    if( ys >= 0 && ye < sourceHeight) {

        float4 kernelPositionVector;
        int imageY, kernelY;

        kernelY = 0;
        for(imageY = ys; imageY <= ye; imageY++) {
            kernelPositionVector = rsGetElementAt_float4(kernelBuffer, kernelSquareRadius, kernelY);
            averageVector += kernelPositionVector.xy * rsGetElementAt_float2(step1Buffer, x, imageY) * kernelPositionVector.z;
            kernelY++;
        }
    }

    return scale * averageVector / totalKernelWeight2N;
}
