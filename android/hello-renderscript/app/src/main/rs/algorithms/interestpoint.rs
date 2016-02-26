#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

int sourceWidth;
int sourceHeight;

// Only try to find those points where there is are corner
// This means look for the occurance of maximum two angles
//
// The input is a 2D vector where:
// in.s0 = angle
// in.s1 = length
//
// The output is a 2D vector where:
// out.s0 = score
// out.s1 = not used

int areaSize;
float binSizeRadians;
float minEdgeSize;
float maxOutOfBinsFactor;
float maxAngleBetweenBinsRadians;
rs_allocation polarEdgeBuffer;
float2 __attribute__((kernel)) calcInterestPoints(float2 in, int32_t x, int32_t y) {

    float2 resultVector = 0;

    if( (x - areaSize) >= 0 &&
        (y - areaSize) >= 0 &&
        (x + areaSize) < sourceWidth &&
        (y + areaSize) < sourceHeight ) {

        int xp, yp;
        float2 edgeVector;
        float angularDeviation;
        float totalWeightOutsideBins = 0;
        float totalWeightInsideBins = 0;
        float2 bins[2];
        bins[0] = -100;
        bins[1] = -100;

        // Try to put all angles into maximum two bins
        for(int py = -areaSize; py <= areaSize; py++) {
            yp = y + py;
            for(int px = -areaSize; px <= areaSize; px++) {
                xp = x + px;
                edgeVector = rsGetElementAt_float2(polarEdgeBuffer, xp, yp);

                if(edgeVector.s1 > minEdgeSize) {
                    // Try to match the vector to a bin
                    bool matched = false;
                    for(int binNumber = 0; binNumber < 2; binNumber++) {
                        float2 *currentBin = &bins[binNumber];

                        if(currentBin->s0 < -99) {
                            // Bin is empty, we can put the vector in
                            currentBin->s0 = edgeVector.s0;
                            currentBin->s1 = edgeVector.s1;
                            matched = true;
                            break;
                        } else {
                            // Bin exists, we should try to match
                            angularDeviation = fabs(M_PI - fabs(fabs(edgeVector.s0 - currentBin->s0) - M_PI));
                            if(angularDeviation < binSizeRadians) {
                                currentBin->s1 += edgeVector.s1;
                                // Also shift the bins angle smartly
                                // currentBin->s0 -= angularDeviation * (edgeVector.s1 / currentBin->s1);
                                matched = true;
                                break;
                            }
                        }
                    }
                    // We must have a match on at least on bin or the number of edges is wrong.
                    if(!matched) {
                        totalWeightOutsideBins += edgeVector.s1;
                    } else {
                        totalWeightInsideBins += edgeVector.s1;
                    }
                }

                if(totalWeightOutsideBins > (totalWeightInsideBins * maxOutOfBinsFactor)) {
                    return 0;
                }
            }
        }

        // Check if all bins are filled
        int filledBins = 0;
        float totalBinWeight = 0;
        for(int binNumber = 0; binNumber < 2; binNumber++) {
            if(bins[binNumber].s1 >= 0) {
                filledBins++;
                totalBinWeight += bins[binNumber].s1;
            }
        }

        if(filledBins > 1) {
            // Check if the angle between the filled bins does not exeed 120 degrees
            angularDeviation = fabs(M_PI - fabs(fabs(bins[0].s0 - bins[1].s0) - M_PI));
            if(angularDeviation < maxAngleBetweenBinsRadians) {
                resultVector.s0 = 1.0;
            }
        }
    }

    return resultVector;
}

rs_allocation plotImageBuffer;
void __attribute__((kernel)) plotInterestPoints(float2 in, int32_t x, int32_t y) {
    if(in.s0 > 0) {
        uchar4 markColor = 255;
        rsSetElementAt_uchar4(plotImageBuffer, markColor, x, y);
    }
}

