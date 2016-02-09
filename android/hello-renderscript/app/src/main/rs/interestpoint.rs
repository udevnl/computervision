#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

int sourceWidth;
int sourceHeight;

// Calculate the average amounth of edge in a rectangle
int areaSize;
rs_allocation polarEdgeBuffer;
float2 __attribute__((kernel)) calcInterestPoints(float2 in, int32_t x, int32_t y) {

    float2 resultVector = 0;

    if( (x - areaSize) >= 0 &&
        (y - areaSize) >= 0 &&
        (x + areaSize) < sourceWidth &&
        (y + areaSize) < sourceHeight &&
        in.s1 > .1) {

        int xp, yp;
        float2 edgeVector;

        // Calculate the average weighted angle in the area:
        float2 averageWeightedAngleVector = 0;
        for(int py = -areaSize; py <= areaSize; py++) {
            yp = y + py;
                for(int px = -areaSize; px <= areaSize; px++) {
                xp = x + px;
                edgeVector = rsGetElementAt_float2(polarEdgeBuffer, xp, yp);
                averageWeightedAngleVector.x += edgeVector.s1 * cos(edgeVector.s0);
                averageWeightedAngleVector.y += edgeVector.s1 * sin(edgeVector.s0);
            }
        }
        float averageWeightedAngle = atan2(averageWeightedAngleVector.y, averageWeightedAngleVector.x);


        // Calculate the total weighted angular deviation in the area:
        float angularDeviation;
        float totalEdgeWeight = 0;

        for(int py = -areaSize; py <= areaSize; py++) {
            yp = y + py;
            for(int px = -areaSize; px <= areaSize; px++) {
                xp = x + px;
                edgeVector = rsGetElementAt_float2(polarEdgeBuffer, xp, yp);
                angularDeviation = fabs(M_PI - fabs(fabs(edgeVector.s0 - averageWeightedAngle) - M_PI));
                resultVector.s1 += edgeVector.s1 * angularDeviation;
                totalEdgeWeight += edgeVector.s1;
            }
        }
        resultVector.s0 = resultVector.s1 / totalEdgeWeight;
    }

    return resultVector;
}

// Compare the amounth of edge at the current position
// with the amounth of edge in the direction perpenducal to the edge direction.
// If significant, plot it
rs_allocation interestPointsBuffer;
rs_allocation plotImageBuffer;
float minAngle;
float maxAngle;
void __attribute__((kernel)) plotInterestPoints(float2 in, int32_t x, int32_t y) {

        // Interest point contains the total angular deviation from the average angle
        // s0=normalized to total edge weight,  s1=not normalized
        float2 interestPoint = rsGetElementAt_float2(interestPointsBuffer, x, y);

        // Only mark angles between 68 and 180 degrees
        if(interestPoint.s0 > minAngle && interestPoint.s0 < maxAngle) {
            uchar4 markColor = 255;
            rsSetElementAt_uchar4(plotImageBuffer, markColor, x, y);
        }
}

