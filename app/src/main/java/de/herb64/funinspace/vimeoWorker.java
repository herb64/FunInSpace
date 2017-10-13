package de.herb64.funinspace;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.HttpsURLConnection;

import de.herb64.funinspace.helpers.dialogDisplay;

/**
 * Created by herbert on 10/11/17.
 * Use this class as a utility class with static member functions.
 * - static member functions called on the "class", NOT an "instance"
 */


public class vimeoWorker {

    private String mVideoId = null;
    private String mThumbUrl = null;
    private ArrayList<String> result;

    // Constructor with URL to use
    public vimeoWorker(String url) {
        new vimeoInfoTask().execute(url);
    }

    public String getVideoId() {
        return mVideoId;
    }

    public String getThumbURL() {
        return mThumbUrl;
    }



    /*public ArrayList<String> getVimeoInfos(String url) {
        result = new ArrayList<>();
        String test;
        vimeoInfoTask task = new vimeoInfoTask();
        task.execute(url);
        //new vimeoInfoTask().execute(url);
        return result;
    }*/

    private class vimeoInfoTask extends AsyncTask<String, String, String> {

        @Override
        protected String doInBackground(String... params) {
            HttpsURLConnection vimeo_conn = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                vimeo_conn = (HttpsURLConnection) url.openConnection();
                vimeo_conn.connect();
                // We now read data from this connection into an input stream
                InputStream jsonstream = vimeo_conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(jsonstream));
                StringBuffer jsonbuffer = new StringBuffer();
                String jsonstring;

                while((jsonstring = reader.readLine()) != null) {
                    jsonbuffer.append(jsonstring);
                }
                // this is returned to onPostExecute
                return jsonbuffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                // TODO : handle over rate limit here
                // OVER_RATE_LIMIT
                // You have exceeded your rate limit. Try again later or contact us at https://api.nasa.gov/contact/ for assistance
                return "FILENOTFOUND";
            } catch (IOException e) {
                // running into this because of DNS problems in AVD device. Seems to be because
                // of LAN card and Wifi present in host - Problem on my Windows Studio installation
                e.printStackTrace();
                //String ttt = e.getMessage();
                return e.getMessage();
            } finally {
                if(vimeo_conn != null) {
                    try {
                        vimeo_conn.disconnect();
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

        @Override
        protected void onPostExecute(String s) {
            JSONObject parent = null;
            Uri resource_uri = null;
            if(s != null) {
                if (s.equals("FILENOTFOUND")) {
                    return;
                }
            }
            try {
                parent = new JSONObject(s);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(parent == null) {
                return;
            }
            // We are interested in video ID and thumbnail URL
            if(parent.has("video_id")) {
                try {
                    mVideoId = parent.getString("video_id");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if(parent.has("thumbnail_url")) {
                try {
                    mThumbUrl = parent.getString("thumbnail_url");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            // TODO: URI also of interest!!!


            super.onPostExecute(s);
        }
    }

}
