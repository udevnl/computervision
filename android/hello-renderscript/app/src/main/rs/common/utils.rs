#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

// ------------------------------------------------------------------------------------------------
// Clear buffer (set all values to zero)
// ------------------------------------------------------------------------------------------------
uchar4 __attribute__((kernel)) clearUchar4(uint32_t x, uint32_t y) {
    return 0;
}

// ------------------------------------------------------------------------------------------------
// Convert uchar RBG to float intensity (0.0 - 1.0)
// ------------------------------------------------------------------------------------------------
float __attribute__((kernel)) calcGreyscaleIntensity(uchar4 in, uint32_t x, uint32_t y) {
    int color = 0;
    color += in.r;
    color += in.g;
    color += in.b;
    return color / 765.0;
}

// ------------------------------------------------------------------------------------------------
// Convert float intensity (0.0 - 1.0) to uchar RBG
// ------------------------------------------------------------------------------------------------
uchar4 __attribute__((kernel)) calcRgbaIntensity(float in, uint32_t x, uint32_t y) {
    return (int) fmax(0.0f, fmin(255.0f, in * 255.0f));
}

// ------------------------------------------------------------------------------------------------
// Convert xy vectors to polar vectors
// ------------------------------------------------------------------------------------------------
float2 __attribute__((kernel)) toPolar2D(float2 in, uint32_t x, uint32_t y) {

    float2 outVector;
    outVector.s0 = atan2(in.y, in.x);
    outVector.s1 = length(in);
    return outVector;
}

// ------------------------------------------------------------------------------------------------
// Multiply a buffer with a constant
// ------------------------------------------------------------------------------------------------

float multiplyFactor;

float __attribute__((kernel)) multiply(float in, uint32_t x, uint32_t y) {
    return in * multiplyFactor;
}

// ------------------------------------------------------------------------------------------------
// Calculate the magnitude of vectors
// ------------------------------------------------------------------------------------------------

float __attribute__((kernel)) magnitude2D(float2 in, uint32_t x, uint32_t y) {
    return length(in);
}

float __attribute__((kernel)) magnitude3D(float3 in, uint32_t x, uint32_t y) {
    return length(in);
}
