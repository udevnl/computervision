package nl.udev.hellorenderscript.common;

import android.renderscript.Allocation;
import android.renderscript.RenderScript;
import android.renderscript.Short4;

import nl.udev.hellorenderscript.ScriptC_colormap;

/**
 * Created by Ben on 15-11-2015.
 */
public class ColorMapRS {

    private ScriptC_colormap mColorMapScript;
    private final Short4[] colors;

    public ColorMapRS(RenderScript mRS) {
        mColorMapScript = new ScriptC_colormap(mRS);
        colors = new Short4[256];
    }

    public void addLinearGradient(int indexFrom, int indexTo,
                                  int redStart, int redEnd,
                                  int greenStart, int greenEnd,
                                  int blueStart, int blueEnd) {

        int indexRange = indexTo - indexFrom;
        int redRange = redEnd - redStart;
        int greenRange = greenEnd - greenStart;
        int blueRange = blueEnd - blueStart;

        for(int c = 0; c < indexRange; c++) {
            int r = redStart + c * redRange / indexRange;
            int g = greenStart + c * greenRange / indexRange;
            int b = blueStart + c * blueRange / indexRange;
            int a = 0;
            colors[indexFrom + c] = new Short4((short) r, (short) g, (short) b, (short) a);
        }
    }

    public void setColors() {
        mColorMapScript.set_colormap(colors);
    }

    public void applyColorMapToUchar(Allocation ucharValues, Allocation outPixels) {
        mColorMapScript.forEach_colormapUchar(ucharValues, outPixels);
    }

    public void applyColorMapToNormalizedFloat(Allocation normalizedFloatValues, Allocation outPixels) {
        mColorMapScript.forEach_colormapFloat(normalizedFloatValues, outPixels);
    }
}
