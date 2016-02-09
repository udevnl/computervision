package nl.udev.hellorenderscript.fractal;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.Type;
import android.support.v4.view.MotionEventCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;

import nl.udev.hellorenderscript.R;
import nl.udev.hellorenderscript.ScriptC_julia;
import nl.udev.hellorenderscript.common.ColorMapRS;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FractalActivity extends AppCompatActivity {

    private Bitmap mBitmap;

    private ImageView mDisplayView;

    private RenderScript mRS;
    private Allocation mJuliaValues;
    private Allocation mOutPixelsAllocation;
    private ScriptC_julia mJuliaScript;
    private ColorMapRS colorMapRS;

    private int mWidth;
    private int mHeight;
    private static final int FACTOR = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Ensure fullscreen hack
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);

        this.setContentView(R.layout.activity_fractal);

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mWidth = (int) (size.x);
        mHeight = (int) (size.y);

        Bitmap.Config conf = Bitmap.Config.ARGB_8888;
        mBitmap = Bitmap.createBitmap(mWidth / FACTOR, mHeight / FACTOR, conf);

        mDisplayView = (ImageView) findViewById(R.id.imageView);
        mDisplayView.setImageBitmap(mBitmap);

        mRS = RenderScript.create(this);
        mOutPixelsAllocation = Allocation.createFromBitmap(mRS, mBitmap, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
        mJuliaValues = Allocation.createTyped(
                mRS,
                new Type.Builder(mRS, Element.U8(mRS))
                        .setX(mWidth / FACTOR)
                        .setY(mHeight / FACTOR)
                        .create(),
                Allocation.USAGE_SCRIPT
        );

        mJuliaScript = new ScriptC_julia(mRS);
        mJuliaScript.set_height(mHeight / FACTOR);
        mJuliaScript.set_width(mWidth / FACTOR);
        mJuliaScript.set_precision(128);

        colorMapRS = new ColorMapRS(mRS);
        colorMapRS.addLinearGradient(0, 64, 0, 0, 0, 0, 0, 255);
        colorMapRS.addLinearGradient(64, 128, 0, 0, 0, 255, 255, 255);
        colorMapRS.addLinearGradient(128, 256, 0, 0, 255, 255, 255, 255);
        colorMapRS.setColors();

        renderJulia(-0.9259259f, 0.30855855f);
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
                renderJulia(cx, cy);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    int[][] a = new int[][]{{1,2,3,4,2},{1,2}};

    private void renderJulia(float cx, float cy) {
        Log.d("tag", "{" + cx + "," + cy + "},");
        mJuliaScript.set_cx(cx);
        mJuliaScript.set_cy(cy);
        mJuliaScript.forEach_julie(mJuliaValues);
        colorMapRS.applyColorMapToUchar(mJuliaValues, mOutPixelsAllocation);
        mOutPixelsAllocation.copyTo(mBitmap);

        mDisplayView.invalidate();
    }
}
