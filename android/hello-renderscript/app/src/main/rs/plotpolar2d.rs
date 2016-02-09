#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)


// ------------------------------------------------------------------------------------------------
// Plot polar vectors where angle is color and length is intensity
// ------------------------------------------------------------------------------------------------
rs_allocation plotColoured_angleColors;

uchar4 __attribute__((kernel)) plotColoured(float2 in, uint32_t x, uint32_t y) {

    // Convert angle to degrees [0...359] and get the matching angleColor
    uint angle = (uint) (degrees(in.s0) + 180);
    float4 angleColor = rsGetElementAt_float4(plotColoured_angleColors, angle);

    // Take the clamped intensity and apply this to the angle color
    float intensity = min(1.0f, in.s1);
    angleColor *= intensity;

    return rsPackColorTo8888(angleColor);
}


// ------------------------------------------------------------------------------------------------
// Plot polar vectors where angle is plotted as point at fixed offset
// ------------------------------------------------------------------------------------------------
int plotOffset_offset;
int plotOffset_destinationWidth;
int plotOffset_destinationHeight;
rs_allocation plotOffset_destinationImage;
void __attribute__((kernel)) plotOffset(float2 in, uint32_t x, uint32_t y) {

    if(     x >= plotOffset_offset &&
            y >= plotOffset_offset &&
            x < (plotOffset_destinationWidth - plotOffset_offset) &&
            y < (plotOffset_destinationHeight - plotOffset_offset)
            && in.s1 >= .5 // Edge thresshold
            ) {
        uchar4 markColor = 128 * fmin(1.0f, in.s1 - .5);
        markColor.a = 255;

        // Draw a mark of the edge direction in green
        rsSetElementAt_uchar4(
            plotOffset_destinationImage,
            markColor,
            x + plotOffset_offset * cos(in.s0),
            y + plotOffset_offset * sin(in.s0)
        );
    }
}
