package com.mbppower;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.AutoFocusCallback;
import android.os.Bundle;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.content.Intent;
import android.os.AsyncTask;


import org.apache.cordova.LOG;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.lang.Math;


public class CameraActivity extends Fragment {

	public interface CameraPreviewListener {
		public void onPictureTaken(String originalPicturePath, String previewPicturePath);
	}

	private CameraPreviewListener eventListener;
	private static final String TAG = "CameraActivity";
	public FrameLayout mainLayout;
	public FrameLayout frameContainerLayout;

    private Preview mPreview;
	private boolean canTakePicture = true;

	private View view;
	private Camera.Parameters cameraParameters;
	private Camera mCamera;
	private int numberOfCameras;
	private int cameraCurrentlyLocked;

    // The first rear facing camera
    private int defaultCameraId;
	public String defaultCamera;
	public boolean tapToTakePicture;
	public boolean dragEnabled;

	public int width;
	public int height;
	public int x;
	public int y;

	//-- BL 20160212
    int displayOrientation;


	public void setEventListener(CameraPreviewListener listener){
		eventListener = listener;
	}

	private String appResourcesPackage;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
	    appResourcesPackage = getActivity().getPackageName();

	    // Inflate the layout for this fragment
	    view = inflater.inflate(getResources().getIdentifier("camera_activity", "layout", appResourcesPackage), container, false);
//		Log.d(TAG,"");
		
