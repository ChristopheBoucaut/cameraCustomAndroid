package org.geneanet.customcamera;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import org.geneanet.customcamera.CameraPreview;
import org.geneanet.customcamera.ManagerCamera;
import org.geneanet.customcamera.TransferBigData;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

/** Activity to use customCamera. */
public class CameraActivity extends Activity {
  
  // Camera resource.
  private Camera customCamera = null;
  // Distance between fingers for the zoom.
  private static float distanceBetweenFingers;
  // Enable miniature mode.
  private boolean modeMiniature = false;
  // The image in Bitmap format of the preview photo.
  private Bitmap photoTaken = null;

  public static final int DEGREE_0 = 0;
  public static final int DEGREE_90 = 90;
  public static final int DEGREE_180 = 180;
  public static final int DEGREE_270 = 270;

  /**
   * To get camera resource or stop this activity.
   * 
   * @return boolean
   */
  protected boolean initCameraResource() {
    customCamera = ManagerCamera.getCameraInstance();

    if (customCamera == null) {
      this.setResult(2,
          new Intent().putExtra("errorMessage", "Camera is unavailable."));
      this.finish();

      return false;
    }

    return true;
  }
  
  /** Method onCreate. Handle the opacity seekBar and general configuration. */
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    /* Remove title bar */
    this.requestWindowFeature(Window.FEATURE_NO_TITLE);
    /* Remove notification bar */
    this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);

    setContentView(R.layout.activity_camera_view);
    setBackground();

    // The opacity bar
    SeekBar switchOpacity = (SeekBar) findViewById(R.id.switchOpacity);

    // Event on change opacity.
    switchOpacity.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
      int progress = 0;

      @Override
      public void onProgressChanged(SeekBar seekBar, int progressValue,
          boolean fromUser) {
        progress = progressValue;
        ImageView background = (ImageView) findViewById(R.id.background);
        float newOpacity = (float) (progress * 0.1);
        background.setAlpha(newOpacity);
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {}

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {}
    });

    if (!this.getIntent().getBooleanExtra("miniature", true)) {
      Button miniature = (Button) findViewById(R.id.miniature);
      miniature.setVisibility(View.INVISIBLE);
    }

    ImageButton imgIcon = (ImageButton)findViewById(R.id.capture);
    final Activity currentActivity = this;
    
    this.setCameraBackgroundColor(this.getIntent().getStringExtra("cameraBackgroundColor"));
    
    imgIcon.setOnTouchListener(new View.OnTouchListener() { 
      @Override
      public boolean onTouch(View view, MotionEvent event) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            ((CameraActivity) currentActivity).setCameraBackgroundColor(
                currentActivity.getIntent().getStringExtra("cameraBackgroundColorPressed"));
            break;
          case MotionEvent.ACTION_UP:
            view.performClick();
            ((CameraActivity) currentActivity).setCameraBackgroundColor(
                currentActivity.getIntent().getStringExtra("cameraBackgroundColor"));
            ((CameraActivity) currentActivity).takePhoto();
            break;
          default:
            break;
        }
        return true;
      }
    });
  }
  
  /**
   * Set the background color of the camera button.
   * @param color The color of the background.
   */
  protected void setCameraBackgroundColor(String color) {
    ImageButton imgIcon = (ImageButton)findViewById(R.id.capture);
    GradientDrawable backgroundGradient = (GradientDrawable)imgIcon.getBackground();
    if (color.length() > 0) {
      try {
        int cameraBackgroundColor = Color.parseColor(color);
        backgroundGradient.setColor(cameraBackgroundColor);
      } catch (IllegalArgumentException e) {
        backgroundGradient.setColor(Color.TRANSPARENT);
      }
    } else {
      backgroundGradient.setColor(Color.TRANSPARENT);
    }
  }

  /** Method onStart. Handle the zoom level seekBar and the camera orientation. */
  @Override
  protected void onStart() {
    super.onStart();

    // Init camera resource.
    if (!initCameraResource()) {
      return;
    }
    
    int orientation = 0;
    switch (getCustomRotation()) {
      case 0:
        orientation = CameraActivity.DEGREE_90;
        break;
      case 1:
        orientation = CameraActivity.DEGREE_0;
        break;
      case 2:
        orientation = CameraActivity.DEGREE_270;
        break;
      case 3:
        orientation = CameraActivity.DEGREE_180;
        break;
      default:
        break;
    }

    customCamera.setDisplayOrientation(orientation);

    DisplayMetrics dm = new DisplayMetrics();
    getWindowManager().getDefaultDisplay().getMetrics(dm);
    
    FrameLayout cameraPreview = (FrameLayout) findViewById(R.id.camera_preview);
    RelativeLayout.LayoutParams paramsCameraPreview = 
        new RelativeLayout.LayoutParams(cameraPreview.getLayoutParams());
    
    Size camParameters = customCamera.getParameters().getPictureSize();
    
    int minSize;
    int maxSize;
    if (camParameters.width > camParameters.height) {
      maxSize = camParameters.width;
      minSize = camParameters.height;
    } else {
      maxSize = camParameters.height;
      minSize = camParameters.width;
    }
    
    int widthScreen = dm.widthPixels;
    int heightScreen = dm.heightPixels;
    float ratio;
    if (widthScreen > heightScreen) {
      paramsCameraPreview.height = LayoutParams.FILL_PARENT;
      ratio = ( (float)minSize / (float)heightScreen );
      paramsCameraPreview.width = (int)(maxSize / ratio);
      int marginLeft = (int) (((float)(widthScreen - paramsCameraPreview.width)) / 2);
      paramsCameraPreview.setMargins(marginLeft, 0, 0, 0);
    } else {
      paramsCameraPreview.width = LayoutParams.FILL_PARENT;
      ratio = ( (float)minSize / (float)widthScreen );
      paramsCameraPreview.height = (int)(maxSize / ratio);
      int marginTop = (int) (((float)(heightScreen - paramsCameraPreview.height)) / 2);
      paramsCameraPreview.setMargins(0, marginTop, 0, 0);
    }
    cameraPreview.setLayoutParams(paramsCameraPreview);
    
    // Assign the render camera to the view
    CameraPreview myPreview = new CameraPreview(this, customCamera);
    cameraPreview.addView(myPreview);

    // The zoom bar progress
    final SeekBar zoomLevel = (SeekBar) findViewById(R.id.zoomLevel);
    final Camera.Parameters paramsCamera = customCamera.getParameters();
    final int zoom = paramsCamera.getZoom();
    int maxZoom = paramsCamera.getMaxZoom();

    zoomLevel.setMax(maxZoom);
    zoomLevel.setProgress(zoom);
    zoomLevel.setVisibility(View.VISIBLE);

    // Event on change zoom with the bar.
    zoomLevel.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
      int progress = 0;

      @Override
      public void onProgressChanged(SeekBar seekBar, int progresValue,
          boolean fromUser) {
        progress = progresValue;
        int newZoom = (int) (zoom + progress);
        zoomLevel.setProgress(newZoom);
        paramsCamera.setZoom(newZoom);
        customCamera.setParameters(paramsCamera);
      }

      @Override
      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      @Override
      public void onStopTrackingTouch(SeekBar seekBar) {
      }
    });
  }
  
  /** Method to pause the activity. */
  protected void onPause() {
    super.onPause();
    ManagerCamera.clearCameraAccess();
    FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
    preview.removeAllViews();
  }
  
  /** Event on touch screen to call the manager of the zoom & the auto focus. */
  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (photoTaken == null) {
      Camera.Parameters paramsCamera = customCamera.getParameters();
      int action = event.getAction();

      if (event.getPointerCount() > 1) {
        // If we touch with more than one finger
        if (action == MotionEvent.ACTION_POINTER_2_DOWN) {
          distanceBetweenFingers = getFingerSpacing(event);
        } else if (action == MotionEvent.ACTION_MOVE
            && paramsCamera.isZoomSupported()) {
          customCamera.cancelAutoFocus();
          handleZoom(event, paramsCamera, distanceBetweenFingers);
        }
      } else {
        // If we touch with one finger -> auto-focus
        if (action == MotionEvent.ACTION_UP) {
          handleFocus(event, paramsCamera);
        }
      }
    }
    
    return true;
  }

  /**
   * Determine the space between the first two fingers.
   * @param MotionEvent event Current event which start this calculation.
   * 
   * @return float
   */
  private float getFingerSpacing(MotionEvent event) {
    float coordX = event.getX(0) - event.getX(1);
    float coordY = event.getY(0) - event.getY(1);
    return (float) Math.sqrt(coordX * coordX + coordY * coordY);
  }

  /**
   * Manage the zoom.
   * 
   * @param MotionEvent event                  Current event which start this action.
   * @param Parameters  paramsCamera           Camera's parameter.
   * @param float       distanceBetweenFingers Distance between two fingers.
   */
  private void handleZoom(MotionEvent event, Camera.Parameters paramsCamera,
      float distanceBetweenFingers) {
    // take zoom max for the camera hardware.
    int maxZoom = paramsCamera.getMaxZoom();
    // current value for the zoom.
    int zoom = paramsCamera.getZoom();
    setZoomProgress(maxZoom, zoom);
    // new distance between fingers.
    float newDist = getFingerSpacing(event);

    if (newDist > distanceBetweenFingers) {
      // zoom in
      if (zoom < maxZoom) {
        zoom++;
      }
    } else if (newDist < distanceBetweenFingers) {
      // zoom out
      if (zoom > 0) {
        zoom--;
      }
    }
    distanceBetweenFingers = newDist;
    paramsCamera.setZoom(zoom);
    customCamera.setParameters(paramsCamera);
  }

  /**
   * To set the seekBar zoom with the pinchZoom.
   * @param int maxZoom The max zoom of the device.
   * @param int zoom    The current zoom.
   */
  private void setZoomProgress(int maxZoom, int zoom) {
    SeekBar zoomLevel = (SeekBar) findViewById(R.id.zoomLevel);
    zoomLevel.setMax(maxZoom);
    zoomLevel.setProgress(zoom * 2);
    zoomLevel.setVisibility(View.VISIBLE);
  }

  /**
   * Manage the focus.
   * @param event        Current event which start this action.
   * @param paramsCamera Camera's parameter.
   */
  public void handleFocus(MotionEvent event, Camera.Parameters paramsCamera) {
    if (photoTaken == null) {
      List<String> supportedFocusModes = paramsCamera.getSupportedFocusModes();
      if (supportedFocusModes != null
          && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
        customCamera.autoFocus(new Camera.AutoFocusCallback() {
          @Override
          public void onAutoFocus(boolean bool, Camera camera) {
          }
        });
      }
    }
  }

  /** To set background in the view. */
  protected void setBackground() {
    // Get the base64 picture for the background only if it's exist.
    byte[] imgBackgroundBase64 = TransferBigData.getImgBackgroundBase64();
    if (imgBackgroundBase64 != null) {
      // Get picture.
      Bitmap imgBackgroundBitmap = BitmapFactory.decodeByteArray(
          imgBackgroundBase64, 0, imgBackgroundBase64.length);

      // Get sizes screen.
      Display defaultDisplay = getWindowManager().getDefaultDisplay();
      DisplayMetrics displayMetrics = new DisplayMetrics();
      defaultDisplay.getMetrics(displayMetrics);
      int displayWidthPx = (int) displayMetrics.widthPixels;
      int displayHeightPx = (int) displayMetrics.heightPixels;

      // Get sizes picture.
      int widthBackground = (int) (imgBackgroundBitmap.getWidth() * displayMetrics.density);
      int heightBackground = (int) (imgBackgroundBitmap.getHeight() * displayMetrics.density);

      // Change size ImageView.
      RelativeLayout.LayoutParams paramsMiniature = new RelativeLayout.LayoutParams(
          widthBackground, heightBackground);
      float ratioX = (float) displayWidthPx / (float) widthBackground;
      float ratioY = (float) displayHeightPx / (float) heightBackground;
      if (ratioX < ratioY && ratioX < 1) {
        paramsMiniature.width = (int) displayWidthPx;
        paramsMiniature.height = (int) (ratioX * heightBackground);
      } else if (ratioX >= ratioY && ratioY < 1) {
        paramsMiniature.width = (int) (ratioY * widthBackground);
        paramsMiniature.height = (int) displayHeightPx;
      }
      
      // set image at the view.
      ImageView background = (ImageView) findViewById(R.id.background);
      background.setImageBitmap(imgBackgroundBitmap);
      background.setAlpha((float)0.5);  // Opacity at the beginning

      paramsMiniature.addRule(RelativeLayout.CENTER_IN_PARENT,
          RelativeLayout.TRUE);
      
      background.setLayoutParams(paramsMiniature);
    } else {
      Button miniature = (Button) findViewById(R.id.miniature);
      miniature.setVisibility(View.INVISIBLE);
      SeekBar switchOpacity = (SeekBar) findViewById(R.id.switchOpacity);
      switchOpacity.setVisibility(View.INVISIBLE);
    }
  }
  
  /** Resize and mask the miniature button. */
  public void buttonMiniature(View view) {
    ImageView background = (ImageView) findViewById(R.id.background);
    final Button miniature = (Button) view;

    modeMiniature = true;
    // Set new size for miniature layout.
    setParamsMiniature(background, true);
    // Hide the miniature button.
    miniature.setVisibility(View.INVISIBLE);
      
    // Add event on click action for the miniature picture.
    background.setOnClickListener(new View.OnClickListener() {
      public void onClick(View view) {
        modeMiniature = false;
        ImageView background = (ImageView) view;
        // Resize miniature.
        background.setClickable(false);
        setBackground();
        miniature.setVisibility(View.VISIBLE);
      }
    });
  }
  
  /**
   * Set the size and the gravity of the miniature function of photo is taken or not.
   * @param imageView Reference to the background image.
   * @param resize    Should we resize or not ? Only when click on "miniature".
   */
  public void setParamsMiniature(ImageView imageView, boolean resize) {
    RelativeLayout.LayoutParams paramsMiniature = 
        (RelativeLayout.LayoutParams) imageView.getLayoutParams();
    if (resize == true) {
      paramsMiniature.width = paramsMiniature.width / 4;
      paramsMiniature.height = paramsMiniature.height / 4;
    }
    
    // Call the method to handle the position of the miniature.
    positioningMiniature(paramsMiniature);
    imageView.setLayoutParams(paramsMiniature);
  }
  
  /**
   * Handle the position of the miniature button
   * @param paramsMiniature The parameters of the layout.
   */
  public void positioningMiniature(RelativeLayout.LayoutParams paramsMiniature) {
    if (photoTaken == null) {
      // Position at the bottom
      paramsMiniature.addRule(RelativeLayout.ALIGN_PARENT_TOP, 0);   
      paramsMiniature.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE);
    } else {
      // Position at the top
      paramsMiniature.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, 0);
      paramsMiniature.addRule(RelativeLayout.ALIGN_PARENT_TOP, RelativeLayout.TRUE);
    }
    // In all cases, position at the left
    paramsMiniature.addRule(RelativeLayout.ALIGN_PARENT_LEFT, RelativeLayout.TRUE);
  }
  
  /**
   * Display the layout accept/decline photo + mask photo button.
   */
  public void displayAcceptDeclineButtons() {
    LinearLayout keepPhoto = (LinearLayout) findViewById(R.id.keepPhoto);
    ImageButton photo = (ImageButton) findViewById(R.id.capture);
    SeekBar zoomLevel = (SeekBar) findViewById(R.id.zoomLevel);
    Button miniature = (Button) findViewById(R.id.miniature);
    ImageView background = (ImageView) findViewById(R.id.background);
    LayoutParams paramsLayoutMiniature = (LinearLayout.LayoutParams) miniature
        .getLayoutParams();
    
    if (photoTaken != null) {
      // Show/hide elements when a photo is taken 
      keepPhoto.setVisibility(View.VISIBLE);  
      photo.setVisibility(View.INVISIBLE);   
      zoomLevel.setVisibility(View.INVISIBLE);
      
      ((LinearLayout.LayoutParams) paramsLayoutMiniature).gravity = Gravity.TOP;
      miniature.setLayoutParams(paramsLayoutMiniature);
      
      if (modeMiniature) {
        setParamsMiniature(background, false);
      }
      
    } else {
      // Show/hide elements when a photo is not taken
      keepPhoto.setVisibility(View.INVISIBLE);
      photo.setVisibility(View.VISIBLE);
      zoomLevel.setVisibility(View.VISIBLE);  
      
      ((LinearLayout.LayoutParams) paramsLayoutMiniature).gravity = Gravity.BOTTOM;
      miniature.setLayoutParams(paramsLayoutMiniature);
      
      if (modeMiniature) {
        setParamsMiniature(background, false);
      }
    }
  }
  
  /**
   * Method to get the device default orientation.
   * 
   * @return int the device orientation.
   */
  public int getDeviceDefaultOrientation() {
    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    Configuration config = getResources().getConfiguration();
    int rotation = windowManager.getDefaultDisplay().getRotation();

    if (
        (
            config.orientation == Configuration.ORIENTATION_LANDSCAPE 
            && (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
        ) || (
            config.orientation == Configuration.ORIENTATION_PORTRAIT
            && (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
        )
    ) {
      return Configuration.ORIENTATION_LANDSCAPE;
    } else {
      return Configuration.ORIENTATION_PORTRAIT;
    }
  }

  /**
   * Method to take picture.
   */
  public void takePhoto() {
    // Handles the moment where picture is taken
    ShutterCallback shutterCallback = new ShutterCallback() {
      public void onShutter() {
      }
    };

    // Handles data for raw picture
    PictureCallback rawCallback = new PictureCallback() {
      public void onPictureTaken(byte[] data, Camera camera) {
      }
    };

    // Handles data for jpeg picture
    PictureCallback jpegCallback = new PictureCallback() {

      /**
       * Event when picture is taken.
       * @param byte[] data Picture with byte format.
       * @param Camera camera Current resource camera.
       */
      public void onPictureTaken(final byte[] data, Camera camera) {
        BitmapFactory.Options opt;
        opt = new BitmapFactory.Options();

        // Temporarily storage to use for decoding
        opt.inTempStorage = new byte[16 * 1024];
        Camera.Parameters paramsCamera = customCamera.getParameters();
        Size size = paramsCamera.getPictureSize();

        int height = size.height;
        int width = size.width;
        float res = (width * height) / 1024000;

        // Return a smaller image for now
        if (res > 4f) {
          opt.inSampleSize = 4;
        } else if (res > 3f) {
          opt.inSampleSize = 2;
        }

        // Preview from camera
        photoTaken = BitmapFactory.decodeByteArray(data, 0, data.length, opt);

        // Matrix to perform rotation
        Matrix mat = new Matrix();
        int defaultOrientation = getDeviceDefaultOrientation();
        int orientationCamera = getOrientationOfCamera();
        int redirect = 0;
        switch (getCustomRotation()) {
          case 0:
            redirect = CameraActivity.DEGREE_90;
            // If the device is in front camera by default
            if (orientationCamera == 1 
                  && defaultOrientation == Configuration.ORIENTATION_PORTRAIT) {
              redirect = CameraActivity.DEGREE_270;
            }
            break;
          case 1:
            redirect = CameraActivity.DEGREE_0;
            break;
          case 2:
            redirect = CameraActivity.DEGREE_270;
            // If the device is in front camera by default
            if (orientationCamera == 1 
                  && defaultOrientation == Configuration.ORIENTATION_PORTRAIT) {
              redirect = CameraActivity.DEGREE_90;
            }
            break;
          case 3:
            redirect = CameraActivity.DEGREE_180;
            break;
          default:
            break;
        }
        mat.postRotate(redirect);

        // Creation of the bitmap
        photoTaken = Bitmap.createBitmap(photoTaken, 0, 0,
            photoTaken.getWidth(), photoTaken.getHeight(), mat, true);
        displayPicture();
      }
    };
    // Start capture picture.
    customCamera.takePicture(shutterCallback, rawCallback, jpegCallback);
  }
  
  /**
   * Call when the photo is accepted.
   * @param view The curretnView.
   */
  public void acceptPhoto(View view) {
    final CameraActivity cameraActivityCurrent = this;

    try {
      ByteArrayOutputStream stream = new ByteArrayOutputStream();
      photoTaken.compress(CompressFormat.JPEG, 70, stream);
      
      // Get path picture to storage.
      String pathPicture = Environment.getExternalStorageDirectory()
          .getPath() + "/" + Environment.DIRECTORY_DCIM + "/Camera/";
      pathPicture = pathPicture
          + String.format("%d.jpeg", System.currentTimeMillis());

      // Write data in file.
      FileOutputStream outStream = new FileOutputStream(pathPicture);
      outStream.write(stream.toByteArray());
      outStream.close();

      TransferBigData.setImgTaken(stream.toByteArray());
      
      // Return to success & finish current activity.
      cameraActivityCurrent.setResult(1,new Intent());
      cameraActivityCurrent.finish();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  /**
   * Get the orientation of the current camera.
   * 
   * @return int The orientation of the current camera (FRONT OR BACK)
   */
  public int getOrientationOfCamera() {
    CameraInfo info = new Camera.CameraInfo();
    // Get info of the default camera (which is called by default)
    Camera.getCameraInfo(0, info);

    return info.facing;
  }
  
  /**
   * Call when the photo is declined.
   * @param view The current View.
   */
  public void declinePhoto(View view) {
    photoTaken = null;
    displayPicture();
  }
  
  /** To save some contains of the activity. */
  @Override
  protected void onSaveInstanceState(Bundle outState) {
    outState.putBoolean("modeMiniature", modeMiniature);
    outState.putParcelable("photoTaken", photoTaken);
    super.onSaveInstanceState(outState);
  }

  /** To restore the contains saved on the method onSaveInstanceState(). */
  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    modeMiniature = savedInstanceState.getBoolean("modeMiniature");
    photoTaken = savedInstanceState.getParcelable("photoTaken");
    
    if (modeMiniature) {
      buttonMiniature(findViewById(R.id.miniature));
    }

    displayPicture();
    super.onRestoreInstanceState(savedInstanceState);
  }

  /** To display or not the picture taken. */
  protected void displayPicture() {
    FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
    ImageView photoResized = (ImageView) findViewById(R.id.photoResized);

    if (photoTaken != null) {
      // Stop link between view and camera to start the preview
      // picture.
      customCamera.stopPreview();

      Bitmap newBitmap = resizePictureTaken();
      photoResized.setImageBitmap(newBitmap);
      photoResized.setVisibility(View.VISIBLE);
      preview.setVisibility(View.INVISIBLE);
    } else {
      customCamera.startPreview();
      photoResized.setVisibility(View.INVISIBLE);
      preview.setVisibility(View.VISIBLE);
    }

    displayAcceptDeclineButtons();
  }

  /**
  * Resize the bitmap saved when you rotate the device.
  * 
  * @return the new bitmap.
  */
  protected Bitmap resizePictureTaken() {
    // Initialize the new bitmap resized
    Bitmap newBitmap = null;
  
    // Get sizes screen.
    Display defaultDisplay = getWindowManager().getDefaultDisplay();
    DisplayMetrics displayMetrics = new DisplayMetrics();
    defaultDisplay.getMetrics(displayMetrics);
    int displayWidthPx = (int) displayMetrics.widthPixels;
    int displayHeightPx = (int) displayMetrics.heightPixels;
  
    // Get sizes picture.
    int widthBackground = (int) (photoTaken.getWidth() * displayMetrics.density);
    int heightBackground = (int) (photoTaken.getHeight() * displayMetrics.density);
   
    // Change size ImageView.
    float ratioX = (float) displayWidthPx / (float) widthBackground;
    float ratioY = (float) displayHeightPx / (float) heightBackground;
    if (ratioX < ratioY && ratioX < 1) {
      newBitmap = Bitmap.createScaledBitmap(photoTaken, (int) displayWidthPx,
         (int) (ratioX * heightBackground), false);
    } else if (ratioX >= ratioY && ratioY < 1) {
      newBitmap = Bitmap.createScaledBitmap(photoTaken,
         (int) (ratioY * widthBackground), (int) displayHeightPx, false);
    }
    
    return newBitmap;
  }
  
  /**
  * Allow to lock the screen or not.
  * 
  * @param boolean lock Do we have to lock or not ?
  */
  protected void lockScreen(boolean lock) {
    if (lock == false) {
      this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
    } else {
      int newOrientation = 0;
     
      switch (getCustomRotation()) {
        case 0:
          newOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
          break;
        case 1:
          newOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
          break;
        case 2:
          newOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
          break;
        case 3:
          newOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
          break;
        default:
          break;
      }
     
      this.setRequestedOrientation(newOrientation);
    }
  }
  
  /**
   * To perform the rotation.
   * @return the code of the rotation (0, 1, 2, 3)
   */
  protected int getCustomRotation() {
    WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    int code = windowManager.getDefaultDisplay().getRotation();
    if (getDeviceDefaultOrientation() == 2) {
      code ++;
    }

    return code == 4 ? 0 : code;
  }

  /**
   * When the back button is pressed.
   */
  @Override
  public void onBackPressed() {
    this.setResult(3);
    this.finish();
  }
}
