package de.herb64.funinspace;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import de.herb64.funinspace.helpers.utils;
import okhttp3.MediaType;

/**
 * Created by herbert on 10/24/17.
 * This class is used to load json objects from a given url. Code inspired by this hightly rated
 * stackoverflow contribution:
 * https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
 * we should be able to check, if the returned string is a jsonarray or jsonobject
 * https://stackoverflow.com/questions/6118708/determine-whether-json-is-a-jsonobject-or-jsonarray
 * use JSONTokener...
 * https://developer.android.com/reference/org/json/JSONTokener.html#nextValue
 * (also JSONStringer etc... quite cool, check)
 * Important: parallel execution of AsyncTasks - did some testing, see also my LaLaTeX doc
 * https://stackoverflow.com/questions/4068984/running-multiple-asynctasks-at-the-same-time-not-possible
 * https://stackoverflow.com/questions/29937556/asynctask-execute-or-executeonexecutor
 *
   Old code with inner class - dropped that to implement interface in MainActivity
   asyncLoad(new asyncLoad.AsyncResponse() {
   @Override
   public void processFinish(Object output) {
   ....
   }
   }).execute("url to load");
 //new asyncLoad(MainActivity.this, "DROPBOX_REFRESH").execute(DROPBOX_JSON);
 //new asyncLoad(MainActivity.this, "DROPBOX_REFRESH").execute(DROPBOX_MINI);
 //new asyncLoad(this, "NASA-TEST").execute("https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY");
 //new asyncLoad(this, "NASA-TEST").executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY");
 //new asyncLoad(this, "JPEG_TEST").execute("https://apod.nasa.gov/apod/image/1710/M51_KerryLeckyHepburn_1024.jpg");

 * Change to support a list of urls and use publishprogress to return one after the other..
 *
 * 07.11.2017 - adding constructor to pass region/scale values in conjunction with wallpaper tests
 */

// params, progress, result
public class asyncLoad extends AsyncTask {

    private AsyncResponse delegate = null;
    private String tag;
    private int status;
    private boolean preLollipopTLS = true;
    private Rect region = null;         // for testing bitmap region decode
    private int scale = 1;              // region decode with insamplesize
    private int socketTimeout = 0;

    // Constants
    protected static final int EXCEPTION = -1000;
    protected static final int NOSOCKET = -1001;
    /*protected static final int IOEXCEPTION = -1001;
    protected static final int FILENOTFOUND = -1002;
    protected static final int MALFORMEDURL = -1003;*/

    /*
     * Interfaces
     * TODO: what about an interface for progress update?
    */
    public interface AsyncResponse {
        void processFinish(int status, String Tag, Object output);
        void processProgressUpdate(int status, String Tag, Object output);
    }

    /**
     * Constructor
     * @param delegate  delegate
     * @param tag       Tag for identification
     */
    public asyncLoad(AsyncResponse delegate, String tag) {
        this.delegate = delegate;
        this.tag = tag;
        //status = OK;
    }

    /**
     * This constructor is used to pass a socket timeout // TODO activate for use, inactive now
     * @param delegate
     * @param tag
     * @param socketTimeout timeout in milliseconds
     */
    public asyncLoad(AsyncResponse delegate, String tag, int socketTimeout) {
        this.delegate = delegate;
        this.tag = tag;
        this.socketTimeout = socketTimeout;
    }

    /**
     * This constructor needs to be used, if we want to run on systems < lollipop and want to
     * be able to disable the TLS option (useful for testing only)
     * @param delegate          delegate
     * @param tag               tag for identification
     * @param preLollipopTLS    flag to enable TLS for systems < lollipop (for debug purpose)
     */
    public asyncLoad(AsyncResponse delegate, String tag, boolean preLollipopTLS) {
        this.delegate = delegate;
        this.tag = tag;
        //status = OK;
        this.preLollipopTLS = preLollipopTLS;
    }

    /**
     * This constructor is used to pass a region/scale value for a bitmap load - experimental
     * @param delegate
     * @param tag
     * @param preLollipopTLS
     * @param region
     */
    public asyncLoad(AsyncResponse delegate, String tag, boolean preLollipopTLS, Rect region, int scale) {
        this.delegate = delegate;
        this.tag = tag;
        //status = OK;
        this.preLollipopTLS = preLollipopTLS;
        this.region = region;
        this.scale = scale;
    }

