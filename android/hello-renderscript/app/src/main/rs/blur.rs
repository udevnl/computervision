#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript)

uchar4 colormap[256];
float cx;
float cy;
float width;
float height;
int precision;

void root(const uchar4 *in, uchar4 *out, uint32_t x, uint32_t y) {

    float3 pixel = convert_float4(in[0]).rgb;

    float fx=(float)((x/width)*2.f-1.f);
    float fy=(float)((y/height)*2.f-1.f);

    float t=0;

    int k=0;

    while(k < precision) {
        t = fx*fx-fy*fy+cx;
	    fy = 2*fx*fy+cy;
	    fx = t;
	    if (fx*fx+fy*fy >= 4) break;
	    k++;
	}

	int c = k * 255 / precision;

    pixel.r = colormap[c].r;
    pixel.g = colormap[c].g;
    pixel.b = colormap[c].b;

    out->xyz = convert_uchar3(pixel);
}