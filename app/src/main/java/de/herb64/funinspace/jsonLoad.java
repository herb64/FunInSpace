package de.herb64.funinspace;

import android.os.AsyncTask;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/**
 * Created by herbert on 10/24/17.
 * This class is used to load json objects from a given url. Code inspired by
 * https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
 *
 * we should be able to check, if the returned string is a jsonarray or jsonobject
 * https://stackoverflow.com/questions/6118708/determine-whether-json-is-a-jsonobject-or-jsonarray
 * use JSONTokener...
 * https://developer.android.com/reference/org/json/JSONTokener.html#nextValue
 * (also JSONStringer etc... quite cool, check)
 */

// params, progress, result
public class jsonLoad extends AsyncTask {

    public AsyncResponse delegate = null;

    /*
     * Interfaces
    */
    public interface AsyncResponse {
        void processFinish(Object output);
    }

    /*
     * Constructor
     */
    public jsonLoad(AsyncResponse delegate){
        this.delegate = delegate;
    }

    @Override
    protected Object doInBackground(Object[] params) {
        HttpsURLConnection dropbox_conn = null;
        BufferedReader reader = null;
        SystemClock.sleep(2000);

        try {
            URL url = new URL((String) params[0]);
            dropbox_conn = (HttpsURLConnection) url.openConnection();
            try {
                dropbox_conn.connect();
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Read data from this connection into an input stream
            InputStream jsonstream = dropbox_conn.getInputStream();
            reader = new BufferedReader(new InputStreamReader(jsonstream));
            StringBuffer jsonbuffer = new StringBuffer();
            String jsonstring;

            while((jsonstring = reader.readLine()) != null) {
                jsonbuffer.append(jsonstring);
            }
            return jsonbuffer.toString();   // returned to onPostExecute

        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            // Running into this because of DNS problems in AVD device. Seems to be because
            // of LAN card and Wifi present in host - only on Win10 Android Studio installation
            e.printStackTrace();
            return e.getMessage();
        } finally {
            if(dropbox_conn != null) {
                try {
                    dropbox_conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /*
     * We check the returned string for valid json content and return either an object or an array
     */
    @Override
    protected void onPostExecute(Object s) {
        // s is a string and we check it for valid json content
        Object json = null;
        try {
            json = new JSONTokener((String) s).nextValue();
        } catch (JSONException e) {
            e.printStackTrace();
            delegate.processFinish(e.toString());
            return;
        }
        if (json instanceof JSONObject) {
            /*try {
                json = new JSONObject((String) s);
            } catch (JSONException e) {
                e.printStackTrace();
            }*/
            delegate.processFinish("This is a JSON Object");
        }
        else if (json instanceof JSONArray) {
            //delegate.processFinish("This is a JSON Array");
            delegate.processFinish(json);
        }
        else {
            delegate.processFinish("This is no JSON");
        }
    }
}
