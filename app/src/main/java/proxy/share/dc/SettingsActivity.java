package proxy.share.dc;

import android.app.Activity;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import com.google.android.material.materialswitch.MaterialSwitch;
import android.widget.Toast;

public class SettingsActivity extends AppCompatActivity {
    private EditText edtHttpPort, edtSocksPort;
    private MaterialSwitch switchLanguage;
    private Button btnSaveSettings;
    private SharedPreferences prefs;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        edtHttpPort = findViewById(R.id.edtHttpPort);
        edtSocksPort = findViewById(R.id.edtSocksPort);
        switchLanguage = findViewById(R.id.switchLanguage);
        btnSaveSettings = findViewById(R.id.btnSaveSettings);

        prefs = getSharedPreferences("ProxyPrefs", MODE_PRIVATE);
        
        edtHttpPort.setText(String.valueOf(prefs.getInt("http_port", 8080)));
        edtSocksPort.setText(String.valueOf(prefs.getInt("socks_port", 1080)));
        
        String currentLang = prefs.getString("app_lang", "fa");
        switchLanguage.setChecked(currentLang.equals("fa"));

        btnSaveSettings.setOnClickListener(v -> {
            try {
                int http = Integer.parseInt(edtHttpPort.getText().toString().trim());
                int socks = Integer.parseInt(edtSocksPort.getText().toString().trim());

                String newLang = switchLanguage.isChecked() ? "fa" : "en";

                prefs.edit()
                        .putInt("http_port", http)
                        .putInt("socks_port", socks)
                        .putString("app_lang", newLang)
                        .apply();

                Toast.makeText(this, "Saved / ذخیره شد", Toast.LENGTH_SHORT).show();
                
                Intent intent = new Intent(this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();

            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid Port!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
