package de.herb64.funinspace;

import android.app.ActivityManager;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import de.herb64.funinspace.helpers.utils;


// This activity is now started with startActivityForResult() to return some values
// to the main activity. This is, because we have not way to determine the size of the
// hires image without loading it.

// TODO handle the rotation of phone, while the asynctask is running
// see also: https://stackoverflow.com/questions/7128670/best-practice-asynctask-during-orientation-change

// very important:
// - http://www.androiddesignpatterns.com/2013/04/retaining-objects-across-config-changes.html
// + http://www.androiddesignpatterns.com/2014/12/activity-fragment-transitions-in-android-lollipop-part1.html
// ... mutliple parts

// - https://stackoverflow.com/questions/5335665/avoid-reloading-activity-with-asynctask-on-orientation-change-in-android/5336057#5336057

// https://www.youtube.com/watch?v=7lBpydmilAk !!!
// http://blog.danlew.net/2014/06/21/the-hidden-pitfalls-of-asynctask/

// and for classes extends vs. implements
// https://stackoverflow.com/questions/10839131/implements-vs-extends-when-to-use-whats-the-difference

// NOTE : A LOT OF CODE FRAGMENTS FROM TESTING WITH ZOOMING IMAGES IS IN TAR FILE FROM 02.08.2017 !!!!!!!!!!!!

// See also about youtube icon
// https://www.youtube.com/yt/about/brand-resources/#logos-icons-colors

// TODO: it would be good, if the second long press could be omitted and the selection just can be
//       taken when closing the window...
// TODO: resizing of the selection rect to

// TODO: CHECK THIS ARTICLE - DOCU
// Android 8 problems with parcels / transaction size too large:
// other versions allow bitmap in parcelable for saved instance state
// https://github.com/codepath/android_guides/wiki/Using-Parcelable
// and
// https://stackoverflow.com/questions/11346275/android-why-is-using-onsaveinsancestate-to-save-a-bitmap-object-not-being-ca
// onRetainNonConfigurationInstance() + getLastNonConfigurationInstance() > deprecated ?
//
// THIS SEEMS TO BE THE SOLUTION: the retained fragment is kept
// https://developer.android.com/guide/topics/resources/runtime-changes.html

// !!!!!!!!!!!!!!!!!!!!!!!!!!!!
// TODO: why does latest crash with home button only happen in wp select mode? Also: if rotating
//       the phone, the saved instance state does also not make this crash...
//       of course, keeping the bitmap in this bundle is bad, anyway...
//

public class ImageActivity extends AppCompatActivity implements ImgHiresFragment.myCallbacks {
    private drawableImageView ivHires = null;
    private Bitmap myBitmap;
    private Bitmap wallBitmap = null;
    private String strHires;
    private String strSz;
    private int listIdx;
    private ProgressBar loadingBar;
    private Intent returnIntent;
    private Matrix imgMatrix;
    private float[] mValues;
    private float imgScale = 1f;
    private float minImgScale = 0.1f;
    private ScaleGestureDetector SGDetector;
    private GestureDetector GDetector;
    private int viewWidth;
    private int viewHeight;
    private int dispWidth;
    private int dispHeight;
    private int imgWidth;
    private int imgHeight;
    private float scaledWidth;
    private float scaledHeight;
    //private Scroller mScroller;
    //private int memClass;
    private int maxAlloc;
    private int maxTextureSize;
    private String imageName;
    private boolean wallPaperSelectMode = false;
    private Rect wallPaperSelectRect;
    private int wallPaperQuality;
    private int wpMinY = 0;
    private int wpMinX = 0;
    private int wpMaxY = 0;
    private int wpMaxX = 0;
    private boolean isLandScape = false;

    private TextReader reader;
    private String explanation;
    private String title;

    private static final String TAG_TASK_FRAGMENT = "img_hires_task_fragment";
    private static final String TAG = "HFCM";
    private static final float DOUBLE_TAP_ZOOMFACTOR = 2.0f;
    // TODO : make quality selectable - maybe just list preference: low - medium - high - excellent with 25/50/80/100
    //private static final int DEFAULT_WP_QUALITY = 80;

