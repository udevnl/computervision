package nl.udev.hellorenderscript.calculus;

import android.renderscript.Allocation;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;

/**
 * Created by ben on 22-4-16.
 */
public abstract class AbstractCalculusAlgorithm extends AbstractAlgorithm {

    /**
     * Process new cycle and display the output in the display buffer.
     *
     * @param displayBufferRgba    Buffer to visualize the output in
     */
    public abstract void cycle(Allocation displayBufferRgba);
}
