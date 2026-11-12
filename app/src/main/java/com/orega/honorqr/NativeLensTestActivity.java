package com.orega.honorqr;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class NativeLensTestActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The "old" HONOR route was confirmed to open the built-in scanner.
        Intent intent = new Intent(Intent.ACTION_VIEW,
                Uri.parse("hn://com.hihonor.lens/code_scan"));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        intent.setPackage("com.hihonor.lens");
        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Не удалось открыть сканер HONOR: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        } finally {
            finish();
        }
    }
}
