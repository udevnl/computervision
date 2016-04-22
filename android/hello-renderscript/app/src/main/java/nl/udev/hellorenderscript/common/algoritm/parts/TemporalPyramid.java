package nl.udev.hellorenderscript.common.algoritm.parts;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import nl.udev.hellorenderscript.video.ScriptC_tpyramid;

import static nl.udev.hellorenderscript.common.algoritm.parts.RsUtils.create2d;

/**
 * A special kind of pyramid for temporal image processing.
 *
 * Just like a regular pyramid which is stacked by size, this pyramid is stacked by time:
 *
 * XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX( L )XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX Level 0 (93 frames)
 *   X X X X X X X X X X X X X X X X X X X X X XE(E+L)EX X X X X X X X X X X X X X X X X X X X   Level 1 (45 frames)
 *       X   X   X   X   X   X   X   X   X   X E (E+L) E X   X   X   X   X   X   X   X   X       Level 2 (21 frames)
 *               X       X       X       X   E   (E+L)   E   X       X       X       X           Level 3 (9 frames)
 *                               X       E       (E+L)       E       X                           Level 4 (3 frames)
 * X: regular frame
 * L: the only frame in the same time-frame between all levels
 * E: extra frames needed for expand operation at L
 *
 * Level frames needed = (framesPLevel * 2) + 3
 *
 * # buffers per pyramid size + memory needed @ 352x288x32bit (396kB):
 * Levels 0,1,2             -> 33  buffers (13.068k)
 * Levels 0,1,2,3           -> 78  buffers (30.888k)
 * Levels 0,1,2,3,4         -> 171 buffers (67.716k)
 *
 * COMPRESSION
 * Video frames in time-compressed levels use the a gaussian algorithm that ensures that each
 * source frame contributes equally to the compressed level.
 *
 * LAPLACIAN
 * Is taken between the levels.
 *
 * EXPANSION
 * The goal of expansion is to only calculate the single expanded L frame
 *
 * The pyramid supports a moving-window.
 *
 *
 * Created by ben on 11-3-16.
 */
public class TemporalPyramid {

    private static final String TAG = "TemporalPyramid";
    private final RenderScript rs;
    private final ScriptC_tpyramid rsPyramid;

    private final List<Level> levels = new ArrayList<>();
    private int width;
    private int height;


    public TemporalPyramid(RenderScript rs, int width, int height, int levelCount) {
        this.rs = rs;
        this.rsPyramid = new ScriptC_tpyramid(rs);

        resizePyramid(width, height, levelCount);
    }

    public void resizePyramid(int width, int height, int levelCount) {
        destroyPyramidLevelBuffers();

        this.width = width;
        this.height = height;
        createPyramidLevelBuffers(levelCount);
    }

    public int getActualLevelCount() {
        return levels.size();
    }

    public Level getLevel(int levelNumer) {
        return levels.get(levelNumer);
    }

    public void plotLevel(int levelNumber,
                          PlotType plotType,
                          Allocation rgbaDestination) {

        Level plotLevel = levels.get(levelNumber);

        switch (plotType) {
            case LAPLACIAN:
                rsPyramid.forEach_plotPyramidLevelLaplacian(
                        plotLevel.getLevelLaplacianBuffers().get(1),
                        rgbaDestination
                );
                break;
            case LEVEL:
                rsPyramid.forEach_plotPyramidLevel(
                        plotLevel.getTemporalCenterGaussian(0),
                        rgbaDestination
                );
                break;
            case COLLAPSED_RESULT:
                rsPyramid.forEach_plotPyramidLevel(
                        plotLevel.getCollapsedResult(),
                        rgbaDestination
                );
                break;
            case LEVEL_EXPANDED:
                if(!plotLevel.expandBuffers.isEmpty()) {
                    rsPyramid.forEach_plotPyramidLevel(
                            plotLevel.expandBuffers.get(1),
                            rgbaDestination
                    );
                }
                break;
        }
    }

