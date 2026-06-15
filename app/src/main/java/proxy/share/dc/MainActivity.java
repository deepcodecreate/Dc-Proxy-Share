package proxy.share.dc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.content.ContextCompat;

public class MainActivity extends Activity {
    private TextView txtProxyStatus, txtIpAddress, txtVpnStatus, txtHotspotStatus;
    private TextView txtDownload, txtUpload;
    private TextView txtHttpPort, txtSocksPort; 
    private Button btnStartStop, btnSettings;
    
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveUpdater = new Runnable() {
        @Override
        public void run() {
            updateUI();
            liveHandler.postDelayed(this, 1000); 
        }
    };

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);
        
        txtProxyStatus = findViewById(R.id.txtProxyStatus);
        txtIpAddress = findViewById(R.id.txtIpAddress);
        txtVpnStatus = findViewById(R.id.txtVpnStatus);
        txtHotspotStatus = findViewById(R.id.txtHotspotStatus);
        txtDownload = findViewById(R.id.txtDownload);
        txtUpload = findViewById(R.id.txtUpload);
        btnStartStop = findViewById(R.id.btnStartStop);
        btnSettings = findViewById(R.id.btnSettings);
        
        txtHttpPort = findViewById(R.id.txtHttpPort);
        txtSocksPort = findViewById(R.id.txtSocksPort);

        btnStartStop.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(MainActivity.this, ProxyService.class);
            if (ProxyService.isRunning) stopService(serviceIntent);
            else startService(serviceIntent);
            updateUI();
        });

        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        liveHandler.post(liveUpdater);
    }

    @Override
    protected void onPause() {
        super.onPause();
        liveHandler.removeCallbacks(liveUpdater);
    }

    private void updateUI() {
        int colorSuccess = ContextCompat.getColor(this, R.color.md3_success);
        int colorError = ContextCompat.getColor(this, R.color.md3_error);
        
        SharedPreferences prefs = getSharedPreferences("ProxyPrefs", MODE_PRIVATE);
        int httpPort = prefs.getInt("http_port", 8080);
        int socksPort = prefs.getInt("socks_port", 1080);
 
        txtHttpPort.setText(String.valueOf(httpPort));
        txtSocksPort.setText(String.valueOf(socksPort));

        if (ProxyService.isRunning) {
            txtProxyStatus.setText(getString(R.string.status_active));
            txtProxyStatus.setTextColor(colorSuccess);
            btnStartStop.setText(getString(R.string.btn_stop_proxy));
            txtIpAddress.setText(NetworkUtils.getLocalIpAddress());
            
            txtDownload.setText(TrafficMonitor.formatBytes(TrafficMonitor.rxBytes.get()));
            txtUpload.setText(TrafficMonitor.formatBytes(TrafficMonitor.txBytes.get()));
        } else {
            txtProxyStatus.setText(getString(R.string.status_inactive));
            txtProxyStatus.setTextColor(colorError);
            btnStartStop.setText(getString(R.string.btn_start_proxy));
            txtIpAddress.setText("0.0.0.0");
            
            txtDownload.setText("0 B");
            txtUpload.setText("0 B");
        }

        if (NetworkUtils.isVpnActive(this)) {
            txtVpnStatus.setText(getString(R.string.status_connected));
            txtVpnStatus.setTextColor(colorSuccess);
        } else {
            txtVpnStatus.setText(getString(R.string.status_disconnected));
            txtVpnStatus.setTextColor(colorError);
        }

        if (NetworkUtils.isHotspotActive(this)) {
            txtHotspotStatus.setText(getString(R.string.status_on));
            txtHotspotStatus.setTextColor(colorSuccess);
        } else {
            txtHotspotStatus.setText(getString(R.string.status_off));
            txtHotspotStatus.setTextColor(colorError);
        }
    }
}
