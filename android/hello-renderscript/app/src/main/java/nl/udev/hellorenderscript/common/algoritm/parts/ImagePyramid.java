package nl.udev.hellorenderscript.common.algoritm.parts;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import nl.udev.hellorenderscript.video.ScriptC_pyramid;

/**
 * The image pyramid consists of stacked image layers.
 *
 * Each image layer is 50% smaller then the layer below.
 * The lowest layer of the pyramid is the original image.
 *
 * Example of a three level pyramid of a 352x288 image:
 *
 * Level 2     XX        88x72
 * Level 1    XXXX      176x144
 * Level 0  XXXXXXXX    352x288
 *
 * COMPRESSION:
 * To generate different levels, each time a lower level is sampled using a Gaussian kernel.
 *
 * EXPANSION:
 * A level can also be expanded to the next lower level, also using a Gaussian kernel.
 *
 * KERNEL:
 * The sampling kernels ensure that each pixel contributes the same weight in the next level.
 *
 *
 * LAPLACIAN pyramid
 * This pyramid also supports generating the laplacian pyramid for each level.
 *
 *
 * Created by ben on 11-3-16.
 */
public class ImagePyramid {

    private static final String TAG = "ImagePyramid";
    private final RenderScript rs;
    private final ScriptC_pyramid rsPyramid;

    private final List<Level> levels = new ArrayList<>();
    private int width;
    private int height;
    private int actualLevelCount;


    public ImagePyramid(RenderScript rs, int width, int height, int levelCount) {
        this.rs = rs;
        this.rsPyramid = new ScriptC_pyramid(rs);

        resizePyramid(width, height, levelCount);
    }

    public void resizePyramid(int width, int height, int levelCount) {
        destroyPyramidLevelBuffers();

        this.width = width;
        this.height = height;
        actualLevelCount = createPyramidLevelBuffers(levelCount);

    }

    public int getActualLevelCount() {
        return actualLevelCount;
    }

    public Level getLevel(int levelNumer) {
        return levels.get(levelNumer);
    }

    public void plotLevel(int levelNumber,
                          PlotType plotType,
                          Allocation rgbaDestination,
                          int destinationWidth,
                          int destinationHeight) {

        Level plotLevel = levels.get(levelNumber);

        rsPyramid.set_plotWidth(destinationWidth);
        rsPyramid.set_plotHeight(destinationHeight);

        switch (plotType) {
            case LAPLACIAN:
                rsPyramid.set_pyramidImage(plotLevel.levelLaplacianBuffer);
                rsPyramid.set_pyramidWidth(plotLevel.width);
                rsPyramid.set_pyramidHeight(plotLevel.height);
                rsPyramid.forEach_plotPyramidLevelLaplacian(rgbaDestination);
                break;
            case LEVEL:
                rsPyramid.set_pyramidImage(plotLevel.levelGaussianBuffer);
                rsPyramid.set_pyramidWidth(plotLevel.width);
                rsPyramid.set_pyramidHeight(plotLevel.height);
                rsPyramid.forEach_plotPyramidLevel(rgbaDestination);
                break;
            case LEVEL_EXPANDED:
                if(plotLevel.expandedBuffer != null) {
                    rsPyramid.set_pyramidImage(plotLevel.expandedBuffer);
                    rsPyramid.set_pyramidWidth(plotLevel.width * 2);
                    rsPyramid.set_pyramidHeight(plotLevel.height * 2);
                    rsPyramid.forEach_plotPyramidLevel(rgbaDestination);
                }
                break;
        }
    }

    /**
     * Calculates:
     * 1. Higher levels using COMPRESS
     * 2. EXPANDED version of levels
     * 3. Laplacian (delta of level and expanded higher level)
     */
    public void calculate(Allocation sourceIntensityBuffer) {

        // Set level0 to the original image
        Level level0 = levels.get(0);
        level0.setLevelGaussianBuffer(sourceIntensityBuffer);

        // Calculate the smaller levels
        for(int level = 1; level < levels.size(); level++) {

            // Set the source to compress
            rsPyramid.set_compressSource(levels.get(level - 1).getLevelGaussianBuffer());

            // Set destination to calculate now
            Level currentLevel = levels.get(level);

            // COMPRESS
            rsPyramid.set_compressTargetWidth(currentLevel.width);
            rsPyramid.set_compressTargetHeight(currentLevel.height);
            rsPyramid.forEach_compressStep1(currentLevel.scratchBuffer);
            rsPyramid.set_compressSource(currentLevel.scratchBuffer);
            rsPyramid.forEach_compressStep2(currentLevel.levelGaussianBuffer);

            // EXPAND
            rsPyramid.set_expandTargetWidth(currentLevel.width * 2);
            rsPyramid.set_expandTargetHeight(currentLevel.height * 2);
            rsPyramid.set_expandSource(currentLevel.levelGaussianBuffer);
            rsPyramid.forEach_expandStep1(currentLevel.scratchBuffer);
            rsPyramid.set_expandSource(currentLevel.scratchBuffer);
            rsPyramid.forEach_expandStep2(currentLevel.expandedBuffer);
        }

        // LAPLACIANS

        // Lowest level is simply the gaussian buffer
        int lowestLevelIndex = levels.size() - 1;
        Level lowestLevel = levels.get(lowestLevelIndex);
        lowestLevel.levelLaplacianBuffer.copyFrom(lowestLevel.levelGaussianBuffer);

        // Other levels stack, working from smallest to biggest
        for(int level = lowestLevelIndex - 1; level >= 0; level--) {
            Level targetLevel = levels.get(level);
            Level smallerLevel = levels.get(level + 1);
            rsPyramid.set_laplacianLowerLevel(smallerLevel.expandedBuffer);
            rsPyramid.forEach_laplacian(targetLevel.levelGaussianBuffer, targetLevel.levelLaplacianBuffer);
        }
    }