    /**
     * The background thread.
     * @param params    Array of objects passed by call to execute()
     * @return          Return of the object
     */
    @Override
    protected Object doInBackground(Object[] params) {
        // Enable TLSv1 below 5.0 (Lollipop) TODO: TLS > 1.0 / move to oncreate / android 7
        // https://blog.dev-area.net/2015/08/13/android-4-1-enable-tls-1-1-and-tls-1-2/
        // https://developer.android.com/about/versions/nougat/android-7.0-changes.html#tls-ssl
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && preLollipopTLS) {
            // shared preferences not available here - would need to pass context
            //if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP &&
            //        sharedPref.getBoolean("enable_tls_pre_lollipop", true)) {
            SSLContext sslcontext = null;
            try {
                sslcontext = SSLContext.getInstance("TLSv1");
                sslcontext.init(null, null, null);
                SSLSocketFactory noSSLv3Factory = new TLSSocketFactory();
                HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory);
                Log.i("HFCM", "Running with TLS on pre Lollipop");
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                e.printStackTrace();
            }
        }

        // check params in case we get a list of multiple urls - not used any more for now
        if (params[0] instanceof ArrayList) {
            int len = ((ArrayList) params[0]).size();
            Log.i("HFCM", "Called asyncLoad() with a list of " + len + " URLs");
            for (String url : (ArrayList<String>) params[0]) {
                publishProgress(getFromUrl(url));
            }
        } else if (params[0] instanceof String) {
            Log.i("HFCM", "Called asyncLoad() with a single URL");
            return getFromUrl((String) params[0]);
        } else {
            Log.e("HFCM", "Called asyncLoad().execute() with bad type");
        }
        return null;
    }

    /**
     * Load an object from a given URL. Called for each element passed in. Important: if not used
     * with socketTimeout, this may hang for 10s * #of resolved IPs if DNS is not available.
     * @param url2load the URL from which to load
     * @return should be 200 (HTTP ok)
     */
    private Object getFromUrl(String url2load) {
        HttpsURLConnection conn = null;
        BufferedReader reader = null;

        try {
            // if socketTimeout has been passed in constructor, trigger a test first to avoid DNS
            // IOException timeout.
            /*if (socketTimeout != 0 && utils.testSocketConnect(socketTimeout) == socketTimeout) {
                status = NOSOCKET;
                return "Socket test failed";
            }*/

            // checking, if https or http - use android webkit classes
            // TODO - determine type of url and put TLS stuff in here!!
            // URLUtil.isHttpUrl((String) params[0]);
            // openConnection: IOException may take 40 seconds in case of DNS problems
            URL url = new URL(url2load);
            conn = (HttpsURLConnection) url.openConnection();
            // conn.setInstanceFollowRedirects(true);
            status = conn.getResponseCode();

            if (status != HttpsURLConnection.HTTP_OK) {
                return "[" + conn.getResponseCode() + "] " + conn.getResponseMessage();
            }

            // Actually, the connection is already setup on openConnection() - do not need to run
            // connect() again. Did do that before as found in some example code, now removed again
            /*try {
                conn.connect();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                return e.getMessage();
            }*/

            // Read data from this connection into a buffered input stream (default: 8192 bytes)
            InputStream istream = conn.getInputStream();
            //InputStream istream = (InputStream) url.getContent();
            BufferedInputStream bStream = new BufferedInputStream(istream);

            String contenttype = conn.getContentType();
            String type = MimeTypeMap.getFileExtensionFromUrl(contenttype);
            // MediaMetadataRetriever also provides MIME information and more...
            Log.i("HFCM", "URL: " + url2load + ", Content type: " + contenttype + ", Typemap info" + type);
            // https://api.nasa.gov/planetary/apod?api_key=X, Content type: application/json
            // https://dl.dropboxusercontent.com/s/./X.json, Content type: text/plain; charset=utf-8
            // https://apod.nasa.gov/apod/image/1710/X.jpg, Content type: image/jpeg
            // TODO this must be done better (image/*) etc...
            if (contenttype.startsWith("image")) {
                if (region != null) {
                    BitmapRegionDecoder rD = BitmapRegionDecoder.newInstance(istream, false);
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = scale;
                    return rD.decodeRegion(region, options);
                } else {
                    return BitmapFactory.decodeStream(istream);
                }
            } else {
                // TODO - bad to handle everything else just as string
                reader = new BufferedReader(new InputStreamReader(istream));
                //StringBuffer jsonbuffer = new StringBuffer(); // better use StringBuilder - DOCU!
                StringBuilder mybuilder = new StringBuilder();
                String readstring;

                while ((readstring = reader.readLine()) != null) {
                    mybuilder.append(readstring);
                    //jsonbuffer.append(jsonstring);
                }
                //return jsonbuffer.toString();   // returned to onPostExecute
                return mybuilder.toString();
            }
        } catch (Exception e) {
            // IOException: AVD on host with active WIFI, but DSL plugged out - leads to
            // Unable to resolve host "api.nasa.gov": No address associated with hostname
            // Note: AVD on Win10 host: LAN card and Wifi present in host show same effect
            e.printStackTrace();
            status = EXCEPTION;
            return e.getMessage();
        /*} catch (MalformedURLException e) {
        } catch (FileNotFoundException e) {
        } catch (IOException e) */
        } finally {
            // return within finally - should not use it!
            if(conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    //return e.getMessage();
                }
            }
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                //return e.getMessage();
            }
        }
    }

    /**
     * The returned object is delegated to the implemented processFinish() function.
     * @param s The object returned from background thread
     */
    @Override
    protected void onPostExecute(Object s) {
        delegate.processFinish(status, tag, s);
    }

    /**
     * Progress update, used if asyncLoad gets passed an array of urls to load
     * @param s The object returned by progress update from background thread publishProgress()
     */
    @Override
    protected void onProgressUpdate(Object[] s) {
        delegate.processProgressUpdate(status, tag, s);
    }
}