    /**
     * Shift in a new buffer.
     *
     * 1. Higher levels using COMPRESS
     * 2. EXPANDED version of levels
     * 3. Laplacian (delta of level and expanded higher level)
     */
    public void calculate(Allocation sourceIntensityBuffer) {

        // Shift in the video frame into the temporal buffers of level0
        Level level0 = levels.get(0);
        level0.shiftInGaussian(sourceIntensityBuffer);

        // Calculate the smaller levels
        for(int level = 1; level < levels.size(); level++) {

            Level sourceLevel = levels.get(level - 1);
            Level currentLevel = levels.get(level);

            // COMPRESS
            for(int destIndex = 0; destIndex < currentLevel.gaussianBuffers.size(); destIndex++) {
                rsPyramid.set_compressSource1(sourceLevel.getGaussian(0 + destIndex * 2));
                rsPyramid.set_compressSource2(sourceLevel.getGaussian(1 + destIndex * 2));
                rsPyramid.set_compressSource3(sourceLevel.getGaussian(2 + destIndex * 2));
                rsPyramid.set_compressSource4(sourceLevel.getGaussian(3 + destIndex * 2));
                rsPyramid.set_compressSource5(sourceLevel.getGaussian(4 + destIndex * 2));
                rsPyramid.forEach_compress(currentLevel.getGaussian(destIndex));
            }

            // EXPAND (only around L)
            int centerBufferIndex = (currentLevel.gaussianBuffers.size() - 1) / 2;
            rsPyramid.set_expandSource1(currentLevel.getGaussian(centerBufferIndex - 1));
            rsPyramid.set_expandSource2(currentLevel.getGaussian(centerBufferIndex));
            rsPyramid.set_expandSource3(currentLevel.getGaussian(centerBufferIndex + 1));
            rsPyramid.forEach_expandOddLeft(currentLevel.expandBuffers.get(0));
            rsPyramid.forEach_expandEven(currentLevel.expandBuffers.get(1));
            rsPyramid.forEach_expandOddRight(currentLevel.expandBuffers.get(2));
        }

        // LAPLACIANS

        // Lowest level is simply the gaussian buffer
        int lowestLevelIndex = levels.size() - 1;
        Level lowestLevel = levels.get(lowestLevelIndex);
        for(int c = 0; c<3 ; c++) {
            lowestLevel.getLevelLaplacianBuffers().get(c).copyFrom(lowestLevel.getTemporalCenterGaussian(c - 1));
        }


        // Other levels stack, working from smallest to biggest
        for(int level = lowestLevelIndex - 1; level >= 0; level--) {
            Level targetLevel = levels.get(level);
            Level smallerLevel = levels.get(level + 1);

            // Calculate three surrounding laplacians
            for(int c = 0; c<3 ; c++) {
                rsPyramid.set_laplacianLowerLevel(smallerLevel.expandBuffers.get(c));
                rsPyramid.forEach_laplacian(targetLevel.getTemporalCenterGaussian(c - 1), targetLevel.getLevelLaplacianBuffers().get(c));
            }
        }
    }

    /**
     * Compute the reconstructed image by collapsing the laplacians...
     *
     * NOTE: this will overwrite the calculate pyramids gaussian buffers!
     */
    public void collapseLaplacian() {

        // Compute all levels by expanding the combined laplacian levels

        // Lowest level:
        // C2 = L2
        int lowestLevelIndex = levels.size() - 1;
        Level lowestLevel = levels.get(lowestLevelIndex);
        for(int c = 0; c<3; c++) {
            lowestLevel.collapseBuffers.get(c).copyFrom(lowestLevel.getLevelLaplacianBuffers().get(c));
        }

        // Other levels:
        // C1 = ex(C2) + L1
        // C0 = ex(C1) + L0
        for(int level = lowestLevelIndex - 1; level >= 0; level--) {

            // EXPAND the lower level collapsed buffer
            Level smallerLevel = levels.get(level + 1);
            Level targetLevel = levels.get(level);
            rsPyramid.set_expandSource1(smallerLevel.collapseBuffers.get(0));
            rsPyramid.set_expandSource2(smallerLevel.collapseBuffers.get(1));
            rsPyramid.set_expandSource3(smallerLevel.collapseBuffers.get(2));
            rsPyramid.forEach_expandOddLeft(targetLevel.expandBuffers.get(0));
            rsPyramid.forEach_expandEven(targetLevel.expandBuffers.get(1));
            rsPyramid.forEach_expandOddRight(targetLevel.expandBuffers.get(2));

            // Now add the laplacian of the current level to the expanded previous level
            for(int c = 0; c<3; c++) {
                rsPyramid.set_collapseLevel(targetLevel.expandBuffers.get(c));
                rsPyramid.forEach_collapse(targetLevel.levelLaplacianBuffers.get(c), targetLevel.collapseBuffers.get(c));
            }
        }
    }

