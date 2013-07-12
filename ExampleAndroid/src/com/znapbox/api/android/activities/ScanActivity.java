package com.znapbox.api.android.activities;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.gson.Gson;
import com.znapbox.api.android.R;
import com.znapbox.api.android.model.Preview;
import com.znapbox.api.android.model.Result;
import com.znapbox.api.android.util.Constants;

public class ScanActivity extends Activity {

	private static final String TAG = ScanActivity.class.getSimpleName();

	private Preview preview;

	private ImageButton snapButton;
	private ImageButton apiKeyButton;
	private Preview.FrameReceiver mreceiver;

	String apiKey = "";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.scan);

		initUI();
		
		// Get apikey from the SharedPreferences
		SharedPreferences settings = getApplicationContext().getSharedPreferences(Constants.ZNAPBOX_PREFERENCES, 0);
		apiKey = settings.getString(Constants.ZNAPBOX_APIKEY, null);
		
		
		mreceiver = new DemoFrameReceiver();
		preview.mPreviewThread = preview.new PreviewThread("Preview Thread");
		preview.mPreviewThread.start();
	}


	PictureCallback mPicture = new PictureCallback() {
		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "onPictureTaken - JPG");

			freezePreview();

			Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length);

			int w = bmp.getWidth();
			int h = bmp.getHeight();

			int neww = Constants.IMAGE_WIDTH;
			int newh = neww * h / w;
			if (newh % 2 != 0) {
				newh++;
			}

			Bitmap bmpScaled = Bitmap.createScaledBitmap(bmp, neww, newh, false);

			WebServiceTask wst = new WebServiceTask(ScanActivity.this, getString(R.string.scan_searching));
			
			ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
			bmpScaled.compress(CompressFormat.JPEG, 80, bos); 
			byte[] bitmapdata = bos.toByteArray();
			ByteArrayInputStream bs = new ByteArrayInputStream(bitmapdata);
			
			
			wst.setPostFile(bs);
			wst.setApiKey(apiKey);
			wst.execute(new String[] { Constants.QUERY_URL });
			
		}
	};

	
	public void handleResponse(String response) {
		try{
			Gson gson = new Gson();
			Result jsonResult = gson.fromJson(response, Result.class);
			
			if (jsonResult.type == null || jsonResult.type.equals(Constants.NOT_FOUND)){
				Toast.makeText(this, getString(R.string.scan_notFound), Toast.LENGTH_LONG).show();
			} else if (jsonResult.type.equals(Constants.INVALID_APIKEY)){
				Toast.makeText(this, getString(R.string.scan_invalidApiKey), Toast.LENGTH_LONG).show();
			} else {
				Toast.makeText(this, jsonResult.label, Toast.LENGTH_LONG).show();
			}
		} catch (Exception e){
			Toast.makeText(this, getString(R.string.scan_notFound), Toast.LENGTH_LONG).show();
		}
	}
	
	// some code unused in specific configurations.
	private void initUI() {
		preview = (Preview) findViewById(R.id.preview);

		snapButton = (ImageButton) findViewById(R.id.capture);
		snapButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				
				if (apiKey == null || apiKey.equals("")){
					Toast.makeText(ScanActivity.this, getString(R.string.alert_apikey), Toast.LENGTH_LONG).show();
					return;
				}
				
				snapButton.setImageResource(R.drawable.btn_ic_camera_shutter);

				if (preview.mCamera == null) {
					return;
				}

				preview.mCamera.autoFocus(new AutoFocusCallback() {
					@Override
					public void onAutoFocus(boolean success, Camera camera) {
						preview.mCamera.takePicture(null,
								null, mPicture);
					}
				});
			}
		});
		
		
		apiKeyButton = (ImageButton) findViewById(R.id.apikey);
		apiKeyButton.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				AlertDialog.Builder alert = new AlertDialog.Builder(ScanActivity.this);

				alert.setTitle(getString(R.string.settings_title));
				alert.setMessage(getString(R.string.settings_message));

				// Set an EditText view to get user input 
				final EditText input = new EditText(ScanActivity.this);
				if (apiKey != null){
					input.setText(apiKey);	
				}
				
				alert.setView(input);

				alert.setPositiveButton(getString(R.string.settings_save), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
				  String value = input.getText().toString();
				  	apiKey = value;
				  	
				  	//save the preference
				  	SharedPreferences settings = getApplicationContext().getSharedPreferences(Constants.ZNAPBOX_PREFERENCES, 0);
				  	SharedPreferences.Editor editor = settings.edit();
				  	editor.putString(Constants.ZNAPBOX_APIKEY, apiKey);
				  	editor.commit();
				  }
				});

				alert.setNegativeButton(getString(R.string.settings_cancel), new DialogInterface.OnClickListener() {
				  public void onClick(DialogInterface dialog, int whichButton) {
				    // Canceled.
				  }
				});

				alert.show();
				
			}
		});

	}


	@Override
	public void onResume() {
		super.onResume();
		preview.setFrameReceiver(mreceiver);
		if (preview.mCamera != null) {
			unfreezePreview();
		}
	}

	@Override
	public void onPause() {
		preview.stopPreview();
		super.onPause();
	}

	private void freezePreview() {
		preview.stopPreview();
		preview.PreviewCallbackScan();
	}

	private void unfreezePreview() {
		preview.startPreview();
	}

	class DemoFrameReceiver implements Preview.FrameReceiver {

		@Override
		public void onFrameReceived(byte[] frameBuffer, Size framePreviewSize) {
			if (!preview.mPreviewThreadRun.get()) {
				return;
			}

			if (frameBuffer == null) {
				return;
			}
		}
	}

	
	
	
	private class WebServiceTask extends AsyncTask<String, Integer, String> {
	     
	    private static final String TAG = "WebServiceTask";

	    // connection timeout, in milliseconds (waiting to connect)
	    private static final int CONN_TIMEOUT = 5000;
	     
	    // socket timeout, in milliseconds (waiting for data)
	    private static final int SOCKET_TIMEOUT = 20000;
	     
	    private Activity mContext = null;
	    private String processMessage = "Processing...";

	    private InputStream postFile = null;
	    private String mApiKey = null;
	    
	    private ProgressDialog pDlg = null;
	    private Exception exception = null;

	    public WebServiceTask(Activity mContext, String processMessage) {
	        this.mContext = mContext;
	        this.processMessage = processMessage;
	    }

	    public void setPostFile(InputStream iStream){
	    	postFile = iStream;
	    }
	    
	    public void setApiKey(String aKey){
	    	mApiKey = aKey;
	    }

	    private void showProgressDialog() {
	        pDlg = new ProgressDialog(mContext);
	        pDlg.setMessage(processMessage);
	        pDlg.setProgressStyle(ProgressDialog.STYLE_SPINNER);
	        pDlg.setCancelable(false);
	        pDlg.show();
	    }

	    @Override
	    protected void onPreExecute() {
	        showProgressDialog();
	    }

	    protected String doInBackground(String... urls) {
	        String url = urls[0];
	        String result = "";

	        HttpResponse response = null;
	        try{
	        	response = doResponse(url);	
	        }catch(Exception e){
	        	exception = e;
	        }

            try {
            	if (response.getEntity() != null){
            		result = inputStreamToString(response.getEntity().getContent());
            	}
            } catch (Exception e) {
                Log.e(TAG, e.getLocalizedMessage(), e);
            }

	        return result;
	    }

	    @Override
	    protected void onPostExecute(String response) {
			unfreezePreview();
			snapButton.setImageResource(R.drawable.ic_camera);
			
	    	if (exception == null){
	    		handleResponse(response);	
	    	}
	        pDlg.dismiss();
	    }
	     
	    // Establish connection and socket (data retrieval) timeouts
	    private HttpParams getHttpParams() {
	        HttpParams htpp = new BasicHttpParams();
	        HttpConnectionParams.setConnectionTimeout(htpp, CONN_TIMEOUT);
	        HttpConnectionParams.setSoTimeout(htpp, SOCKET_TIMEOUT);
	         
	        return htpp;
	    }
	     
	    private HttpResponse doResponse(String url) throws Exception {
	        HttpClient httpclient = new DefaultHttpClient(getHttpParams());
	        HttpResponse response = null;

	        try {
                HttpPost httppost = new HttpPost(url);
                // Add parameters
                if (postFile != null){
                	MultipartEntity entity = new MultipartEntity();
                	entity.addPart(Constants.FILE_PARAMETER, new InputStreamBody(postFile,""));
                	entity.addPart(Constants.API_KEY_PARAMETER, new StringBody(mApiKey) );
                	httppost.setEntity(entity);
                }
                
                response = httpclient.execute(httppost);
	        } catch (Exception e) {
	            Log.e(TAG, e.getLocalizedMessage(), e);
	            mContext.runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(mContext, R.string.error_connecting, Toast.LENGTH_LONG).show(); 
					}
				});
	            
	            throw e;
	        }
	        return response;
	    }
	     
	    private String inputStreamToString(InputStream is) {
	        String line = "";
	        StringBuilder total = new StringBuilder();

	        // Wrap a BufferedReader around the InputStream
	        BufferedReader rd = new BufferedReader(new InputStreamReader(is));
	        try {
	            // Read response until the end
	            while ((line = rd.readLine()) != null) {
	                total.append(line);
	            }
	        } catch (IOException e) {
	            Log.e(TAG, e.getLocalizedMessage(), e);
	        }

	        // Return full string
	        return total.toString();
	    }

	}

	
}
	
	



 


