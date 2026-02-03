package com.phicomm.r1manager;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import com.phicomm.r1manager.server.service.AudioVisualizerService;
import com.phicomm.r1manager.server.service.MusicLedSyncService;
import com.phicomm.r1manager.server.service.XiaozhiService;
import com.phicomm.r1manager.util.NetworkUtils;

/**
 * MainActivity - Entry point of the application
 * Displays IP:Port for user to access web UI
 * Starts background service running web server
 */
public class MainActivity extends Activity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView tvIpAddress;
    private TextView tvStatus;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        tvIpAddress = findViewById(R.id.tvIpAddress);
        tvStatus = findViewById(R.id.tvStatus);

        handler = new Handler();

        // Initialize ExoPlayerService
        com.phicomm.r1manager.server.service.ExoPlayerService.getInstance().init(this);

        // Request permissions for Android 6.0+
        requestPermissions();

        // Start background service
        startAllServices();

        // Update IP display after short delay (wait for network)
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateIpDisplay();
            }
        }, 1000);
    }

    /**
     * Request runtime permissions for Android 6.0+
     */
    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO
            };

            boolean needRequest = false;
            for (String permission : permissions) {
                if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    needRequest = true;
                    break;
                }
            }

            if (needRequest) {
                requestPermissions(permissions, PERMISSION_REQUEST_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission required for WiFi scanning", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Start All Services
     */
    private void startAllServices() {
        // Start web server
        Intent serviceIntent = new Intent(this, WebServerService.class);
        startService(serviceIntent);

        // Start music services
        Intent audioVisualizerIntent = new Intent(this, AudioVisualizerService.class);
        startService(audioVisualizerIntent);

        Intent musicLedSyncIntent = new Intent(this, MusicLedSyncService.class);
        startService(musicLedSyncIntent);

        Intent xiaozhiServiceIntent = new Intent(this, XiaozhiService.class);
        startService(xiaozhiServiceIntent);
    }

    /**
     * Update IP address display
     */
    private void updateIpDisplay() {
        String ip = NetworkUtils.getLocalIpAddress();

        if (ip != null && !ip.isEmpty()) {
            int port = WebServerService.getSavedPort(this);
            String serverUrl = "http://" + ip + ":" + port;
            tvIpAddress.setText(serverUrl);
            tvStatus.setText(R.string.running);
        } else {
            tvIpAddress.setText("IP not found");
            tvStatus.setText("Check network connection");
        }
    }

    private Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            updateIpDisplay();
            handler.postDelayed(this, 5000); // Refresh every 5 seconds
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        updateIpDisplay();
        // Start periodic refresh
        handler.postDelayed(refreshRunnable, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop periodic refresh
        handler.removeCallbacks(refreshRunnable);
    }
}
