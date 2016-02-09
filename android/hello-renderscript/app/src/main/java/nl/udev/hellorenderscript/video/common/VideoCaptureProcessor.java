package nl.udev.hellorenderscript.video.common;

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Helper class to get captured frames from the camera.
 *
 * Created by ben on 12-1-16.
 */
public class VideoCaptureProcessor {

    public static final String TAG = "VidCapProc";
    private static final long START_CAPTURE_TIMEOUT_MILLIS = 3000;

    private final RenderScript mRS;

    private Allocation cameraCaptureAllocation;
    private Allocation captureRgbBuffer;
    private ScriptIntrinsicYuvToRGB yuvToRgbScript;

    // Locking and status
    final Lock captureLock = new ReentrantLock();
    private volatile boolean initialized = false;
    private volatile boolean readyForCapture = false;

    // Variables used while initialized
    private Size videoSize = null;
    private CameraManager cameraManager;
    private String cameraId;
    private CameraDevice mCameraDevice = null;
    private CaptureRequest.Builder mPreviewBuilder = null;
    private CameraCaptureSession mPreviewSession = null;
    private VideoCaptureListener videoCaptureListener;


    public VideoCaptureProcessor(RenderScript mRS) {
        this.mRS = mRS;
    }

    public Size[] getAvailableResolutions(CameraManager manager) {
        try {
            this.cameraManager = manager;
            this.cameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = map.getOutputSizes(SurfaceHolder.class);

            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Get available resolutions: " + Arrays.toString(sizes));
            return sizes;
        } catch (CameraAccessException e) {
            throw new IllegalStateException("No camera permissions!");
        }
    }

