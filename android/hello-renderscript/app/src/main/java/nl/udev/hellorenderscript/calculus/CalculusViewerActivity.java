package nl.udev.hellorenderscript.calculus;

import android.app.AlertDialog;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import nl.udev.hellorenderscript.R;
import nl.udev.hellorenderscript.calculus.algorithms.GravityAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.AbstractAlgorithm;
import nl.udev.hellorenderscript.common.algoritm.parameter.AbstractParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.IntegerParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.LimitedSettingsParameter;
import nl.udev.hellorenderscript.common.algoritm.parameter.ParameterType;
import nl.udev.hellorenderscript.common.algoritm.parameter.TouchPositionParameter;
import nl.udev.hellorenderscript.video.algoritms.VectorEdgeDetectionAlgorithm;
import nl.udev.hellorenderscript.video.common.VideoCaptureListener;

public class CalculusViewerActivity extends AppCompatActivity {

    private static final String TAG = "AlgViewCalculus";

    private RenderScript rs;

    ExecutorService algorithmExecutor;
    Future<?> algorithmRunner;

    // Display part
    private Bitmap displayBitmap;
    private ImageView hmiDisplayView;
    private Allocation displayBuffer;

    private Size videoSize;
    private boolean algorithmActive = false;
    private long lastTime = 0;
    private double frameRate;

    private final List<AbstractCalculusAlgorithm> algorithmList = new ArrayList<>();
    private AbstractCalculusAlgorithm selectedAlgorithm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Ensure fullscreen hack
        {
            requestWindowFeature(Window.FEATURE_NO_TITLE);
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            super.onCreate(savedInstanceState);
        }

        setContentView(R.layout.activity_calculus_viewer);

        // Get references to the hmi elements
        hmiDisplayView = (ImageView) findViewById(R.id.imageView);

        // Add dialog to the algorithm viewer button
        findViewById(R.id.algorithmInfoButton).setOnClickListener(new AlgorithmInfoPopupHandler());

        // Initialize the algorithm parts
        rs = RenderScript.create(this);
        algorithmList.add(new GravityAlgorithm());

        algorithmExecutor = Executors.newSingleThreadExecutor();

        // Populate HMI with supported algorithms / resolutions
        initializeResolutionSelection();
        initializeAlgorithmSelection();
    }

    @Override
    protected void onResume() {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Resume");

        super.onResume();

        // Start the first algorithm at some default resolution
        if(selectedAlgorithm == null) {
            selectedAlgorithm = algorithmList.get(0);
        }

        startAlgorithm(selectedAlgorithm, new Size(512, 512));
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

        hmiDisplayView = null;
        rs.destroy();
        rs = null;

        algorithmExecutor.shutdownNow();
    }

    /**
     * Change resolution (called while running)
     *
     * @param resolution    New desired resolution
     */
    private void changeResolution(Size resolution) {
        Log.i("[" + Thread.currentThread().getName() + "] - " + TAG, "Change resolution to: " + resolution);

        if(algorithmActive) {
            stopAlgorithm();
        }
        startAlgorithm(selectedAlgorithm, resolution);
    }

    /**
     * Change the algorithm (called while running)
     *
     * @param algorithm    The new algorithm to run
     */
    private void changeAlgorithm(AbstractCalculusAlgorithm algorithm) {
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
    private void startAlgorithm(AbstractCalculusAlgorithm algorithm, Size desiredResolution) {

        videoSize = desiredResolution;

        initializeVideoBuffers(videoSize);

        // Initialize the algorithm
        this.selectedAlgorithm = algorithm;
        this.selectedAlgorithm.initialize(videoSize, rs);

        showAlgorithmHmi(selectedAlgorithm);

        algorithmRunner = algorithmExecutor.submit(new Runnable() {
            @Override
            public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    cycle();
                    Thread.yield();
                }
            }
        });

        algorithmActive = true;
    }

    private void stopAlgorithm() {
        algorithmActive = false;

        // Stop render loop
        algorithmRunner.cancel(true);

        try {
            algorithmExecutor.submit(new Runnable() {
                @Override
                public void run() {
                }
            }).get();
        } catch (InterruptedException e) {
            Log.e(TAG, "Stopping algorithm loop interrupted", e);
        } catch (ExecutionException e) {
            Log.e(TAG, "Stopping algorithm loop execution exception occurred", e);
        }

        removeAlgorithmHmi();
        selectedAlgorithm.cleanup();

        releaseVideoBuffers();
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

        boolean consumed = false;

        if(algorithmActive) {

            int action = MotionEventCompat.getActionMasked(event);

            for(AbstractParameter parameter : selectedAlgorithm.getParameters()) {
                if(parameter.getType() == ParameterType.TOUCH_POSITION) {
                    TouchPositionParameter touchParameter = (TouchPositionParameter) parameter;

                    switch (action) {
                        case (MotionEvent.ACTION_MOVE):
                            float cx, cy;
                            float x = event.getAxisValue(MotionEvent.AXIS_X);
                            float y = event.getAxisValue(MotionEvent.AXIS_Y);
                            cx = ((x / videoSize.getWidth()) * 2f) - 1f;
                            cy = ((y / videoSize.getHeight()) * 2f) - 1f;
                            touchParameter.moved(cx, cy);
                            consumed = true;
                            break;
                        case (MotionEvent.ACTION_UP):
                            touchParameter.released();
                            consumed = true;
                            break;
                    }
                }
            }
        }

        if(!consumed) {
            return super.onTouchEvent(event);
        } else {
            return true;
        }
    }

    private void cycle() {
        updateFrameRate();

        if(selectedAlgorithm != null) {
            selectedAlgorithm.cycle(displayBuffer);
        }

        displayBuffer.copyTo(displayBitmap);
        hmiDisplayView.postInvalidate();

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

    private void initializeResolutionSelection() {

        List<Size> resolutions = new ArrayList<>();
        resolutions.add(new Size(128, 128));
        resolutions.add(new Size(256, 256));
        resolutions.add(new Size(512, 512));
        resolutions.add(new Size(1024, 1024));
        resolutions.add(new Size(2048, 2048));

        Spinner spinner = (Spinner) findViewById(R.id.algorithmResolutionSpinner);
        ArrayAdapter<Size> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                resolutions
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if (algorithmActive) {
                            Size newResolution = (Size) parent.getItemAtPosition(position);
                            changeResolution(newResolution);
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
        ArrayAdapter<AbstractCalculusAlgorithm> adapter = new ArrayAdapter<>(
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
                            AbstractCalculusAlgorithm newAlgorithm = (AbstractCalculusAlgorithm) parent.getItemAtPosition(position);
                            changeAlgorithm(newAlgorithm);
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

    private class AlgorithmInfoPopupHandler implements View.OnClickListener {

        @Override
        public void onClick(View v) {
            if(selectedAlgorithm != null) {
                new AlertDialog.Builder(CalculusViewerActivity.this)
                        .setTitle(selectedAlgorithm.getName())
                        .setMessage(selectedAlgorithm.getDescription())
                        .show();
            }
        }
    }
}
