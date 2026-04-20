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
import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

// استيراد ملف الـ R بشكل صريح لحل مشكلة "Package R does not exist"
import com.backup.restore.sh.R;

// استيرادات مكتبة Shizuku الرسمية
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

        // تعريف العناصر
        etPackage = findViewById(R.id.et_package);
        tvLog = findViewById(R.id.tv_log);

        // طلب صلاحيات الملفات لأندرويد 11 فما فوق
        checkFilesPermission();

        // إعداد البطاقات والأزرار
        setupCards();
    }

    private void setupCards() {
        // بطاقة حذف التطبيق
        View cardUninstall = findViewById(R.id.card_uninstall);
        setupCard(cardUninstall, "حذف تطبيق", "حذف مع إبقاء البيانات (-k)", v -> {
            String pkg = getPkg();
            if (!pkg.isEmpty()) executeCmd("pm uninstall -k " + pkg);
        });

        // بطاقة إعادة التشغيل
        View cardReboot = findViewById(R.id.card_reboot);
        setupCard(cardReboot, "إعادة التشغيل", "إعادة تشغيل فورية للجهاز", v -> executeCmd("reboot"));

        // بطاقة النسخ الاحتياطي
        View cardBackup = findViewById(R.id.card_backup);
        setupCard(cardBackup, "نسخ احتياطي (AB)", "حفظ في Downloads/aa.ab", v -> startBackup(getPkg()));

        // بطاقة الاستعادة
        View cardRestore = findViewById(R.id.card_restore);
        setupCard(cardRestore, "استعادة (AB)", "استعادة من Downloads/aa.ab", v -> startRestore());
    }

    private void setupCard(View v, String title, String desc, View.OnClickListener l) {
        if (v == null) return;
        TextView tvTitle = v.findViewById(R.id.title);
        TextView tvDesc = v.findViewById(R.id.desc);
        View btnAction = v.findViewById(R.id.btn_action);

        if (tvTitle != null) tvTitle.setText(title);
        if (tvDesc != null) tvDesc.setText(desc);
        if (btnAction != null) btnAction.setOnClickListener(l);
    }

    private String getPkg() {
        return etPackage.getText().toString().trim();
    }

    private void checkFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("إذن الملفات")
                    .setMessage("يرجى منح إذن الوصول للملفات لإجراء النسخ الاحتياطي")
                    .setPositiveButton("إعدادات", (d, w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    }).show();
        }
    }

    private boolean isReady() {
        if (!Shizuku.pingBinder()) {
            showShizukuError();
            return false;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(100);
            return false;
        }
        return true;
    }

    private void showShizukuError() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Shizuku مطلوب")
                .setMessage("خدمة Shizuku لا تعمل، يرجى تشغيلها أولاً.")
                .setPositiveButton("فتح Shizuku", (d, w) -> {
                    Intent i = getPackageManager().getLaunchIntentForPackage("dev.rikka.shizuku");
                    if (i != null) startActivity(i);
                    else Toast.makeText(this, "تطبيق Shizuku غير مثبت", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void executeCmd(String cmd) {
        if (!isReady()) return;
        try {
            ShizukuShell.newProcess(new String[]{"sh", "-c", cmd}, null, null);
            log("تم التنفيذ: " + cmd);
        } catch (Exception e) {
            log("خطأ: " + e.getMessage());
        }
    }

    private void startBackup(String pkg) {
        if (pkg.isEmpty()) {
            log("يرجى إدخال اسم الحزمة");
            return;
        }
        if (!isReady()) return;

        new Thread(() -> {
            try {
                File f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "aa.ab");
                ShizukuRemoteProcess p = ShizukuShell.newProcess(new String[]{"sh", "-c", "bu backup -noapk " + pkg}, null, null);
                
                InputStream is = p.getInputStream();
                FileOutputStream fos = new FileOutputStream(f);
                byte[] buf = new byte[16384];
                int len;
                while ((len = is.read(buf)) != -1) {
                    fos.write(buf, 0, len);
                }
                fos.close();
                is.close();
                runOnUiThread(() -> log("تم الحفظ في Downloads/aa.ab"));
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
                if (!f.exists()) {
                    runOnUiThread(() -> log("الملف aa.ab غير موجود في Downloads"));
                    return;
                }
                ShizukuRemoteProcess p = ShizukuShell.newProcess(new String[]{"sh", "-c", "bu restore"}, null, null);
                
                OutputStream os = p.getOutputStream();
                FileInputStream fis = new FileInputStream(f);
                byte[] buf = new byte[16384];
                int len;
                while ((len = fis.read(buf)) != -1) {
                    os.write(buf, 0, len);
                }
                os.flush();
                os.close();
                fis.close();
                runOnUiThread(() -> log("بدأت الاستعادة.. راجع شاشة الهاتف لتأكيد العملية."));
            } catch (Exception e) {
                runOnUiThread(() -> log("فشل الاستعادة: " + e.getMessage()));
            }
        }).start();
    }

    private void log(String m) {
        runOnUiThread(() -> tvLog.append("\n> " + m));
    }
}
