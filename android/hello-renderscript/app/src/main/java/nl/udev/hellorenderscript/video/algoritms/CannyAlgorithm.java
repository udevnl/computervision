package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.Script;
import android.renderscript.Type;

import com.android.example.cannylive.ScriptC_canny;

import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.AbstractVideoAlgorithm;

/**
 * Canny edge detector
 *
 * Implementation taken and ported from Google and ported to view the algorithm.
 * https://android.googlesource.com/platform/frameworks/rs/+/b7a6edc/java/tests/CannyLive/src/com/android/example/cannylive
 *
 * Created by ben on 17-9-17.
 */
public class CannyAlgorithm extends AbstractVideoAlgorithm {

    private static final String TAG = "CannyAlgorithm";
    private ScriptC_canny rsCanny;

    private Allocation mBlurAllocation;
    private Allocation mEdgeAllocation;
    private Allocation mHoughOutput;
    private Allocation mHoughSlices;

    private Mode mode;

    private enum Mode {
        HOUGH_TRANSFORM,
        BLACK,
        BLACK_FUZZ,
        WHITE_FUZZ,
        WHITE_RGB,
        CARTOON
    }

    public CannyAlgorithm() {
        addParameter(new LimitedSettingsParameter<>("Mode", Mode.values(), Mode.CARTOON, new ModeMonitor()));
        this.mode = Mode.CARTOON;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public String getDescription() {
        return "Canny edge detector. " +
                "5x5 blur, 3x3 edge detect, line thinning, " +
                "removal of faint edges not connected to strong edges.";
    }

    @Override
    protected void initialize() {
        // Create algorithm data buffers here (not used for this simple algorithm)

       Type.Builder buffTypeBuilder = new Type.Builder(getRenderScript(), Element.U8(getRenderScript()));
        buffTypeBuilder.setX(getResolution().getWidth());
        buffTypeBuilder.setY(getResolution().getHeight());
        mBlurAllocation = Allocation.createTyped(getRenderScript(), buffTypeBuilder.create());
        mEdgeAllocation = Allocation.createTyped(getRenderScript(), buffTypeBuilder.create());

        int NO_OF_SLICES = 8;
        int[] slices = new int[NO_OF_SLICES * 2];
        for (int i = 0; i < NO_OF_SLICES; i++) {
            int s1 = i * 360 / NO_OF_SLICES;
            int s2 = ((1 + i) * 360) / NO_OF_SLICES;
            slices[i * 2] = s1;
            slices[i * 2 + 1] = s2;
        }
        Type.Builder houghSliceBuilder = new Type.Builder(getRenderScript(), Element.I32_2(getRenderScript()));
        houghSliceBuilder.setX(NO_OF_SLICES);
        mHoughSlices = Allocation.createTyped(getRenderScript(), houghSliceBuilder.create(), Allocation.USAGE_SCRIPT);
        mHoughSlices.copyFrom(slices);
        Type.Builder houghOutputBuilder = new Type.Builder(getRenderScript(), Element.U8(getRenderScript()));
        houghOutputBuilder.setX(800);
        houghOutputBuilder.setY(360);
        mHoughOutput = Allocation.createTyped(getRenderScript(), houghOutputBuilder.create());

        // Create scriptlets (RenderScript)
        rsCanny = new ScriptC_canny(getRenderScript());

        rsCanny.set_blurImage(mBlurAllocation);
        rsCanny.set_edgeImage(mEdgeAllocation);
        rsCanny.set_hough_output(mHoughOutput);
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsCanny.destroy();

        // Destroy buffers created during initialization (not used for this algorithm)
        mBlurAllocation.destroy();
        mEdgeAllocation.destroy();
        mHoughSlices.destroy();
        mHoughOutput.destroy();
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        rsCanny.set_gCurrentRGBFrame(captureBufferRgba);

        // Run processing pass
        rsCanny.forEach_getLum(captureBufferRgba, mEdgeAllocation);

        Script.LaunchOptions opt = new Script.LaunchOptions();
        opt.setX(2, mBlurAllocation.getType().getX() - 2);
        opt.setY(2, mBlurAllocation.getType().getY() - 2);
        rsCanny.forEach_blur_uchar(mBlurAllocation, opt);

        opt.setX(3, mBlurAllocation.getType().getX() - 3);
        opt.setY(3, mBlurAllocation.getType().getY() - 3);
        rsCanny.forEach_edge(mEdgeAllocation, opt);

        opt.setX(4, mBlurAllocation.getType().getX() - 4);
        opt.setY(4, mBlurAllocation.getType().getY() - 4);
        rsCanny.forEach_thin(mBlurAllocation, opt);

        opt.setX(5, mBlurAllocation.getType().getX() - 5);
        opt.setY(5, mBlurAllocation.getType().getY() - 5);
        rsCanny.forEach_hysteresis(mBlurAllocation, mEdgeAllocation, opt);

        switch (mode) {
            case HOUGH_TRANSFORM:
            default:
                rsCanny.forEach_black_uchar(mHoughOutput);
                rsCanny.forEach_hough(mHoughSlices);
                getRenderScript().finish();
                rsCanny.forEach_hough_map(displayBufferRgba);
                break;
            case BLACK:
                rsCanny.forEach_toRGB(displayBufferRgba, opt);
                break;
            case BLACK_FUZZ:
                rsCanny.forEach_toRGBfuzz(displayBufferRgba, opt);
                break;
            case WHITE_FUZZ:
                rsCanny.forEach_toWhiteRGBfuzz(displayBufferRgba, opt);
                break;
            case WHITE_RGB:
                rsCanny.forEach_toWhiteRGB(displayBufferRgba, opt);
                break;
            case CARTOON:
                rsCanny.forEach_toCartoon(displayBufferRgba, opt);
                break;
        }
    }

    private class ModeMonitor implements ParameterUser<Mode> {

        @Override
        public String displayValue(Mode value) {
            return value.toString();
        }

        @Override
        public void handleValueChanged(Mode newValue) {
            mode = newValue;
        }
    }
}