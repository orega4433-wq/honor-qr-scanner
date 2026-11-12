package com.orega.honorqr;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;

public class NativeLensTestActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int)(20 * getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);
        status = new TextView(this);
        status.setTextSize(17);
        boolean granted = checkSelfPermission("com.hihonor.docscan.permission.ACCESS") == PackageManager.PERMISSION_GRANTED;
        status.setText("Разрешение HONOR docscan: " + (granted ? "ПОЛУЧЕНО" : "НЕ ПОЛУЧЕНО") + "\nНажмите маршрут для проверки.");
        root.addView(status);
        add(root, "HONOR QR (новый)", "qr_code");
        add(root, "HONOR QR (старый)", "code_scan");
        add(root, "HONOR QR (touch)", "touch_qr_code");
        Button fallback = new Button(this);
        fallback.setText("Наш сканер (резервный)");
        fallback.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        root.addView(fallback);
        setContentView(root);
        // Verified on HONOR: code_scan opens the native QR scanner.
        launchNative("code_scan");
    }
    private void add(LinearLayout root, String title, String path) {
        Button b = new Button(this);
        b.setText(title);
        b.setOnClickListener(v -> {
            launchNative(path);
        });
        root.addView(b, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }
    private void launchNative(String path) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("hn://com.hihonor.lens/" + path));
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.setPackage("com.hihonor.lens");
            try {
                startActivity(i);
                status.setText("Отправлен запрос: " + path + ". Проверьте, какой экран открылся.");
            } catch (SecurityException e) {
                status.setText("Запуск запрещён: " + path + "\n" + e.getMessage());
            } catch (Exception e) {
                status.setText("Ошибка: " + path + "\n" + e.toString());
            }
    }
}
