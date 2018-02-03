package de.herb64.funinspace;

import android.content.Intent;
import android.net.UrlQuerySanitizer;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by herbert on 18.01.18.
 * Display the help information: should we have local or network based resources?
 * Network:
 * + Flexibility - help changes are online without installing a new app
 * - Needs own web server to host the pages. Using Google Sites shows annoying redirect pages,
 *   which can be avoided by parsing the query with UrlQuerySanitizer and using the direct link
 * - Google sites does only offer limited designs - to be checked
 * Local:
 * + Help is independent from network
 * - apk size increase
 * - help changes require new app version to be created
 *
 * HTML for local page using css...
 * https://www.w3schools.com/css/tryit.asp?filename=trycss_template3
 *
 * about webview.. CHECK!!!
 * http://tutorials.jenkov.com/android/android-web-apps-using-android-webview.html
 *
 */

public class HelpActivity extends AppCompatActivity {
    private WebView helpView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy);

        String startUrl = getIntent().getStringExtra("help_start_url");

        helpView = (WebView) findViewById(R.id.wv_privacy);
        // see https://developer.android.com/studio/publish/preparing.html - security
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(false);
        }

        //WebSettings settings = helpView.getSettings();
        //helpView.getSettings().setJavaScriptEnabled(true);
        //helpView.getSettings().setSupportMultipleWindows(true);
        /*helpView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                getWindow().setTitle(title); //Set Activity tile to page title.
            }

        });*/

        // We need to be able to open links within our webview. Use the following code to do that.
        // Here, we are able to start another activity (mp4 playback) when clicking a link instead
        // of just opening a link in the browser.
        // https://stackoverflow.com/questions/17788362/how-to-start-an-activity-when-a-link-is-clicked-in-webview
        // redirect stuff with sites.google.com: link within is
        // https://www.google.com/url?q=https%3A%2F%2Fsites.google.com%2Fview%2Ffuninspace-privacy-en%2Fstartseite&sa=D&sntz=1&usg=AFQjCNG0Z_8qqRdbYQErZb5jnc7J8HUsQg
        helpView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.endsWith("mp4")) {
                    Intent mp4Intent = new Intent(getApplicationContext(), MP4Activity.class);
                    mp4Intent.putExtra("mp4url", url);
                    mp4Intent.putExtra("showstats", false);
                    startActivity(mp4Intent);
                    return true;
                } else {
                    try {
                        // Handle redirections within google sites, which cause redirection window
                        // displays on navigation. Query contains the final link, so extract this
                        // and visit to avoid the redirected link.
                        String q = new URL(url).getQuery();
                        if (q != null) {
                            UrlQuerySanitizer sani = new UrlQuerySanitizer();
                            sani.setAllowUnregisteredParamaters(true);  // TYPO IN API CODE!!!
                            sani.parseQuery(q);
                            //List<UrlQuerySanitizer.ParameterValuePair> test = sani.getParameterList();
                            url = sani.getValue("q");
                        }
                        view.loadUrl(url);
                        return true;
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                        return false;
                    }
                }
            }

            // this one could be useful to intercept and load resources from assets instead
            // http://tutorials.jenkov.com/android/android-web-apps-using-android-webview.html
            /*@Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return super.shouldInterceptRequest(view, request);
            }*/

        });

        // Check this site!
        // https://www.sitepoint.com/understanding-android-webviews/

        // https://developer.android.com/studio/projects/index.html
        // ASSETS folder in app/src/main/assets -> for files to be compiled into apk as is
        // https://stackoverflow.com/questions/11961975/what-does-file-android-asset-www-index-html-mean


        /*String htmlString = "<h1>This is header one.</h1>\n" +
                "<a href='file:///android_asset/" + startUrl + "'>Link to local help url</a>" +
                "<h2>This is header two.</h2>\n" +
                "<a href='https://dl.dropboxusercontent.com/s/q0bdku8t68vc7w0/SPD.mp4'>Alfred wieder aktuell</a>" +
                "<h3>This is header three.</h3>" +
                "<a href='https://sites.google.com/view/hfcm-helptest/startseite'>Link to sites.google.com</a>";*/

        //helpView.getSettings().setJavaScriptEnabled(false);
        helpView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        helpView.getSettings().setAllowFileAccess(false);
        helpView.getSettings().setAllowContentAccess(false);
        helpView.getSettings().setAllowUniversalAccessFromFileURLs(false);
        helpView.getSettings().setLoadsImagesAutomatically(true);
        helpView.getSettings().setAppCacheEnabled(true);

        //helpView.loadUrl("https://sites.google.com/view/hfcm-helptest/startseite");
        //helpView.loadData(htmlString, "text/html", null);

        // handle phone rotation
        if (savedInstanceState == null) {
            //helpView.loadDataWithBaseURL("file:///android_asset/",
            //        htmlString, "text/html", null, "");
            helpView.loadUrl("file:///android_asset/" + startUrl);
        }
    }

    /**
     * Handle rotation
     * @param outState state to be saved
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        helpView.saveState(outState);
    }

    /**
     * Handle rotation
     * @param savedInstanceState state to be restored
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        helpView.restoreState(savedInstanceState);
    }

    /**
     * Use onBackPressed to get back to the calling site. We need to go back 2 times, because
     * google presents a redirect page ...
     */
    @Override
    public void onBackPressed() {
        if (helpView.canGoBack()) {
            helpView.goBack();
            //helpView.goBack();      // 2 x goback to skip the redirection page!!!
        } else {
            super.onBackPressed();
        }
    }
}
