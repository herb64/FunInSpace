package de.herb64.funinspace;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import de.herb64.funinspace.helpers.utils;

/**
 * Created by herbert on 11/29/17.
 * This class is used to just load the daily json file in a separate thread to a local file. This
 * is used by the scheduler service so that the daily file is loaded even if the user does not
 * start the app. The app needs to check on startup, if it can find any missed files locally set
 * by the scheduler job.
 */

public class apodJsonLoader implements Runnable {

    private Context ctx;
    private String url2load;
    private String file2save;
    //private TextToSpeech tts2;

    /**
     * @param ctx
     * @param url
     * @param filename
     * @param preLollipopTLS
     */
    public apodJsonLoader(Context ctx, String url, String filename, boolean preLollipopTLS) {
        this.ctx = ctx;
        this.url2load = url;
        this.file2save = filename;
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
                utils.logAppend(ctx,
                        MainActivity.DEBUG_LOG,
                        "apodJsonLoader() - " + e.getMessage());
            }
        }
    }

    /**
     *
     */
    @Override
    public void run() {
        HttpsURLConnection conn = null;
        BufferedReader reader = null;
        try {
            URL url = new URL(url2load);
            conn = (HttpsURLConnection) url.openConnection();
            try {
                conn.connect();
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                utils.logAppend(ctx,
                        MainActivity.DEBUG_LOG,
                        "apodJsonLoader() - " + e.getMessage());
            }
            InputStream istream = conn.getInputStream();
            //InputStream istream = (InputStream) url.getContent();
            reader = new BufferedReader(new InputStreamReader(istream));
            StringBuilder mybuilder = new StringBuilder();
            String jsonstring;
            while ((jsonstring = reader.readLine()) != null) {
                mybuilder.append(jsonstring);
            }
            // TODO: verify epoch in filename against datetime contained within json file
            //       THIS MUST MATCH, ELSE ERROR!!! This should handle the problem of the "old"
            //       json returned as found on 21.12.2017. In this case: how could we reschedule
            //       the loader? Otherwise, the day could be lost, if the app is not started that day

            utils.writef(ctx, file2save, mybuilder.toString());
            utils.logAppend(ctx,
                    MainActivity.DEBUG_LOG,
                    "apodJsonLoader() - " + mybuilder.toString());

            // TEST with tts - TODO: only works, if debugging, if running through, no sound
            /*tts2 = new TextToSpeech(ctx, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if(status != TextToSpeech.ERROR) {
                        tts2.setLanguage(Locale.UK);
                    }
                }
            });
            HashMap<String, String> infos = utils.parseNASAJson(mybuilder.toString());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts2.speak("New Picture" + infos.get("title"),
                        TextToSpeech.QUEUE_ADD,
                        null, null);
            } else {
                tts2.speak("New Picture" + infos.get("title"),
                        TextToSpeech.QUEUE_ADD,
                        null);
            }*/
        } catch (MalformedURLException e) {
            e.printStackTrace();
            utils.logAppend(ctx,
                    MainActivity.DEBUG_LOG,
                    "apodJsonLoader() - " + e.getMessage());
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            utils.logAppend(ctx,
                    MainActivity.DEBUG_LOG,
                    "apodJsonLoader() - " + e.getMessage());
        } catch (IOException e) {
            // Running into this because of DNS problems in AVD device. Seems to be because
            // of LAN card and Wifi present in host - only on Win10 Android Studio installation
            e.printStackTrace();
            utils.logAppend(ctx,
                    MainActivity.DEBUG_LOG,
                    "apodJsonLoader() - " + e.getMessage());
        } finally {
            // return within finally - should not use it!
            if(conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    utils.logAppend(ctx,
                            MainActivity.DEBUG_LOG,
                            "apodJsonLoader() - " + e.getMessage());
                }
            }
            try {
                if(reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
                utils.logAppend(ctx,
                        MainActivity.DEBUG_LOG,
                        "apodJsonLoader() - " + e.getMessage());
            }
        }
    }
}
