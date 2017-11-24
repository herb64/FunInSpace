package de.herb64.funinspace;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.media.Image;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.IntegerRes;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import de.herb64.funinspace.helpers.dialogDisplay;
import de.herb64.funinspace.helpers.utils;

/**
 * Created by herbert on 7/22/17.
 * Load the hires image given in passed URL. If the URL is not http..., it contains a filename to
 * be loaded locally.
 * In addition, constraints for memory allocation / glTextureSize are passed, from which the loader
 * gets the largest possible image size that can be loaded.
 * Return values: the bitmap that has been created, information about the size of the original, non
 * scaled bitmap, a logstring and the filename.
 */
// https://stackoverflow.com/questions/36947709/how-do-i-fix-or-correct-the-default-file-template-warning-in-intellij-idea

public class ImgHiresFragment extends Fragment {

    /**
     * Interface implementation is found in ImageActivity
     */
    interface myCallbacks {
        void onPreExecute();
        //void onProgressUpdate(int percent);
        void onCancelled();
        void onPostExecute(Bitmap bitmap, String logstring, String origsize, String filename);
    }

    // CONSTANTS
    private static final int KIB = 1024;
    private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;
    private static final String TAG = "HFCM";
    private static final float ALLOC_FACTOR = 0.75f;        // heap usage safety factor
    private static final boolean ALLOW_RGB565 = false;      // make this a config option ???

    // VARIABLES
    private myCallbacks mCallbacks = null;
    private int memClass;
    private int maxAlloc;
    private int texLimit;
    private String imageName = "";
    private String imgFullSize = "";
    private String logString = "";

    /**
     * Passed intent information:
     * urltoparse       the url to be loaded, or filepath if local copy exists
     * memclass
     * maxAlloc
     * maxTextureSize
     * imageName        the hires filename to be saved
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // !! Retain this fragment across configuration changes !!
        setRetainInstance(true);
        // Create and execute the background task, using the passed fragment arguments
        // Do NOT use non-default constructors for fragment classes, instead use setArguments()
        // and getArguments()
        String Url2Load = getArguments().getString("urltoparse");
        memClass = getArguments().getInt("memclass");
        maxAlloc = getArguments().getInt("maxAlloc");
        texLimit = getArguments().getInt("maxTextureSize");
        imageName = getArguments().getString("imageName");
        Log.i("HFCM", "ImageHiresFragment called for '" + imageName + "'");
        new hiresTask().execute(Url2Load);
    }

    // TODO - onAttach() - context vs. activity as parameter
    // http://www.androiddesignpatterns.com/2013/04/retaining-objects-across-config-changes.html
    // The above example uses deprecated onAttach(Activity ...)
    // Now, onAttach has a Context as parameter. Which one to use? or both? in which order?
    // BAD IDEA: on 24.07.2017 - sent apk to Klaus - but android 5 fails if the "deprecated"
    // version was commented out. So now use both methods... this is really ugly...
    // android 5: only calls onAttach(activity)
    // android 6: calls both, first activity, second context
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        Log.d(TAG, "onAttach with ACTIVITY (deprecated) in fragment");
        mCallbacks = (myCallbacks) activity;
    }

    // TODO: just cast context as myCallbacks and it works as well... WHY?
    // Well, the context is an activity as well... ??
    @Override
    public void onAttach(Context context) {
        if(mCallbacks == null) {
            super.onAttach(context);
            Log.d(TAG, "onAttach with CONTEXT in fragment");
            mCallbacks = (myCallbacks) context;
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mCallbacks = null;
    }

    // For low memory testing - this should be called if low memory situation exists
    /*@Override
    public void onLowMemory() {
        super.onLowMemory();
    }*/

    // ======================================================================================
    // This inner class contains the AsyncTask based class to load the hires image from NASA.
    // ======================================================================================

