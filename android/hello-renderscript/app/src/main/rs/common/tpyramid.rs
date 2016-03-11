#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

// ============================================================================================
// Compressing the pyramid levels
// ============================================================================================

rs_allocation compressSource1; // float
rs_allocation compressSource2; // float
rs_allocation compressSource3; // float
rs_allocation compressSource4; // float
rs_allocation compressSource5; // float

float __attribute__((kernel)) compress(int32_t x, int32_t y) {

    float out = 0;
    out += rsGetElementAt_float(compressSource1, x, y) * 0.05f;
    out += rsGetElementAt_float(compressSource2, x, y) * 0.25f;
    out += rsGetElementAt_float(compressSource3, x, y) * 0.4f;
    out += rsGetElementAt_float(compressSource4, x, y) * 0.25f;
    out += rsGetElementAt_float(compressSource5, x, y) * 0.05f;
    return out;
}

// ============================================================================================
// Expanding the pyramid levels
// ============================================================================================

rs_allocation expandSource1; // float
rs_allocation expandSource2; // float
rs_allocation expandSource3; // float

float __attribute__((kernel)) expandEven(int32_t x, int32_t y) {

    float out = 0;
    out += rsGetElementAt_float(expandSource1, x, y) * 0.175f;
    out += rsGetElementAt_float(expandSource2, x, y) * 0.65f;
    out += rsGetElementAt_float(expandSource3, x, y) * 0.175f;
    return out;
}

float __attribute__((kernel)) expandOddLeft(int32_t x, int32_t y) {

    float out = 0;
    out += rsGetElementAt_float(expandSource1, x, y) * 0.5f;
    out += rsGetElementAt_float(expandSource2, x, y) * 0.5f;
    return out;
}

float __attribute__((kernel)) expandOddRight(int32_t x, int32_t y) {

    float out = 0;
    out += rsGetElementAt_float(expandSource2, x, y) * 0.5f;
    out += rsGetElementAt_float(expandSource3, x, y) * 0.5f;
    return out;
}

// ============================================================================================
// Transmuting to Laplacian pyramid levels
// ============================================================================================

rs_allocation laplacianLowerLevel; // float

float __attribute__((kernel)) laplacian(float in, int32_t x, int32_t y) {
    return in - rsGetElementAt_float(laplacianLowerLevel, x, y);
}

// ============================================================================================
// Collapsing Laplacian pyramid levels
// ============================================================================================

rs_allocation collapseLevel; // float

float __attribute__((kernel)) collapse(float in, int32_t x, int32_t y) {
    return rsGetElementAt_float(collapseLevel, x, y) + in;
}

// ============================================================================================
// Drawing a pyramid level
// ============================================================================================

uchar4 __attribute__((kernel)) plotPyramidLevel(float in, int32_t x, int32_t y) {
    return max(0, min(255, (int) (255 * in)));
}

uchar4 __attribute__((kernel)) plotPyramidLevelLaplacian(float in, int32_t x, int32_t y) {

    int value = 512 * in;
    uchar4 color = 0;

    if(value < 0) {
        color.r = min(255, -value);
    } else {
        color.g = min(255, value);
    }

    color.a = 255;
    return color;
}