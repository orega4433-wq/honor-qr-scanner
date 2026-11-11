package com.orega.honorqr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 101;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        TextView title = new TextView(this);
        title.setText("QR Сканер");
        title.setTextSize(24);
        layout.addView(title);
        status = new TextView(this);
        status.setText("Нажмите кнопку, чтобы открыть камеру.");
        status.setPadding(0, pad, 0, pad);
        layout.addView(status);
        Button scan = new Button(this);
        scan.setText("Сканировать QR");
        scan.setOnClickListener(v -> startScanner());
        layout.addView(scan);
        setContentView(layout);
    }

    private void startScanner() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        try {
            status.setText("Открываю камеру...");
            new IntentIntegrator(this)
                .setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                .setPrompt("Наведите камеру на QR-код")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
                .initiateScan();
        } catch (Exception error) {
            status.setText("Не удалось открыть сканер: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == CAMERA_REQUEST) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startScanner();
            else status.setText("Для сканирования нужен доступ к камере.");
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) return;
        if (result.getContents() == null) {
            status.setText("Сканирование отменено. Нажмите кнопку для повторного запуска.");
            return;
        }
        String value = result.getContents();
        status.setText("QR-код считан.");
        new AlertDialog.Builder(this).setTitle("QR-код считан").setMessage(value)
            .setPositiveButton("Открыть", (dialog, which) -> {
                try {
                    Uri uri = Uri.parse(value);
                    String scheme = uri.getScheme();
                    if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")))
                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    else Toast.makeText(this, "Это не веб-ссылка", Toast.LENGTH_LONG).show();
                } catch (Exception error) {
                    Toast.makeText(this, "Не удалось открыть ссылку", Toast.LENGTH_LONG).show();
                }
            })
            .setNeutralButton("Копировать", (dialog, which) -> {
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                    .setPrimaryClip(ClipData.newPlainText("QR", value));
            })
            .setNegativeButton("Закрыть", (dialog, which) -> {})
            .show();
    }
}
