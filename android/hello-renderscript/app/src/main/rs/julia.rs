#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript)

float cx;
float cy;
float width;
float height;
int precision;

uchar __attribute__((kernel)) julie(uint32_t x, uint32_t y) {

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

	return k * 255 / precision;
}