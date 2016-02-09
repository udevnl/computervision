package nl.udev.hellorenderscript.video;

import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import nl.udev.hellorenderscript.R;
import nl.udev.hellorenderscript.video.common.VideoCaptureListener;
import nl.udev.hellorenderscript.video.common.VideoCaptureProcessor;

public class VectorEdgeDetection extends AppCompatActivity {

    private static final String TAG = "EdgeDet";

    private RenderScript mRS;
    private ScriptC_gradientvector rsGradientVector;
    private ScriptC_plotpolar2d rsPlotPolar2d;
    private ScriptC_utils rsUtilsScript;
    private ScriptC_interestpoint rsInterestPoint;
    private Allocation intensityBuffer;
    private Allocation gradientVectorsBuffer;
    private Allocation polarBuffer1;
    private Allocation imageVectorsPolarBuffer;
    private Allocation kernelVectorsBuffer;
    private Allocation displayBuffer;
    private Allocation angleColorsBuffer;

    private Bitmap displayBitmap;
    private ImageView displayView;

    private VideoCaptureProcessor videoCaptureProcessor;
    private Size videoSize;
    private boolean displayActive = false;
    private volatile boolean capturing = false;
    private volatile float amplification = 2.0f;
    private volatile int kernelSize = 3;
    private volatile boolean kernelSizeChanged = false;
    private volatile boolean circularKernel = false;
    private long lastTime = 0;
    private double frameRate;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Create");

