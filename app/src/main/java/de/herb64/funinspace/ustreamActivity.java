package de.herb64.funinspace;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.WindowManager;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;

public class ustreamActivity extends AppCompatActivity {

    WebView ustreamView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make any bars invisible - call before setContentView()
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // + XML def in manifest: android:theme="@style/AppTheme.NoActionBar"
        setContentView(R.layout.activity_ustream);

        ustreamView = (WebView) findViewById(R.id.wv_ustream);
        ustreamView.setBackgroundColor(getResources().getColor(android.R.color.black));
        ustreamView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return super.onJsAlert(view, url, message, result);
            }
        });

        // see https://developer.android.com/studio/publish/preparing.html - security
        WebView.setWebContentsDebuggingEnabled(false);

        // just some test - but did not allow to change that...
        //ustreamView.getSettings().setUserAgentString("Mozilla/5.0 (X11; Linux x86_64; rv:10.0) Gecko/20100101 Firefox/10.0");
        //String ddd = ustreamView.getSettings().getUserAgentString();
        ustreamView.getSettings().setJavaScriptEnabled(true);     // Yes, we really need it :)
        ustreamView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        ustreamView.getSettings().setAllowFileAccess(false);
        ustreamView.getSettings().setAllowContentAccess(false);
        ustreamView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        ustreamView.getSettings().setAllowContentAccess(false);
        ustreamView.getSettings().setLoadsImagesAutomatically(true);
        ustreamView.getSettings().setAppCacheEnabled(true);
        //ustreamView.getSettings().setBuiltInZoomControls(true);
        //ustreamView.getSettings().setDomStorageEnabled(true);
        //ustreamView.getSettings().setPluginState(WebSettings.PluginState.ON);


        Intent intent = getIntent();
        String streamurl = intent.getStringExtra("ustreamurl");
        //String streamurl = "https://www.ustream.tv/embed/17074538?v=3&wmode=direct&autoplay=true";
        ustreamView.loadUrl(streamurl);
    }

    /**
     * CHECK, if that's needed. For vimeo this was a workaround (see VideoActivity)
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        //ustreamView.loadUrl("about:blank");
    }
}