    /**
     * Initialize capture.
     *
     * @param manager             CameraManager used to get to the camera
     * @param desiredVideoSize    A desired video resolution
     * @return                    True if successful
     */
    public boolean initialize(CameraManager manager, Size desiredVideoSize) {

        try {
            captureLock.lock();

            initialized = false;

            this.cameraManager = manager;
            this.cameraId = cameraManager.getCameraIdList()[0];
            this.videoSize = desiredVideoSize;

            // Create the buffer used for capturing to.
            Type.Builder yuvTypeBuilder = new Type.Builder(mRS, Element.YUV(mRS));
            yuvTypeBuilder.setX(videoSize.getWidth());
            yuvTypeBuilder.setY(videoSize.getHeight());
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            cameraCaptureAllocation = Allocation.createTyped(mRS, yuvTypeBuilder.create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

            // Create the buffers used to store the capture converted to RGB
            Type.Builder rgbTypeBuilder = new Type.Builder(mRS, Element.RGBA_8888(mRS));
            rgbTypeBuilder.setX(videoSize.getWidth());
            rgbTypeBuilder.setY(videoSize.getHeight());
            captureRgbBuffer = Allocation.createTyped(mRS, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT);

            // Create the conversion script
            yuvToRgbScript = ScriptIntrinsicYuvToRGB.create(mRS, Element.RGBA_8888(mRS));
            yuvToRgbScript.setInput(cameraCaptureAllocation);

            initialized = true;

        } catch (CameraAccessException e) {
            Log.e("[" + Thread.currentThread().getName() + "] - " + TAG, "Failed to initialize", e);
        }finally {
            captureLock.unlock();
        }

        return initialized;

    }

    /**
     * Release all resources, stops capture if currently capturing.
     * After release completes a new capture can be started again by first calling initialize(...)
     */
    public void release() {
        try {
            captureLock.lock();

            if(initialized) {
                stopCapture();

                initialized = false;

                Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Releasing video capture buffers");
                cameraManager = null;
                cameraId = null;
                videoSize = null;
                yuvToRgbScript.destroy();
                cameraCaptureAllocation.destroy();
                captureRgbBuffer.destroy();
            }

        } finally {
            captureLock.unlock();
        }
    }

    /**
     * @return                              The video size that is used to capture
     * @throws IllegalStateException        If not initialized.
     */
    public Size getVideoSize() {
        try {
            captureLock.lock();

            if (!initialized) {
                throw new IllegalStateException("Not initialized!");
            }
            return videoSize;

        } finally {
            captureLock.unlock();
        }
    }

    /**
     * Start capture.
     *
     * @param videoCaptureListener          Listener that will be informed of captured frames.
     * @throws IllegalStateException        If not initialized.
     */
    public void startCapture(VideoCaptureListener videoCaptureListener) {

        try {
            captureLock.lock();

            if (!initialized) {
                throw new IllegalStateException("Not initialized!");
            }

            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "startCapture");

            this.videoCaptureListener = videoCaptureListener;

            //noinspection ResourceType, not the responsibility of this class to get permissions
            cameraManager.openCamera(
                    cameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            handleCameraOpened(camera);
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Camera disconnected");
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            Log.e("[" + Thread.currentThread().getName() + "] - " + TAG, "Error " + error + " occurred");
                        }
                    },
                    null
            );

        } catch (CameraAccessException e) {
            throw new IllegalStateException(
                    "No permission to use camera, you should have taken care of it before calling this method!",
                    e
            );
        } finally {
            captureLock.unlock();
        }
    }

    private void handleCameraOpened(CameraDevice camera) {

        try {
            captureLock.lock();

            mCameraDevice = camera;

            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "handleCameraOpened");

            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = cameraCaptureAllocation.getSurface();
            mPreviewBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(
                    Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            handleCameraConfigured(session);
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e("[" + Thread.currentThread().getName() + "] - " + TAG, "CameraCaptureSession configure failed");
                        }
                    },
                    null
            );

        } catch (CameraAccessException e){
            Log.e("[" + Thread.currentThread().getName() + "] - " + TAG, "Unexpected no access to camera!", e);
        } finally {
            captureLock.unlock();
        }
    }

    private void handleCameraConfigured(CameraCaptureSession session) {

        try {
            captureLock.lock();

            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "handleCameraConfigured");
            mPreviewSession = session;
            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            HandlerThread backgroundThread = new HandlerThread("CameraPreview");
            backgroundThread.start();
            Handler backgroundHandler = new Handler(backgroundThread.getLooper());

            // Start capture.
            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Starting repeating capture request");
            mPreviewSession.setRepeatingRequest(
                    mPreviewBuilder.build(),
                    new CaptureDataHandler(),
                    backgroundHandler
            );

            readyForCapture = true;

        } catch (CameraAccessException e) {
            Log.e("[" + Thread.currentThread().getName() + "] - " + TAG, "Unexpected no access to camera!", e);
        } finally {
            captureLock.unlock();
        }
    }

    /**
     * Stops capture.
     *
     * @throws IllegalStateException        If not initialized.
     */
    public void stopCapture() {

        try {
            captureLock.lock();

            if(!initialized) {
                throw new IllegalStateException("Not initialized!");
            }

            readyForCapture = false;

            Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "stopCapture");

            if (mCameraDevice != null) {
                mCameraDevice.close();
            }

            //mRS.finish();

            videoCaptureListener = null;
            mCameraDevice = null;

        } finally {
            captureLock.unlock();
        }
    }

    private class CaptureDataHandler extends CameraCaptureSession.CaptureCallback {

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

            if(captureLock.tryLock()) {
                try {
                    if(readyForCapture) {
                        // Get captured frame
                        cameraCaptureAllocation.ioReceive();

                        // Convert to RGB
                        yuvToRgbScript.forEach(captureRgbBuffer);

                        // Post to listener
                        if (videoCaptureListener != null) {
                            videoCaptureListener.receiveVideoFrame(captureRgbBuffer);
                        }
                    } else {
                        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "onCaptureCompleted - not ready for capture, ignoring");
                    }
                } finally {
                    captureLock.unlock();
                }
            } else {
                Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "onCaptureCompleted - busy, ignoring");
            }
        }
    }
}