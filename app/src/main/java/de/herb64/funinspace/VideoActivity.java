package de.herb64.funinspace;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * This class is used to play VIMEO videos in a simple Webview
 */
public class VideoActivity extends AppCompatActivity {

    WebView vimeoView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Make any bars invisible - call before setContentView()
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // + XML def in manifest: android:theme="@style/AppTheme.NoActionBar"
        setContentView(R.layout.activity_video);

        // also see
        // https://stackoverflow.com/questions/15768837/playing-html5-video-on-fullscreen-in-android-webview/

        vimeoView = (WebView) findViewById(R.id.wv_vimeo);
        vimeoView.setBackgroundColor(getResources().getColor(android.R.color.black));
        vimeoView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }
        });

        // see https://developer.android.com/studio/publish/preparing.html - security
        WebView.setWebContentsDebuggingEnabled(false);

        // just some test - but did not allow to change that...
        //vimeoView.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20100101 Firefox/10.0");
        //String ddd = vimeoView.getSettings().getUserAgentString();
        vimeoView.getSettings().setJavaScriptEnabled(true);     // Yes, we really need it :)
        vimeoView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        vimeoView.getSettings().setAllowFileAccess(false);
        vimeoView.getSettings().setAllowContentAccess(false);
        vimeoView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        vimeoView.getSettings().setAllowContentAccess(false);
        vimeoView.getSettings().setLoadsImagesAutomatically(true);
        vimeoView.getSettings().setAppCacheEnabled(true);
        //vimeoView.getSettings().setBuiltInZoomControls(true);
        //vimeoView.getSettings().setDomStorageEnabled(true);
        //vimeoView.getSettings().setPluginState(WebSettings.PluginState.ON);

        Intent intent = getIntent();
        String nasaurl = intent.getStringExtra("vimeourl");
        //String nasaurl = "https://player.vimeo.com/video/11386048#t=0m58s?color=8BA0FF&portrait=0";
        vimeoView.loadUrl(nasaurl);

        // some embedding iframe test code...
        //String iframe = "<iframe src=\"https://player.vimeo.com/video/11386048\" width=\"480\" height=\"272\" frameborder=\"0\" title=\"5.6k Saturn Cassini Photographic Animation - First 1 minute of footage from In Saturn&#039;s Rings\" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>";
        //String iframe = "<iframe src=\"https://player.vimeo.com/video/11386048?badge=0&autopause=0&player_id=0\" width=\"1280\" height=\"720\" frameborder=\"0\" title=\"5.6k Saturn Cassini Photographic Animation - First 1 minute of footage from In Saturn&#039;s Rings\" webkitallowfullscreen mozallowfullscreen allowfullscreen></iframe>";
        //String iframe = "<iframe src=\"https://player.vimeo.com/video/11386048\" width=\"480\" height=\"272\" frameborder=\"0\" title=\"5.6k Saturn Cassini Photographic Animation - First 1 minute of footage from In Saturn&#039;s Rings\"></iframe>";
        //String summary = "<html><body>You scored <b>192</b> points.</body></html>";
        //String ttt = "<html><body>" + summary + iframe + "</body></html>";
        //vimeoView.loadData(ttt, "text/html", null);

        // Just loading video in webview
        //vimeoView.loadUrl("http://player.vimeo.com/video/11386048?player_id=player&autoplay=1&title=0&byline=0&portrait=0&api=1&maxheight=480&maxwidth=800");
        //vimeoView.loadUrl("https://www.google.de");

        // infos
        // getting "MediaResourceGetter: Unable to configure metadata extractor" when hitting the play button on video view
        // find: https://bugs.chromium.org/p/chromium/issues/detail?id=400145
    }

    /**
     * TODO: rotation of phone disturbs playback (music remains, but video hangs). This
     * one here just reloads and also avoids, that music keeps playing after webview is closed
     * needs a solution here for
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        vimeoView.loadUrl("about:blank");
    }
}
