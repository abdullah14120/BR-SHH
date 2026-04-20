package com.backup.restore.sh;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

// استدعاء المصادر المحلية (R)
import com.backup.restore.sh.R;

/* * ملاحظة: هذه الاستيرادات ستعمل الآن لأن Gradle 
 * قام بفك ضغط shizuku-api.aar و shizuku-provider.aar 
 * ودمجها في مسار البناء (Build Path) محلياً.
 */
import dev.rikka.shizuku.Shizuku;
import dev.rikka.shizuku.ShizukuRemoteProcess;
import dev.rikka.shizuku.ShizukuShell;

public class MainActivity extends AppCompatActivity {

    private EditText etPackage;
    private TextView tvLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        etPackage = findViewById(R.id.et_package);
        tvLog = findViewById(R.id.tv_log);

        checkFilesPermission();
        setupCards();
    }

    private void setupCards() {
        // تأكد أن المعرفات (IDs) تطابق ملف الـ XML الخاص بك
        setupCard(findViewById(R.id.card_uninstall), "حذف تطبيق", "حذف مع إبقاء البيانات", v -> {
            String pkg = getPkg();
            if (!pkg.isEmpty()) executeCmd("pm uninstall -k " + pkg);
        });

        setupCard(findViewById(R.id.card_reboot), "إعادة التشغيل", "إعادة تشغيل فورية", v -> executeCmd("reboot"));

        setupCard(findViewById(R.id.card_backup), "نسخ احتياطي", "حفظ في Downloads", v -> startBackup(getPkg()));

        setupCard(findViewById(R.id.card_restore), "استعادة", "استعادة من Downloads", v -> startRestore());
    }

    private void setupCard(View v, String title, String desc, View.OnClickListener l) {
        if (v == null) return;
        ((TextView) v.findViewById(R.id.title)).setText(title);
        ((TextView) v.findViewById(R.id.desc)).setText(desc);
        v.findViewById(R.id.btn_action).setOnClickListener(l);
    }

    private String getPkg() {
        return etPackage != null ? etPackage.getText().toString().trim() : "";
    }

    private void checkFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("إذن الملفات")
                    .setMessage("يرجى منح إذن الوصول للملفات")
                    .setPositiveButton("إعدادات", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }).show();
        }
    }

    private boolean isReady() {
        try {
            if (!Shizuku.pingBinder()) {
                showShizukuError();
                return false;
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(100);
                return false;
            }
            return true;
        } catch (Exception e) {
            log("Shizuku Error: " + e.getMessage());
            return false;
        }
    }

    private void showShizukuError() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Shizuku مطلوب")
                .setMessage("خدمة Shizuku لا تعمل.")
                .setPositiveButton("فتح Shizuku", (d, w) -> {
                    Intent i = getPackageManager().getLaunchIntentForPackage("dev.rikka.shizuku");
                    if (i != null) startActivity(i);
                }).show();
    }

    private void executeCmd(String cmd) {
        if (!isReady()) return;
        try {
            ShizukuShell.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            log("تم التنفيذ: " + cmd);
        } catch (Exception e) {
            log("خطأ تنفيذ: " + e.getMessage());
        }
    }

    private void startBackup(String pkg) {
        if (pkg.isEmpty() || !isReady()) return;
        new Thread(() -> {
            try {
                File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "aa.ab");
                ShizukuRemoteProcess p = ShizukuShell.newProcess(new String[]{"sh", "-c", "bu backup -noapk " + pkg}, null, null);
                InputStream is = p.getInputStream();
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = new byte[8192];
                int len;
                while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
                fos.close();
                is.close();
                runOnUiThread(() -> log("تم النسخ بنجاح"));
            } catch (Exception e) {
                runOnUiThread(() -> log("فشل النسخ: " + e.getMessage()));
            }
        }).start();
    }

    private void startRestore() {
        if (!isReady()) return;
        new Thread(() -> {
            try {
                File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "aa.ab");
                if (!f.exists()) return;
                ShizukuRemoteProcess p = ShizukuShell.newProcess(new String[]{"sh", "-c", "bu restore"}, null, null);
                OutputStream os = p.getOutputStream();
                FileInputStream fis = new FileInputStream(f);
                byte[] buf = new byte[8192];
                int len;
                while ((len = fis.read(buf)) != -1) os.write(buf, 0, len);
                os.flush();
                os.close();
                fis.close();
                runOnUiThread(() -> log("بدأت الاستعادة.."));
            } catch (Exception e) {
                runOnUiThread(() -> log("فشل الاستعادة: " + e.getMessage()));
            }
        }).start();
    }

    private void log(String m) {
        runOnUiThread(() -> {
            if (tvLog != null) tvLog.append("\n> " + m);
        });
    }
}