    /**
     * Passed information via intent
     * hiresurl             url to load (or absolutepath of file to load, if exists)
     * listidx
     * maxalloc
     * maxtexturesize
     * imagename            base name of image, without prefixes
     * wallpaperquality     quality in which to save wallpaper - from preferences
     * wallpaperselectmode
     *
     * @param savedInstanceState    save instance state
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make the image display in fullscreen, no borders. See
        // https://stackoverflow.com/questions/14475109/remove-android-app-title-bar
        // this removes the upper bar only, the larger app bar is removed by manifest entry
        // android:theme="@style/AppTheme.NoActionBar" for the activity
        // call it before setContentView()
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_image);
        ivHires = (drawableImageView) findViewById(R.id.iv_large);
        //ivHires.setOnTouchListener(new imgTouchListener());
        SGDetector = new ScaleGestureDetector(this, new imgScaleListener());
        GDetector = new GestureDetector(this, new imgDragListener());
        //mScroller = new Scroller(getApplicationContext());
        loadingBar = (ProgressBar) findViewById(R.id.pb_loading);
        imgMatrix = new Matrix();
        mValues = new float[9];
        wallPaperSelectRect = new Rect();

        // Fix: get viewWidth and Height on each create instead of using savedInstanceState
        // TODO: check if this is correct using DisplayMetrics
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        viewWidth = metrics.widthPixels;
        viewHeight = metrics.heightPixels;

        // Get the "real" display values (e.g. 1920x1080) - need this for wallpaper stuff
        // Unfortunately, only SDK 17+ supports this in a nice way
        // TODO: find solution for lower sdk levels, for now the range is not perfect
        //WindowManager wm = (WindowManager) getApplicationContext().getSystemService(WINDOW_SERVICE);
        //Display disp = wm.getDefaultDisplay();
        Display disp = getWindowManager().getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            DisplayMetrics realmetrics = new DisplayMetrics();
            disp.getRealMetrics(realmetrics);
            dispWidth = realmetrics.widthPixels;
            dispHeight = realmetrics.heightPixels;
        } else {
            // TODO: This is BAD and does not return the real screen pixels - below api 17
            // there seems to be no way to get the real pixels without doing many calculations
            Point dispsize = new Point();
            disp.getSize(dispsize);
            dispWidth = dispsize.x;
            dispHeight = dispsize.y;
        }
        // Get orientation: see https://developer.android.com/reference/android/view/Display.html
        isLandScape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        /*wallPaperSelectMode = false;
        //wallPaperQuality = DEFAULT_WP_QUALITY;
        ivHires.setSelectRect(null);*/

        // Get info on memoryClass - needed for bitmap loading to avoid OOM situations
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        int memClass = activityManager.getMemoryClass();

