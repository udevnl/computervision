package nl.udev.hellorenderscript.video.common;

import android.renderscript.Allocation;

/**
 * Interface for receiver of captured frames from the camera.
 *
 * Created by ben on 12-1-16.
 */
public interface VideoCaptureListener {

    /**
     * Receive a captured video frame.
     *
     * @param capturedRgbBuffer    The video frame in RGB format.
     */
    void receiveVideoFrame(Allocation capturedRgbBuffer);
}
