package nl.udev.hellorenderscript.video;

import android.renderscript.Allocation;

import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;

/**
 * Created by ben on 22-4-16.
 */
public abstract class AbstractVideoAlgorithm extends AbstractAlgorithm {

    /**
     * Process the new captured data and display the output in the display buffer.
     * @param captureBufferRgba    Buffer containing the newly captured video frame
     * @param displayBufferRgba    Buffer to visualize the output in
     */
    public abstract void process(Allocation captureBufferRgba, Allocation displayBufferRgba);

}
