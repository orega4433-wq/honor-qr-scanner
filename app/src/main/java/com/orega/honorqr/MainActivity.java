package com.orega.honorqr;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.widget.Toast;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
public class MainActivity extends Activity {
 @Override public void onCreate(Bundle b) { super.onCreate(b); new IntentIntegrator(this).setDesiredBarcodeFormats(IntentIntegrator.QR_CODE).setPrompt("Наведите камеру на QR-код").setBeepEnabled(false).setOrientationLocked(false).initiateScan(); }
 @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) { super.onActivityResult(requestCode,resultCode,data); IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data); if(result==null || result.getContents()==null){finish();return;} String value=result.getContents(); new AlertDialog.Builder(this).setTitle("QR-код считан").setMessage(value).setPositiveButton("Открыть",(d,w)->{try{Uri uri=Uri.parse(value);String s=uri.getScheme();if(s!=null&&(s.equalsIgnoreCase("http")||s.equalsIgnoreCase("https")))startActivity(new Intent(Intent.ACTION_VIEW,uri));else Toast.makeText(this,"Это не веб-ссылка",Toast.LENGTH_LONG).show();}catch(Exception e){Toast.makeText(this,"Не удалось открыть ссылку",Toast.LENGTH_LONG).show();}finish();}).setNeutralButton("Копировать",(d,w)->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("QR",value));finish();}).setNegativeButton("Закрыть",(d,w)->finish()).setOnCancelListener(d->finish()).show(); }
}
