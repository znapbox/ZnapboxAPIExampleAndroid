package com.znapbox.api.android.activities;

import static com.znapbox.api.android.util.Constants.SPLASH_TIME;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.Window;

import com.znapbox.api.android.R;


public class SplashActivity extends Activity {
	
	/** Thread de controle do tempo de apresentacao. */
    private SplashThread splashTread;
    
    /** Handler utilizado para realizar a troca de mensagem entre as Threads. */
    private Handler mHandler;
    

	
    /**
     * {@inheritDoc}
     */
    @Override
    public final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.splash);

        mHandler = new Handler() {
        	
        	@Override
        	public void handleMessage(Message msg) {
        		if(msg.what == RESULT_OK) {
        			
        	        Intent mainActivity = new Intent(SplashActivity.this, ScanActivity.class);
                    startActivity(mainActivity);

                    finish();
        		}
        	}
        	
        };
  
        
        // thread for displaying the SplashScreen
        splashTread = new SplashThread(mHandler);
        splashTread.start();    
    }

	@Override
	protected void onStart() {
		super.onStart();
	}
	
	@Override
	protected void onStop() {
		// TODO Auto-generated method stub
		super.onStop();
	}
	
    @Override
    public final boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            synchronized (splashTread) {
                 splashTread.notifyAll();
            }
        }
        return true;
    }
    
    

    
    
    class SplashThread extends Thread {
    	
    	Handler mHandler;
    	
    	
    	public SplashThread(Handler mHandler) {
    		this.mHandler = mHandler;
		}
    	
    	 @Override
         public void run() {	 
             try {
                 synchronized (this) {
                     wait(SPLASH_TIME);
                 }
             } catch (InterruptedException e) { 
             	e.printStackTrace();
             } finally {               
     			 mHandler.sendEmptyMessage(RESULT_OK);
             }
         }
    	
    }
}