	    createCameraPreview();
	    return view;
    }

	@Override
	public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }
	public void setRect(int x, int y, int width, int height){
		this.x = x;
		this.y = y;
		this.width = width;
		this.height = height;
	}

	private void createCameraPreview(){
        if(mPreview == null) {
            setDefaultCameraId();

	        //set box position and size
	        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);
	        layoutParams.setMargins(x, y, 0, 0);
	        frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
	        frameContainerLayout.setLayoutParams(layoutParams);

			Log.d(TAG, "dims Cam activity: x:" + x + " y:" + y + " w:" + width + " h:" + height );

	        //video view
	        mPreview = new Preview(getActivity());
	        mainLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("video_view", "id", appResourcesPackage));
	        mainLayout.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
	        mainLayout.addView(mPreview);
	        mainLayout.setEnabled(false);
			
	        final GestureDetector gestureDetector = new GestureDetector(getActivity().getApplicationContext(), new TapGestureDetector());
			
	        getActivity().runOnUiThread(new Runnable() {
		        @Override
		        public void run() {
			        frameContainerLayout.setClickable(true);
			        frameContainerLayout.setOnTouchListener(new View.OnTouchListener() {
						
				        private int mLastTouchX;
				        private int mLastTouchY;
				        private int mPosX = 0;
				        private int mPosY = 0;

				        @Override
				        public boolean onTouch(View v, MotionEvent event) {
					        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) frameContainerLayout.getLayoutParams();
							
					        boolean isSingleTapTouch = gestureDetector.onTouchEvent(event);
					        if (event.getAction() != MotionEvent.ACTION_MOVE && isSingleTapTouch) {
						        if (tapToTakePicture) {
									Log.d(TAG,"isSingleTapTouch");
							        takePicture(0, 0);
						        }
						        return true;
					        }
					        else {
						        if (dragEnabled) {
							        int x;
							        int y;

							        switch (event.getAction()) {
								        case MotionEvent.ACTION_DOWN:
											if(mLastTouchX == 0 || mLastTouchY == 0) {
												mLastTouchX = (int)event.getRawX() - layoutParams.leftMargin;
												mLastTouchY = (int)event.getRawY() - layoutParams.topMargin;
											}
									        else{
												mLastTouchX = (int)event.getRawX();
												mLastTouchY = (int)event.getRawY();
											}
									        break;
								        case MotionEvent.ACTION_MOVE:

									        x = (int) event.getRawX();
									        y = (int) event.getRawY();

									        final float dx = x - mLastTouchX;
									        final float dy = y - mLastTouchY;

									        mPosX += dx;
									        mPosY += dy;

									        layoutParams.leftMargin = mPosX;
									        layoutParams.topMargin = mPosY;

									        frameContainerLayout.setLayoutParams(layoutParams);

									        // Remember this touch position for the next move event
									        mLastTouchX = x;
									        mLastTouchY = y;

									        break;
								        default:
									        break;
							        }
						        }
					        }
					        return true;
				        }
			        });
		        }
	        });
        }
    }
	
    private void setDefaultCameraId() {
		
		// Find the total number of cameras available
        numberOfCameras = Camera.getNumberOfCameras();
		
		int camId = defaultCamera.equals("front") ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
		
		// Find the ID of the default camera
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		for (int i = 0; i < numberOfCameras; i++) {
			Camera.getCameraInfo(i, cameraInfo);
//			Log.d(TAG, cameraInfo);
			if (cameraInfo.facing == camId) {
				defaultCameraId = camId;
				break;
			}
		}
	}
	
    private void setCameraDisplayOrientation() {
//        Camera.CameraInfo info=new Camera.CameraInfo();
//        int rotation=((Activity)getContext()).getWindowManager().getDefaultDisplay().getRotation();
        int degrees=90;
//        DisplayMetrics dm=new DisplayMetrics();
		
//        Camera.getCameraInfo(cameraId, info);
//        ((Activity)getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);
/*		
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees=0;
                break;
            case Surface.ROTATION_90:
                degrees=90;
                break;
            case Surface.ROTATION_180:
                degrees=180;
                break;
            case Surface.ROTATION_270:
                degrees=270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        	displayOrientation=(info.orientation + degrees) % 360;
        	displayOrientation=(360 - displayOrientation) % 360;
        } else {
*/
        	displayOrientation=degrees;
//        }

        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
        mCamera.setDisplayOrientation(displayOrientation);
    }


    @Override
    public void onResume() {
        super.onResume();
		
		//-- BL 20160212 hier dus!
		
        mCamera = Camera.open(defaultCameraId);
		
        if (cameraParameters != null) {
			mCamera.setParameters(cameraParameters);
        }
		
        cameraCurrentlyLocked = defaultCameraId;
        
        if(mPreview.mPreviewSize == null){
			mPreview.setCamera(mCamera, cameraCurrentlyLocked);
		} else {
			mPreview.switchCamera(mCamera, cameraCurrentlyLocked);
			mCamera.startPreview();
		}
		
		
        final FrameLayout frameContainerLayout = (FrameLayout) view.findViewById(getResources().getIdentifier("frame_container", "id", appResourcesPackage));
        ViewTreeObserver viewTreeObserver = frameContainerLayout.getViewTreeObserver();
        if (viewTreeObserver.isAlive()) {
            viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    frameContainerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    frameContainerLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
                    final RelativeLayout frameCamContainerLayout = (RelativeLayout) view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage));

                    FrameLayout.LayoutParams camViewLayout = new FrameLayout.LayoutParams(frameContainerLayout.getWidth(), frameContainerLayout.getHeight());
                    camViewLayout.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
                    frameCamContainerLayout.setLayoutParams(camViewLayout);
                }
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        // Because the Camera object is a shared resource, it's very
        // important to release it when the activity is paused.
        if (mCamera != null) {
            mPreview.setCamera(null, -1);
            mCamera.release();
            mCamera = null;
        }
    }

    public Camera getCamera() {
      return mCamera;
    }

    public void switchCamera() {
        // check for availability of multiple cameras
        if (numberOfCameras == 1) {
            //There is only one camera available
        }
		Log.d(TAG, "numberOfCameras: " + numberOfCameras);

		// OK, we have multiple cameras.
		// Release this camera -> cameraCurrentlyLocked
		if (mCamera != null) {
			mCamera.stopPreview();
			mPreview.setCamera(null, -1);
			mCamera.release();
			mCamera = null;
		}

		// Acquire the next camera and request Preview to reconfigure
		// parameters.
		mCamera = Camera.open((cameraCurrentlyLocked + 1) % numberOfCameras);

		if (cameraParameters != null) {
			mCamera.setParameters(cameraParameters);
		}

		cameraCurrentlyLocked = (cameraCurrentlyLocked + 1) % numberOfCameras;
		mPreview.switchCamera(mCamera, cameraCurrentlyLocked);

	    Log.d(TAG, "cameraCurrentlyLocked new: " + cameraCurrentlyLocked);

		// Start the preview
		mCamera.startPreview();
    }

    public void setCameraParameters(Camera.Parameters params) {
      cameraParameters = params;

      if (mCamera != null && cameraParameters != null) {
        mCamera.setParameters(cameraParameters);
      }
    }
	
    public boolean hasFrontCamera(){
        return getActivity().getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT);
    }
	
    public Bitmap cropBitmap(Bitmap bitmap, Rect rect){
        int w = rect.right - rect.left;
        int h = rect.bottom - rect.top;
        Bitmap ret = Bitmap.createBitmap(w, h, bitmap.getConfig());
        Canvas canvas= new Canvas(ret);
        canvas.drawBitmap(bitmap, -rect.left, -rect.top, null);
        return ret;
    }
	

