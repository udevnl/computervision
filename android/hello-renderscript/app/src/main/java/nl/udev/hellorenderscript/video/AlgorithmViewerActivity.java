package nl.udev.hellorenderscript.video;

import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import nl.udev.hellorenderscript.R;
import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.AbstractParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterUser;
import nl.udev.hellorenderscript.video.algoritms.*;
import nl.udev.hellorenderscript.video.common.VideoCaptureListener;
import nl.udev.hellorenderscript.video.common.VideoCaptureProcessor;

/**
 * Activity that supports the generic "AbstractAlgorithm".
 *
 * The activity is the wrapper that invokes the algorithm and displays the controls provided by
 * the algorithm to the user.
 */
public class AlgorithmViewerActivity extends AppCompatActivity {

    private static final String TAG = "AlgView";

    private RenderScript rs;

    // Display part
    private Bitmap displayBitmap;
    private ImageView hmiDisplayView;
    private Allocation displayBuffer;

    private VideoCaptureProcessor videoCaptureProcessor;
    private Size videoSize;
    private boolean algorithmActive = false;
    private volatile boolean capturing = false;
    private long lastTime = 0;
    private double frameRate;

    private final List<AbstractAlgorithm> algorithmList = new ArrayList<>();
    private AbstractAlgorithm selectedAlgorithm;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Ensure fullscreen hack
        {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            super.onCreate(savedInstanceState);
        }

        setContentView(R.layout.activity_algoritm_viewer);

        // Get references to the hmi elements
        hmiDisplayView = (ImageView) findViewById(R.id.imageView);

        // Initialize the algoritm parts
        rs = RenderScript.create(this);
        videoCaptureProcessor = new VideoCaptureProcessor(rs);
        algorithmList.add(new VectorEdgeDetectionAlgorithm());
        algorithmList.add(new InterestPointDetectionAlgorithm());
        algorithmList.add(new ImagePyramidAlgorithm());
        algorithmList.add(new TemporalPyramidAlgorithm());
        algorithmList.add(new IntensityAlgorithm());

