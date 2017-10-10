package de.herb64.funinspace.helpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Debug;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import de.herb64.funinspace.MainActivity;

import static android.content.Context.ACTIVITY_SERVICE;

/**
 * Created by herbert on 7/14/17.
 */
// https://stackoverflow.com/questions/36947709/how-do-i-fix-or-correct-the-default-file-template-warning-in-intellij-idea

public class helpers {
    Context m_context;
    private int maxAlloc;
    private static final int KIB = 1024;
    private static final int MIB = 1024 * KIB;
    private static final int GIB = 1024 * MIB;

    // constructor gets context passed, because outside of Activity, many functions
    // are not available.
    public helpers(Context c) {
        m_context = c;
    }

    // read file
    // TODO; check for openFileInput()
    // https://developer.android.com/guide/topics/data/data-storage.html
    public String readf(String filename) {
        String line = null;
        // why is getApplicationContext() not found here??
        // getApplicationContext only exists in Activity, so first do getactivity
        // or pass via constructor or just extend activity, as here...

        String filesdir = m_context.getFilesDir().toString();
        try {
            //FileInputStream fileInputStream = new FileInputStream (new File(filesdir + "/" + filename));
            // why is openFileInput() not found here?
            // same: it is in activity, needs to be called in context!!!
            FileInputStream fileInputStream = m_context.openFileInput(filename);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line + System.getProperty("line.separator"));
            }
            fileInputStream.close();
            line = stringBuilder.toString();

            bufferedReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            //return("File Not found");
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            //return("IO Exception");
            return null;
        }
        return line;
    }

    public String getTestString() {
        return "This content has been created by the helper test() function within helpers package";
    }

    public void writef(String filename, String content) {
        FileOutputStream outputStream;
        try {
            outputStream = m_context.openFileOutput(filename, m_context.MODE_PRIVATE);
            //outputStream = openFileOutput(filename, getApplicationContext().MODE_WORLD_READABLE);
            outputStream.write(content.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // adding try/catch to check, if it gets catched or not. See some discussion on web, that
    // it is an ERROR, and therefore cannot be catched (at least reliably)
    // see also
    // https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/memleaks002.html
    public Bitmap makeLargeBmp(int size) {
        try {
            Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            // we might fill this with some contents for black screen issue...
            return bmp;
        } catch (OutOfMemoryError e) {
            Log.e("Error", "OutOfMemory Exception on Bitmap create");
            Log.e("BAD", e.toString());
            // E/BAD: java.lang.OutOfMemoryError: Failed to allocate a 302760012 byte allocation with 2940148 free bytes and 123MB until OOM
            // not one of those in the oracle link above...
        }
        return null;
    }

    // memory checker code to get maximum possible contiguous allocation
/*    public int getMaxAllocatable() {
        maxAlloc = 0;
        ActivityManager actMgr = (ActivityManager) m_context.getSystemService(ACTIVITY_SERVICE);
        int memClass = actMgr.getMemoryClass();
        allocTest(memClass, memClass / 2);
        return maxAlloc;
    }

    // RECURSIVE test function for memory allocations
    private void allocTest(int size, int step) {
        int result = makeBigData(size * 1024 * 1024);
        if (size > maxAlloc && result == 1) {
            maxAlloc = size;
        }
        if (step > 1) {                         // stopping here, not going to the last byte
            allocTest(size + step * result, step / 2);
        }
    }

    // Allocate a byte array of given size in bytes
    private int makeBigData(int size) {
        try {
            byte[] bigData = new byte[size];    // Alloc with new already sufficient for OOM
            //for (int i = 0; i < size; i++) {  // Fill of data needed to actually show
            //    bigData[i] = 0;               // up in memory monitor - (this could be
            //}                                 // theoretically skipped...)
            return 1;                           // return +1 on successful allocation
        } catch (OutOfMemoryError e) {          // e.toString() - to be logged in logcat
            return -1;                          // return -1 on OOM condition
        }
    }
    // END OF MEMORY ALLOCATOR TEST CODE
*/

    // Get a string with some system information for testing, which can be displayed in an
    // alertdialog. - Using different sources of information - see lalatex document for details
    // for bitmap: see
    // https://developer.android.com/topic/performance/graphics/manage-memory.html
    // and about references (strong, weak ...) - important!!
    // https://www.raizlabs.com/dev/2014/03/wrangling-dalvik-memory-management-in-android-part-1-of-2/

    // https://www.youtube.com/watch?v=_CruQY55HOk - memory management
    // heap size: G1 16MB, xoom... -> getMemoryClass() shows the heap size!, see video
    // since hnoeycomb: bitmap and pixel data both in dalvik heap, before, pixel data was separated
    // on native heap - video 13:00
    // statics live longer than the activity - memory leak - 26:10

    // to check: funinspace gc in logcat:
    // Alloc concurrent mark sweep GC freed 2176(163KB) AllocSpace objects, 1(91MB) LOS objects, 40% free, 4MB/6MB, paused 159us total 10.568ms

    // https://stackoverflow.com/questions/1945142/bitmaps-in-android (hackbod answer) bitmaps
    // are accounted on dalvik heap but bits are still in native heap now...

    // https://android.googlesource.com/kernel/exynos/+/android-exynos-3.4/Documentation/contiguous-memory.txt

    // http://ruchitsharma.blogspot.de/2013/04/android-bitmap-memory-management.html
    // AND free up memory, and weak references...
    // https://stackoverflow.com/questions/20715442/android-free-up-bitmap-memory-resources-programmatically


    // ---------------------------------------------------------------
    // UGLY CODE BELOW - JUST FOR COPYIN TEST DATA ON NON ROOTED PHONE
    // ---------------------------------------------------------------
    // Just test function used during debugging to copy files from local web server
    // to the Phone hardware /data/data/<package> directory
    /*public void copyTestFileToPhone(String url) {
        Uri fileuri = Uri.parse(url);
        String fname = fileuri.getLastPathSegment();
        if(fname.endsWith(".json")) {
            new textGetter().execute(url);
        } else if(fname.endsWith("jpg")) {
            new imageGetter().execute(url);
        }
        //Toast.makeText(MainActivity.this, "New Image of the Day has been loaded", Toast.LENGTH_LONG).show();
    }*/

    // just a testing class to get test data into the phone (images)
    private class imageGetter extends AsyncTask<String, String, Bitmap> {
        private String filename;

        @Override
        protected Bitmap doInBackground(String... params) {
            // url for image should be in params[0]...
            String url = params[0];
            Bitmap bitmap = null;
            Uri fileuri = Uri.parse(url);
            filename = fileuri.getLastPathSegment();
            try {
                URL imgurl = new URL(url);
                try {
                    bitmap = BitmapFactory.decodeStream((InputStream) imgurl.getContent());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);

            File imgFile = new File(m_context.getFilesDir(), filename);
            String outfile = imgFile.toString();

            FileOutputStream outstream = null;
            try {
                outstream = new FileOutputStream(imgFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outstream);
            try {
                if (outstream != null) {
                    outstream.flush();
                    outstream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // just a testing class to get test data into the phone (text)
    private class textGetter extends AsyncTask<String, String, String> {
        private String filename;

        @Override
        protected String doInBackground(String... params) {
            // url for image should be in params[0]...
            String url = params[0];
            String content = null;
            Uri fileuri = Uri.parse(url);
            HttpURLConnection conn = null;
            filename = fileuri.getLastPathSegment();
            BufferedReader reader = null;
            try {
                URL txturl = new URL(url);
                conn = (HttpURLConnection) txturl.openConnection();
                conn.connect();
                // We now read data from this connection into an input stream
                InputStream txtstream = conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(txtstream));
                StringBuffer txtbuffer = new StringBuffer();
                String txtstring;

                while ((txtstring = reader.readLine()) != null) {
                    txtbuffer.append(txtstring);
                }
                // this is returned to onPostExecute
                return txtbuffer.toString();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String content) {
            super.onPostExecute(content);
            writef(filename, content);
        }
    }
}
