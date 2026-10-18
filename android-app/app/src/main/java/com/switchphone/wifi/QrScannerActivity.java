package com.switchphone.wifi;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.*;
import android.util.Size;
import android.view.*;
import android.widget.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import com.switchphone.R;
import java.util.*;

/** Local camera decoding only. The camera and its images are released whenever this screen pauses. */
public final class QrScannerActivity extends Activity {
    private TextureView preview;private TextView hint;private Button retry;
    private HandlerThread thread;private Handler background;private CameraDevice camera;private CameraCaptureSession capture;private ImageReader images;private Surface previewSurface;
    private volatile boolean active,finished,opening;private volatile int generation;private long lastFrame,lastInvalid;
    private final MultiFormatReader decoder=new MultiFormatReader();
    private final Handler main=new Handler(Looper.getMainLooper());private int autofocus=CaptureRequest.CONTROL_AF_MODE_OFF;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);hints.put(DecodeHintType.POSSIBLE_FORMATS,Collections.singletonList(BarcodeFormat.QR_CODE));hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);decoder.setHints(hints);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xff0c131c);
        LinearLayout bar=new LinearLayout(this);root.addView(bar);hint=new TextView(this);hint.setText(R.string.qr_scan_hint);hint.setTextColor(0xffe8e4f0);hint.setTextSize(16);hint.setPadding(16,8,16,8);bar.addView(hint,new LinearLayout.LayoutParams(0,-2,1));
        Button cancel=new Button(this);cancel.setText(R.string.qr_back);cancel.setAllCaps(false);cancel.setOnClickListener(v->finish());bar.addView(cancel);
        preview=new TextureView(this);root.addView(preview,new LinearLayout.LayoutParams(-1,0,1));preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener(){
            public void onSurfaceTextureAvailable(SurfaceTexture surface,int width,int height){open();}
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface,int width,int height){}
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface){closeCamera();return true;}
            public void onSurfaceTextureUpdated(SurfaceTexture surface){}
        });
        retry=new Button(this);retry.setText(R.string.qr_retry);retry.setAllCaps(false);retry.setVisibility(View.GONE);retry.setOnClickListener(v->{closeCamera();if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},7);else open();});root.addView(retry);setContentView(root);
    }
    @Override protected void onResume(){super.onResume();active=true;finished=false;thread=new HandlerThread("Nordhub-QR");thread.start();background=new Handler(thread.getLooper());if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.CAMERA},7);else if(preview.isAvailable())open();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request!=7)return;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)open();else problem("A câmera é opcional. Permita o acesso para ler o QR ou volte e use Avançado: conexão manual.");}
    private void problem(String message){runOnUiThread(()->{if(active&&!finished){hint.setText(message);retry.setVisibility(View.VISIBLE);}});}
    private void open(){
        if(!active||finished||!preview.isAvailable()||background==null||camera!=null||opening)return;
        if(checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)return;
        final int token=++generation;opening=true;retry.setVisibility(View.GONE);CameraManager manager=getSystemService(CameraManager.class);
        try{
            String selected=null;CameraCharacteristics characteristics=null;
            for(String id:manager.getCameraIdList()){CameraCharacteristics c=manager.getCameraCharacteristics(id);if(selected==null||Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK)){selected=id;characteristics=c;if(Objects.equals(c.get(CameraCharacteristics.LENS_FACING),CameraCharacteristics.LENS_FACING_BACK))break;}}
            if(selected==null||characteristics==null){problem("Não foi encontrada uma câmera. Volte e use a conexão manual.");return;}
            android.hardware.camera2.params.StreamConfigurationMap map=characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes=map==null?null:map.getOutputSizes(ImageFormat.YUV_420_888);if(sizes==null||sizes.length==0){problem("Esta câmera não oferece o formato necessário. Use a conexão manual.");return;}
            Size[] previews=map.getOutputSizes(SurfaceTexture.class);Set<Size> supported=new HashSet<>();if(previews!=null)Collections.addAll(supported,previews);
            Size size=null;long best=Long.MAX_VALUE;for(Size candidate:sizes){if(!supported.contains(candidate))continue;long area=(long)candidate.getWidth()*candidate.getHeight(),distance=Math.abs(area-640L*480);if(distance<best){size=candidate;best=distance;}}
            if(size==null){problem("Esta câmera não oferece uma prévia compatível. Use a conexão manual.");return;}
            int[] modes=characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);autofocus=CaptureRequest.CONTROL_AF_MODE_OFF;if(modes!=null)for(int mode:modes)if(mode==CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE){autofocus=mode;break;}
            SurfaceTexture texture=preview.getSurfaceTexture();if(texture==null)return;texture.setDefaultBufferSize(size.getWidth(),size.getHeight());previewSurface=new Surface(texture);
            images=ImageReader.newInstance(size.getWidth(),size.getHeight(),ImageFormat.YUV_420_888,2);images.setOnImageAvailableListener(reader->decode(reader,token),background);
            manager.openCamera(selected,new CameraDevice.StateCallback(){
                @Override public void onOpened(CameraDevice device){if(!active||generation!=token){device.close();return;}opening=false;camera=device;configure(device,token);}
                @Override public void onDisconnected(CameraDevice device){device.close();if(camera==device)camera=null;if(active&&generation==token)problem("A câmera foi desconectada. Tente novamente ou use a conexão manual.");}
                @Override public void onError(CameraDevice device,int error){device.close();if(camera==device)camera=null;if(active&&generation==token)problem("Não foi possível abrir a câmera. Feche outro app que a esteja usando e tente novamente.");}
            },main);
        }catch(CameraAccessException|SecurityException|IllegalArgumentException|IllegalStateException e){closeCamera();problem("Não foi possível iniciar a câmera. Tente novamente ou use a conexão manual.");}
    }
    private void configure(CameraDevice device,int token){
        try{
            Surface surface=previewSurface;ImageReader reader=images;if(surface==null||reader==null||!active||token!=generation)return;
            CaptureRequest.Builder request=device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);request.addTarget(surface);request.addTarget(reader.getSurface());request.set(CaptureRequest.CONTROL_AF_MODE,autofocus);
            device.createCaptureSession(Arrays.asList(surface,reader.getSurface()),new CameraCaptureSession.StateCallback(){
                @Override public void onConfigured(CameraCaptureSession session){if(!active||token!=generation){session.close();return;}capture=session;try{session.setRepeatingRequest(request.build(),null,background);}catch(CameraAccessException|IllegalStateException e){problem("A prévia da câmera falhou. Tente novamente ou use a conexão manual.");}}
                @Override public void onConfigureFailed(CameraCaptureSession session){session.close();if(active&&token==generation)problem("Esta câmera não conseguiu iniciar a leitura. Use a conexão manual.");}
            },main);
        }catch(CameraAccessException|IllegalArgumentException|IllegalStateException e){problem("Não foi possível iniciar a leitura. Tente novamente ou use a conexão manual.");}
    }
    private synchronized void decode(ImageReader reader,int token){
        Image image=null;
        try{
            image=reader.acquireLatestImage();if(image==null||!active||finished||token!=generation)return;long now=SystemClock.elapsedRealtime();if(now-lastFrame<180)return;lastFrame=now;
            Image.Plane plane=image.getPlanes()[0];int width=image.getWidth(),height=image.getHeight();byte[] luminance=QrLuminance.read(plane.getBuffer(),width,height,plane.getRowStride(),plane.getPixelStride());
            BinaryBitmap bitmap=new BinaryBitmap(new HybridBinarizer(new PlanarYUVLuminanceSource(luminance,width,height,0,0,width,height,false)));
            String text=decoder.decodeWithState(bitmap).getText();PairingQr.parse(text);
            runOnUiThread(()->{if(active&&!finished&&token==generation){finished=true;setResult(RESULT_OK,new Intent().putExtra("pairing",text));finish();}});
        }catch(NotFoundException ignored){}
        catch(IllegalArgumentException e){long now=SystemClock.elapsedRealtime();if(now-lastInvalid>2000){lastInvalid=now;runOnUiThread(()->{if(active&&!finished)hint.setText(R.string.qr_wrong);});}}
                catch(IllegalStateException|IndexOutOfBoundsException ignored){}
        finally{decoder.reset();if(image!=null)image.close();}
    }
    private void closeCamera(){generation++;opening=false;if(capture!=null){capture.close();capture=null;}if(camera!=null){camera.close();camera=null;}if(images!=null){images.close();images=null;}if(previewSurface!=null){previewSurface.release();previewSurface=null;}}
    @Override protected void onPause(){active=false;closeCamera();HandlerThread old=thread;thread=null;background=null;if(old!=null)old.quitSafely();super.onPause();}
}
