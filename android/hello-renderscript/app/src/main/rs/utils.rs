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
// Convert xy vectors to polar vectors
// ------------------------------------------------------------------------------------------------
float2 __attribute__((kernel)) toPolar2D(float2 in, uint32_t x, uint32_t y) {

    float2 outVector;
    outVector.s0 = atan2(in.y, in.x);
    outVector.s1 = length(in);
    return outVector;
}
