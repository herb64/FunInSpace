package de.herb64.funinspace;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;

/**
 * Created by herbert on 17.01.18.
 * Display the privacy dialog. This is hosted on google sites
 */

public class PrivacyActivity extends AppCompatActivity {

    WebView privacyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);
        privacyView = (WebView) findViewById(R.id.wv_privacy);
        // see https://developer.android.com/studio/publish/preparing.html - security
        WebView.setWebContentsDebuggingEnabled(false);
        privacyView.getSettings().setJavaScriptEnabled(false);
        privacyView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        privacyView.getSettings().setAllowFileAccess(false);
        privacyView.getSettings().setAllowContentAccess(false);
        privacyView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        privacyView.getSettings().setLoadsImagesAutomatically(true);
        privacyView.getSettings().setAppCacheEnabled(true);
        Intent intent = getIntent();
        String privacyurl = intent.getStringExtra("privacyurl");
        privacyView.loadUrl(privacyurl);
    }
}