/*
	@Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) 
    {
        if (requestCode == TAKE_PICTURE && resultCode == RESULT_OK) {  
            Bitmap photo = (Bitmap)intent.getExtras().get("data"); 
			ivThumbnailPhoto.setImageBitmap(photo);
			ivThumbnailPhoto.setVisibility(View.VISIBLE);
        }
    }
*/


	private void configure(Camera camera) 
	{
        Camera.Parameters params = camera.getParameters();
		
        // Configure image format. RGB_565 is the most common format.
        List<Integer> formats = params.getSupportedPictureFormats();

        if (formats.contains(PixelFormat.RGB_565)) {
            params.setPictureFormat(PixelFormat.RGB_565);
            Log.d(TAG,"PixelFormat.RGB_565");
        }
        else {
            params.setPictureFormat(PixelFormat.JPEG);
            Log.d(TAG,"PixelFormat.JPEG");
        }
		
		int idx = 0;
		int selIdx = 0;
        // Choose the biggest picture size supported by the hardware
        List<Camera.Size> sizes = params.getSupportedPictureSizes();
		
		float ratioScreen = (float)this.height / (float)this.width;
		
		float ratioCam,rdiff,rdiffDest=(float)3.0;
		for (Camera.Size size : sizes) {
			
			ratioCam = (float)size.width / (float)size.height;
			
			Log.d(TAG, "cam size -> w: " + size.width + " h: " + size.height + " r:" + ratioCam + " sr:" + ratioScreen );
			
			//-- let op we zijn in portrait dus height = width
			//-- HACK NIET GENERIEK MAAR JA
			rdiff = Math.abs(ratioScreen - ratioCam);
			Log.d(TAG, " size.height:" + size.height +  " > " + this.width + " rdiff:" + rdiff + " <  rdiffDest:"+ rdiffDest);
			
			if( size.height > this.width && rdiff < rdiffDest ) {
				rdiffDest = rdiff;
				selIdx = idx;
			}
			idx++;
		}
		Log.d(TAG,"CAM SELECTED = " + selIdx);
        Camera.Size size = sizes.get(selIdx);
        params.setPictureSize(size.width, size.height);
		
		Log.d(TAG, "cam SELECTED size -> w: " + size.width + " h: " + size.height);
		
//        List<String> flashModes = params.getSupportedFlashModes();
//        if (flashModes.size() > 0)
//            params.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);

        // Action mode take pictures of fast moving objects
//        List<String> sceneModes = params.getSupportedSceneModes();
//        if (sceneModes.contains(Camera.Parameters.SCENE_MODE_ACTION))
//            params.setSceneMode(Camera.Parameters.SCENE_MODE_ACTION);
//        else
          params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
		
        // if you choose FOCUS_MODE_AUTO remember to call autoFocus() on
        // the Camera object before taking a picture 
//        params.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
        
        camera.setParameters(params);
    }
	//-- BL 20160211
	public void takePicture2(final double maxWidth, final double maxHeight){
		if(!canTakePicture)
			return;
		
		canTakePicture = false;
		
		Log.d(TAG,"====>>>> takePicture2 w:"+this.width + " h:" +this.height);
		
		configure( mCamera );

		if( 1 == 1 )
		{
            mCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
		}
		else 
		{
			mCamera.autoFocus(new AutoFocusCallback() {
		        public void onAutoFocus(boolean success, Camera camera) {
		            if(success){
		            	//-- set camera parameters for taking picture
	//						configure( camera );
						//-- hier eventueel de camera parameters afhandelen
		                camera.takePicture(shutterCallback, rawCallback, jpegCallback);
		            }
		        }
		    });
		}
	}
	
	ShutterCallback shutterCallback = new ShutterCallback() {
		public void onShutter() {
//			Log.d(TAG, "onShutter'd");
		}
	};
	
	PictureCallback rawCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
