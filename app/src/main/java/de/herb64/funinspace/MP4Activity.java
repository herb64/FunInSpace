package de.herb64.funinspace;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.VideoView;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by herbert on 11/13/17.
 * MediaPlayer: https://developer.android.com/reference/android/media/MediaPlayer.html
 *              https://developer.android.com/guide/topics/media/media-formats.html
 *              https://github.com/aosp-mirror/platform_frameworks_base/blob/master/media/java/android/media/MediaPlayer.java
 * Getting metadata via async task
 */

public class MP4Activity extends AppCompatActivity {

    private VideoView mp4View;
    private MediaController mc;
    private ProgressBar pbLoad;
    private String mp4Url;
    private boolean bShowStats;
    private int start;
    private boolean bIsPlaying;

    // Metadata information for mp4 stream, filled asynchronously in separate thread
    private String bitrate = "0.0";
    private String duration = "0.0";
    private String mimetype = "n/a";
    private String fmt = "n/a";

    /**
     * @param savedInstanceState The saved infos
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make any bars invisible - call before setContentView()
        // + XML def in manifest: android:theme="@style/AppTheme.NoActionBar"
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_mp4);

        pbLoad = (ProgressBar) findViewById(R.id.pb_mp4_loading);
        pbLoad.setVisibility(View.VISIBLE);

        // mp4View.setBackgroundColor(getResources().getColor(android.R.color.black));
        // https://stackoverflow.com/questions/22402746/setting-background-colour-for-videoview-hides-the-video
        // TODO: ... PorterDuff... check
        // My trick for black bg: Use framelayout and put a black match parent imageview behind
        // videoview. Framelayout is used for loading indicator display anyway...
        mp4View = (VideoView) findViewById(R.id.vv_mp4);
        // TODO: need to recheck the reset() logic - need to reinstanciate the player as well
        mp4View.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                Log.e("HFCM", "Error on MediaPlayer: what = " + what + ", extra = " + extra);
                mediaPlayer.reset();
                return false;
            }
        });

        // With JellyBean MR1 we can get infos on buffering state. This is used to display the
        // loading indicator, if we run out of buffer during playback
        // what = 701: MEDIA_INFO_BUFFERING_START - temporarily pausing playback to refill buffers
        //        702: MEDIA_INFO_BUFFERING_END - resuming playback after buffers filled
        // also encountered these: what = 703, extra = 1819
        // TODO: Just interesting, and i seem to have found an answer to question on stackoverflow
        // https://stackoverflow.com/questions/45317172/listener-when-videoview-pause-because-of-loading-buffer-and-play-again-in-androi
        // https://developer.android.com/guide/topics/media/exoplayer.html  (exoplayer / exomedia)
        // TODO: http://google.github.io/ExoPlayer/
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mp4View.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
                    Log.w("HFCM", "Info/Warning on MediaPlayer: what = " +
                            what + ", extra = " + extra);
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        pbLoad.setVisibility(View.VISIBLE);
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        pbLoad.setVisibility(View.INVISIBLE);
                    }
                    return false;
                }
            });
        }

        // Video playback is prepared. Seek to start (might be in middle of video after a screen
        // rotation has occurred)
        mp4View.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                Log.i("HFCM", "OnPrepared() - video duration: " + mp4View.getDuration() +
                        ", starting at:" + start);
                // size change listener should not be needed at all
                mediaPlayer.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                    @Override
                    public void onVideoSizeChanged(MediaPlayer mediaPlayer, int w, int h) {
                        Log.i("HFCM", "onVideoSizeChangedListener called: " + w + "/" + h);
                    }
                });
                mediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
                    @Override
                    public void onBufferingUpdate(MediaPlayer mediaPlayer, int i) {
                        Log.i("HFCM", "OnBufferingUpdateListener called: " + i);
                    }
                });
                mp4View.seekTo(start);
                if (bIsPlaying) {
                    mp4View.start();
                }
                pbLoad.setVisibility(View.INVISIBLE);
                mc.show(4000);
            }
        });

        mp4View.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                int pos = mediaPlayer.getCurrentPosition();
                Log.i("HFCM", "Video complete, pos = " + pos);
                //mp4View.seekTo(3000); // it automatically seeks to start...
                mc.show(4000);
            }
        });

        // Read the URL from the intent (or get status from savedInstanceState on rotate)
        if (savedInstanceState != null) {
            mp4Url = savedInstanceState.getString("mp4url");
            start = savedInstanceState.getInt("playpos");
            bIsPlaying = savedInstanceState.getBoolean("isplaying");
            bShowStats = savedInstanceState.getBoolean("showstats");
            Log.i("HFCM", "Restored instance: " + mp4Url + ", Pos: " + start +
                    " ,Playing: " + bIsPlaying);
        } else {
            Intent intent = getIntent();
            mp4Url = intent.getStringExtra("mp4url");
            bShowStats = intent.getBooleanExtra("showstats", true);
            start = 0;
            bIsPlaying = true;
        }

        // MediaController allows to control playback with buttons and progress bar
        mc = new MediaController(this);
        mc.setAnchorView(mp4View);
        mp4View.setMediaController(mc);
        mp4View.setVideoURI(Uri.parse(mp4Url));

        // Get metadata (extra thread to avoid delays in main thread)
        new metaDataTask().execute();
    }

    /**
     * Save current playback position and url string
     * @param outState The bundle to put in relevant data
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("playpos", mp4View.getCurrentPosition());
        outState.putBoolean("isplaying", mp4View.isPlaying());
        outState.putString("mp4url", mp4Url);
        outState.putBoolean("showstats", bShowStats);
        /*Log.i("HFCM", "Saving instance: " + mp4Url +
                ", Pos: " + mp4View.getCurrentPosition() +
                ", Playing: " + mp4View.isPlaying());*/
    }

    /**
     * AsyncTask to retrieve metadata
     * // TODO - again unsure about using asynctask this way (in terms of memoryleaks)
     */
    private class metaDataTask extends AsyncTask<Void, Void, ArrayList<String>> {
        /**
         * Kick off the MediaMetadataRetriever in background, which adjusts the informations on
         * bitrate, duration, mimetype and format
         * @param voids it's called with no parameters at all
         * @return ArrayList of Strings containing requested metadata
         */
        @Override
        protected ArrayList<String> doInBackground(Void... voids) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(Uri.parse(mp4Url).toString(),
                        new HashMap<String, String>());
                // For Docu and debugging: seems like timeout
                // retriever.setDataSource(Uri.parse("http://192.168.1.33/hugo.mp4").toString(),
                //         new HashMap<String, String>());
                // E/NuCachedSource2: source returned error -1, 1 retries left
                // E/NuCachedSource2: source returned error -1, 0 retries left
                // E/StagefrightMetadataRetriever: Unable to instantiate an extractor for 'http://192.168.1.33/hugo.mp4'.
                // this delays the exception to appear even after the activity might have been closed
                bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                mimetype = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);
                fmt = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) +
                        "x" +
                        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            } catch (Exception e) {
                e.printStackTrace();
                Log.e("HFCM", e.toString());
            }
            return null;
        }

        /**
         * TODO acutally, no parameters are needed, we just update variables of activity class
         * @param strings The arraylist of strings
         */
        @Override
        protected void onPostExecute(ArrayList<String> strings) {
            if (bShowStats && !bitrate.equals("0.0")) {
                Toast.makeText(getApplicationContext(),
                        "Bitrate: "
                        + new DecimalFormat("#.##").format(Float.parseFloat(bitrate) / 1048576)
                        + "Mbit/s\nMimeType: "
                        + mimetype
                        + "\nDuration: "
                        + new DecimalFormat("#.#").format(Float.parseFloat(duration) / 1000)
                        + " sec \nFormat: " + fmt,
                        Toast.LENGTH_LONG).show();
            }
            super.onPostExecute(strings);
        }
    }
}
