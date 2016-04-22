package nl.udev.hellorenderscript.common.algoritm.parts;

import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Short4;

import nl.udev.hellorenderscript.video.ScriptC_plotting;

/**
 * Plotting helper.
 *
 * Created by ben on 8-4-16.
 */
public class Plotting {

    private final ScriptC_plotting rsPlot;
    private final Allocation angleColorMapBuffer;
    private final Allocation eightBitColorMapBuffer;

    /**
     * Create new helper for plotting.
     *
     * @param rs    Boss script
     */
    public Plotting(RenderScript rs) {
        this.rsPlot = new ScriptC_plotting(rs);
        this.angleColorMapBuffer = RsUtils.create1d(rs, 360, Element.F32_4(rs));
        this.eightBitColorMapBuffer = RsUtils.create1d(rs, 256, Element.F32_4(rs));

        setAngularColormap(createDefaultAngularColorMap());
        setEightBitColormap(createDefault8BitColorMap());
    }

    /**
     * Clean-up
     */
    public void destroy() {
        this.rsPlot.destroy();
        this.angleColorMapBuffer.destroy();
        this.eightBitColorMapBuffer.destroy();
    }

    /**
     * Set new angular colors.
     *
     * @param colormap    Colormap with 360 x 4 (RGBA) float entries
     */
    public void setAngularColormap(ColorMap colormap) {
        if(colormap.colors.length / 4 != 360) {
            throw new IllegalArgumentException("Angular color map must contain 360 color entries!");
        }

        angleColorMapBuffer.copyFrom(colormap.colors);
        rsPlot.set_angleColormap(angleColorMapBuffer);
    }

    /**
     * Set new angular colors.
     *
     * @param colormap    Colormap with 360 x 4 (RGBA) float entries
     */
    public void setEightBitColormap(ColorMap colormap) {
        if(colormap.colors.length / 4 != 256) {
            throw new IllegalArgumentException("8 bit color map must contain 256 color entries!");
        }

        eightBitColorMapBuffer.copyFrom(colormap.colors);
        rsPlot.set_eightBitColormap(eightBitColorMapBuffer);
    }

    /**
     * Plots the given 2D vectors [angle, magnitude] into the given outBufferRgba so that:
     *
     * vector.angle ==> output.color
     * vector.magnitude ==> output.brightness (clamped at 1.0)
     *
     * @param polar2dVectors    buffer with polar vectors to plot
     * @param outBufferRgba     destination buffer to plot into
     */
    public void plotColormapPolar2d(Allocation polar2dVectors, Allocation outBufferRgba) {
        rsPlot.forEach_plotPolar2dColormap(polar2dVectors, outBufferRgba);
    }

    /**
     *
     * @param ucharBuffer
     * @param outBufferRgba
     */
    public void plotColormapUchar(Allocation ucharBuffer, Allocation outBufferRgba) {
        rsPlot.forEach_plotUchar8BitColormap(ucharBuffer, outBufferRgba);
    }

    /**
     *
     * @param normalizedFloatBuffer
     * @param outBufferRgba
     */
    public void plotColormapNormalizedFloat(Allocation normalizedFloatBuffer, Allocation outBufferRgba) {
        rsPlot.forEach_plotNormalizedFloat8BitColormap(normalizedFloatBuffer, outBufferRgba);
    }

    public void plot(Allocation points, int pointCount, Short4 color, Allocation image, int width, int height) {
        rsPlot.invoke_plotDots(points, pointCount, color, image, width, height);
    }

    public static ColorMap createDefaultAngularColorMap() {
        return new ColorMap.Builder(360)
                .addLinearGradient(0, 90,       1.0f, 1.0f, 0.0f, 1.0f, 0.0f, 0.0f) // Red -> Yellow
                .addLinearGradient(90, 180,     1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f) // Yellow -> Green
                .addLinearGradient(180, 270,    0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f) // Green -> Cyan
                .addLinearGradient(270, 360,    0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 0.0f) // Cyan -> Red
                .build();
    }

    private static ColorMap createDefault8BitColorMap() {
        return new ColorMap.Builder(256)
                .addLinearGradient(0, 64,       0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f) // Black -> Blue
                .addLinearGradient(64, 128, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f) // Blue -> Cyan
                .addLinearGradient(128, 192, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f) // Cyan -> Green
                .addLinearGradient(192, 256, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 1.0f) // Green -> White
                .build();
    }

    public static class ColorMap {
        private final float[] colors;

        private ColorMap(float colors[]) {
            this.colors = colors;
        }

        public static class Builder {
            private final float[] colors;

            public Builder(int colorCount) {
                colors = new float[colorCount * 4];
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

            public ColorMap build() {
                return new ColorMap(colors);
            }
        }
    }
}
