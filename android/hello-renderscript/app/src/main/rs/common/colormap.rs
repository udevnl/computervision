#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript)

uchar4 colormap[256];

uchar4 __attribute__((kernel)) colormapUchar(uchar in) {
    uchar4 out;

    // Scale value's 0..255 to 0..size
    int c = in * 255 / 255;

    out.r = colormap[c].r;
    out.g = colormap[c].g;
    out.b = colormap[c].b;
    out.a = colormap[c].a;

    return out;
}

uchar4 __attribute__((kernel)) colormapFloat(float in) {
    uchar4 out;

    // Scale value's 0..1 to 0..size
    int c = in * 255;

    out.r = colormap[c].r;
    out.g = colormap[c].g;
    out.b = colormap[c].b;
    out.a = colormap[c].a;

    return out;
}