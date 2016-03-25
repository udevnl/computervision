package nl.udev.hellorenderscript.video.algoritms;

import android.renderscript.Allocation;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.ScriptC_intensity;

/**
 * Simple algorithm that can adjust the intensity of an image.
 *
 * Created by ben on 25-3-16.
 */
public class IntensityAlgorithm extends AbstractAlgorithm {

    private static final String TAG = "IntensityAlg";
    private ScriptC_intensity rsIntensity;

    private float intensityFactor;

    public IntensityAlgorithm() {
        addParameter(new IntegerParameter("Intensity", 0, 500, 100, new IntensityParameterMonitor()));
        this.intensityFactor = 1.0f;
    }

    @Override
    protected String getName() {
        return "Intensity";
    }

    @Override
    protected void initialize() {
        // Create algorithm data buffers here (not used for this simple algorithm)
        // myBuffer = create2d(Element.F32(getRenderScript()));

        // Create scriptlets (RenderScript)
        rsIntensity = new ScriptC_intensity(getRenderScript());
    }

    @Override
    protected void unInitialize() {

        // Destroy scriptlets
        rsIntensity.destroy();

        // Destroy buffers created during initialization (not used for this algorithm)
        // myBuffer.destroy();
    }

    @Override
    public void process(Allocation captureBufferRgba, Allocation displayBufferRgba) {

        // Apply intensity to captureBufferRgba and output into displayBufferRgba
        rsIntensity.set_intensityFactor(intensityFactor);
        rsIntensity.forEach_calcGreyscaleIntensity(captureBufferRgba, displayBufferRgba);
    }

    private class IntensityParameterMonitor implements ParameterUser<Integer> {

        @Override
        public String displayValue(Integer value) {
            return String.format("%3.0f%%", intensityFactor * 100.0f);
        }

        @Override
        public void handleValueChanged(Integer newValue) {
            intensityFactor = newValue / 100.0f;
        }
    }
}