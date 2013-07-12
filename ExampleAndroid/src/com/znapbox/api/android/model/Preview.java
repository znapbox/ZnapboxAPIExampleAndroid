
package com.znapbox.api.android.model;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;
import android.widget.Toast;

import com.znapbox.api.android.R;

public class Preview extends SurfaceView implements SurfaceHolder.Callback {
   
	private boolean DEBUG =true;
    
	private static final String TAG = Preview.class.getSimpleName();
    
    public static final int SCAN = 1; 
    
    public static final int IMAGE_COPIED = 2;
    
    private SurfaceHolder mHolder;
    
    public Camera mCamera;

    public Size mPreviewSize;
    
    public ScanningHandler mPreviewHandler;
    
    public Thread mPreviewThread;
    
    public AtomicBoolean mPreviewThreadRun = new AtomicBoolean(false);
    
    private int angle;
    
    private byte[] mLastFrameCopy;

    private FrameReceiver mFrameReceiver;

    private Size mFramePreviewSize;
    
    public interface FrameReceiver {
        public void onFrameReceived(byte[] frameBuffer, Size framePreviewSize);
    }
    
    public Preview(Context context) {
        this(context, null);		
    }

    public Preview(Context context, AttributeSet attrs) {
        super(context, attrs);
        mHolder = getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private Size getOptimalSize(List<Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.2;
        double targetRatio = (double) w / h;
        
        if (sizes == null)
            return null;

        Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        int targetHeight = h;
        int targetWidth = w;

        // Try to find an size match aspect ratio and size
        for (Size size : sizes) {
            double ratio = (double) size.width / size.height;
            boolean fitToView = size.width <= w && size.height <= h;
            if (!fitToView) {
                // we can not use preview size bigger than surface dimensions
                // skipping
                continue;
            }
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) {
                continue;
            }

            double hypot = Math.hypot(size.height - targetHeight, size.width - targetWidth);
            if (hypot < minDiff) {
                optimalSize = size;
                minDiff = hypot;
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Size size : sizes) {
                if (size.width > w || size.height > h) {
                    // we can not use preview size bigger than surface
                    // dimensions
                    continue;
                }

                double hypot = Math.hypot(size.height - targetHeight, size.width - targetWidth);
                if (hypot < minDiff) {
                    optimalSize = size;
                    minDiff = hypot;
                }
            }
        }

        if (optimalSize == null) {
        	optimalSize = sizes.get(0);
        }
        if (DEBUG) Log.d(TAG, "optimalSize.width=" + optimalSize.width + ", optimalSize.height="
                + optimalSize.height);

        return optimalSize;
    }

    @SuppressLint("InlinedApi")
	@Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    	
    	if (mCamera == null) {
            return;
        }
        Camera.Parameters params = mCamera.getParameters();
        Display display = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
                .getDefaultDisplay();

        switch (display.getRotation()) {
            case Surface.ROTATION_0:
                angle = 90;
                break;
            case Surface.ROTATION_90:
                angle = 0;
                break;
            case Surface.ROTATION_180:
                angle = 270;
                break;
            case Surface.ROTATION_270:
                angle = 180;
                break;
            default:
                throw new AssertionError("Wrong surface rotation value");
        }

        setDisplayOrientation(params, angle);
        
        if (mPreviewSize == null) {
            // h and w get inverted on purpose
            mPreviewSize = getOptimalSize(params.getSupportedPreviewSizes(), width > height ? width
                    : height, width > height ? height : width);
        }
        	
