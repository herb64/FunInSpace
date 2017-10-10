package de.herb64.funinspace;

import android.app.ActivityManager;
import android.app.FragmentManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Scroller;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import de.herb64.funinspace.helpers.dialogDisplay;


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

public class ImageActivity extends AppCompatActivity implements ImgHiresFragment.myCallbacks{
    private ImageView ivHires = null;
    private Bitmap myBitmap;
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
    private int imgWidth;
    private int imgHeight;
    private float scaledWidth;
    private float scaledHeight;
    //private int evtCount;
    private Scroller mScroller;
    private int memClass;
    private int maxAlloc;
    private int maxTextureSize;
    private String imageName;

    private static final String TAG_TASK_FRAGMENT = "img_hires_task_fragment";
    private static final String TAG = "HFCM";

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
        ivHires = (ImageView) findViewById(R.id.iv_large);
        //ivHires.setOnTouchListener(new imgTouchListener());
        SGDetector = new ScaleGestureDetector(this, new imgScaleListener());
        GDetector = new GestureDetector(this, new imgDragListener());
        mScroller = new Scroller(getApplicationContext());
        loadingBar = (ProgressBar) findViewById(R.id.pb_loading);
        imgMatrix = new Matrix();
        mValues = new float[9];
        //evtCount = 0;

        // Get info on memoryClass - needed for bitmap loading to avoid OOM situations
        ActivityManager activityManager = (ActivityManager) this.getSystemService(ACTIVITY_SERVICE);
        memClass = activityManager.getMemoryClass();