//			Log.d(TAG, "onPictureTaken - raw");
			
//			final Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
//			Log.d(TAG,"saveImageTask picW:" + pic.getWidth()+ " picH:" + pic.getHeight());
			
		}
	};
	
	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			
			final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
			
			try {
				
				final Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
				Log.d(TAG,"jpegCallback picW:" + pic.getWidth()+ " picH:" + pic.getHeight());
				
				//-- rotate en scale!
				
				//scale down
				float scale = (float)height/(float)pic.getWidth();
				Bitmap scaledBitmap = Bitmap.createScaledBitmap(pic, (int)(pic.getWidth()*scale), (int)(pic.getHeight()*scale), false);
				
				final Matrix matrix = new Matrix();
				
				Log.d(TAG, "preRotate " + mPreview.getDisplayOrientation() + "deg");
				matrix.postRotate(mPreview.getDisplayOrientation());
				
				final Bitmap fixedPic = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, false);
				
				Log.d(TAG,"fixedPic width:" + fixedPic.getWidth() + " height:" + fixedPic.getHeight() );
				
				final Rect rect = new Rect(mPreview.mSurfaceView.getLeft(), mPreview.mSurfaceView.getTop(), mPreview.mSurfaceView.getRight(), mPreview.mSurfaceView.getBottom());
				
				Log.d(TAG,"mPreview Rect left:" + rect.left + " top:" + rect.top + " right:" + rect.right + " bottom:" +  rect.bottom);

				pictureView.setImageBitmap(fixedPic);
				pictureView.layout(rect.left, rect.top, rect.right, rect.bottom);

				final Rect rectPV = new Rect(pictureView.getLeft(), pictureView.getTop(), pictureView.getRight(), pictureView.getBottom());
				
				Log.d(TAG,"pictureView Rect layout left:" + rectPV.left + " top:" + rectPV.top + " right:" + rectPV.right + " bottom:" +  rectPV.bottom);

			} catch(Exception e){
				Log.d(TAG,"jpegCallback",e);
			}
			
			//-- callback vooor teruggave
			
//			final Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
//			Log.d(TAG,"saveImageTask picW:" + pic.getWidth()+ " picH:" + pic.getHeight());
			
