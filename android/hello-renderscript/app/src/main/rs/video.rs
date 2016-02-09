#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

rs_allocation shiftIn;
rs_allocation shiftOut;
rs_allocation sumBuffer;

int4 __attribute__((kernel)) updateSums(uint32_t x, uint32_t y) {

    int4 sumPixel = rsGetElementAt_int4(sumBuffer, x, y);

    sumPixel += convert_int4(rsGetElementAt_ushort4(shiftIn, x, y));
    sumPixel -= convert_int4(rsGetElementAt_ushort4(shiftOut, x, y));

    return sumPixel;
}

int avgSize;
int avgWidth;
int avgHeight;
rs_allocation avgIn;

ushort4 __attribute__((kernel)) avgMe(uint32_t x, uint32_t y) {

    ushort4 out = 0;
    int xp, yp;

    for(int px = -2; px <= 2; px++) {
        xp = x + px;
        if(xp >= 0 && xp < avgWidth) {
            for(int py = -2; py <= 2; py++) {
                yp = y + py;
                if(yp >= 0 && yp < avgHeight) {
                    out += convert_ushort4(rsGetElementAt_uchar4(avgIn, xp, yp));
                }
            }
        }
    }

    return out;
}

rs_allocation sumIn;
rs_allocation sumOut;
int sumInSize;
int sumOutSize;
int amplification;

uchar4 __attribute__((kernel)) deltaSums(uint32_t x, uint32_t y) {

    // Multiply so they are in the same domain
    int4 inSum = rsGetElementAt_int4(sumIn, x, y) * sumOutSize;
    int4 outSum = rsGetElementAt_int4(sumOut, x, y) * sumInSize;

    int factor = sumInSize * sumOutSize * 50;

    inSum -= outSum;
    inSum *= amplification;
    inSum /= factor;
    inSum += 128;
    inSum.a = 255;

    return convert_uchar4(clamp(inSum, 0, 255));
}

uchar4 __attribute__((kernel)) multiplySums(uchar4 in, uint32_t x, uint32_t y) {
    // Multiply so they are in the same domain
    int4 inSum = rsGetElementAt_int4(sumIn, x, y) * sumOutSize;
    int4 outSum = rsGetElementAt_int4(sumOut, x, y) * sumInSize;

    int factor = sumInSize * sumOutSize * 50;

    inSum -= outSum;
    inSum *= amplification;
    inSum /= factor;

    // The value to multiply by between 0-255
    int absVal = abs((inSum.r + inSum.g + inSum.b) / 3);
    int multiplyFactor = clamp(absVal, 0, 255);

    // Now multiply the output
    return convert_uchar4(convert_int4(in) * multiplyFactor / 255);
}


ushort4 __attribute__((kernel)) clearBufferUshort4(uint32_t x, uint32_t y) {
    return 0;
}

int4 __attribute__((kernel)) clearBufferInt4(uint32_t x, uint32_t y) {
    return 0;
}


uchar4 __attribute__((kernel)) clearBufferUchar4(uint32_t x, uint32_t y) {
    return 0;
}