    /**
     * Clean up the pyramid.
     *
     * After this call completes the pyramid should not be used anymore!
     */
    public void destroy() {
        destroyPyramidLevelBuffers();

        rsPyramid.destroy();
    }

    private void createPyramidLevelBuffers(int desiredLevelCount) {
        for(int levelNumber = 0; levelNumber < desiredLevelCount; levelNumber++) {
            Level level = new Level(levelNumber, desiredLevelCount);
            levels.add(level);
            Log.d(TAG, "Created level " + levelNumber + ", buffers " + level.gaussianBuffers.size());
        }
    }

    private void destroyPyramidLevelBuffers() {
        for(Level level : levels) {
            level.destroyLaplacianBuffers();
            level.destroyExpandBuffers();
            level.destroyCollapseBuffers();
            level.destroyGaussianBuffers();
        }
        levels.clear();
    }

    public class Level {

        final int level;
        final List<Allocation> expandBuffers = new ArrayList<>();
        final List<Allocation> collapseBuffers = new ArrayList<>();
        final List<Allocation> gaussianBuffers = new ArrayList<>();
        final List<Allocation> levelLaplacianBuffers = new ArrayList<>();
        int oldestGaussianBufferIndex;

        private Level(int level, int totalLevels) {
            this.level = level;
            this.oldestGaussianBufferIndex = 0;

            // Add three expand buffers if this is not level 0
            expandBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            expandBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            expandBuffers.add(create2d(rs, width, height, Element.F32(rs)));

            collapseBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            collapseBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            collapseBuffers.add(create2d(rs, width, height, Element.F32(rs)));

            levelLaplacianBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            levelLaplacianBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            levelLaplacianBuffers.add(create2d(rs, width, height, Element.F32(rs)));

            // Calculate the number of buffers needed and add them
            int bufferCount = 0;
            for(int c = level; c < totalLevels; c++) {
                bufferCount *= 2;
                bufferCount += 3;
            }
            for(int c = 0; c < bufferCount; c++) {
                gaussianBuffers.add(create2d(rs, width, height, Element.F32(rs)));
            }
        }

        public int getLevel() {
            return level;
        }

        public List<Allocation> getLevelLaplacianBuffers() {
            return levelLaplacianBuffers;
        }

        private void destroyExpandBuffers() {
            for(Allocation allocation : expandBuffers) {
                allocation.destroy();
            }
            expandBuffers.clear();
        }

        private void destroyCollapseBuffers() {
            for(Allocation allocation : collapseBuffers) {
                allocation.destroy();
            }
            collapseBuffers.clear();
        }

        private void destroyGaussianBuffers() {
            for(Allocation allocation : gaussianBuffers) {
                allocation.destroy();
            }
            gaussianBuffers.clear();
        }

        private void destroyLaplacianBuffers() {
            for(Allocation allocation : levelLaplacianBuffers) {
                allocation.destroy();
            }
            levelLaplacianBuffers.clear();
        }

        void shiftInGaussian(Allocation newVideoFrame) {
            Allocation shiftInBuffer = gaussianBuffers.get(oldestGaussianBufferIndex);
            shiftInBuffer.copyFrom(newVideoFrame);
            oldestGaussianBufferIndex = (oldestGaussianBufferIndex + 1) % gaussianBuffers.size();
        }

        Allocation getGaussian(int temporalIndex) {
            int correctIndex = (oldestGaussianBufferIndex + temporalIndex) % gaussianBuffers.size();
            return gaussianBuffers.get(correctIndex);
        }

        Allocation getTemporalCenterGaussian(int offset) {
            int temporalCenterIndex = (gaussianBuffers.size() - 1) / 2;
            return getGaussian(temporalCenterIndex + offset);
        }

        Allocation getCollapsedResult() {
            return collapseBuffers.get(1);
        }
    }

    public enum PlotType {
        LEVEL,
        LEVEL_EXPANDED,
        LAPLACIAN,
        COLLAPSED_RESULT
    }
}
