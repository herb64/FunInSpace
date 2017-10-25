package de.herb64.funinspace;

import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.MimeTypeMap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

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
 */

// params, progress, result
public class asyncLoad extends AsyncTask {

    private AsyncResponse delegate = null;
    private String tag;
    private static final int OK = 0;

    /*
     * Interfaces
    */
    public interface AsyncResponse {
        void processFinish(int status, String Tag, Object output);
    }

    /*
     * Constructor
     */
    public asyncLoad(AsyncResponse delegate, String tag) {
        this.delegate = delegate;
        this.tag = tag;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        HttpsURLConnection conn = null;
        BufferedReader reader = null;
        // Just some test for asynctask parallel execution
        //Log.i("HFCM", "going to sleep:" + this.tag);
        //SystemClock.sleep(10000);

        try {
            URL url = new URL((String) params[0]);
            conn = (HttpsURLConnection) url.openConnection();
            // conn.setInstanceFollowRedirects(true);
            try {
                conn.connect();
            } catch (Exception e) {
                e.printStackTrace();
                return e.getMessage();
            }
            // Read data from this connection into an input stream
            InputStream istream = conn.getInputStream();
            String contenttype = conn.getContentType();
            String type = MimeTypeMap.getFileExtensionFromUrl(contenttype);
            Log.i("HFCM", "URL: " + params[0] + ", Content type: " + contenttype + ", Typemap info" + type);
            // URL: https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY, Content type: application/json
            // URL: https://dl.dropboxusercontent.com/s/3yqsmthlxth44w6/nasatest.json, Content type: text/plain; charset=utf-8
            // URL: https://apod.nasa.gov/apod/image/1710/M51_KerryLeckyHepburn_1024.jpg, Content type: image/jpeg
            //InputStream istream = (InputStream) url.getContent();
            reader = new BufferedReader(new InputStreamReader(istream));
            //StringBuffer jsonbuffer = new StringBuffer();
            StringBuilder mybuilder = new StringBuilder();
            String jsonstring;

            while((jsonstring = reader.readLine()) != null) {
                mybuilder.append(jsonstring);
                //jsonbuffer.append(jsonstring);
            }
            //return jsonbuffer.toString();   // returned to onPostExecute
            return mybuilder.toString();

        } catch (MalformedURLException e) {
            e.printStackTrace();
            return e.getMessage();
        } catch (IOException e) {
            // Running into this because of DNS problems in AVD device. Seems to be because
            // of LAN card and Wifi present in host - only on Win10 Android Studio installation
            e.printStackTrace();
            return e.getMessage();
        } finally {
            if(conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    return e.getMessage();
                }
            }
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            }
        }
    }

    /*
     * The returned object is delegated to the implemented processFinish() function.
     */
    @Override
    protected void onPostExecute(Object s) {
        delegate.processFinish(OK, tag, s);
    }
}
