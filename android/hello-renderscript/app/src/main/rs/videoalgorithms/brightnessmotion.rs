#pragma version(1)
#pragma rs java_package_name(nl.udev.hellorenderscript.video)

#include "rs_graphics.rsh"

// Global algorithm variables
int blockSizeX;
int blockSizeY;
int blockCount;

// ------------------------------------------------------------------------------------------------
// Calculate the brightness center position and total brightness in a block
// Result vector [4]:
//      x = brightness center X position
//      y = brightness center Y position
//      z = total brightness
// ------------------------------------------------------------------------------------------------
rs_allocation sourceImage; // float

float4 __attribute__((kernel)) calcBrightnessCenterBlocks(uint32_t x, uint32_t y) {

    // Start position in image for this block
    int xs = x * blockSizeX;
    int ys = y * blockSizeY;

    // End position in image for this block
    int xe = xs + blockSizeX;
    int ye = ys + blockSizeY;

    int xPos, yPos;
    float4 result = 0;
    float pixel;

    for(xPos = xs; xPos < xe; xPos++) {
        for(yPos = ys; yPos < ye; yPos++) {
            pixel = rsGetElementAt_float(sourceImage, xPos, yPos);
            result.x += xPos * pixel;
            result.y += yPos * pixel;
            result.z += pixel;
        }
    }

    // Normalize
    result.x = (result.x / result.z) - xs;
    result.y = (result.y / result.z) - ys;

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

    // Apply the magnitude
    previousBlock.x *= previousBlock.z;
    previousBlock.y *= previousBlock.z;
    currentBlock.x *= currentBlock.z;
    currentBlock.y *= currentBlock.z;

    // Determine motion
    motionResult.x = currentBlock.x - previousBlock.x;
    motionResult.y = currentBlock.y - previousBlock.y;

    // Extra amplification
    motionResult.x *= motionAmplification;
    motionResult.y *= motionAmplification;

    motionResult.z = currentBlock.z + fabs(currentBlock.z - previousBlock.z);

    return motionResult;
}


// ------------------------------------------------------------------------------------------------
// Visualize brightness blocks buffer as an overlay
// ------------------------------------------------------------------------------------------------
rs_allocation overlayBrightnessBlocks; // float4
uchar4 __attribute__((kernel)) calcOverlayBrightnessBlocks(uchar4 in, uint32_t x, uint32_t y) {

    // Dim the image by 50% so we can draw the overlay
    uchar4 out = in / 2;

    // Determine the block we are in at this position
    int xBlock = x / blockSizeX;
    int yBlock = y / blockSizeY;

    // Only visualize if the block is valid
    if(xBlock < blockCount && yBlock < blockCount) {

        float4 block = rsGetElementAt_float4(overlayBrightnessBlocks, xBlock, yBlock);

        float xBlockPosition = x % blockSizeX;
        float yBlockPosition = y % blockSizeY;

        // Determine if the current pixel is the brightness center position
        float xDistanceBlockCenter = fabs(xBlockPosition - block.x);
        float yDistanceBlockCenter = fabs(yBlockPosition - block.y);

        if(xDistanceBlockCenter < 2 && yDistanceBlockCenter < 2) {
            if(block.z < 1.0f) {
                out.r = 255;
                out.g = 0;
                out.b = 0;
            } else {
                out = 255;  // Mark the hot spot
            }

        } else if(xDistanceBlockCenter < 1 || yDistanceBlockCenter < 1) {
            out += 64;  // draw cross-hair
        }
    }

    return out;
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

    if(in.z > 1.0f) {
        int xCenter = x * blockSizeX + blockSizeX / 2;
        int yCenter = y * blockSizeY + blockSizeY / 2;
        int xVector = xCenter + in.x;
        int yVector = yCenter + in.y;

        uchar4 color = 0;
        color.b = 255;
        color.a = 255;

        plotLine(xCenter, yCenter, xVector, yVector, color);
    }
}

rs_allocation motionBlocks;
void plotGlobalMotion() {

    float2 globalMotion = 0;
    int usedBlocksCount = 0;

    for(int x = 0; x < blockCount; x++) {
        for(int y = 0; y < blockCount; y++) {
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