        params.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
        List<String> focusModes = params.getSupportedFocusModes();
        if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
        if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        
        mCamera.setParameters(params);
        
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.e(TAG, "Can't set preview display", e);
        }
        
        startPreview();
        
		mFramePreviewSize = mCamera.getParameters().getPreviewSize();
	    
	    int bitsPerPixel = 12;
        mLastFrameCopy = new byte[mFramePreviewSize.height*mFramePreviewSize.width * bitsPerPixel / 8];
        PreviewCallbackScan();
        mPreviewThreadRun.set(true);
    }
    

    
    public void stopPreview() {
    	if (mCamera!=null) mCamera.cancelAutoFocus();
    	if (mCamera!=null) mCamera.stopPreview();
    }	
    
    public void startPreview(){
    	if (mCamera!=null){
    		mCamera.startPreview();
    	}
    }

    @SuppressLint("NewApi")
	@Override
    public void surfaceCreated(SurfaceHolder holder) {
    	if(mCamera==null){
    		try {
    					
    			mCamera = Camera.open();
    		} catch (RuntimeException e) {
    			Toast.makeText(getContext(),
      	    		  getContext().getString(R.string.scan_error_cameare_in_use),
     	              Toast.LENGTH_LONG).show();
    		}
    	}
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    	
        if (mCamera != null) {
            synchronized (this) {
            	mCamera.setPreviewCallback(null);           	
                mCamera.stopPreview();
                mCamera.release();
                mCamera = null;
            }
        }
    }

    private void setDisplayOrientation(Camera.Parameters params, int angle) {
        try {
            Method method = mCamera.getClass().getMethod("setDisplayOrientation", new Class[] {
                int.class
            });
            if (method != null)
                method.invoke(mCamera, new Object[] {
                    angle
                });
        } catch (Exception e) {
        	if (DEBUG) Log.d(TAG, "Can't call Camera.setDisplayOrientation on this device, trying another way");
            if (angle == 90 || angle == 270) params.set("orientation", "portrait");
            else if (angle == 0 || angle == 180)  params.set("orientation", "landscape");
        }
        params.setRotation(angle);
    }

    
    public class PreviewThread extends Thread{
    	
    	public PreviewThread(String string){
    		super(string);
    	}

    	@Override
    	public void run() {
    		Looper.prepare();
    		Thread.currentThread().setPriority(MIN_PRIORITY);
    		mPreviewHandler = new ScanningHandler();
    		Looper.loop();
    	};
    }

    public void setFrameReceiver(FrameReceiver receiver) {
        if (DEBUG) Log.d(TAG,"set Frame Receiver");
        mFrameReceiver = receiver;
        
    }

    private Object mLastFrameCopyLock = new Object();
    
    public void copyLastFrame(byte[] frame) {
    	
    	synchronized(mLastFrameCopyLock){
    		if (DEBUG) Log.d(TAG,"copying frame");
	        System.arraycopy(frame, 0, mLastFrameCopy, 0, frame.length);
    	}
    	mPreviewHandler.obtainMessage(IMAGE_COPIED).sendToTarget();
    }
    
    public byte[] getLastFrameCopy() {
    	
    	synchronized(mLastFrameCopyLock){
    		return mLastFrameCopy;
    	}
    } 
    
    public void scan(){
    	if (DEBUG) Log.d(TAG,"<<<<<<<<<<<<<< scan called >>>>>>>>>>>>>>>>");
    	removeAllMessages();
    	mPreviewHandler.obtainMessage(SCAN).sendToTarget();
    }
    
    /**
     * @return the default angle of the camera
     */
    
    public int getAngle(){
    	return angle;
    }
    
     public void PreviewCallbackScan(){
    	
    	 mCamera.setPreviewCallback(null);	

    }
     
     
     public class ScanningHandler extends Handler{
    	 
    	 @Override
    	 public void handleMessage(Message message){
    		 switch(message.what){
    		 
    		 	case(SCAN):
    		 		if (mPreviewThreadRun.get()){
    		 			mCamera.addCallbackBuffer(mLastFrameCopy);
    		 			break;
    		 		}
    		 	break;
    		 
    		 	case(IMAGE_COPIED):
    		 		if (mPreviewThreadRun.get()){
    		 			if (DEBUG)  Log.d(TAG,"frame copied");
    		 			mFrameReceiver.onFrameReceived(getLastFrameCopy(), mFramePreviewSize);
    		 			break;
    		 		}
    		 	break;
    		}
    	}
    	 
     }
     
     public void removeAllMessages(){
    	 if (mPreviewHandler != null){
    		 mPreviewHandler.removeMessages(SCAN);
    		 mPreviewHandler.removeMessages(IMAGE_COPIED);
    	 }
     }

}