        // Handle restored instance state to recover from phone rotation
        // https://stackoverflow.com/questions/19856359/imageview-not-retaining-image-when-screen-rotation-occurs
        // New logic: use RESULT_CANCELED as default. If user returns before image has been loaded,
        // onActivityResult for HIRES_LOAD_REQUEST gets called with canceled result...
        if (savedInstanceState != null) {
            // TODO: Docu note: using noinspection for ResourceType to avoid complaint in setVisibility()
            //       about non correct value, because we restore this from Bundle...

            // FIX for transaction size exception - keep image in fragment.
            //myBitmap = savedInstanceState.getParcelable("apodimg");  // BBBBBBBBBBBBBBBBB don't do it
            // why does it not crash on lower android version and why only in wp select mode home button?
            FragmentManager fm = getFragmentManager();
            ImgHiresFragment mHiresFragment = (ImgHiresFragment) fm.findFragmentByTag(TAG_TASK_FRAGMENT);
            // this one should actually only be a reference, so no double memory needed!
            myBitmap = mHiresFragment.getBitmap();

            ivHires.setImageBitmap(myBitmap);
            strSz = savedInstanceState.getString("size");
            strHires = savedInstanceState.getString("strhires");
            int visibility = savedInstanceState.getInt("loading");
            listIdx = savedInstanceState.getInt("lstidx");
            //noinspection ResourceType -> this flag hides warnings in setVisibility() below
            loadingBar.setVisibility(visibility);
            returnIntent = savedInstanceState.getParcelable("returnintent");
            // Rotation for image - during onCreate() the imageview does not yet report width and
            // height information and returns 0 for getWidth()/Height(). This is only available
            // after attach. We could override onWindowFocusChanged()
            // Use Display getSize() / getRealMetrics() (before, storing in savedInstanceState and
            // exchanging values was a quick and dirty solution)
            imgHeight = savedInstanceState.getInt("imgHeight");
            imgWidth = savedInstanceState.getInt("imgWidth");
            maxAlloc = savedInstanceState.getInt("maxAlloc");
            maxTextureSize = savedInstanceState.getInt("maxtexturesize");
            imageName = savedInstanceState.getString("imagename");
            wallPaperQuality = savedInstanceState.getInt("wallpaper_quality");
            explanation = savedInstanceState.getString("explanation");
            title = savedInstanceState.getString("title");
            wallPaperSelectMode = false;
            // Fix to make "cached" symbol show up immediately, also if phone has been rotated
            // while in image view.
            if (myBitmap != null) {
                initializeMatrix();
                setResult(RESULT_OK, returnIntent);
            } else {
                // TODO Docu: it is important to call setResult after restoring instance state again
                setResult(RESULT_CANCELED, returnIntent);
            }
        } else {
            Intent intent = getIntent();
            strHires = intent.getStringExtra("hiresurl");
            strSz = "";
            listIdx = intent.getIntExtra("listIdx", 0);
            maxAlloc = intent.getIntExtra("maxAlloc", 0);
            maxTextureSize = intent.getIntExtra("maxtexturesize",0);
            imageName = intent.getStringExtra("imagename");
            wallPaperQuality = Integer.parseInt(intent.getStringExtra("wallpaper_quality"));
            // We now can start the activity in select mode without need to first long press
            wallPaperSelectMode = intent.getBooleanExtra("wpselect", false);
            explanation = intent.getStringExtra("explanation");
            title = intent.getStringExtra("title");
            returnIntent = new Intent();

            // null pointer on data in returnintent - was never set
            //returnIntent.putExtra("sizeHires", originalSize);
            returnIntent.putExtra("lstIdx", listIdx);
            returnIntent.putExtra("hiresurl", strHires);
            returnIntent.putExtra("logString", "hires load canceled");
            returnIntent.putExtra("filename", imageName);
            setResult(RESULT_CANCELED, returnIntent);

            // TODO Docu
            // OLD CODE: start the asynctask to load the image
            // NEW CODE: asynctask in retained fragment using FragmentManager
            // Fragmentmanager: see https://www.youtube.com/watch?v=Nv24t2CJ6yw !! for docu

            FragmentManager fm = getFragmentManager();
            ImgHiresFragment mHiresFragment = (ImgHiresFragment) fm.findFragmentByTag(TAG_TASK_FRAGMENT);
            // If the Fragment is non-null, it is currently being retained across a config change.
            if (mHiresFragment == null) {
                SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(this);
                if (shPref.getBoolean("show_debug_infos", true)) {
                    String toaster = "<" + String.valueOf(listIdx) + "> " + strHires;
                    Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_SHORT).show();
                }
                mHiresFragment = new ImgHiresFragment();
                // setArguments is the way to go, do NOT use non default constructor with fragments!
                Bundle fragArguments = new Bundle();
                fragArguments.putString("urltoparse", strHires);
                fragArguments.putInt("memclass", memClass);
                fragArguments.putInt("maxAlloc", maxAlloc);
                fragArguments.putInt("maxTextureSize", maxTextureSize);
                //fragArguments.putString("imageName", imageName.replace("wp_", ""));
                fragArguments.putString("imageName", imageName);
                // check, if image file exists and pass info to hires loader fragment
                mHiresFragment.setArguments(fragArguments);
                fm.beginTransaction().add(mHiresFragment, TAG_TASK_FRAGMENT).commit();
            }
        }

        // Create text to speech reader
        android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
        reader = (TextReader) fm.findFragmentByTag(TextReader.TAG_READ_FRAGMENT);
        if (reader == null) {
            reader = new TextReader();
            fm.beginTransaction().add(reader, TextReader.TAG_READ_FRAGMENT).commit();
        }

        //wallPaperSelectMode = false;
        //wallPaperQuality = DEFAULT_WP_QUALITY;
        ivHires.setSelectRect(null);

        //Log.i("HFCM", "Finished oncreate in imageactivity with quality " + wallPaperQuality);
    }

    /*@Override
    public void onConfigurationChanged(Configuration newConfig) {
        // and see manifest:
        // android.util.SuperNotCalledException: Activity ImageActivity did not call through to super.onConfigurationChanged()
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
        } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
            Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
        }
    }*/

    // HFCM Add for state saving for rotation
    // see http://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
    // for some good explanation on what happens here...
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //outState.putParcelable("apodimg", myBitmap);        // BBBBBBBBBBB don't do this!
        outState.putString("strhires", strHires);
        outState.putString("size", strSz);
        outState.putInt("loading", loadingBar.getVisibility());
        outState.putInt("lstidx", listIdx);
        outState.putParcelable("returnintent", returnIntent);
        outState.putInt("imgWidth", imgWidth);
        outState.putInt("imgHeight", imgHeight);
        outState.putInt("maxAlloc", maxAlloc);
        outState.putInt("maxtexturesize", maxTextureSize);
        outState.putString("imagename", imageName);
        outState.putInt("wallpaper_quality", wallPaperQuality);
        outState.putString("explanation", explanation);
        outState.putString("title", title);
    }

    // Code to calculate stuff for rotation. Note, that imageview size is stored in instance state
    // TODO; this is not nice yet, just a reset to initial after each rotation
    // TODO: depending on image size, we might want to change max scale/min scale ??
    public void initializeMatrix() {
        RectF imgRect = new RectF(0, 0, imgWidth, imgHeight);
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        imgMatrix.setRectToRect(imgRect, viewRect, Matrix.ScaleToFit.CENTER);
        ivHires.setImageMatrix(imgMatrix);
        imgMatrix.getValues(mValues);
        minImgScale = mValues[Matrix.MSCALE_X];
        imgScale = minImgScale;
        scaledWidth = imgWidth * imgScale;
        scaledHeight = imgHeight * imgScale;
    }

    /* not used here, see tar from 02.08.2017
    private class imgTouchListener implements View.OnTouchListener {
    */
    // touch events: onTouchEvent() vs. onTouch()
    // see https://stackoverflow.com/questions/5002049/ontouchevent-vs-ontouch
    //     https://stackoverflow.com/questions/19620451/a-views-ontouchlistener-vs-ontouchevent
    // onTouchEvent() is called for the view, on which a user is touching the screen
    // https://developer.android.com/training/gestures/detector.html
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // We might work with our event here and perform activities. But here, we decide to use
        // the gesture detectors provided by android to detect gestures, instead of getting
        // pointers ourselves and detect for example the pinch zoom or doubletap events.
        // HMMM, but we might be able to create own gestures ????
        // TODO; interesting topic
        /*switch (event.getAction()) {
            case MotionEvent.ACTION_MOVE :
            {
                // these return real device coordiates, with 0/0 in upper left corner
                // this is also called, if 
                float xaxis = event.getAxisValue(MotionEvent.AXIS_X);
                float yaxis = event.getAxisValue(MotionEvent.AXIS_Y);
                Log.d(TAG, "onTouchEvent Move action X=" + String.valueOf(xaxis) +", Y=" + String.valueOf(yaxis));
            }
            break;
        }*/
        // we have 2 detectors, one for scale and one for move... So we pass our event ot both.
        // https://stackoverflow.com/questions/15309743/use-scalegesturedetector-with-gesturedetector
        // see also this article on multitouch
        // https://android-developers.googleblog.com/2010/06/making-sense-of-multitouch.html
        // we might check our returncodes here:
        // https://developer.android.com/training/gestures/scale.html
        //evtCount++;  // some debug counter
        boolean ret = SGDetector.onTouchEvent(event);
        ret = GDetector.onTouchEvent(event) || ret;
        return ret || super.onTouchEvent(event);    // super is only called if 'ret' is false !!
    }

    // =============== BEGIN gesture detection stuff
    //
    // GestureDetector: for single pointer (one finger)
    // SimpleOnGestureListener; This one is good for detecting just a subset of gestures instead of
    // implementing the full interface of GestureDetector.onGestureListener
    // see: https://developer.android.com/training/gestures/detector.html
    // GestureDetector.SimpleOnGestureListener provides an implementation for all of the
    // on<TouchEvent> methods by returning false for all of them. Thus you can override only the
    // methods you care about. For example, the snippet below creates a class that extends
    // see also
    // https://developer.android.com/samples/BasicGestureDetect/src/com.example.android.basicgesturedetect/GestureListener.html
    // Here, we are interested in onScoll and onDoubleTap
    private class imgDragListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            //return super.onScroll(e1, e2, distanceX, distanceY);
            float transX;
            float transY;

            // Intercept, if we are in wallpaper selection mode and adjust the wallpaper selection
            // rectangle according to the user drag operations
            if (wallPaperSelectMode) {
                // for now, only moving left to right for images with larger aspect than screen!
                //Log.i("HFCM", "Having distance in select mode: " + distanceX + " / " + distanceY);
                if (wallPaperSelectRect.top - (int)distanceY >= wpMinY &&       // 0
                        wallPaperSelectRect.bottom - (int)distanceY <= wpMaxY) { // viewHeight
                    wallPaperSelectRect.set(
                            wallPaperSelectRect.left,
                            wallPaperSelectRect.top - (int) distanceY,
                            wallPaperSelectRect.right,
                            wallPaperSelectRect.bottom - (int) distanceY);
                }

                if (wallPaperSelectRect.left - (int)distanceX >= wpMinX &&      // 0
                        wallPaperSelectRect.right - (int)distanceX <= wpMaxX) {  // viewWidth
                    wallPaperSelectRect.set(
                            wallPaperSelectRect.left - (int) distanceX,
                            wallPaperSelectRect.top,
                            wallPaperSelectRect.right - (int) distanceX,
                            wallPaperSelectRect.bottom);
                }
                if (distanceX != 0f || distanceY != 0f) {
                    ivHires.setSelectRect(wallPaperSelectRect);
                    ivHires.invalidate();
                }
                return true;
            }

            // e1 is the first down motion event that started the scrolling (this usually stays
            // constant during the scroll) while e2 is the move motion event that triggered
            // the current onScroll
            transX = mValues[Matrix.MTRANS_X];
            transY = mValues[Matrix.MTRANS_Y];

            // Calculate translation correction offsets
            float xx = -distanceX;
            float yy = -distanceY;
            if (scaledHeight <= viewHeight) {
                yy = 0.0f;
            } else {
                if (transY + yy > 0.0f) {   // upper bound reached
                    yy = 0.0f;
                } else if (transY + yy < -(scaledHeight - (float) viewHeight)) {
                    if (yy < 0) {           // lower bound reached
                        yy = 0.0f;
                    }
                }
            }
            if (scaledWidth <= viewWidth) {
                xx = 0.0f;
            } else {
                 if (transX + xx > 0.0f) {  // left bound reached
                     xx = 0.0f;
                 } else if (transX + xx < -(scaledWidth - (float) viewWidth)) {
                     if (xx < 0) {          // right bound reached
                         xx = 0.0f;
                     }
                 }
            }
            // if there is any translation to be applied, do it
            if ((xx != 0.0f) || (yy != 0.0f)) {
                imgMatrix.postTranslate(xx, yy);
                ivHires.setImageMatrix(imgMatrix);
                imgMatrix.getValues(mValues);
            }
            return true;
        }

        /**
         * Double Tap: performs a zoom in fullscreen
         * TODO: doubletap does not work on my elephone, so how to do it ?
         * @param e     motion event
         * @return      boolean
         */
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            //return super.onDoubleTap(e);
            if (wallPaperSelectMode) {
                return true;
            } else {
                adjustMatrixForScaling(e.getAxisValue(MotionEvent.AXIS_X),
                        e.getAxisValue(MotionEvent.AXIS_Y),
                        DOUBLE_TAP_ZOOMFACTOR);
                ivHires.setImageMatrix(imgMatrix);
                return true;
            }
        }

        /**
         * We use a long press to activate wallpaper selection mode from fullscreen display.
         * First long press enables, second long press disables and creates the wallpaper.
         * Rotation of phone disables selection mode with no action. Wallpapers are always generated
         * for the standard rotation of the phone (portrait). Check for tablets...
         * @param e     MotionEvent to be processed
         */
        @Override
        public void onLongPress(MotionEvent e) {
            // TODO: REAL display size information for Android < api17
            // https://stackoverflow.com/questions/35780980/getting-the-actual-screen-height-android
            // https://developer.android.com/guide/practices/screens_support.html
            // TODO  - fix that initWpSelect stuff -- not yet clean!!!

            float aspectWall = (float) dispWidth / (float) dispHeight;
            if (isLandScape) {
                aspectWall = 1f / aspectWall;
            }
            int wallWidth = isLandScape ? dispHeight : dispWidth;
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                aspectWall *= 2f;
                wallWidth *= 2;
            }
            float aspectBitmap = (float) imgWidth / (float) imgHeight;

            // Set wallpapper selection rectangle size and moving bounds within the image view
            float wallSelectHeight;
            float wallSelectWidth;
            if (aspectBitmap >= aspectWall) {
                wallSelectHeight = scaledHeight;
                wallSelectWidth = wallSelectHeight * aspectWall;
            } else {
                wallSelectWidth = scaledWidth;
                wallSelectHeight = wallSelectWidth / aspectWall;
            }
            wpMinX = (int) (((float)viewWidth - scaledWidth) / 2f);
            wpMaxX = (int) (((float)viewWidth + scaledWidth) / 2f);
            wpMinY = (int) (((float)viewHeight - scaledHeight) / 2f);
            wpMaxY = (int) (((float)viewHeight + scaledHeight) / 2f);

            // While in wallpaper select mode, the second long press ends this mode and uses
            // the selected range to create the wallpaper bitmap
            // TODO: invalidation with optimized regions...
            if (wallPaperSelectMode) {
                wallPaperSelectMode = false;
                ivHires.setSelectRect(null);
                //SystemClock.sleep(3000);
                // Map wallPaperSelectRect to a hires bitmap region and use BitmapRegionDecoder
                // to cut this range + apply scaling for the wallpaper bitmap object
                // TODO: correct mapping to bitmap for landscape!!!
                Rect region = new Rect();
                float scale;
                if (aspectBitmap >= aspectWall) {
                    scale = (imgHeight > dispHeight) ? (float) imgHeight / (float) dispHeight : 1f;
                    region.set(
                            //(int) ((float) wallPaperSelectRect.left / imgScale), // FIX
                            (int) ((float) (wallPaperSelectRect.left - wpMinX) / imgScale),
                            0,
                            //(int) ((float) wallPaperSelectRect.right / imgScale), // FIX
                            (int) ((float) (wallPaperSelectRect.right - wpMinX) / imgScale),
                            imgHeight
                    );
                } else {
                    scale = (imgWidth > dispWidth) ? (float) imgWidth / (float) dispWidth : 1f;
                    region.set(
                            0,
                            (int) ((float) wallPaperSelectRect.top / imgScale),
                            imgWidth,
                            (int) ((float) wallPaperSelectRect.bottom / imgScale)
                    );
                }

                // Use decodeRegion() and streams to directly decode from hires image bitmap.
                // TODO - what about memory consumption, JPEG vs. PNG, JPEG quality (config option?)
                // slider in preferences screen?
                // https://stackoverflow.com/questions/24793465/how-to-set-a-slider-in-preferencescreen-of-android
                // https://stackoverflow.com/questions/1974193/slider-on-my-preferencescreen?answertab=oldest#tab-top
                // BAD news on inScaled option: BitmapRegionDecoder ignores this flag, so we go
                // with createScaledBitmap(), using BitmapRegionDecoder output as source
                ByteArrayOutputStream os = new ByteArrayOutputStream();
                myBitmap.compress(Bitmap.CompressFormat.JPEG, wallPaperQuality, os);
                ByteArrayInputStream is = new ByteArrayInputStream(os.toByteArray());
                try {
                    BitmapRegionDecoder rD = BitmapRegionDecoder.newInstance(is, false);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = Integer.highestOneBit((int)scale);
                    //options.inScaled = true;            // IGNORED IN REGIONDECODER!!!!
                    //options.inDensity = imgHeight;
                    //options.inTargetDensity = viewHeight * options.inSampleSize;
                    //wallBitmap = rD.decodeRegion(region, options);
                    // TODO: avoid scaleup for small images - keep original region size (in this case it will be scaled by the system internally, anyway)
                    //int hgt = isLandScape ? dispWidth : dispHeight;
                    wallBitmap = Bitmap.createScaledBitmap(rD.decodeRegion(region, options),
                            wallWidth,
                            //hgt, //dispHeight,
                            isLandScape ? dispWidth : dispHeight,
                            false
                            );
                } catch (IOException e1) {
                    e1.printStackTrace();
                }

                // Save wallpaper bitmap as JPEG (now with quality) and return filename via intent.
                if (wallBitmap != null) {
                    //Log.i("HFCM", "Saving wallpaper image into 'wp_" + imageName +
                    //        "', Quality: " + wallPaperQuality);
                    utils.writeJPG(getApplicationContext(),
                            "wp_" + imageName,
                            wallBitmap,
                            wallPaperQuality);
                    returnIntent.putExtra("wallpaperfile", "wp_" + imageName);
                    String toaster = getString(R.string.toast_wp_select_finished);
                    Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_SHORT).show();
                }
                //String toaster = getString(R.string.toast_wp_select_finished);
                //Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_SHORT).show();

                ivHires.invalidate();

            } else {
                // Initialize wallpaper selection mode - create a centered rectangle
                String toaster = getString(R.string.toast_wp_start_select);
                Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_SHORT).show();
                wallPaperSelectMode = true;

                // Reset view matrix to initial fullscreen view for selection of wallpaper range
                initializeMatrix();
                ivHires.setImageMatrix(imgMatrix);

                // Calculate starting wallpaper selection Rectangle for image view
                initWPSelect();
                /*wallPaperSelectRect.set(
                        (int) (((float)viewWidth - wallSelectWidth) / 2f),
                        (int) (((float)viewHeight - wallSelectHeight) / 2f),
                        (int) (((float)viewWidth + wallSelectWidth) / 2f),
                        (int) (((float)viewHeight + wallSelectHeight) / 2f)
                );*/
                //ivHires.setSelectRect(wallPaperSelectRect);
            }
            //ivHires.invalidate();
            //loadingBar.setVisibility(View.GONE);
            super.onLongPress(e);
        }

        /**
         * TODO: implement smoother scrolling - Scroller might be your friend :)
         * https://developer.android.com/reference/android/widget/Scroller.html
         * @param e1 motion event
         * @param e2 motion event
         * @param velocityX velocity
         * @param velocityY velocity
         * @return boolean
         */
        //
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //super.onFling(e1, e2, velocityX, velocityY);
            //mScroller.fling();
            //Log.i(TAG, "Fling gesture: " + String.valueOf(velocityX) + "/" + String.valueOf(velocityY));
            return super.onFling(e1, e2, velocityX, velocityY);
            //return true;
        }
    }

    /**
     * ScaleGestureDetector: since android 2.2 - multiple fingers (pinch zoom)
     * return true, if we handle the event, false if not
     * see also interesting internal
     * https://stackoverflow.com/questions/30414892/how-does-matrix-postscale-sx-sy-px-py-work
     */
    private class imgScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //return super.onScale(detector);
            adjustMatrixForScaling(detector.getFocusX(),
                    detector.getFocusY(),
                    detector.getScaleFactor());
            // Apply the matrix to the image view
            ivHires.setImageMatrix(imgMatrix);
            return true;
        }
    }

    /**
     * Adjust the matrix for scaling operations. This is used by pinch zoom and doubletap zoom.
     * After the scaling operation is done, an adjustment via post translation follows to
     * - keep the image in the view bounds
     * - keep the image centered, if one edge is getting smaller than corresponding view size
     * @param focX      x focus
     * @param focY      y focus
     * @param factor    factor
     */
    private void adjustMatrixForScaling(float focX, float focY, float factor) {
        float originalfactor = factor;
        // TODO: factor sometimes < 1.0 during scaleup - possible bug?

            /*float cSpan = detector.getCurrentSpan();  // just some test to verify
            float pSpan = detector.getPreviousSpan();   // factor calculation
            float testFactor = cSpan / pSpan;           // yes, this is same factor*/

        // Calculate the new scale using the factor from detector and limit to our bounds
        // TODO rethink upper factor - should depend on image size and screen resolution
        float imgScaleNew = imgScale * factor;
        imgScale = Math.max(minImgScale, Math.min(imgScaleNew, 5.0f));

        // adjust original factor returned by detector if scale limits have been reached
        if (imgScale != imgScaleNew) {
            if ( (factor < 1.0f) && (imgScale == minImgScale) ) {  // lower scale limit reached
                factor *= minImgScale / imgScaleNew;
            } else if ((factor > 1.0f) && (imgScale == 5.0f)) {    // upper scale limit reached
                factor *= 5.0f / imgScaleNew;
            }
        }

        // Apply the scaling to the matrix and update matrix values array
        imgMatrix.postScale(factor, factor, focX, focY);
        imgMatrix.getValues(mValues);
        scaledWidth = imgWidth * imgScale;
        scaledHeight = imgHeight * imgScale;

        // Adjust translation for downscaling, so that image remains within view and gets
        // centered, if one edge is getting smaller than corresponding view size
        if (originalfactor < 1.0f) {
            float transY = mValues[Matrix.MTRANS_Y];
            float transX = mValues[Matrix.MTRANS_X];
            float xoff;
            float yoff;
            if (scaledHeight < viewHeight) {
                float lower = viewHeight - transY - scaledHeight;
                yoff = -(transY - lower) / 2;
            } else {
                float lower = scaledHeight - viewHeight + transY;
                yoff = 0.0f;
                if (transY > 0.0f) {
                    yoff = -transY;
                } else if (lower < 0.0f) {
                    yoff = -lower;
                }
            }
            if (scaledWidth < viewWidth) {
                float right = viewWidth - transX - scaledWidth;
                xoff = -(transX - right) / 2;
            } else {
                float right = scaledWidth - viewWidth + transX;
                xoff = 0.0f;
                if (transX > 0.0f) {
                    xoff = -transX;
                } else if (right < 0.0f) {
                    xoff = -right;
                }
            }
            // Apply the corrective translation to the matrix
            imgMatrix.postTranslate(xoff, yoff);
        }
    }

    // =============== END gesture detection stuff

    // MATRIX restriction stuff - removed, see 02.08.2017 tarball !!

    // ======================================================================================
    // IMPLEMENTATIONS of interface functions for ImgHiresFragment - get called from fragment
    // ======================================================================================
    @Override
    public void onPreExecute() {
        //Log.d(TAG, "Reached onPreExecute() in ImageActivity");
    }

    /*@Override
    public void onProgressUpdate(int percent) {
        Log.d(TAG, "Progress is now " + String.valueOf(percent));
    }*/

    @Override
    public void onCancelled() {
        //Log.d(TAG, "Reached onCancelled() in ImageActivity");
    }

    /**
     * This gets called when the hires image loader thread has finished.
     * @param bitmap        Bitmap object that has been read
     * @param logstring     String containing logging infos
     * @param originalSize  Size of the image as it is provided by NASA (unscaled)
     */
    @Override
    public void onPostExecute(Bitmap bitmap, String logstring, String originalSize, String filename) {
        myBitmap = bitmap;
        Log.i(TAG, logstring);
        if(myBitmap != null) {
            // Save local hires image copy to local storage. Note: this is not the original image,
            // but one, that might have been scaled to fit the device memory/texture constraints
            SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(this);
            if (shPref.getBoolean("hires_save", false)) {
                Log.i("HFCM", "Creating local copy of hires image to 'hd_" + filename);
                File img = new File(getFilesDir(), "hd_" + filename);
                if (!img.exists()) {
                    utils.writeJPG(getApplicationContext(), "hd_" + filename, bitmap, 100);
                    // TODO: catch errors!!! - change utils function
                    returnIntent.putExtra("new_hd_cached_file", true);
                }
            }

            //I/HFCM: Displaymetrics = DisplayMetrics{density=2.625, width=1080, height=1794, scaledDensity=2.625, xdpi=420.0, ydpi=420.0}
            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);
            Log.i(TAG, "Displaymetrics = " + metrics.toString());

            // these turn out to show the xdpi/ydpi values for scaledwidth/height
            // I/HFCM: Bitmap scaled width/height = 420/420
            int scaledWidth = myBitmap.getScaledWidth(metrics);
            int scaledHeight = myBitmap.getScaledHeight(metrics);
            Log.i(TAG, "Bitmap scaled width/height = " + scaledWidth + "/" + scaledHeight);

            //strSz = String.valueOf(myBitmap.getWidth()) + "x" + String.valueOf(myBitmap.getHeight());
            ivHires.setImageBitmap(myBitmap);
            //String toaster = "onPostExecute: Returned Bitmap, size = " + originalSize;
            //Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_LONG).show();
            //Log.i(TAG, "ImageActivity - onPostExecute: Returned Bitmap, size = " + originalSize);
            // new code for "matrix" scaleType - need call after loading the bmp
            // to solve problem with upper left image when using matrix
            // this now centers the image after loading.
            // constructor: left, top, right, bottom
            imgWidth = myBitmap.getWidth();
            imgHeight = myBitmap.getHeight();
            viewWidth = ivHires.getWidth();
            viewHeight = ivHires.getHeight();
            initializeMatrix();     // Init to default to fit into screen

            // Begin reading
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            if (sharedPref.getBoolean("read_out", false) && !wallPaperSelectMode) {
                android.support.v4.app.FragmentManager fm = getSupportFragmentManager();
                reader = (TextReader) fm.findFragmentByTag(TextReader.TAG_READ_FRAGMENT);
                if (reader != null) {
                    reader.read(title, explanation);
                }
            }
        } else {
            // Instead of displaying nothing, display an error bitmap. Using internal one:
            // builtin drawables: http://androiddrawables.com/
            // https://developer.android.com/reference/android/R.drawable.html
            // note: android.R.drawable vs. R.drawable
            //Bitmap bmp = BitmapFactory.decodeResource(getResources(), android.R.drawable.ic_dialog_alert);
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), R.drawable.oops);
            ivHires.setScaleType(ImageView.ScaleType.CENTER);
            ivHires.setImageBitmap(bmp);
            String toaster = "onPostExecute: No bitmap has been returned (null)";
            Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_LONG).show();
        }
        loadingBar.setVisibility(View.GONE);
        // Prepare data to be returned after closing the activity
        returnIntent.putExtra("sizeHires", originalSize);
        returnIntent.putExtra("lstIdx", listIdx);
        returnIntent.putExtra("hiresurl", strHires);
        returnIntent.putExtra("logString", logstring);
        returnIntent.putExtra("filename", imageName);
        setResult(RESULT_OK, returnIntent);

        // Initialize wallpaper selection rectangle, if we already started in active selection mode
        if (wallPaperSelectMode) {
            initWPSelect();
        } else {
            // Display a toast as TIP to the user how to enable wp selection mode
            String toaster = getString(R.string.wp_tip_long_press);
            Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_LONG).show();
        }
    }

    /**
     * We do not necessarily longpress twice, if selection is done, the wallpaper gets prepared
     * on leaving as well - resulting in dialogbox shown for confirmation
     */
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (reader != null) {
            reader.stop();
        }

        // TODO: shouldn't we remove the fragment? verify !!!
        /*FragmentManager fm = getFragmentManager();
        mHiresFragment = (ImgHiresFragment) fm.findFragmentByTag(TAG_TASK_FRAGMENT);
        if (mHiresFragment != null) {
            Log.d(TAG, "About to remove fragment");
            fm.beginTransaction().remove(mHiresFragment).commit();
        } else {
            Log.d(TAG, "Fragment is null, no remove");
        }*/
    }


    /**
     * Initialize the wallpaper selection rectangle without having the need to long click on the
     * image to activate. This is used when image activity is started from contextual action mode
     * This only gets called, if wallpaperselection mode was set by the intent.
     * TODO: this code is double from what is in onLongPress handler - cleanup the code!!
     */
    private void initWPSelect() {
        float aspectWall = (float) dispWidth / (float) dispHeight;
        if (isLandScape) {
            aspectWall = 1f / aspectWall;
        }
        //int wallWidth = isLandScape ? dispHeight : dispWidth;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
            aspectWall *= 2f;
            //wallWidth *= 2;
        }
        float aspectBitmap = (float) imgWidth / (float) imgHeight;

        // Set wallpapper selection rectangle size and moving bounds within the image view
        float wallSelectHeight;
        float wallSelectWidth;
        if (aspectBitmap >= aspectWall) {
            wallSelectHeight = scaledHeight;
            wallSelectWidth = wallSelectHeight * aspectWall;
        } else {
            wallSelectWidth = scaledWidth;
            wallSelectHeight = wallSelectWidth / aspectWall;
        }
        wpMinX = (int) (((float)viewWidth - scaledWidth) / 2f);
        wpMaxX = (int) (((float)viewWidth + scaledWidth) / 2f);
        wpMinY = (int) (((float)viewHeight - scaledHeight) / 2f);
        wpMaxY = (int) (((float)viewHeight + scaledHeight) / 2f);

        // Reset view matrix to initial fullscreen view for selection of wallpaper range
        initializeMatrix();
        ivHires.setImageMatrix(imgMatrix);

        // Calculate starting wallpaper selection Rectangle for image view
        wallPaperSelectRect.set(
                (int) (((float)viewWidth - wallSelectWidth) / 2f),
                (int) (((float)viewHeight - wallSelectHeight) / 2f),
                (int) (((float)viewWidth + wallSelectWidth) / 2f),
                (int) (((float)viewHeight + wallSelectHeight) / 2f)
        );
        ivHires.setSelectRect(wallPaperSelectRect);
        ivHires.invalidate();
    }
}
