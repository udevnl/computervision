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
int kernelWidth;
int kernelHeight;
int sourceWidth;
int sourceHeight;
rs_allocation kernelBuffer;
rs_allocation videoBuffer;

float2 __attribute__((kernel)) calcVectors(uint32_t x, uint32_t y) {

    float2 averageVector = 0;
    float2 localVector;
    float4 kernelPositionVector;
    int xp, yp;
    float4 color;
    float intensity;

    for(int px = 0; px < kernelWidth; px++) {
        xp = x + px;
        if(xp < sourceWidth) {
            for(int py = 0; py < kernelHeight; py++) {
                yp = y + py;
                if(yp < sourceHeight) {
                    color = rsUnpackColor8888(rsGetElementAt_uchar4(videoBuffer, xp, yp));
                    kernelPositionVector = rsGetElementAt_float4(kernelBuffer, px, py);
                    intensity = (color.r + color.g + color.b) / 3.0f;
                    localVector.x = kernelPositionVector.x * intensity;
                    localVector.y = kernelPositionVector.y * intensity;
                    averageVector += localVector;
                }
            }
        }
    }

    return averageVector / (kernelWidth * kernelWidth);
}

float2 __attribute__((kernel)) calcVectorsCircular(uint32_t x, uint32_t y) {

    float2 averageVector = 0;
    float2 localVector;
    float4 kernelPositionVector;
    int xp, yp;
    float4 color;
    float intensity;

    for(int px = 0; px < kernelWidth; px++) {
        xp = x + px;
        if(xp < sourceWidth) {
            for(int py = 0; py < kernelHeight; py++) {
                yp = y + py;
                if(yp < sourceHeight) {
                    color = rsUnpackColor8888(rsGetElementAt_uchar4(videoBuffer, xp, yp));
                    kernelPositionVector = rsGetElementAt_float4(kernelBuffer, px, py);
                    if(kernelPositionVector.z > 0.0) {
                        intensity = (color.r + color.g + color.b) / 3.0f;
                        intensity *= kernelPositionVector.z;
                        localVector.x = kernelPositionVector.x * intensity;
                        localVector.y = kernelPositionVector.y * intensity;
                        averageVector += localVector;
                    }
                }
            }
        }
    }

    return averageVector / (kernelWidth * kernelWidth);
}



// ------------------------------------------------------------------------------------------------
// Convert the given vectors to colors based on their angle and intensity based on their length.
// ------------------------------------------------------------------------------------------------
float scale;
rs_allocation angleColors;

uchar4 __attribute__((kernel)) plotAngles(float2 in, uint32_t x, uint32_t y) {

    // Calculate the angle in whole degrees and get the matching color
    uint angle = (uint) (native_atan2pi(in.y, in.x) * 180.0f + 180.0f);
    float4 angleColor = rsGetElementAt_float4(angleColors, angle);

    // Take the clamped intensity and apply this to the angle color
    float intensity = min(1.0f, length(in) * scale);
    angleColor *= intensity;

    return rsPackColorTo8888(angleColor);
}