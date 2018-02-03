package de.herb64.funinspace;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.MotionEvent;
import android.view.Window;
import android.webkit.WebView;
import android.widget.TextView;

import java.util.Locale;

import de.herb64.funinspace.helpers.utils;

/**
 * Created by herbert on 20.12.17.
 * https://www.youtube.com/watch?v=rsKHeuBKnNc check this one, makoto and also github...
 */

public class AboutDialog extends Dialog {

    private Context ctx;
    private WebView wvAbout;

    public AboutDialog(@NonNull Context context) {
        super(context);
        this.ctx = context;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //super.onCreate(savedInstanceState);
        setContentView(R.layout.about_dialog);
        TextView tvVersion = (TextView) findViewById(R.id.tv_about_version);
        tvVersion.setText("FunInSpace " + utils.getVersionInfo(ctx));
        tvVersion.setTextSize(12f);

        TextView tvInfoTitle = findViewById(R.id.tv_about_infos_title);
        TextView tvInfoContent = findViewById(R.id.tv_about_infos_content);
        tvInfoTitle.setText("About the app");
        tvInfoContent.setText("This is the alpha testing version of FunInSpace, which delivers the daily image provided by NASA ...");
        tvInfoContent.setTextSize(10f);

        TextView tvCredTitle = findViewById(R.id.tv_about_credits_title);
        TextView tvCredContent = findViewById(R.id.tv_about_credits_content);
        tvCredTitle.setText("Credits");
        tvCredContent.setText(R.string.about_credits);
        tvCredContent.setTextSize(10f);

        TextView tvStatsTitle = findViewById(R.id.tv_about_stats_title);
        TextView tvStatsContent = findViewById(R.id.tv_about_stats_content);
        tvStatsTitle.setText("Memory Use");
        tvStatsContent.setText(utils.getFileStats(ctx, Locale.getDefault()));
        tvStatsContent.setTextSize(10f);

        TextView tvThksTitle = findViewById(R.id.tv_about_thanks_title);
        TextView tvThksContent = findViewById(R.id.tv_about_thanks_content);
        tvThksTitle.setText("Special thanks");
        tvThksContent.setText("Special thanks for very early testing to Klaus Eckel, Michael Lauffs, Jonathan Hermann, Markus Hilger, Gerhard Biefel");
        tvThksContent.setTextSize(10f);

        /* WebView: does load once, then not ... alternating. loadUrl does not work at all...
        wvAbout = findViewById(R.id.wv_about);
        wvAbout.setBackgroundColor(Color.CYAN);
        // https://developer.android.com/reference/android/webkit/WebView.html
        // https://www.journaldev.com/9333/android-webview-example-tutorial
        String summary = "<html><body>You scored <b>192</b> points.</body></html>";
        //wvAbout.loadData(summary, "text/html", "UTF-8");
        wvAbout.loadUrl("www.google.de");
        //wvAbout.loadDataWithBaseURL("", summary, "text/html", "UTF-8", "");*/

    }



    @Override
    public boolean onTouchEvent(@NonNull MotionEvent event) {
        //this.dismiss();
        return super.onTouchEvent(event);
    }
}
