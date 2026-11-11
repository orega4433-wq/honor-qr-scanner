package com.orega.honorqr;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 101;
    private TextureView preview;
    private TextView status;
    private CameraDevice camera;
    private CameraCaptureSession session;
    private ImageReader reader;
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private boolean scanning = false;
    private long lastDecode = 0;
    private final MultiFormatReader decoder = new MultiFormatReader();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        decoder.setHints(hints);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(p,p,p,p);
        status = new TextView(this);
        status.setText("Наведите камеру на QR-код");
        status.setTextSize(18);
        status.setPadding(0,0,0,p);
        root.addView(status);
        preview = new TextureView(this);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        setContentView(root);
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture,int w,int h) { startCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture,int w,int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) { closeCamera(); return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
    }

    @Override protected void onResume() {
        super.onResume();
        cameraThread = new HandlerThread("QR-Camera");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
        if (preview != null && preview.isAvailable()) startCamera();
    }

    private void startCamera() {
        if (camera != null || cameraHandler == null || isFinishing()) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
            return;
        }
        try {
            CameraManager manager = (CameraManager)getSystemService(CAMERA_SERVICE);
            String chosen = null;
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { chosen = id; break; }
            }
            if (chosen == null) { showError("Задняя камера не найдена"); return; }
            reader = ImageReader.newInstance(640,480,ImageFormat.YUV_420_888,2);
            reader.setOnImageAvailableListener(r -> decodeFrame(r),cameraHandler);
            status.setText("Открываю камеру...");
            manager.openCamera(chosen,new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    camera = device;
                    try {
                        SurfaceTexture texture = preview.getSurfaceTexture();
                        if (texture == null) { showError("Поверхность камеры недоступна"); return; }
                        texture.setDefaultBufferSize(640,480);
                        Surface surface = new Surface(texture);
                        CaptureRequest.Builder request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                        request.addTarget(surface);
                        request.addTarget(reader.getSurface());
                        request.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        device.createCaptureSession(Arrays.asList(surface,reader.getSurface()),new CameraCaptureSession.StateCallback() {
                            @Override public void onConfigured(CameraCaptureSession s) {
                                session = s;
                                try {
                                    s.setRepeatingRequest(request.build(),null,cameraHandler);
                                    runOnUiThread(() -> status.setText("Наведите камеру на QR-код"));
                                } catch (Exception e) { showError("Запуск потока: "+e.getMessage()); }
                            }
                            @Override public void onConfigureFailed(CameraCaptureSession s) { showError("Не удалось настроить камеру"); }
                        },cameraHandler);
                    } catch (Exception e) { showError("Настройка камеры: "+e.getMessage()); }
                }
                @Override public void onDisconnected(CameraDevice device) { device.close(); camera=null; showError("Камера отключена"); }
                @Override public void onError(CameraDevice device,int error) { device.close(); camera=null; showError("Ошибка камеры: "+error); }
            },cameraHandler);
        } catch (Exception e) { showError("Открытие камеры: "+e.getClass().getSimpleName()+": "+e.getMessage()); }
    }

    private void decodeFrame(ImageReader source) {
        Image image = null;
        try {
            image = source.acquireLatestImage();
            if (image == null || scanning || System.currentTimeMillis()-lastDecode < 180) return;
            lastDecode = System.currentTimeMillis();
            int width = image.getWidth(), height = image.getHeight();
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buffer = plane.getBuffer();
            int rowStride=plane.getRowStride(),pixelStride=plane.getPixelStride();
            byte[] pixels = new byte[width*height];
            int offset=buffer.position();
            for(int y=0;y<height;y++)
                for(int x=0;x<width;x++)
                    pixels[y*width+x]=buffer.get(offset+y*rowStride+x*pixelStride);
            LuminanceSource lum = new PlanarYUVLuminanceSource(pixels,width,height,0,0,width,height,false);
            Result result = null;
            try { result=decoder.decodeWithState(new BinaryBitmap(new HybridBinarizer(lum))); }
            catch (NotFoundException ignored) {}
            finally { decoder.reset(); }
            if (result == null) {
                byte[] rotated = new byte[pixels.length];
                for(int y=0;y<height;y++) for(int x=0;x<width;x++) rotated[x*height+(height-y-1)]=pixels[y*width+x];
                try { result=decoder.decodeWithState(new BinaryBitmap(new HybridBinarizer(
                    new PlanarYUVLuminanceSource(rotated,height,width,0,0,height,width,false)))); }
                catch (NotFoundException ignored) {}
                finally { decoder.reset(); }
            }
            if(result!=null) {
                scanning=true;
                String value=result.getText();
                runOnUiThread(() -> showResult(value));
            }
        } catch (Exception e) {
            // Bad camera frames are ignored; the preview remains available.
        } finally { if(image!=null) image.close(); }
    }

    private void showResult(String value) {
        closeCamera();
        status.setText("QR-код считан");
        new AlertDialog.Builder(this).setTitle("QR-код считан").setMessage(value)
            .setPositiveButton("Открыть",(d,w)->{
                Uri uri=Uri.parse(value);
                String scheme=uri.getScheme();
                if(scheme!=null&&(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https"))) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW,uri)); }
                    catch(Exception e) { Toast.makeText(this,"Не удалось открыть ссылку",Toast.LENGTH_LONG).show(); }
                } else Toast.makeText(this,"Это не веб-ссылка",Toast.LENGTH_LONG).show();
                scanning=false; startCamera();
            })
            .setNeutralButton("Копировать",(d,w)->{
                ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("QR",value));
                scanning=false; startCamera();
            })
            .setNegativeButton("Закрыть",(d,w)->{scanning=false;startCamera();})
            .setOnCancelListener(d->{scanning=false;startCamera();})
            .show();
    }

    private void showError(String message) { runOnUiThread(()->status.setText(message)); }
    private void closeCamera() {
        if(session!=null){try{session.close();}catch(Exception ignored){}session=null;}
        if(camera!=null){try{camera.close();}catch(Exception ignored){}camera=null;}
        if(reader!=null){try{reader.close();}catch(Exception ignored){}reader=null;}
    }
    @Override protected void onPause() {
        closeCamera();
        if(cameraThread!=null){cameraThread.quitSafely();cameraThread=null;cameraHandler=null;}
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(requestCode,permissions,grants);
        if(requestCode==CAMERA_REQUEST) {
            if(grants.length>0&&grants[0]==PackageManager.PERMISSION_GRANTED)startCamera();
            else showError("Нет разрешения на камеру");
        }
    }
}
