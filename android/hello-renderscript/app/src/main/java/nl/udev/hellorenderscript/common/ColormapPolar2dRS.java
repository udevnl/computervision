package nl.udev.hellorenderscript.common;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Short4;
import android.renderscript.Type;

import nl.udev.hellorenderscript.ScriptC_colormap;
import nl.udev.hellorenderscript.video.ScriptC_plotpolar2d;

/**
 * Helper wrapper class for the angle plotting.
 *
 * Created by ben on 9-2-16.
 */
public class ColormapPolar2dRS {

    private final ScriptC_plotpolar2d rsPlotPolar2d;
    private final Allocation angleColorsBuffer;

    private ColormapPolar2dRS(float[] colors, RenderScript rs) {
        this.rsPlotPolar2d = new ScriptC_plotpolar2d(rs);

        Type.Builder angleColorsBufferBuilder = new Type.Builder(rs, Element.F32_4(rs));
        angleColorsBufferBuilder.setX(360);
        angleColorsBuffer = Allocation.createTyped(rs, angleColorsBufferBuilder.create(), Allocation.USAGE_SCRIPT);
        angleColorsBuffer.copyFrom(colors);

        rsPlotPolar2d.set_plotColoured_angleColors(angleColorsBuffer);
    }

    public void cleanup() {
        angleColorsBuffer.destroy();
        rsPlotPolar2d.destroy();
    }

    /**
     * Plots the given 2D vectors [angle, magnitude] into the given outBufferRgba so that:
     * vector.angle ==> output.color
     * vector.magnitude ==> output.brightness (clamped at 1.0)
     *
     * @param angleMagnitude2dVectors
     * @param outBufferRgba
     */
    public void plotVectorAngles(Allocation angleMagnitude2dVectors, Allocation outBufferRgba) {
        rsPlotPolar2d.forEach_plotColoured(angleMagnitude2dVectors, outBufferRgba);
    }


    public static class Builder {
        private final RenderScript rs;
        private final float[] colors;

        public Builder(RenderScript rs) {
            this.rs = rs;
            this.colors  = new float[360 * 4];
        }

        public Builder addLinearGradient(int indexFrom, int indexTo,
                                         float redStart, float redEnd,
                                         float greenStart, float greenEnd,
                                         float blueStart, float blueEnd) {
            int indexRange = indexTo - indexFrom;
            float redRange = redEnd - redStart;
            float greenRange = greenEnd - greenStart;
            float blueRange = blueEnd - blueStart;

            for(int c = 0; c < indexRange; c++) {
                float r = redStart + c * redRange / indexRange;
                float g = greenStart + c * greenRange / indexRange;
                float b = blueStart + c * blueRange / indexRange;
                float a = 1.0f;

                int offset = (indexFrom + c) * 4;
                colors[offset + 0] = r;
                colors[offset + 1] = g;
                colors[offset + 2] = b;
                colors[offset + 3] = a;
            }

            return this;
        }

        public ColormapPolar2dRS build() {
            return new ColormapPolar2dRS(colors, rs);
        }

    }
}
