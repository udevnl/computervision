#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

#include "rs_graphics.rsh"

// Global algorithm variables
int blockSize;
int blockCountX;
int blockCountY;

// ------------------------------------------------------------------------------------------------
// Calculate the gradient block and total weight
// Result vector [4]:
//      xy = gradient direction
//      z = total weight
// ------------------------------------------------------------------------------------------------
rs_allocation sourceImage; // float
rs_allocation kernelVectorBuffer; // float4
float totalKernelWeight;

float4 __attribute__((kernel)) calcGradientBlocks(uint32_t x, uint32_t y) {

    // Start position in image for this block
    int xs = x * blockSize;
    int ys = y * blockSize;

    // End position in image for this block
    int xe = xs + blockSize;
    int ye = ys + blockSize;

    int xPos, yPos, xKernel, yKernel;
    float4 result = 0;
    float4 kernelVector;
    float pixel;

    xKernel = 0;
    for(xPos = xs; xPos < xe; xPos++) {
        yKernel = 0;
        for(yPos = ys; yPos < ye; yPos++) {

            kernelVector = rsGetElementAt_float4(kernelVectorBuffer, xKernel, yKernel);
            pixel = rsGetElementAt_float(sourceImage, xPos, yPos);

            // Apply the kernel vector to the image at this position
            // kernel.xy    - Contains the xy vector at the kernel position
            // kernel.z     - Contains the weight
            result.xy += kernelVector.xy * pixel * kernelVector.z;
            result.z += pixel * kernelVector.z;

            yKernel++;
        }
        xKernel++;
    }

    // Normalize
    result.xy /= totalKernelWeight;
    result.z /= totalKernelWeight;

    return result;
}

// ------------------------------------------------------------------------------------------------
// Calculate the motion based on the current and previous brightness centers
// Result vector [4]:
//      x = motion X direction
//      y = motion Y direction
//      z = motion validity?
// ------------------------------------------------------------------------------------------------
rs_allocation currentBrightnessCenterBlocks; // float4
rs_allocation previousBrightnessCenterBlocks; // float4
float motionAmplification;

float4 __attribute__((kernel)) calcMotionBlocks(uint32_t x, uint32_t y) {
    float4 motionResult = 0;

    float4 previousBlock = rsGetElementAt_float4(previousBrightnessCenterBlocks, x, y);
    float4 currentBlock = rsGetElementAt_float4(currentBrightnessCenterBlocks, x, y);

    // delta weight
    float deltaWeight = currentBlock.z - previousBlock.z;

    // Extra amplification
    deltaWeight *= motionAmplification;

    // Determine motion
    float2 averageGradientDirection = previousBlock.xy;
    if(fabs(averageGradientDirection.x) > 0 && fabs(averageGradientDirection.y) > 0) {
        motionResult.xy = deltaWeight / averageGradientDirection;
        if(length(motionResult.xy) > 10) {
            motionResult.xy = 0;
        }
    }

    return motionResult;
}


// ------------------------------------------------------------------------------------------------
// Visualize motion blocks buffer as an overlay
// Draws lines in the direction of the motion
// ------------------------------------------------------------------------------------------------
rs_allocation plotDestination; // uchar4
int plotWidth;
int plotHeight;

void myPixel(int x, int y, uchar4 color) {
    if(x >= 0 && x < plotWidth && y >=0 && y < plotHeight) {
        rsSetElementAt_uchar4(plotDestination, color, x, y);
    }
}

void plotLine(int x, int y, int x2, int y2, uchar4 color) {
	bool yLonger=false;
	int incrementVal;
	int shortLen=y2-y;
	int longLen=x2-x;

	if (abs(shortLen)>abs(longLen)) {
		int swap=shortLen;
		shortLen=longLen;
		longLen=swap;
		yLonger=true;
	}

	if (longLen<0) incrementVal=-1;
	else incrementVal=1;

	double multDiff;
	if (longLen==0.0) multDiff=(float)shortLen;
	else multDiff=(float)shortLen/(float)longLen;
	if (yLonger) {
		for (int i=0;i!=longLen;i+=incrementVal) {
			myPixel(x+(int)((float)i*multDiff),y+i,color);
		}
	} else {
		for (int i=0;i!=longLen;i+=incrementVal) {
			myPixel(x+i,y+(int)((float)i*multDiff),color);
		}
	}
}

void __attribute__((kernel)) calcOverlayMotionBlocks(float4 in, uint32_t x, uint32_t y) {

    int xCenter = x * blockSize + blockSize / 2;
    int yCenter = y * blockSize + blockSize / 2;
    int xVector = xCenter + in.x;
    int yVector = yCenter + in.y;

    uchar4 color = 0;
    color.b = 255;
    color.a = 255;

    plotLine(xCenter, yCenter, xVector, yVector, color);
}

rs_allocation motionBlocks;
void plotGlobalMotion() {

    float2 globalMotion = 0;
    int usedBlocksCount = 0;

    for(int x = 0; x < blockCountX; x++) {
        for(int y = 0; y < blockCountY; y++) {
            float4 block = rsGetElementAt_float4(motionBlocks, x, y);
            if(block.z > 1.0f) {
                globalMotion.x += block.x;
                globalMotion.y += block.y;
                usedBlocksCount++;
            }
        }
    }

    if(usedBlocksCount > 0) {
        globalMotion /= usedBlocksCount;

        int xCenter = plotWidth / 2;
        int yCenter = plotHeight / 2;
        int xVector = xCenter + globalMotion.x * 20.0f;
        int yVector = yCenter + globalMotion.y * 20.0f;

        uchar4 color = 0;
        color.g = 255;
        color.a = 255;
        plotLine(xCenter, yCenter, xVector, yVector, color);
    }
}
