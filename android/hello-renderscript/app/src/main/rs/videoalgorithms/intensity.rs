#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

// ------------------------------------------------------------------------------------------------
// Apply intensity factor
// ------------------------------------------------------------------------------------------------
float intensityFactor;

uchar4 __attribute__((kernel)) calcGreyscaleIntensity(uchar4 in, uint32_t x, uint32_t y) {
    float4  output = convert_float4(in);
    output = max(0, min(255, output * intensityFactor));
    return convert_uchar4(output);
}
