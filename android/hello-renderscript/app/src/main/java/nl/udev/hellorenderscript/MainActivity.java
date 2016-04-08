package nl.udev.hellorenderscript;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;

import nl.udev.hellorenderscript.fractal.FractalActivity;
import nl.udev.hellorenderscript.video.AlgorithmViewerActivity;
import nl.udev.hellorenderscript.video.VideoActivity;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HelloRenderScriptMain";
    private static final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;

    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        rootView = findViewById(R.id.demoMenu);
    }

    public void startFractal(View view) {
        Intent intent = new Intent(this, FractalActivity.class);
        startActivity(intent);
    }

    public void startDeltaMovingAverage(View view) {
        if (checkCameraPermissions()) {
            Intent intent = new Intent(this, VideoActivity.class);
            startActivity(intent);
        } else {
            requestCameraPermissions();
        }
    }

    public void startAlgorithmViewer(View view) {
        if (checkCameraPermissions()) {
            Intent intent = new Intent(this, AlgorithmViewerActivity.class);
            startActivity(intent);
        } else {
            requestCameraPermissions();
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
                            ActivityCompat.requestPermissions(MainActivity.this,
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
                // TODO: execute code that triggered the request, for now no action keep simple
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
