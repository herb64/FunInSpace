package de.herb64.funinspace;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Created by herbert on 18.01.18.
 * Display the help information. TODO: local or via network?
 * Network requires a web server. Google Sites shows annoying redirect pages
 * Local: disadvantage: help change requires new app version to be created...
 */

public class HelpActivity extends AppCompatActivity {
    WebView helpView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);
        helpView = (WebView) findViewById(R.id.wv_privacy);
        // see https://developer.android.com/studio/publish/preparing.html - security
        WebView.setWebContentsDebuggingEnabled(false);

        WebSettings settings = helpView.getSettings();
        //helpView.getSettings().setJavaScriptEnabled(true);
        //helpView.getSettings().setSupportMultipleWindows(true);
        /*helpView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                getWindow().setTitle(title); //Set Activity tile to page title.
            }

        });*/

        helpView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return false;
            }

        });

        // TODO CHECK
        // https://www.sitepoint.com/understanding-android-webviews/

        // https://developer.android.com/studio/projects/index.html
        // ASSETS folder in app/src/main/assets -> for files to be compiled into apk as is
        // https://stackoverflow.com/questions/11961975/what-does-file-android-asset-www-index-html-mean

        String htmlString = "<h1>This is header one.</h1>\n" +
                "<a href='file:///android_asset/test.html'>der link</a>" +
                "<h2>This is header two.</h2>\n" +
                "<h3>This is header three.</h3>";

        //helpView.getSettings().setJavaScriptEnabled(false);
        helpView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        helpView.getSettings().setAllowFileAccess(false);
        helpView.getSettings().setAllowContentAccess(false);
        helpView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        helpView.getSettings().setLoadsImagesAutomatically(true);
        helpView.getSettings().setAppCacheEnabled(true);
        //Intent intent = getIntent();
        //String privacyurl = intent.getStringExtra("privacyurl");
        //helpView.loadUrl("https://sites.google.com/view/hfcm-helptest/startseite");
        //helpView.loadData(htmlString, "text/html", null);
        helpView.loadDataWithBaseURL("file:///android_asset/",
                htmlString, "text/html", null, "");
        //helpView.loadUrl("file:///test.html");
    }

    /**
     * Use onBackPressed to get back to the calling site. We need to go back 2 times, because
     * google presents a redirect page ...
     */
    @Override
    public void onBackPressed() {
        if (helpView.canGoBack()) {
            helpView.goBack();
            helpView.goBack();      // 2 x goback to skip the redirection page!!! // TODO CAUTION WITH LOCAL!!!
        } else {
            super.onBackPressed();
        }
    }
}
