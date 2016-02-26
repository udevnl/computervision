#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

// ============================================================================================
// Compressing the pyramid levels
// ============================================================================================

int compressTargetWidth;
int compressTargetHeight;
rs_allocation compressSource; // float

// Step 1: compress along X direction
float __attribute__((kernel)) compressStep1(int32_t x, int32_t y) {

    float out = 0;

    if(x > 1 && x < (compressTargetWidth - 2)) {
        int xp = x * 2;
        out += rsGetElementAt_float(compressSource, xp - 2, y) * 0.05f;
        out += rsGetElementAt_float(compressSource, xp - 1, y) * 0.25f;
        out += rsGetElementAt_float(compressSource, xp, y) * 0.4f;
        out += rsGetElementAt_float(compressSource, xp + 1, y) * 0.25f;
        out += rsGetElementAt_float(compressSource, xp + 2, y) * 0.05f;
    }

    return out;
}

// Step 2: compress along Y direction
float __attribute__((kernel)) compressStep2(int32_t x, int32_t y) {

    float out = 0;

    if(y > 1 && y < (compressTargetHeight - 2)) {
        int yp = y * 2;
        out += rsGetElementAt_float(compressSource, x, yp - 2) * 0.05f;
        out += rsGetElementAt_float(compressSource, x, yp - 1) * 0.25f;
        out += rsGetElementAt_float(compressSource, x, yp) * 0.4f;
        out += rsGetElementAt_float(compressSource, x, yp + 1) * 0.25f;
        out += rsGetElementAt_float(compressSource, x, yp + 2) * 0.05f;
    }

    return out;
}

// ============================================================================================
// Expanding the pyramid levels
// ============================================================================================

int expandTargetWidth;
int expandTargetHeight;
rs_allocation expandSource; // float

// Step 1: expand the Y direction
float __attribute__((kernel)) expandStep1(int32_t x, int32_t y) {

    float out = 0;

    if(y > 1 && y < (expandTargetHeight - 2)) {
        int yp = y / 2;

        if(yp * 2 == y) {
            // Even number, we are in-line with the source
            out += rsGetElementAt_float(expandSource, x, yp - 1) * 0.175f;
            out += rsGetElementAt_float(expandSource, x, yp) * 0.65f;
            out += rsGetElementAt_float(expandSource, x, yp + 1) * 0.175f;

        } else {
            // Odd number, we are in-between the source
            out += rsGetElementAt_float(expandSource, x, yp) * 0.5f;
            out += rsGetElementAt_float(expandSource, x, yp + 1) * 0.5f;
        }
    }

    return out;
}

// Step 1: expand the X direction
float __attribute__((kernel)) expandStep2(int32_t x, int32_t y) {

    float out = 0;

    if(x > 1 && x < (expandTargetWidth - 2)) {
        int xp = x / 2;

        if(xp * 2 == x) {
            // Even number, we are in-line with the source
            out += rsGetElementAt_float(expandSource, xp - 1, y) * 0.175f;
            out += rsGetElementAt_float(expandSource, xp, y) * 0.65f;
            out += rsGetElementAt_float(expandSource, xp + 1, y) * 0.175f;

        } else {
            // Odd number, we are in-between the source
            out += rsGetElementAt_float(expandSource, xp, y) * 0.5f;
            out += rsGetElementAt_float(expandSource, xp + 1, y) * 0.5f;
        }
    }

    return out;
}

// ============================================================================================
// Transmuting to Laplacian pyramid levels
// ============================================================================================

rs_allocation laplacianLowerLevel; // float

float __attribute__((kernel)) laplacian(float in, int32_t x, int32_t y) {
    return rsGetElementAt_float(laplacianLowerLevel, x, y) - in;
}

// ============================================================================================
// Drawing a pyramid level
// ============================================================================================

int pyramidWidth;
int pyramidHeight;
int plotWidth;
int plotHeight;
rs_allocation pyramidImage; // float

uchar4 __attribute__((kernel)) plotPyramidLevel(int32_t x, int32_t y) {

    int xp = x * pyramidWidth / plotWidth;
    int yp = y * pyramidHeight / plotHeight;

    int value = (int) 128 + (128 * rsGetElementAt_float(pyramidImage, xp, yp));

    return min(255, value);
}