        // Populate HMI with supported algorithms / resolutions
        initializeCameraResolutionSelection();
        initializeAlgorithmSelection();
    }

    @Override
    protected void onResume() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Resume");

        super.onResume();

        // Start the first algorithm at some default resolution
        startAlgorithm(algorithmList.get(0), new Size(352, 288));
    }

    @Override
    protected void onPause() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Pause");

        super.onPause();
        stopAlgorithm();
    }

    @Override
    protected void onDestroy() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Destroy");

        super.onDestroy();

        videoCaptureProcessor = null;
        hmiDisplayView = null;
        rs.destroy();
        rs = null;
    }

    /**
     * Change camera resolution (called while running)
     *
     * @param resolution    New desired camera resolution
     */
    private void changeCameraResolution(Size resolution) {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Change resolution to: " + resolution);

        if(algorithmActive) {
            stopAlgorithm();
        }
        startAlgorithm(selectedAlgorithm, resolution);
    }

    /**
     * Change the algoritm (called while running)
     *
     * @param algorithm    The new algorithm to run
     */
    private void changeAlgorithm(AbstractAlgorithm algorithm) {
        if(algorithmActive) {
            stopAlgorithm();
        }

        startAlgorithm(algorithm, videoSize);
    }

    /**
     * Starts the given algorithm with the given video resolution.
     *
     * @param algorithm            Algorithm to start
     * @param desiredResolution    Desired resolution to run at
     */
    private void startAlgorithm(AbstractAlgorithm algorithm, Size desiredResolution) {

        videoCaptureProcessor.initialize(
                (CameraManager) getSystemService(CAMERA_SERVICE),
                desiredResolution
        );

        videoSize = videoCaptureProcessor.getVideoSize();

        initializeVideoBuffers(videoSize);

        // Initialize the algorithm
        this.selectedAlgorithm = algorithm;
        this.selectedAlgorithm.initialize(videoSize, rs);

        showAlgorithmHmi(selectedAlgorithm);

        videoCaptureProcessor.startCapture(new CaptureDataHandler());
        algorithmActive = true;
    }

    private void stopAlgorithm() {
        algorithmActive = false;
        if(videoCaptureProcessor != null) {
            videoCaptureProcessor.release();
        }

        removeAlgorithmHmi();
        selectedAlgorithm.cleanup();

        releaseVideoBuffers();
        capturing = false;
    }


    private void initializeVideoBuffers(Size videoSize) {

        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "initializeVideoBuffers at size " + videoSize);

        this.videoSize = videoSize;

        // Create the buffers to calculate with

        {   // Display buffer
            Type.Builder displayBufferBuilder = new Type.Builder(rs, Element.RGBA_8888(rs));
            displayBufferBuilder.setX(videoSize.getWidth());
            displayBufferBuilder.setY(videoSize.getHeight());
            displayBuffer = Allocation.createTyped(rs, displayBufferBuilder.create(), Allocation.USAGE_SCRIPT);
        }

        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        displayBitmap = Bitmap.createBitmap(videoSize.getWidth(), videoSize.getHeight(), conf);
        hmiDisplayView.setImageBitmap(displayBitmap);
    }

    private void releaseVideoBuffers() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Releasing video buffers.");
        displayBuffer.destroy();
        hmiDisplayView.setImageBitmap(null);
        displayBitmap.recycle();
        displayBitmap = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        if(algorithmActive) {
            int action = MotionEventCompat.getActionMasked(event);

            switch (action) {

                case (MotionEvent.ACTION_MOVE):
                    float cx, cy;
                    float x = event.getAxisValue(MotionEvent.AXIS_X);
                    float y = event.getAxisValue(MotionEvent.AXIS_Y);
                    cx = ((x / videoSize.getWidth()) * 4f) - 2f;
                    cy = ((y / videoSize.getHeight()) * 4f) - 2f;

                    // TODO: forward touch action to active algorithm

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

            if(selectedAlgorithm != null) {
                selectedAlgorithm.process(capturedRgbBuffer, displayBuffer);
            }

            displayBuffer.copyTo(displayBitmap);
            hmiDisplayView.postInvalidate();

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
                TextView label = (TextView) findViewById(R.id.algorithmFpsLabel);
                label.setText(String.format("%s, %3.2f fps", videoSize, frameRate));
            }
        });
    }

    private void initializeCameraResolutionSelection() {
        Spinner spinner = (Spinner) findViewById(R.id.algorithmResolutionSpinner);
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
                        if (algorithmActive) {
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

    private void initializeAlgorithmSelection() {
        Spinner spinner = (Spinner) findViewById(R.id.algorithmSelectionSpinner);
        ArrayAdapter<AbstractAlgorithm> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                algorithmList
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (algorithmActive) {
                            AbstractAlgorithm newAlgorithm = (AbstractAlgorithm) parent.getItemAtPosition(position);
                            if (capturing) {
                                changeAlgorithm(newAlgorithm);
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

    private void showAlgorithmHmi(AbstractAlgorithm selectedAlgorithm) {
        int counter = 1;
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.algorithmParameters);


        for(final AbstractParameter parameter : selectedAlgorithm.getParameters()) {

            switch (parameter.getType()) {
                case INTEGER:
                    counter = addIntegerControls(
                            counter,
                            linearLayout,
                            (IntegerParameter) parameter
                    );
                    break;
                case LIMITED_SETTINGS:
                    counter = addSelectControls(
                            counter,
                            linearLayout,
                            (LimitedSettingsParameter) parameter
                    );
            }
        }
    }

    /**
     * Adds a control to the given container for the given parameter.
     */
    private int addIntegerControls(int startId, LinearLayout container, final IntegerParameter intParameter) {

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);


        // Add a label
        final TextView valueLabel = new TextView(this);
        valueLabel.setId(R.id.algorithmParameters + startId++);
        container.addView(valueLabel, params);

        // Create the function that can be used to update the label
        final Runnable labelUpdateFunction = new Runnable() {
            @Override
            public void run() {
                valueLabel.setText(intParameter.getName() + ", " + intParameter.getDisplayValue());
            }
        };

        // Add a SeekBar to change the value
        SeekBar valueSetter = new SeekBar(this);
        valueSetter.setId(R.id.algorithmParameters + startId++);

        int range = intParameter.getMaxValue() - intParameter.getMinValue();
        valueSetter.setMax(range);
        container.addView(valueSetter, params);

        // Set the seekBar to the current value provided by the algorithm
        valueSetter.setProgress(intParameter.getCurrentValue());

        valueSetter.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        intParameter.setValue(progress + intParameter.getMinValue());
                        labelUpdateFunction.run();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // NOP
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        // NOP
                    }
                }
        );

        // Set the label to the current value
        labelUpdateFunction.run();

        return startId;
    }

    private int addSelectControls(int startId, LinearLayout container, final LimitedSettingsParameter parameter) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        // Add a Spinner to change the value
        Spinner valueSetter = new Spinner(this);
        valueSetter.setId(R.id.algorithmParameters + startId++);

        //noinspection unchecked, guaranteed by the parameter
        ArrayAdapter adapter = new ArrayAdapter(
                this,
                android.R.layout.simple_spinner_item,
                parameter.getPossibleValues()
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        valueSetter.setAdapter(adapter);
        valueSetter.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        //noinspection unchecked, guaranteed by the parameter
                        parameter.setValue(parent.getItemAtPosition(position));
                    }

                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {
                        // No action
                    }
                }
        );

        container.addView(valueSetter, params);

        // Set the Spinner to the current value provided by the algorithm
        int selectedIndex = parameter.getPossibleValues().indexOf(parameter.getCurrentValue());
        valueSetter.setSelection(selectedIndex);

        return startId;
    }

    private void removeAlgorithmHmi() {
        LinearLayout linearLayout = (LinearLayout) findViewById(R.id.algorithmParameters);
        linearLayout.removeAllViews();
    }
}