    //private class hiresTask extends AsyncTask<Void, Integer, Void> {
    private class hiresTask extends AsyncTask<String, String, Bitmap> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if(mCallbacks != null) {
                mCallbacks.onPreExecute();
            }
        }

        /* TODO Klaus Samsung S4 - still crashes, reports enough memory in memoryClass
         * check this:
         * https://stackoverflow.com/questions/1945142/bitmaps-in-android
         * AND
         * https://developer.android.com/topic/performance/graphics/manage-memory.html
         * options inBitmap - check this !!!
         * As of Android 3.0 (API level 11), the pixel data is stored on the Dalvik heap
         * along with the associated bitmap.
         * https://developer.android.com/topic/performance/memory-overview.html
         * https://stackoverflow.com/questions/21520110/android-ndk-dalvik-heap-and-native-heap-how-separate-between-the-two
         * https://developer.android.com/topic/performance/graphics/load-bitmap.html
         * mime-type not yet taken into account, Config would be fine, needs api >=26
         * Bitmap.Config cfg = options.outConfig; > api 26 min, so just assume 4 bytes
         */
        @Override
        protected Bitmap doInBackground(String... params) {
            Bitmap bmp = null;

            // avoid complaints on String.format()...
            Locale loc;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
                loc = getResources().getConfiguration().getLocales().get(0);
            } else{
                //noinspection deprecation
                loc = getResources().getConfiguration().locale;
            }

            // If we have a local file to load: url contains file path of basename
            if (!params[0].startsWith("http")) {
                Log.i("HFCM", "Loading file locally: " + params[0]);
                bmp = BitmapFactory.decodeFile(params[0]);
                logString = "Image " + imageName + " has been loaded locally from a cached copy";
                imgFullSize = "no-change";
                return bmp;
            }

            //"https://www.dropbox.com/s/q7rvk28orcx7qom/HFCM-20120927-06048.jpg";
            //"https://dl.dropboxusercontent.com/s/q7rvk28orcx7qom/HFCM-20120927-06048.jpg";
            try {
                URL imgurl = new URL(params[0]);
                try {
                    // TODO - code for documentation using HttpURLConnection
                    /*HttpURLConnection conn = (HttpURLConnection) imgurl.openConnection();
                    conn.addRequestProperty("User-Agent", ua);
                    conn.setDoInput(true);
                    conn.connect();
                    InputStream myStream = conn.getInputStream();
                    InputStream myStream = null;
                    myStream = (InputStream) imgurl.getContent();
                    bitmap = BitmapFactory.decodeStream(myStream);*/

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream((InputStream)imgurl.getContent(),null,options);
                    options.inJustDecodeBounds = false;
                    int fullW = options.outWidth;
                    int fullH = options.outHeight;
                    int scaleW = options.outWidth;
                    int scaleH = options.outHeight;
                    imgFullSize = String.valueOf(fullW) + "x" +String.valueOf(fullH);
                    int expectedMem = scaleW * scaleH * 4;
                    int maxHeap = (int) (maxAlloc * ALLOC_FACTOR) * MIB;

                    logString = String.format(loc, "Image Size: %s\n" +
                            "Needs (est): %d MiB\n" +
                            "Memory Class: %d\n" +
                            "Max Alloc: %d MiB\n" +
                            "%d%% Alloc: %d MiB\n" +
                            "Max GL Texture: %d",
                            imgFullSize,
                            expectedMem/MIB,
                            memClass,
                            maxAlloc,
                            (int) (ALLOC_FACTOR*100f), maxHeap/MIB,
                            texLimit);

                    // 04.10.2017 - Android >=N: MAX_TEXTURE_SIZE 100MiB in DisplayListCanvas.java
                    // "Canvas: trying to draw too large" exception on android >= N
                    // See sourcecode
                    // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/view/DisplayListCanvas.java
                    // hard coded limit in android code:
                    // private static final int MAX_BITMAP_SIZE = 100 * 1024 * 1024; // 100 MB
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        if (maxHeap >= 100 * MIB && expectedMem >= 100 * MIB) {
                            logString += String.format(loc, "\n\n+++ LIMIT TO CANVAS +++\n" +
                                    "Limiting %d MiB to 100 MiB (due to Android N and above hardcoded limit in DisplayListCanvas.java)",
                                    maxHeap/MIB);
                            maxHeap = 100 * MIB;
                        }
                    }

                    // TESTING - these are the actual limits for our bitmap:
                    //TODO we might add this as hidden config options to the app for testing
                    //maxHeap = 20 * MIB;
                    //texLimit = 1650;

                    // Test, if bitmap exceeds heap and/or texture limits. If yes, calculate options
                    // to rescale to optimum size value for a maximum quality in imageview
                    if (expectedMem > maxHeap || fullW > texLimit || fullH > texLimit) {
                        double aspect = (double)fullW / (double)fullH;
                        // options for memory reduction:
                        // a) inSampleSize - power of 2
                        // b) inDensity/inTargetDensity  - full control
                        // c) inDensity/inTargetDensity in combination with inSampleSize - nice
                        // d) color format RGB565 - factor 2 in storage size at same resolution

                        // First check for heap memory limitations
                        if (expectedMem > maxHeap) {
                            // get width/height that would exactly fit into allowed heap limit
                            // with: maxheap = w * h * 4 = aspect * h * h * 4
                            double fittingHeight = Math.sqrt((double)maxHeap/(4f * aspect));
                            scaleH = (int) fittingHeight;
                            scaleW = (int) (aspect * fittingHeight);
                            logString += String.format(loc, "\n\n+++ RESCALE TO MEMORY +++\n" +
                                            "Memory Match W/H: %d/%d",
                                    scaleW, scaleH);
                        }

                        // Note, texLimit with extremely low values (e.g. 100) produces strange
                        // zooming out results in imageview - but in real life, we have larger sizes

                        // We now know, that the current size will fit into memory, but does it
                        // fit into the texture size limit? If not, reduce size again to fit
                        if (scaleH > texLimit || scaleW > texLimit) {
                            if (fullW > fullH) {        // we can use full image size to compare
                                scaleW = texLimit;
                                scaleH = (int) (scaleW / aspect); // we can skip that if not logging
                            } else {
                                scaleH = texLimit;
                                scaleW = (int) (aspect * scaleH);
                            }
                            logString += String.format(loc, "\n\n+++ RESCALE TO MAX TEXTURE +++\n" +
                                    "Texture Match W/H: %d/%d",
                                    scaleW, scaleH);
                        }

                        // TODO: rethink RGB565
                        //if ALLOW_RGB565 ..
                        // We could adjust to rgb565 to allow for higher resolution at the cost of
                        // color loss, if half of required memory could be shown with same
                        // resolution. BUT: For images with dark blue sky, this looks ugly. It looks
                        // like this is not a good way, but why not add it as config option to the
                        // application, so that the user can choose.... ?
                        //options.inPreferredConfig = Bitmap.Config.RGB_565;

                        // Calculate sampling using inScaled in combination with inSampleSize for
                        // best quality and memory efficiency
                        int factor = (int) ((double)fullW / (double)scaleW);
                        // Adjust ratio to lowest power of 2 for inSampleSize - do NOT rely on the
                        // automatic adjustment to power of 2 during decode because we need this
                        // as factor for inTargetDensity as well.
                        options.inSampleSize = Integer.highestOneBit(factor); // adjust to power2
                        options.inScaled = true;
                        options.inDensity = fullW;
                        options.inTargetDensity = scaleW * options.inSampleSize;
                        logString += String.format(loc, "\ninSampleSize: %d (%d)",
                                options.inSampleSize, factor);
                    } else {
                        logString = logString + "\n\n+++NO RESCALE REQUIRED +++";
                    }

                    // jusr for Markus Hilger test:
                    //return null;

                    /* some old code snippet to calculate inSampleSize
                    int factor = (int) Math.ceil(Math.sqrt((double)requiredMemo / (double)maxMem));
                    int exp = 32 - Integer.numberOfLeadingZeros(factor-1);
                    options.inSampleSize = (int) Math.pow(2, exp);
                    // This could be better done by using the double of the lower powerof2
                    int factor = (int) ((double)maxMem / (double)requiredMem);
                    options.inSampleSize = Integer.highestOneBit(ratio) * 2;
                    */

                    // TODO document this finding about Density problem if using inScaled option
                    // >> test with createScaledBitmap() - this works
                    //    created bitmap has mDensity set to 420
                    /*
                    Bitmap tmp = BitmapFactory.decodeStream((InputStream)imgurl.getContent(),null,options);
                    Bitmap tmp1 = Bitmap.createScaledBitmap(tmp, 2289, 2289, true);
                    */

                    // >> setting inScaled option fails in ImageView Display. Image is shown
                    //    very small in top left. Saving to disk shows, that image itself is correct.
                    //    bitmap objects comparison shows, that mDensity is set to 2289 instead
                    //    of the 420 without inScaled...
                    /*
                    options.inScaled = true;
                    options.inDensity = fullW;
                    options.inTargetDensity = 2289;
                    Bitmap tmp2 = BitmapFactory.decodeStream((InputStream)imgurl.getContent(),null,options);
                    tmp2.setDensity(Bitmap.DENSITY_NONE);
                    */

                    // RESULT: bmp.setDensity(Bitmap.DENSITY_NONE) if using inScaled mechanism to
                    // avoid imageview showing bitmap very small in upper left.. at least for my
                    // scaletype=MATRIX view.

                    // interesting
                    // https://stackoverflow.com/questions/13742496/bitmapfactory-decodestream-returns-null-when-downloading-a-image-form-web
                    // 20.09.2017 - getting null bmp for apod image - but only on Elephone!
                    // reusing a stream is bad, but i do not reuse the stream for getting size of image
                    // https://stackoverflow.com/questions/41719305/android-bitmapfactory-decodestream-return-null-on-samsung-devices
                    // hmmm, very strange...


                    InputStream iStream = (InputStream) imgurl.getContent();
                    // TODO possible performance improvement? 8k is default buffer
                    BufferedInputStream bStream = new BufferedInputStream(iStream, 10240);
                    bmp = BitmapFactory.decodeStream(bStream, null, options);
                    //bmp = BitmapFactory.decodeStream((InputStream) imgurl.getContent(), null, options);
                    if (bmp != null) {
                        bmp.setDensity(Bitmap.DENSITY_NONE);        // to FIX problems in imageview
                        //int allocByteCount = utils.getBMPBytes(bmp);
                        /*int allocByteCount;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            allocByteCount = bmp.getAllocationByteCount();
                        } else {
                            allocByteCount = bmp.getByteCount();
                        }*/
                        logString += String.format(loc, "\n\nResulting Bitmap:\n" +
                                        "Scaled Size: %d/%d\n" +
                                        "Used Memory: %.2f MiB\n" +
                                        "Byte Count: %d",
                                bmp.getWidth(), bmp.getHeight(),
                                (float) bmp.getWidth() * (float) bmp.getHeight() * 4f / (float) MIB,
                                utils.getBMPBytes(bmp));
                                //allocByteCount);
                    } else {
                        logString += "\n\nA problem occurred while decoding the bitmap stream for this image";
                    }
                } catch (IOException e) {
                    logString += String.format(loc,
                            "\n\nNo image was found on URL '%s' provided by NASA",
                            imgurl);
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            return bmp;
        }

        /**
         * Implementation of onPostExecute is in ImageActivity via callback
         * @param bitmap
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if(mCallbacks != null) {
                mCallbacks.onPostExecute(bitmap, logString, imgFullSize, imageName);
            }
        }

        /**
         * Cancelled
         */
        @Override
        protected void onCancelled() {
            super.onCancelled();
            if(mCallbacks != null) {
                mCallbacks.onCancelled();
            }
        }


    }
}