        // Ensure fullscreen hack
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_vector_edge_detection);
        displayView = (ImageView) findViewById(R.id.imageView);

        mRS = RenderScript.create(this);

        // Create the kernel scripts
        rsPlotPolar2d = new ScriptC_plotpolar2d(mRS);
        rsGradientVector = new ScriptC_gradientvector(mRS);
        rsUtilsScript = new ScriptC_utils(mRS);
        rsInterestPoint = new ScriptC_interestpoint(mRS);

        videoCaptureProcessor = new VideoCaptureProcessor(mRS);

        initializeCameraResolutionSelection();
        initializeAmplificationSlider();
        initializeKernelSizeSlider();
        initializeKernelShapeSelection();
    }

    private void changeCameraResolution(Size resolution) {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Change resolution to: " + resolution);

        if(displayActive) {
            stopAlgorithm();
        }
        startAlgorithm(resolution);
    }

    @Override
    protected void onResume() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Resume");

        super.onResume();

        startAlgorithm(new Size(352, 288));

    }

    private void startAlgorithm(Size resolution) {

        setActiveResolutionLabel(resolution);

        videoCaptureProcessor.initialize(
                (CameraManager) getSystemService(CAMERA_SERVICE),
                resolution
        );

        initializeVideoBuffers(videoCaptureProcessor.getVideoSize());
        kernelSizeChanged = true;

        videoCaptureProcessor.startCapture(new CaptureDataHandler());
        displayActive = true;
    }

    @Override
    protected void onPause() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Pause");

        super.onPause();
        stopAlgorithm();
    }

    private void stopAlgorithm() {
        displayActive = false;
        if(videoCaptureProcessor != null) {
            videoCaptureProcessor.release();
        }

        releaseVideoBuffers();

        capturing = false;
    }

    @Override
    protected void onDestroy() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Destroy");

        super.onDestroy();

        videoCaptureProcessor = null;
        displayView = null;
        rsGradientVector = null;
        rsPlotPolar2d = null;
        rsUtilsScript = null;
        rsInterestPoint = null;
        mRS.destroy();
        mRS = null;
    }

    private void initializeVideoBuffers(Size videoSize) {

        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "initializeVideoBuffers at size " + videoSize);

        this.videoSize = videoSize;

        // Create the buffers to calculate with
        
        {   // Calculated vectors buffer
            Type.Builder vectorBufferBuilder = new Type.Builder(mRS, Element.F32_2(mRS));
            vectorBufferBuilder.setX(videoSize.getWidth());
            vectorBufferBuilder.setY(videoSize.getHeight());
            gradientVectorsBuffer = Allocation.createTyped(mRS, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
            polarBuffer1 = Allocation.createTyped(mRS, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
            imageVectorsPolarBuffer = Allocation.createTyped(mRS, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
        }
        {   // Monochrome (intensity) view of image
            Type.Builder vectorBufferBuilder = new Type.Builder(mRS, Element.F32(mRS));
            vectorBufferBuilder.setX(videoSize.getWidth());
            vectorBufferBuilder.setY(videoSize.getHeight());
            intensityBuffer = Allocation.createTyped(mRS, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
        }

        {   // Kernel vectors buffer
            Type.Builder vectorBufferBuilder = new Type.Builder(mRS, Element.F32_4(mRS));
            vectorBufferBuilder.setX(kernelSize);
            vectorBufferBuilder.setY(kernelSize);
            kernelVectorsBuffer = Allocation.createTyped(mRS, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);
            
            float kernelBuffer[] = createKernelVectorBuffer(kernelSize);
            kernelVectorsBuffer.copyFrom(kernelBuffer);
        }
        
        {   // Display buffer
            Type.Builder displayBufferBuilder = new Type.Builder(mRS, Element.RGBA_8888(mRS));
            displayBufferBuilder.setX(videoSize.getWidth());
            displayBufferBuilder.setY(videoSize.getHeight());
            displayBuffer = Allocation.createTyped(mRS, displayBufferBuilder.create(), Allocation.USAGE_SCRIPT);
        }
        
        {   // Angle color conversion
            Type.Builder angleColorsBufferBuilder = new Type.Builder(mRS, Element.F32_4(mRS));
            angleColorsBufferBuilder.setX(360);
            angleColorsBuffer = Allocation.createTyped(mRS, angleColorsBufferBuilder.create(), Allocation.USAGE_SCRIPT);
            
            float colorsBuffer[] = createAngleColorsBuffer();
            angleColorsBuffer.copyFrom(colorsBuffer);
        }
        

        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        displayBitmap = Bitmap.createBitmap(videoSize.getWidth(), videoSize.getHeight(), conf);
        displayView.setImageBitmap(displayBitmap);
    }

    private static float[] createAngleColorsBuffer() {
        float[] colorsBuffer = new float[360 * 4];
        int offset = 0;
        
        // Create four gradients
        
        // 000-090 degrees, from red to yellow
        for(int c = 0; c < 90; c++) {
            // R, G, B, A
            colorsBuffer[offset++] = 1.0f;
            colorsBuffer[offset++] = (c / 90.0f);
            colorsBuffer[offset++] = 0;
            colorsBuffer[offset++] = 1;
        }
        
        // 090-180 degrees, from yellow to green
        for(int c = 0; c < 90; c++) {
            // R, G, B, A
            colorsBuffer[offset++] = 1.0f - (c / 90.0f);
            colorsBuffer[offset++] = 1.0f;
            colorsBuffer[offset++] = 0;
            colorsBuffer[offset++] = 1;
        }
        
        // 180-270 degrees, from green to cyan
        for(int c = 0; c < 90; c++) {
            // R, G, B, A
            colorsBuffer[offset++] = 0;
            colorsBuffer[offset++] = 1.0f;
            colorsBuffer[offset++] = (c / 90.0f);
            colorsBuffer[offset++] = 1;
        }
        
        // 270-360 degrees, from cyan to red
        for(int c = 0; c < 90; c++) {
            // R, G, B, A
            colorsBuffer[offset++] = (c / 90.0f);
            colorsBuffer[offset++] = 1.0f - (c / 90.0f);
            colorsBuffer[offset++] = 1.0f - (c / 90.0f);
            colorsBuffer[offset++] = 1;
        }
        
        return colorsBuffer;
    }

    private static float[] createKernelVectorBuffer(int kernelSize) {
        
        float[] kernelBuffer = new float[kernelSize * kernelSize * 4];
        float kernelCenter = (kernelSize - 1) / 2.0f;
        int offset = 0;
        
        StringBuilder bufferAsString = new StringBuilder(kernelBuffer.length * 5);
        
        for(int y = 0; y < kernelSize; y++) {
            float yp = y - kernelCenter;
            for(int x = 0; x < kernelSize; x++) {
                float xp = x - kernelCenter;

                double angleAtKernelPosition = Math.atan2(yp, xp);

                float vX = (float) Math.cos(angleAtKernelPosition);
                float vY = (float) Math.sin(angleAtKernelPosition);

                // Calculate the weight so that a circle is formed with a smooth gradient outside.
                float edgeSize = 1.0f;
                float edgeBoundaryRadius = (kernelSize / 2) - edgeSize;
                float currentRadius = (float) Math.sqrt(xp * xp + yp * yp);
                currentRadius += 0.5f; // Add 0.5 because to compensate for the kernel centering

                float vW;
                if(yp == 0 && xp == 0) {
                    // The center of the kernel is 0 because it has no valid angle
                    vW = 0.0f;
                } else if(currentRadius <= edgeBoundaryRadius) {
                    // Inside the circle
                    // Weight should be 1.0
                    vW = 1.0f;
                } else {
                    // Outside circle
                    // Weight should decay to 0.0 linearly depending on the edgeSize

                    float pixelsIntoEdge = currentRadius - edgeBoundaryRadius;

                    // Each pixel into the edge we should decay so that the pixel outside of the edge has weight 0.0.
                    // Examples:
                    //      EdgeSize=1 ==> 0.5, 0.0
                    //      EdgeSize=2 ==> 0.66, 0.33, 0.0
                    //      EdgeSize=3 ==> 0.75, 0.50, 0.25, 0.0
                    float edgeStepSize = 1.0f / (1.0f + edgeSize);

                    // Calculate the weight, but ensure it never goes below 0.0
                    float edgeFactor = 1.0f - pixelsIntoEdge * edgeStepSize;
                    vW = (float) Math.max(0.0, edgeFactor);
                }

                kernelBuffer[offset++] = vX;
                kernelBuffer[offset++] = vY;
                kernelBuffer[offset++] = vW;
                kernelBuffer[offset++] = 0; // Padding

                bufferAsString.append(String.format("(%1.2f, %1.2f, %1.2f) ", vX, vY, vW));
            }

            bufferAsString.append("\n");
        }
        
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Created kernel buffer:\n" + bufferAsString.toString());
        
        return kernelBuffer;
    }

    private static float calculateTotalKernelWeight(float[] kernelBuffer) {
        float totalWeight = 0;

        for(int c = 0; c < kernelBuffer.length / 4; c++) {
            totalWeight += kernelBuffer[c * 4 + 2];
        }

        return totalWeight;
    }

    private void releaseVideoBuffers() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Releasing video buffers.");

        intensityBuffer.destroy();
        gradientVectorsBuffer.destroy();
        polarBuffer1.destroy();
        imageVectorsPolarBuffer.destroy();
        kernelVectorsBuffer.destroy();
        displayBuffer.destroy();
        angleColorsBuffer.destroy();

        displayView.setImageBitmap(null);
        displayBitmap.recycle();
        displayBitmap = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if(displayActive) {
            int action = MotionEventCompat.getActionMasked(event);

            switch (action) {

                case (MotionEvent.ACTION_MOVE):
                    float cx, cy;
                    float x = event.getAxisValue(MotionEvent.AXIS_X);
                    float y = event.getAxisValue(MotionEvent.AXIS_Y);
                    cx = ((x / videoSize.getWidth()) * 4f) - 2f;
                    cy = ((y / videoSize.getHeight()) * 4f) - 2f;
                    return true;

                default:
                    return super.onTouchEvent(event);
            }
        } else {
            return super.onTouchEvent(event);
        }
    }

    private class CaptureDataHandler implements VideoCaptureListener {

        @Override
        public void receiveVideoFrame(Allocation capturedRgbBuffer) {

            updateFrameRate();
            updateKernelSizeIfChanged();

            // Convert RGB image to intensity (black/white) image
            rsUtilsScript.forEach_calcGreyscaleIntensity(capturedRgbBuffer, intensityBuffer);

            // Calculate the gradients on the intensity buffer
            rsGradientVector.set_sourceWidth(videoSize.getWidth());
            rsGradientVector.set_sourceHeight(videoSize.getHeight());
            rsGradientVector.set_scale(amplification);

            rsGradientVector.set_intensityBuffer(intensityBuffer);
            rsGradientVector.forEach_calcGradientVectors(gradientVectorsBuffer);

            switch (0) {

                // Show edge directions as colors
                case 0: {
                    // Convert gradient vectors to polar vectors
                    rsUtilsScript.forEach_toPolar2D(gradientVectorsBuffer, imageVectorsPolarBuffer);

                    // Plot polar vectors
                    rsPlotPolar2d.set_plotColoured_angleColors(angleColorsBuffer);
                    rsPlotPolar2d.forEach_plotColoured(imageVectorsPolarBuffer, displayBuffer);
                    break;
                }

                // Show edge direction as green points
                case 1: {
                    // Convert gradient vectors to polar vectors
                    rsUtilsScript.forEach_toPolar2D(gradientVectorsBuffer, imageVectorsPolarBuffer);

                    // Plot angles as colors
                    rsPlotPolar2d.set_plotColoured_angleColors(angleColorsBuffer);
                    rsPlotPolar2d.forEach_plotColoured(imageVectorsPolarBuffer, displayBuffer);

                    // Plot angles with offset
                    rsPlotPolar2d.set_plotOffset_offset(10);
                    rsPlotPolar2d.set_plotOffset_destinationImage(displayBuffer);
                    rsPlotPolar2d.set_plotOffset_destinationWidth(videoSize.getWidth());
                    rsPlotPolar2d.set_plotOffset_destinationHeight(videoSize.getHeight());
                    rsPlotPolar2d.forEach_plotOffset(imageVectorsPolarBuffer);
                    break;
                }

                // Show interest points
                case 2: {
                    // Convert gradient vectors to polar vectors
                    rsUtilsScript.forEach_toPolar2D(gradientVectorsBuffer, imageVectorsPolarBuffer);

                    // Plot angles as colors
                    rsPlotPolar2d.set_plotColoured_angleColors(angleColorsBuffer);
                    rsPlotPolar2d.forEach_plotColoured(imageVectorsPolarBuffer, displayBuffer);

                    // Global settings
                    rsInterestPoint.set_sourceWidth(videoSize.getWidth());
                    rsInterestPoint.set_sourceHeight(videoSize.getHeight());

                    // Calculate the amount of edge in a certain area
                    rsInterestPoint.set_areaSize(5);
                    rsInterestPoint.set_polarEdgeBuffer(imageVectorsPolarBuffer);
                    rsInterestPoint.forEach_calcInterestPoints(imageVectorsPolarBuffer, polarBuffer1);

                    // Plot the interest points
                    rsInterestPoint.set_interestPointsBuffer(polarBuffer1);
                    rsInterestPoint.set_plotImageBuffer(displayBuffer);
                    rsInterestPoint.forEach_plotInterestPoints(imageVectorsPolarBuffer);
                }
            }


            displayBuffer.copyTo(displayBitmap);
            displayView.postInvalidate();

            capturing = true;
        }
    }

    private void updateFrameRate() {
        long timeNow = System.nanoTime();
        long dt = timeNow - lastTime;
        lastTime = timeNow;

        frameRate = (frameRate * 10 + 1000000000.0 / dt) / 11.0;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                TextView label = (TextView) findViewById(R.id.edgeDetectionFramerateLabel);
                label.setText(String.format("%3.2f fps", frameRate));
            }
        });
    }

    private void updateKernelSizeIfChanged() {

        if(kernelSizeChanged) {

            kernelVectorsBuffer.destroy();

            Type.Builder vectorBufferBuilder = new Type.Builder(mRS, Element.F32_4(mRS));
            vectorBufferBuilder.setX(kernelSize);
            vectorBufferBuilder.setY(kernelSize);
            kernelVectorsBuffer = Allocation.createTyped(mRS, vectorBufferBuilder.create(), Allocation.USAGE_SCRIPT);

            float kernelBuffer[] = createKernelVectorBuffer(kernelSize);
            kernelVectorsBuffer.copyFrom(kernelBuffer);

            rsGradientVector.set_kernelBuffer(kernelVectorsBuffer);
            rsGradientVector.set_kernelSquareRadius((kernelSize - 1) / 2);
            rsGradientVector.set_totalKernelWeight(calculateTotalKernelWeight(kernelBuffer));

            kernelSizeChanged = false;
        }
    }

    private void initializeAmplificationSlider() {
        SeekBar seekBar = (SeekBar) findViewById(R.id.edgeDetectionAmplificationSeekbar);
        seekBar.setProgress((int) amplification);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                amplification = (int) Math.pow(1.1, progress);
                updateActiveAmplificationLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // NOP
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // NOP
            }
        });
        updateActiveAmplificationLabel();
    }

    private void initializeKernelSizeSlider() {
        SeekBar seekBar = (SeekBar) findViewById(R.id.edgeDetectionKernelSizeSeekbar);
        seekBar.setProgress((int) kernelSize);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if(progress == 0) {
//                    kernelSize = 1;
//                } else {
//                    kernelSize = progress * 2;
//                }
                kernelSize = 3 + progress * 2;
                kernelSizeChanged = true;
                updateActiveKernelSizeLabel();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // NOP
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // NOP
            }
        });
        updateActiveKernelSizeLabel();
    }

    private void initializeKernelShapeSelection() {
        CheckBox checkBox = (CheckBox) findViewById(R.id.edgeDetectionCircularKernelCheckbox);
        checkBox.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        circularKernel = isChecked;
                        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Set circular kernel = " + circularKernel);
                    }
                }
        );
    }

    private void initializeCameraResolutionSelection() {
        Spinner spinner = (Spinner) findViewById(R.id.edgeDetectionResolutionSpinner);
        ArrayAdapter<Size> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                videoCaptureProcessor.getAvailableResolutions(
                        (CameraManager) getSystemService(CAMERA_SERVICE)
                )
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (displayActive) {
                            Size newResolution = (Size) parent.getItemAtPosition(position);
                            if (capturing) {
                                changeCameraResolution(newResolution);
                            }
                        }
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // No action
                    }
                }
        );
    }

    private void setActiveResolutionLabel(Size resolution) {
        TextView label = (TextView) findViewById(R.id.edgeDetectionActiveResolutionLabel);
        label.setText(resolution.toString());
    }

    private void updateActiveAmplificationLabel() {
        TextView label = (TextView) findViewById(R.id.edgeDetectionActiveAmplificationLabel);
        label.setText(String.format("%2.2f", amplification));
    }

    private void updateActiveKernelSizeLabel() {
        TextView label = (TextView) findViewById(R.id.edgeDetectionActiveKernelSizeLabel);
        label.setText(kernelSize + "x" + kernelSize);
    }
}