        // Handle restored instance state to recover from phone rotation
        // https://stackoverflow.com/questions/19856359/imageview-not-retaining-image-when-screen-rotation-occurs
        if (savedInstanceState != null) {
            // TODO: Docu note: using noinspection for ResourceType to avoid complaint in setVisibility()
            //       about non correct value, because we restore this from Bundle...
            myBitmap = savedInstanceState.getParcelable("apodimg");
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
            // MY SOLUTIION: just keep values and restore them exchanged (this is possible, because
            // we only use fullscreen, so x/y can be interchanged for each rotation
            viewHeight = savedInstanceState.getInt("viewWidth");
            viewWidth = savedInstanceState.getInt("viewHeight");
            imgHeight = savedInstanceState.getInt("imgHeight");
            imgWidth = savedInstanceState.getInt("imgWidth");
            maxAlloc = savedInstanceState.getInt("maxAlloc");
            maxTextureSize = savedInstanceState.getInt("maxtexturesize");
            imageName = savedInstanceState.getString("imagename");
            if (myBitmap != null) {
                initializeMatrix();
            }
            // TODO Docu: it is important to call setResult here again
            setResult(RESULT_OK, returnIntent);
        } else {
            Intent intent = getIntent();
            strHires = intent.getStringExtra("hiresurl");
            strSz = "";
            listIdx = intent.getIntExtra("listIdx", 0);
            maxAlloc = intent.getIntExtra("maxAlloc", 0);
            maxTextureSize = intent.getIntExtra("maxtexturesize",0);
            imageName = intent.getStringExtra("imagename");
            returnIntent = new Intent();

            // TODO Docu
            // OLD CODE: start the asynctask to load the image
            // NEW CODE: asynctask in retained fragment using FragmentManager
            // Fragmentmanager: see https://www.youtube.com/watch?v=Nv24t2CJ6yw !! for docu

            FragmentManager fm = getFragmentManager();
            ImgHiresFragment mHiresFragment = (ImgHiresFragment) fm.findFragmentByTag(TAG_TASK_FRAGMENT);
            // If the Fragment is non-null, it is currently being retained across a config change.
            if (mHiresFragment == null) {
                String toaster = "<" + String.valueOf(listIdx) + "> " + strHires;
                Toast.makeText(ImageActivity.this, toaster, Toast.LENGTH_LONG).show();
                mHiresFragment = new ImgHiresFragment();
                // setArguments is the way to go, do NOT use non default constructor with fragments!
                Bundle fragArguments = new Bundle();
                fragArguments.putString("urltoparse", strHires);
                fragArguments.putInt("memclass", memClass);
                fragArguments.putInt("maxAlloc", maxAlloc);
                fragArguments.putInt("maxTextureSize", maxTextureSize);
                fragArguments.putString("imageName", imageName);
                mHiresFragment.setArguments(fragArguments);
                fm.beginTransaction().add(mHiresFragment, TAG_TASK_FRAGMENT).commit();
            }
        }
    }

    // HFCM Add for state saving for rotation
    // see http://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html
    // for some good explanation on what happens here...
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable("apodimg", myBitmap);
        outState.putString("strhires", strHires);
        outState.putString("size", strSz);
        outState.putInt("loading", loadingBar.getVisibility());
        outState.putInt("lstidx", listIdx);
        outState.putParcelable("returnintent", returnIntent);
        outState.putInt("viewWidth", viewWidth);
        outState.putInt("viewHeight", viewHeight);
        outState.putInt("imgWidth", imgWidth);
        outState.putInt("imgHeight", imgHeight);
        outState.putInt("maxAlloc", maxAlloc);
        outState.putInt("maxtexturesize", maxTextureSize);
        outState.putString("imagename", imageName);
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

        // reset the matrix if doubletap is done
        // TODO does not work on Elephone P9000 - how to check this?
        @Override
        public boolean onDoubleTap(MotionEvent e) {
            //return super.onDoubleTap(e);
            initializeMatrix();
            ivHires.setImageMatrix(imgMatrix);
            return true;
        }

        // TODO: implement smoother scrolling - Scroller might be your friend :)
        // https://developer.android.com/reference/android/widget/Scroller.html
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            //super.onFling(e1, e2, velocityX, velocityY);
            //mScroller.fling();
            //Log.i(TAG, "Fling gesture: " + String.valueOf(velocityX) + "/" + String.valueOf(velocityY));
            return super.onFling(e1, e2, velocityX, velocityY);
            //return true;
        }
    }

    // ScaleGestureDetector: since android 2.2 - multiple fingers (pinch zoom)
    // return true, if we handle the event, false if not
    // see also interesting internal
    // https://stackoverflow.com/questions/30414892/how-does-matrix-postscale-sx-sy-px-py-work
    private class imgScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            //return super.onScale(detector);
            float focX = detector.getFocusX();
            float focY = detector.getFocusY();
            float factor = detector.getScaleFactor();
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

            // Apply the matrix to the image view
            ivHires.setImageMatrix(imgMatrix);
            //imgMatrix.getValues(mValues);

            // We just return true -> TODO: verify, if this is ok
            return true;
        }
    }
    // =============== END gesture detection stuff

    // MATRIX restriction stuff - removed, see 02.08.2017 tarball !!

    // ======================================================================================
    // IMPLEMENTATIONS of interface functions for ImgHiresFragment - get called from fragment
    // ======================================================================================
    @Override
    public void onPreExecute() {
        Log.d(TAG, "Reached onPreExecute() in ImageActivity");
    }

    /*@Override
    public void onProgressUpdate(int percent) {
        Log.d(TAG, "Progress is now " + String.valueOf(percent));
    }*/

    @Override
    public void onCancelled() {
        Log.d(TAG, "Reached onCancelled() in ImageActivity");
    }

    @Override
    public void onPostExecute(Bitmap bitmap, String teststring, String originalSize) {
        myBitmap = bitmap;
        Log.i(TAG, teststring);
        if(myBitmap != null) {

            // some testing here
            //saveBmpTest(myBitmap, "herbert2.jpg");

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
            Log.i(TAG, "ImageActivity - onPostExecute: Returned Bitmap, size = " + originalSize);
            // new code for "matrix" scaleType - need call after loading the bmp
            // to solve problem with upper left image when using matrix
            // this now centers the image after loading.
            // constructor: left, top, right, bottom
            imgWidth = myBitmap.getWidth();
            imgHeight = myBitmap.getHeight();
            viewWidth = ivHires.getWidth();
            viewHeight = ivHires.getHeight();
            initializeMatrix();     // Init to default to fit into screen
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
            Log.d(TAG, "ImageActivity - onPostExecute: No bitmap has been returned (null)");
        }
        loadingBar.setVisibility(View.GONE);
        // Prepare data to be returned after closing the activity
        returnIntent.putExtra("sizeHires", originalSize);
        returnIntent.putExtra("lstIdx", listIdx);
        returnIntent.putExtra("hiresurl", strHires);
        returnIntent.putExtra("logString", teststring);
        setResult(RESULT_OK,returnIntent);
    }

    /* THE OLD CODE: AsyncTask was defined HERE with doInBackground and onPostExecute.
       Problem with that: this was not safe against rotation of phone while the asynctask was
       still running, because the ImageActivity is recreated on such a config event.
       Needed to switch over to a 'retained fragment'. See new code in ImgHiresFragment.java.
    public class ImgHiresTask extends AsyncTask<String, String, Bitmap> {
        now moved this to ImgHiresFragment class, which is derived from Fragment
    }
    */

    // Just for debugging problems - make a bitmap file copy
    private void saveBmpTest(Bitmap bmp, String filename) {
        // And write the thumbnail to internal storage
        File testFile = new File(getApplicationContext().getFilesDir(), filename);
        FileOutputStream outstream = null;
        try {
            outstream = new FileOutputStream(testFile);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            if (outstream != null) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, outstream);
                outstream.flush();
                outstream.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // HFCM - was just some test
    @Override
    public void onBackPressed() {
        super.onBackPressed();
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
}