//			new ShowImageTask().execute(data);
//			resetCam();
			Log.d(TAG, "onPictureTaken - jpeg");

			eventListener.onPictureTaken("", "");
			
			//-- we kunnen weer
			canTakePicture = true;
		}
	};
	
	private class ShowImageTask extends AsyncTask<byte[], Void, Void> {
		
		@Override
		protected Void doInBackground(byte[]... data) {
			
			final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
			
			
//			final Bitmap pic = BitmapFactory.decodeByteArray(data, 0, data.length);
//			Log.d(TAG,"saveImageTask picW:" + pic.getWidth()+ " picH:" + pic.getHeight());
			
			
//			FileOutputStream outStream = null;
/*
			// Write to SD Card
			try {
				File sdCard = Environment.getExternalStorageDirectory();
				File dir = new File (sdCard.getAbsolutePath() + "/camtest");
				dir.mkdirs();
				
				String fileName = String.format("%d.jpg", System.currentTimeMillis());
				File outFile = new File(dir, fileName);
				
				outStream = new FileOutputStream(outFile);
				outStream.write(data[0]);
				outStream.flush();
				outStream.close();
				
				Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());
				
//				refreshGallery(outFile);
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}
*/
			return null;
		}

	}	
	public void takePicture(final double maxWidth, final double maxHeight){
		final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
		if(mPreview != null) {
			
			if(!canTakePicture)
				return;
			
			canTakePicture = false;
			
			Log.d(TAG,"takePicture");
			
			mPreview.setOneShotPreviewCallback(new Camera.PreviewCallback() {
				
				@Override
				public void onPreviewFrame(final byte[] data, final Camera camera) {
					
					new Thread() {
						public void run() {
							
							//raw picture
							byte[] bytes = mPreview.getFramePicture(data, camera);
							final Bitmap pic = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
							
							Log.d(TAG,"picW:" + pic.getWidth()+ " picH:" + pic.getHeight());
							
							//scale down
							float scale = (float)pictureView.getWidth()/(float)pic.getWidth();
							Bitmap scaledBitmap = Bitmap.createScaledBitmap(pic, (int)(pic.getWidth()*scale), (int)(pic.getHeight()*scale), false);
							
							final Matrix matrix = new Matrix();
/*
							if (cameraCurrentlyLocked == Camera.CameraInfo.CAMERA_FACING_FRONT) {
								Log.d(TAG, "mirror y axis");
								matrix.preScale(-1.0f, 1.0f);
							}
*/
							Log.d(TAG, "preRotate " + mPreview.getDisplayOrientation() + "deg");
							matrix.postRotate(mPreview.getDisplayOrientation());
							
							final Bitmap fixedPic = Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix, false);
							final Rect rect = new Rect(mPreview.mSurfaceView.getLeft(), mPreview.mSurfaceView.getTop(), mPreview.mSurfaceView.getRight(), mPreview.mSurfaceView.getBottom());
							
							getActivity().runOnUiThread(new Runnable() {
								@Override
								public void run() {
									pictureView.setImageBitmap(fixedPic);
									pictureView.layout(rect.left, rect.top, rect.right, rect.bottom);
									
									if( 1 == 0 )
									{
										Bitmap finalPic = null;
										//scale final picture
										if(maxWidth > 0 && maxHeight > 0){
											final double scaleHeight = maxWidth/(double)pic.getHeight();
											final double scaleWidth = maxHeight/(double)pic.getWidth();
											final double scale  = scaleHeight < scaleWidth ? scaleWidth : scaleHeight;
											finalPic = Bitmap.createScaledBitmap(pic, (int)(pic.getWidth()*scale), (int)(pic.getHeight()*scale), false);
										}
										else{
											finalPic = pic;
										}
										
										Bitmap originalPicture = Bitmap.createBitmap(finalPic, 0, 0, (int)(finalPic.getWidth()), (int)(finalPic.getHeight()), matrix, false);
										
										//get bitmap and compress
										Bitmap picture = loadBitmapFromView(view.findViewById(getResources().getIdentifier("frame_camera_cont", "id", appResourcesPackage)));
										ByteArrayOutputStream stream = new ByteArrayOutputStream();
										picture.compress(Bitmap.CompressFormat.PNG, 80, stream);
										
										generatePictureFromView(originalPicture, picture);
									}
									else
									{
										generatePictureFromView_2();
									}
									
									canTakePicture = true;
									
									Log.d(TAG,"image ready");
								}
							});
						}
					}.start();
				}
			});
		}
		else{
			canTakePicture = true;
		}
	}
    private void generatePictureFromView_2(){
		
	    final FrameLayout cameraLoader = (FrameLayout)view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
	    cameraLoader.setVisibility(View.VISIBLE);
	    final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
		
	    new Thread() {
		    public void run() {
				
			    try {
//				    final File picFile = storeImage(picture, "_preview");
//				    final File originalPictureFile = storeImage(originalPicture, "_original");
					
					eventListener.onPictureTaken("", "");
					
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
//						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
			    catch(Exception e){
				    //An unexpected error occurred while saving the picture.
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
//						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
		    }
	    }.start();
    }
    public void clearPreview(){
	    final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
	    new Thread() {
		    public void run() {
			    try {
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
						    pictureView.setImageBitmap(null);
					    }
				    });
					
					mCamera.startPreview();
					
			    }
			    catch(Exception e){
				    //An unexpected error occurred while saving the picture.
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
		    }
	    }.start();
    }
    private void generatePictureFromView(final Bitmap originalPicture, final Bitmap picture){
		
	    final FrameLayout cameraLoader = (FrameLayout)view.findViewById(getResources().getIdentifier("camera_loader", "id", appResourcesPackage));
	    cameraLoader.setVisibility(View.VISIBLE);
	    final ImageView pictureView = (ImageView) view.findViewById(getResources().getIdentifier("picture_view", "id", appResourcesPackage));
	    new Thread() {
		    public void run() {

			    try {
				    final File picFile = storeImage(picture, "_preview");
				    final File originalPictureFile = storeImage(originalPicture, "_original");
					
					eventListener.onPictureTaken(originalPictureFile.getAbsolutePath(), picFile.getAbsolutePath());
					
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
			    catch(Exception e){
				    //An unexpected error occurred while saving the picture.
				    getActivity().runOnUiThread(new Runnable() {
					    @Override
					    public void run() {
				            cameraLoader.setVisibility(View.INVISIBLE);
						    pictureView.setImageBitmap(null);
					    }
				    });
			    }
		    }
	    }.start();
    }

    private File getOutputMediaFile(String suffix){

	    File mediaStorageDir = getActivity().getApplicationContext().getFilesDir();
	    /*if(Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED && Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED_READ_ONLY) {
		    mediaStorageDir = new File(Environment.getExternalStorageDirectory() + "/Android/data/" + getActivity().getApplicationContext().getPackageName() + "/Files");
	    }*/
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                return null;
            }
        }
        // Create a media file name
