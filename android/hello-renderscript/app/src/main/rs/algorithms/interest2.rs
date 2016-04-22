#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

int sourceWidth;
int sourceHeight;
int areaSize;

rs_allocation sourceEdgeVectorBuffer;
rs_allocation sourcePolarEdgeVectorBuffer;

// In simple words this algorithm detects area's where there are edges with different directions.
// ------------------------------------------------------------------------------------------------
// Given an NxN areasize, where the NxN area contains edges (direction and magnitudes):
//
// This algorithm calculates:
// resultVector.s1: the length of the vector when adding all edge vectors
// resultVector.s0: the ratio that s1 is from the total length of all individual edge vectors
//
// What I want to investigate is if this ratio can be used for detecting interest points.
//
// Because:
// a) if the ratio very small, this means there are a lot of vectors with different directions.
//    these regions are candidate
// b) if the ratio is close to 1 all vectors are in the same direction.
float2 __attribute__((kernel)) calcInterestPoints(float2 in, int32_t x, int32_t y) {

    float2 resultVector = 0;

    if( (x - areaSize) >= 0 &&
        (y - areaSize) >= 0 &&
        (x + areaSize) < sourceWidth &&
        (y + areaSize) < sourceHeight ) {

        int xp, yp;
        float2 sourceEdgeVector;
        float2 sourcePolarEdgeVector;
        float2 netVector = 0;

        // The length of all individual edge vectors total
        float idealLength = 0;

        for(int py = -areaSize; py <= areaSize; py++) {
            yp = y + py;
            for(int px = -areaSize; px <= areaSize; px++) {
                xp = x + px;

                sourcePolarEdgeVector = rsGetElementAt_float2(sourcePolarEdgeVectorBuffer, xp, yp);
                sourceEdgeVector = rsGetElementAt_float2(sourceEdgeVectorBuffer, xp, yp);

                // Add the length of the edge polar vector
                idealLength += sourcePolarEdgeVector.s1;

                // Add the edge vector to our netVector so we can see the net direction in the end
                netVector += sourceEdgeVector;
            }
        }

        if(idealLength > 1.0f) {
            resultVector.s1 = length(netVector);
            resultVector.s0 = resultVector.s1 / idealLength;
        }

    }

    return resultVector;
}

rs_allocation overlaySourceBuffer;
float startFraction;
float endFraction;
float minLength;
uchar4 __attribute__((kernel)) plotInterestPoints(float2 in, int32_t x, int32_t y) {
    uchar4 pixel = rsGetElementAt_uchar4(overlaySourceBuffer, x, y);

    // There is a sweet spot that we are interested in
    if(in.s0 > startFraction && in.s0 < endFraction && in.s1 > minLength) {
        pixel = 255;
    }

    return pixel;
}