    /**
     * Compute the reconstructed image by collapsing the laplacians...
     *
     * NOTE: this will overwrite the calculate pyramids gaussian buffers!
     */
    public void collapseLaplacian() {

        // Lowest level
        int lowestLevelIndex = levels.size() - 1;
        Level lowestLevel = levels.get(lowestLevelIndex);
        lowestLevel.levelGaussianBuffer.copyFrom(lowestLevel.levelLaplacianBuffer);

        // Compute all levels by expanding the combined laplacian levels
        // Levels 0 1 2
        // G2 = L2
        // G1 = ex(G2) + L1
        // G0 = ex(G1) + L0

        for(int level = lowestLevelIndex - 1; level >= 0; level--) {

            // EXPAND the buffer of the previous level (this is the overwritten gaussian buffer)
            Level smallerLevel = levels.get(level + 1);
            rsPyramid.set_expandTargetWidth(smallerLevel.width * 2);
            rsPyramid.set_expandTargetHeight(smallerLevel.height * 2);
            rsPyramid.set_expandSource(smallerLevel.levelGaussianBuffer);
            rsPyramid.forEach_expandStep1(smallerLevel.scratchBuffer);
            rsPyramid.set_expandSource(smallerLevel.scratchBuffer);
            rsPyramid.forEach_expandStep2(smallerLevel.expandedBuffer);

            // Now add the laplacian of the current level to the expanded previous level
            Level targetLevel = levels.get(level);
            rsPyramid.set_collapseLevel(smallerLevel.expandedBuffer);
            rsPyramid.forEach_collapse(targetLevel.levelLaplacianBuffer, targetLevel.levelGaussianBuffer);
        }
    }

    /**
     * Clean up the pyramid.
     *
     * After this call completes the pyramic should not be used anymore!
     */
    public void destroy() {
        destroyPyramidLevelBuffers();

        rsPyramid.destroy();
    }

    private int
    createPyramidLevelBuffers(int desiredLevelCount) {
        // Try to build a pyramid with the given number of levels.
        // Note that this will only succeed if the resolution divides in whole numbers all the way.

        // Insert level 0...
        levels.add(new Level(0, width, height, null, RsUtils.create2d(rs, width, height, Element.F32(rs)), null, null));

        int levelCount = 0;
        int levelWidth = width;
        int levelHeight = height;
        for(int levelNumber = 1; levelNumber <= desiredLevelCount; levelNumber++) {

            // Only create the level if it can be 50% size and stay integer
            if(levelWidth % 2 == 0 && levelHeight % 2 == 0) {
                levelWidth /= 2;
                levelHeight /= 2;
                levels.add(
                        new Level(
                                levelNumber,
                                levelWidth,
                                levelHeight,
                                RsUtils.create2d(rs, levelWidth, levelHeight, Element.F32(rs)),
                                RsUtils.create2d(rs, levelWidth, levelHeight, Element.F32(rs)),
                                RsUtils.create2d(rs, levelWidth * 2, levelHeight * 2, Element.F32(rs)),
                                RsUtils.create2d(rs, levelWidth, levelHeight * 2, Element.F32(rs))
                        )
                );

                Log.d(TAG, "Created level " + levelNumber + ", size " + levelWidth + "x" + levelHeight + ".");
                levelCount++;
            } else {
                // Unable to create all pyramids
                Log.w(TAG, "Cannot finish pyramid at level " + levelNumber + ", size now " + levelWidth + "x" + levelHeight + ".");
                break;
            }
        }

        return levelCount;
    }

    private void destroyPyramidLevelBuffers() {
        for(Level level : levels) {
            // Only destroy the buffers of the sub-levels as level 0 is special
            if(level.level == 0) {
                level.levelLaplacianBuffer.destroy();
            } else {
                level.levelGaussianBuffer.destroy();
                level.levelLaplacianBuffer.destroy();
                level.expandedBuffer.destroy();
                level.scratchBuffer.destroy();
            }
        }
        levels.clear();
    }

    public static class Level {

        final int level;
        final int width;
        final int height;
        Allocation levelGaussianBuffer;
        final Allocation levelLaplacianBuffer;
        final Allocation expandedBuffer;
        final Allocation scratchBuffer;

        private Level(int level,
                     int width,
                     int height,
                     Allocation levelGaussianBuffer,
                     Allocation levelLaplacianBuffer, Allocation expandedBuffer,
                     Allocation scratchBuffer) {
            this.level = level;
            this.width = width;
            this.height = height;
            this.levelGaussianBuffer = levelGaussianBuffer;
            this.levelLaplacianBuffer = levelLaplacianBuffer;
            this.expandedBuffer = expandedBuffer;
            this.scratchBuffer = scratchBuffer;
        }

        public void setLevelGaussianBuffer(Allocation levelGaussianBuffer) {
            this.levelGaussianBuffer = levelGaussianBuffer;
        }

        public int getLevel() {
            return level;
        }

        public int getWidth() {
            return width;
        }

        public int getHeight() {
            return height;
        }

        public Allocation getLevelGaussianBuffer() {
            return levelGaussianBuffer;
        }

        public Allocation getLevelLaplacianBuffer() {
            return levelLaplacianBuffer;
        }

        public Allocation getExpandedBuffer() {
            return expandedBuffer;
        }
    }

    public enum PlotType {
        LEVEL,
        LEVEL_EXPANDED,
        LAPLACIAN
    }
}
