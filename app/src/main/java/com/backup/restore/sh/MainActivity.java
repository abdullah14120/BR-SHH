package com.backup.restore.sh;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {

    private EditText etPackage;
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etPackage = findViewById(R.id.et_package);
        tvLog = findViewById(R.id.tv_log);

        setupCards();
    }

    private void setupCards() {
        // بطاقة حذف التطبيق
        setupCard(findViewById(R.id.card_uninstall), "حذف تطبيق", v -> {
            executeShizukuCmd("pm uninstall " + getPkg());
        });

        // بطاقة إعادة التشغيل
        setupCard(findViewById(R.id.card_reboot), "إعادة التشغيل", v -> {
            executeShizukuCmd("reboot");
        });
    }

    private void setupCard(View card, String title, View.OnClickListener listener) {
        if (card == null) return;
        TextView tvTitle = card.findViewById(R.id.title);
        if (tvTitle != null) tvTitle.setText(title);
        
        View btn = card.findViewById(R.id.btn_action);
        if (btn != null) btn.setOnClickListener(listener);
    }

    private String getPkg() {
        return etPackage.getText().toString().trim();
    }

    // هذه هي الدالة المفتاحية التي ستعدلها في الـ Smali
    private void executeShizukuCmd(String cmd) {
        log("الطلب: " + cmd);
        // لاحقاً في MT Manager:
        // قم باستدعاء ShizukuShell.newProcess هنا يدوياً
    }

    private void log(String m) {
        runOnUiThread(() -> tvLog.append("\n> " + m));
    }
}
