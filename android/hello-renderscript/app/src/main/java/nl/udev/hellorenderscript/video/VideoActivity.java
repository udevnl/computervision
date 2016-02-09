package nl.udev.hellorenderscript.video;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import nl.udev.hellorenderscript.BuildConfig;
import nl.udev.hellorenderscript.R;
import nl.udev.hellorenderscript.common.ColorMapRS;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class VideoActivity extends AppCompatActivity {

    private static final String TAG = "VideoActivity";
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private static final int COUNT = 50;

    private RenderScript mRS;
    private Surface cameraCaptureSurface;
    private Allocation cameraCaptureAllocation;
    private Allocation captureRgbBuffer;
    private List<Allocation> captureHistoryBuffers;
    private List<Allocation> captureMovingAverageBuffers;
    private ScriptC_video videoScript;
    private ScriptIntrinsicYuvToRGB yuvToRgbScript;
    private ColorMapRS colorMapRS;

    private Bitmap mBitmap;
    private int mWidth;
    private int mHeight;
    private View rootView;
    private ImageView mDisplayView;

    ParameterStore parameterStore;

    private Size mPreviewSize = null;
    private CameraDevice mCameraDevice = null;
    private CaptureRequest.Builder mPreviewBuilder = null;
    private CameraCaptureSession mPreviewSession = null;
    private CameraDevice.StateCallback mStateCallback = new CameraCallback();
    private CaptureDataHandler captureDataListener = new CaptureDataHandler();

    private CameraCaptureSession.StateCallback mPreviewStateCallback = new CameraCaptureSession.StateCallback() {

        @Override
        public void onConfigured(CameraCaptureSession session) {
            // TODO Auto-generated method stub
            Log.i(TAG, "onConfigured");
            mPreviewSession = session;

            mPreviewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);

            HandlerThread backgroundThread = new HandlerThread("CameraPreview");
            backgroundThread.start();
            Handler backgroundHandler = new Handler(backgroundThread.getLooper());

            try {
                // Start capture.
                mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), captureDataListener, backgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }

        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {
            // TODO Auto-generated method stub
            Log.e(TAG, "CameraCaptureSession Configure failed");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Ensure fullscreen hack
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.activity_video);

        this.rootView = findViewById(R.id.imageView);
        mDisplayView = (ImageView) findViewById(R.id.imageView);

        parameterStore = new ParameterStore(10, 20, 1);
        parameterStore.initialize();

//        Display display = getWindowManager().getDefaultDisplay();
//        Point size = new Point();
//        display.getSize(size);
//        mWidth = (int) (size.x);
//        mHeight = (int) (size.y);

//        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
//        mBitmap = Bitmap.createBitmap(mWidth / FACTOR, mHeight / FACTOR, conf);
//
//        mDisplayView = (ImageView) findViewById(R.id.imageView2);
//        mDisplayView.setImageBitmap(mBitmap);

        mRS = RenderScript.create(this);
//        mOutPixelsAllocation = Allocation.createFromBitmap(mRS, mBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
//        mJuliaValues = Allocation.createTyped(
//                mRS,
//                new Type.Builder(mRS, Element.U8(mRS))
//                        .setX(mWidth / FACTOR)
//                        .setY(mHeight / FACTOR)
//                        .create(),
//                Allocation.USAGE_SCRIPT
//        );
//
//        mJuliaScript = new ScriptC_julia(mRS);
//        mJuliaScript.set_height(mHeight / FACTOR);
//        mJuliaScript.set_width(mWidth / FACTOR);
//        mJuliaScript.set_precision(128);

        colorMapRS = new ColorMapRS(mRS);
        colorMapRS.addLinearGradient(0, 64, 0, 0, 0, 0, 0, 255);
        colorMapRS.addLinearGradient(64, 128, 0, 0, 0, 255, 255, 255);
        colorMapRS.addLinearGradient(128, 256, 0, 0, 255, 255, 255, 255);
        colorMapRS.setColors();

        yuvToRgbScript = ScriptIntrinsicYuvToRGB.create(mRS, Element.RGBA_8888(mRS));
        videoScript = new ScriptC_video(mRS);

//        renderJulia(-0.9259259f, 0.30855855f);

        // When permissions are revoked the app is restarted so onCreate is sufficient to check for
        // permissions core to the Activity's functionality.
        if (!checkCameraPermissions()) {
            requestCameraPermissions();
        } else {
            findAndOpenCamera();
        }

    }

    @Override
    protected void onPause() {
        // TODO Auto-generated method stub
        super.onPause();

        if (mCameraDevice != null)
        {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }


    private void findAndOpenCamera() {

        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try{
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            mPreviewSize = map.getOutputSizes(SurfaceHolder.class)[20];

            mWidth = mPreviewSize.getWidth();
            mHeight = mPreviewSize.getHeight();

            // Create the buffer used for capturing to.
            Type.Builder yuvTypeBuilder = new Type.Builder(mRS, Element.YUV(mRS));
            yuvTypeBuilder.setX(mWidth);
            yuvTypeBuilder.setY(mHeight);
            yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888);
            cameraCaptureAllocation = Allocation.createTyped(mRS, yuvTypeBuilder.create(), Allocation.USAGE_IO_INPUT | Allocation.USAGE_SCRIPT);

            // Create the buffers to calculate with
            Type.Builder rgbTypeBuilder = new Type.Builder(mRS, Element.RGBA_8888(mRS));
            rgbTypeBuilder.setX(mWidth);
            rgbTypeBuilder.setY(mHeight);
            captureRgbBuffer = Allocation.createTyped(mRS, rgbTypeBuilder.create(), Allocation.USAGE_SCRIPT);

            Type.Builder countBuilder = new Type.Builder(mRS, Element.U16_4(mRS));
            countBuilder.setX(mWidth);
            countBuilder.setY(mHeight);

            captureHistoryBuffers = new ArrayList<>();
            for(int c = 0; c < COUNT; c++) {
                captureHistoryBuffers.add(Allocation.createTyped(mRS, countBuilder.create(), Allocation.USAGE_SCRIPT));
            }

            Type.Builder avgBuilder = new Type.Builder(mRS, Element.I32_4(mRS));
            avgBuilder.setX(mWidth);
            avgBuilder.setY(mHeight);

            captureMovingAverageBuffers = new ArrayList<>();
            captureMovingAverageBuffers.add(Allocation.createTyped(mRS, avgBuilder.create(), Allocation.USAGE_SCRIPT));
            captureMovingAverageBuffers.add(Allocation.createTyped(mRS, avgBuilder.create(), Allocation.USAGE_SCRIPT));

            yuvToRgbScript.setInput(cameraCaptureAllocation);

            Bitmap.Config conf = Bitmap.Config.ARGB_8888;
            mBitmap = Bitmap.createBitmap(mWidth, mHeight, conf);

            mDisplayView.setImageBitmap(mBitmap);

            manager.openCamera(cameraId, mStateCallback, null);
        }
        catch(CameraAccessException e)
        {
            e.printStackTrace();
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        int action = MotionEventCompat.getActionMasked(event);

        switch (action) {

            case (MotionEvent.ACTION_MOVE):
                float cx = 0f,
                        cy = 0f;
                float x = event.getAxisValue(MotionEvent.AXIS_X);
                float y = event.getAxisValue(MotionEvent.AXIS_Y);
                cx = ((x / mWidth) * 4f) - 2f;
                cy = ((y / mHeight) * 4f) - 2f;
                Log.i(TAG, "Touched @" + cx + "," + cy);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private class CameraCallback extends CameraDevice.StateCallback {

        @Override
        public void onOpened(CameraDevice camera) {
            Log.i(TAG, "onOpened");
            mCameraDevice = camera;


            Surface surface = cameraCaptureAllocation.getSurface();

            try {
                mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e){
                e.printStackTrace();
            }

            mPreviewBuilder.addTarget(surface);

            try {
                mCameraDevice.createCaptureSession(Arrays.asList(surface), mPreviewStateCallback, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            // TODO Auto-generated method stub
            Log.e(TAG, "onError");

        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            // TODO Auto-generated method stub
            Log.e(TAG, "onDisconnected");

        }
    }

    private class CaptureDataHandler extends CameraCaptureSession.CaptureCallback {

        private int pos = 0;
        private int inputPos, outputPos;

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {

            cameraCaptureAllocation.ioReceive();

            // Store the capture as RGB
            yuvToRgbScript.forEach(captureRgbBuffer);

            // Calculate the capture destination
            Allocation destinationBuffer = captureHistoryBuffers.get(pos);
            videoScript.set_avgIn(captureRgbBuffer);
            videoScript.set_avgSize(1);
            videoScript.set_avgWidth(mWidth);
            videoScript.set_avgHeight(mHeight);
            videoScript.forEach_avgMe(destinationBuffer);

            int newInputPos = parameterStore.getInputSize();
            int newOutputPos = parameterStore.getOutputSize();

            if(inputPos != newInputPos || outputPos != newOutputPos) {
                videoScript.forEach_clearBufferInt4(captureMovingAverageBuffers.get(0));
                videoScript.forEach_clearBufferInt4(captureMovingAverageBuffers.get(1));
                for(int c = 0; c < COUNT; c++) {
                    videoScript.forEach_clearBufferUshort4(captureHistoryBuffers.get(c));
                }
            }
            inputPos = newInputPos;
            outputPos = newOutputPos;

            // Calculate two moving averages and then take the difference
            int[] averages = {inputPos, outputPos};

            // Update the moving averages
            int bufferNumber = 0;
            for(int c : averages) {
                int bufferOutPos = (pos + 1 + COUNT - c) % COUNT;

                Allocation shiftInBuffer = destinationBuffer;
                Allocation shiftOutBuffer = captureHistoryBuffers.get(bufferOutPos);
                Allocation sumBuffer = captureMovingAverageBuffers.get(bufferNumber);

                videoScript.set_shiftIn(shiftInBuffer);
                videoScript.set_shiftOut(shiftOutBuffer);
                videoScript.set_sumBuffer(sumBuffer);
                videoScript.forEach_updateSums(sumBuffer);

                // Log.i(TAG, "Buffer " + bufferNumber + ": in=" + pos + ", out=" + bufferOutPos);

                bufferNumber++;
            }

            pos = (pos + 1) % COUNT;

            videoScript.set_sumIn(captureMovingAverageBuffers.get(0));
            videoScript.set_sumOut(captureMovingAverageBuffers.get(1));
            videoScript.set_sumInSize(averages[0] - 1);
            videoScript.set_sumOutSize(averages[1] - 1);
            videoScript.set_amplification(parameterStore.getAmplification());

            // videoScript.forEach_deltaSums(captureRgbBuffer);
            videoScript.forEach_multiplySums(captureRgbBuffer, captureRgbBuffer);

            captureRgbBuffer.copyTo(mBitmap);
            mDisplayView.postInvalidate();
        }
    }

    private class ParameterStore implements SeekBar.OnSeekBarChangeListener {

        SeekBar inputSeekbar;
        SeekBar outputSeekbar;
        SeekBar amplificationSeekbar;
        TextView inputTextView;
        TextView outputTextView;
        TextView amplificationTextView;

        int inputSize;
        int outputSize;
        int amplification;

        ParameterStore(int inputSize, int outputSize, int amplification) {
            this.inputSize = inputSize;
            this.outputSize = outputSize;
            this.amplification = amplification;
        }


        void initialize() {

            inputTextView = (TextView) findViewById(R.id.inputTextView);
            outputTextView = (TextView) findViewById(R.id.outputTextView);
            amplificationTextView = (TextView) findViewById(R.id.amplificationTextView);

            inputSeekbar = (SeekBar) findViewById(R.id.inputSeekbar);
            outputSeekbar = (SeekBar) findViewById(R.id.outputSeekbar);
            amplificationSeekbar = (SeekBar) findViewById(R.id.amplificationSeekbar);

            inputSeekbar.setMax(COUNT);
            outputSeekbar.setMax(COUNT);
            amplificationSeekbar.setMax(100);

            inputSeekbar.setProgress(inputSize);
            outputSeekbar.setProgress(outputSize);
            amplificationSeekbar.setProgress(amplification);

            inputTextView.setText("" + inputSize);
            outputTextView.setText("" + outputSize);
            amplificationTextView.setText("" + amplification);

            inputSeekbar.setOnSeekBarChangeListener(this);
            outputSeekbar.setOnSeekBarChangeListener(this);
            amplificationSeekbar.setOnSeekBarChangeListener(this);
        }

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            synchronized (this) {
                if (seekBar == inputSeekbar) {
                    inputSize = progress;
                    inputTextView.setText("" + inputSize);
                } else if (seekBar == outputSeekbar) {
                    outputSize = progress;
                    outputTextView.setText("" + outputSize);
                } else if (seekBar == amplificationSeekbar) {
                    amplification = 1 + (int)Math.exp(progress / 10.0);
                    amplificationTextView.setText("" + amplification);
                }
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // NOP
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // NOP
        }

        public int getInputSize() {
            synchronized (this) {
                return inputSize;
            }
        }

        public int getOutputSize() {
            synchronized (this) {
                return outputSize;
            }
        }

        public int getAmplification() {
            synchronized (this) {
                return amplification;
            }
        }
    }

    //region Permissions
    /**
     * Return the current state of the camera permissions.
     */
    private boolean checkCameraPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);

        // Check if the Camera permission is already available.
        if (permissionState != PackageManager.PERMISSION_GRANTED) {
            // Camera permission has not been granted.
            Log.i(TAG, "CAMERA permission has NOT been granted.");
            return false;
        } else {
            // Camera permissions are available.
            Log.i(TAG, "CAMERA permission has already been granted.");
            return true;
        }
    }

    private void requestCameraPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying camera permission rationale to provide additional context.");
            Snackbar.make(rootView, R.string.camera_permission_rationale, Snackbar
                    .LENGTH_INDEFINITE)
                    .setAction(R.string.ok, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            // Request Camera permission
                            ActivityCompat.requestPermissions(VideoActivity.this,
                                    new String[]{Manifest.permission.CAMERA},
                                    REQUEST_PERMISSIONS_REQUEST_CODE);
                        }
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting camera permission");
            // Request Camera permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                findAndOpenCamera();
            } else {
                // Permission denied.

                // In this Activity we've chosen to notify the user that they
                // have rejected a core permission for the app since it makes the Activity useless.
                // We're communicating this message in a Snackbar since this is a sample app, but
                // core permissions would typically be best requested during a welcome-screen flow.

                // Additionally, it is important to remember that a permission might have been
                // rejected without asking the user for permission (device policy or "Never ask
                // again" prompts). Therefore, a user interface affordance is typically implemented
                // when permissions are denied. Otherwise, your app could appear unresponsive to
                // touches or interactions which have required permissions.
                Snackbar.make(rootView, R.string.camera_permission_denied_explanation, Snackbar
                        .LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }
    //endregion
}