//        String timeStamp = new SimpleDateFormat("dd_MM_yyyy_HHmm_ss").format(new Date());
		String timeStamp = "tmp";
        String mImageName = "camerapreview_" + timeStamp + suffix + ".jpg";
		
		try {
//	        getActivity().getApplicationContext().deleteFile(mImageName);
	        File delFile;
	        delFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
			delFile.delete();
		}
		catch (Exception ex) {
			Log.d(TAG,  ex.getMessage() );
		}
		
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator + mImageName);
		
        return mediaFile;
    }

    private File storeImage(Bitmap image, String suffix) {
        File pictureFile = getOutputMediaFile(suffix);
        if (pictureFile != null) {
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                image.compress(Bitmap.CompressFormat.JPEG, 80, fos);
                fos.close();
                return pictureFile;
            }
            catch (Exception ex) {
				Log.d(TAG, ex.getMessage() );
            }
        }
        return null;
    }

	public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
		// Raw height and width of image
		final int height = options.outHeight;
		final int width = options.outWidth;
		int inSampleSize = 1;

		if (height > reqHeight || width > reqWidth) {

			final int halfHeight = height / 2;
			final int halfWidth = width / 2;

			// Calculate the largest inSampleSize value that is a power of 2 and keeps both
			// height and width larger than the requested height and width.
			while ((halfHeight / inSampleSize) > reqHeight && (halfWidth / inSampleSize) > reqWidth) {
				inSampleSize *= 2;
			}
		}
		return inSampleSize;
	}
	
    private Bitmap loadBitmapFromView(View v) {
        Bitmap b = Bitmap.createBitmap( v.getMeasuredWidth(), v.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        v.layout(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
        v.draw(c);
        return b;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}

/*


	Preview --------------------------------------------------------------------------------------------


*/

class Preview extends RelativeLayout implements SurfaceHolder.Callback {
    private final String TAG = "Preview";
	
    CustomSurfaceView mSurfaceView;
    SurfaceHolder mHolder;
    Camera.Size mPreviewSize;
    List<Camera.Size> mSupportedPreviewSizes;
	List<Camera.Size> mSupportedPictureSizes;
	
    Camera mCamera;
	
    int cameraId;
    int displayOrientation;
	
    Preview(Context context) {
        super(context);
		
        mSurfaceView = new CustomSurfaceView(context);
        addView(mSurfaceView);
		
        requestLayout();
		
		//-- deprecated < android 3.0
        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = mSurfaceView.getHolder();
        mHolder.addCallback(this);
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		
    }
	
    public void setCamera(Camera camera, int cameraId) {
        mCamera = camera;
        this.cameraId = cameraId;
        if (mCamera != null) {
			
			Camera.Parameters params = camera.getParameters();
            mSupportedPreviewSizes = params.getSupportedPreviewSizes();
			mSupportedPictureSizes = params.getSupportedPictureSizes();
			
			for (Camera.Size size : mSupportedPictureSizes) {
		        Log.d(TAG, "cam pic size: w: " + size.width + " h: " + size.height);
			}

			
            setCameraDisplayOrientation();
            //mCamera.getParameters().setRotation(getDisplayOrientation());
            //requestLayout();
        }
    }
	
    public int getDisplayOrientation() {
    	return displayOrientation;
    }
	
    private void setCameraDisplayOrientation() {
        Camera.CameraInfo info=new Camera.CameraInfo();
        int rotation=((Activity)getContext()).getWindowManager().getDefaultDisplay().getRotation();
        int degrees=0;
        DisplayMetrics dm=new DisplayMetrics();
		
        Camera.getCameraInfo(cameraId, info);
        ((Activity)getContext()).getWindowManager().getDefaultDisplay().getMetrics(dm);
		
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees=0;
                break;
            case Surface.ROTATION_90:
                degrees=90;
                break;
            case Surface.ROTATION_180:
                degrees=180;
                break;
            case Surface.ROTATION_270:
                degrees=270;
                break;
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
        	displayOrientation=(info.orientation + degrees) % 360;
        	displayOrientation=(360 - displayOrientation) % 360;
        } else {
        	displayOrientation=(info.orientation - degrees + 360) % 360;
        }

        Log.d(TAG, "getMetrics: dm.density:" + dm.density + " dm.densityDpi:" + dm.densityDpi + " dm.widthPixels:" + dm.widthPixels + " dm.heightPixels:" + dm.heightPixels + " dm.scaledDensity:" + dm.scaledDensity + " dm.xdpi:"+dm.xdpi + " dm.ydpi:" + dm.ydpi);
        Log.d(TAG, "screen is rotated " + degrees + "deg from natural");
        Log.d(TAG, (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front" : "back")
        	+ " camera is oriented -" + info.orientation + "deg from natural");
        Log.d(TAG, "need to rotate preview " + displayOrientation + "deg");
        mCamera.setDisplayOrientation(displayOrientation);
    }

    public void switchCamera(Camera camera, int cameraId) {
        setCamera(camera, cameraId);
        try {
			
			Log.d(TAG, "mPreviewSize.width:" + mPreviewSize.width + " mPreviewSize.height:" + mPreviewSize.height);
			
            camera.setPreviewDisplay(mHolder);
	        Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);

            //-- BL 20160211
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
			if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
			  	parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
			}
	        camera.setParameters(parameters);
        }
        catch (IOException exception) {
            Log.e(TAG, exception.getMessage());
        }
        //requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // We purposely disregard child measurements because act as a
        // wrapper to a SurfaceView that centers the camera preview instead
        // of stretching it.
//		int sw = View.MeasureSpec.getSize( widthMeasureSpec );
		Log.d(TAG,"onMeasure -> widthMeasureSpec : "+widthMeasureSpec +  " heightMeasureSpec:"+heightMeasureSpec);
		
        final int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        final int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        setMeasuredDimension(width, height);
		Log.d(TAG,"onMeasure -> width : "+width+  " height:"+height);
		
        if (mSupportedPreviewSizes != null) {
            mPreviewSize = getOptimalPreviewSize(mSupportedPreviewSizes, width, height);
        }
//		mPreviewSize.width = 800; // = Camera.Size(800,600);
//		mPreviewSize.height = 600;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
		
        if (changed && getChildCount() > 0) {
            final View child = getChildAt(0);
			
            int width = r - l;
            int height = b - t;
			
            int previewWidth = width;
            int previewHeight = height;
			
            if (mPreviewSize != null) {
                previewWidth = mPreviewSize.width;
                previewHeight = mPreviewSize.height;
				
                if(displayOrientation == 90 || displayOrientation == 270) {
                    previewWidth = mPreviewSize.height;
                    previewHeight = mPreviewSize.width;
                }
				
	            LOG.d(TAG, "previewWidth:" + previewWidth + " previewHeight:" + previewHeight);
            }
			
            int nW;
            int nH;
            int top;
            int left;

            float scale = 1.0f;

            // Center the child SurfaceView within the parent.
            if (width * previewHeight < height * previewWidth) {
                Log.d(TAG, "center horizontally");
                int scaledChildWidth = (int)((previewWidth * height / previewHeight) * scale);
                nW = (width + scaledChildWidth) / 2;
                nH = (int)(height * scale);
                top = 0;
                left = (width - scaledChildWidth) / 2;
            }
            else {
                Log.d(TAG, "center vertically");
                int scaledChildHeight = (int)((previewHeight * width / previewWidth) * scale);
                nW = (int)(width * scale);
                nH = (height + scaledChildHeight) / 2;
                top = (height - scaledChildHeight) / 2;
                left = 0;
            }
            child.layout(left, top, nW, nH);

            Log.d(TAG, "left:" + left);
            Log.d(TAG, "top:" + top);
            Log.d(TAG, "right:" + nW);
            Log.d(TAG, "bottom:" + nH);
        }
    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, acquire the camera and tell it where
        // to draw.
        try {
            if (mCamera != null) {
                mSurfaceView.setWillNotDraw(false);
                mCamera.setPreviewDisplay(holder);
            }
        } catch (IOException exception) {
            Log.e(TAG, "IOException caused by setPreviewDisplay()", exception);
        }
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        // Surface will be destroyed when we return, so stop the preview.
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }
    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;
        if (displayOrientation == 90 || displayOrientation == 270) {
            targetRatio = (double) h / w;
        }
        if (sizes == null) return null;
		
        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;
		
        int targetHeight = h;

        // Try to find an size match aspect ratio and size
        for (Camera.Size size : sizes) {
			
	        Log.d(TAG, "cam preview size: w: " + size.width + " h: " + size.height);
			
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - targetHeight) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - targetHeight);
            }
        }

        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - targetHeight) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - targetHeight);
                }
            }
        }
		
		//-- bl 20160210
		//-- overrule preview size
		for (Camera.Size size : sizes) {
			optimalSize = size;
			break;
		}

        Log.d(TAG, "optimal preview size: w: " + optimalSize.width + " h: " + optimalSize.height);
        return optimalSize;
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
	    if(mCamera != null) {
		    // Now that the size is known, set up the camera parameters and begin
		    // the preview.
			
			Log.d(TAG, "mPreviewSize.width:" + mPreviewSize.width + " mPreviewSize.height:" + mPreviewSize.height);
			
		    Camera.Parameters parameters = mCamera.getParameters();
            parameters.setPreviewSize(mPreviewSize.width, mPreviewSize.height);
            //-- BL 20160211
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
			if (parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
			  	parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
			}
		    requestLayout();
		    //mCamera.setDisplayOrientation(90);
		    mCamera.setParameters(parameters);
		    mCamera.startPreview();
	    }
    }

    public byte[] getFramePicture(byte[] data, Camera camera) {
        Camera.Parameters parameters = camera.getParameters();
        int format = parameters.getPreviewFormat();
		
        //YUV formats require conversion
        if (format == ImageFormat.NV21 || format == ImageFormat.YUY2 || format == ImageFormat.NV16) {
            int w = parameters.getPreviewSize().width;
            int h = parameters.getPreviewSize().height;
			
			Log.d(TAG,"getFramePicture -> w:"+ w + " h:" + h);
			
            // Get the YuV image
            YuvImage yuvImage = new YuvImage(data, format, w, h, null);
            // Convert YuV to Jpeg
            Rect rect = new Rect(0, 0, w, h);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(rect, 80, outputStream);
            return outputStream.toByteArray();
        }
        return data;
    }
    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        if(mCamera != null) {
            mCamera.setOneShotPreviewCallback(callback);
        }
    }
}
class TapGestureDetector extends GestureDetector.SimpleOnGestureListener{

	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return true;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e) {
		return true;
	}
}
class CustomSurfaceView extends SurfaceView implements SurfaceHolder.Callback{
    private final String TAG = "CustomSurfaceView";

    CustomSurfaceView(Context context){
        super(context);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }
}
