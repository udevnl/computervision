#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)


// ------------------------------------------------------------------------------------------------
// Plot polar vectors where angle is color and length is intensity
// ------------------------------------------------------------------------------------------------
rs_allocation angleColormap;

uchar4 __attribute__((kernel)) plotPolar2dColormap(float2 in, uint32_t x, uint32_t y) {

    // Convert angle to degrees [0...359] and get the matching angleColor
    uint angle = (uint) (degrees(in.s0) + 180);
    float4 angleColor = rsGetElementAt_float4(angleColormap, angle);

    // Take the clamped intensity and apply this to the angle color
    float intensity = min(1.0f, in.s1);
    angleColor *= intensity;

    return rsPackColorTo8888(angleColor);
}


// ------------------------------------------------------------------------------------------------
// Plot pixels values to 8 bit colormap index
// ------------------------------------------------------------------------------------------------
rs_allocation eightBitColormap;

uchar4 __attribute__((kernel)) plotUchar8BitColormap(uchar in) {
    return rsPackColorTo8888(rsGetElementAt_float4(eightBitColormap, in));
}

uchar4 __attribute__((kernel)) plotNormalizedFloat8BitColormap(float in) {
    return rsPackColorTo8888(rsGetElementAt_float4(eightBitColormap, (int)(in * 255.0f)));
}


// ------------------------------------------------------------------------------------------------
// Plot dots at the given coordinates (positions are float and normalized)
// ------------------------------------------------------------------------------------------------

void plotDots(rs_allocation points, int pointCount, uchar4 color, rs_allocation image, int width, int height) {
    float2 point;
    int2 pos;
    for(int i = 0; i < pointCount; i++) {
        point = rsGetElementAt_float2(points, i);
        if(point.x >= -1 && point.y >= -1 && point.x < 1.0 && point.y < 1.0) {
            pos.x = ((point.x + 1.0) * width) / 2.0;
            pos.y = ((point.y + 1.0) * height) / 2.0;
            rsSetElementAt_uchar4(image, color, pos.x, pos.y);
        }
    }
}


// ------------------------------------------------------------------------------------------------
// Plot polar vectors where angle is plotted as point at fixed offset
// ------------------------------------------------------------------------------------------------
// int plotOffset_offset;
// int plotOffset_destinationWidth;
// int plotOffset_destinationHeight;
// rs_allocation plotOffset_destinationImage;
// void __attribute__((kernel)) plotOffset(float2 in, uint32_t x, uint32_t y) {
//
//     if(     x >= plotOffset_offset &&
//             y >= plotOffset_offset &&
//             x < (plotOffset_destinationWidth - plotOffset_offset) &&
//             y < (plotOffset_destinationHeight - plotOffset_offset)
//             && in.s1 >= .5 // Edge thresshold
//             ) {
//         uchar4 markColor = 128 * fmin(1.0f, in.s1 - .5);
//         markColor.a = 255;
//
//         // Draw a mark of the edge direction in green
//         rsSetElementAt_uchar4(
//             plotOffset_destinationImage,
//             markColor,
//             x + plotOffset_offset * cos(in.s0),
//             y + plotOffset_offset * sin(in.s0)
//         );
//     }
// }

