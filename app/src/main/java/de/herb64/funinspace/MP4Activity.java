package de.herb64.funinspace;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.VideoView;

/**
 * Created by herbert on 11/13/17.
 * MediaPlayer: https://developer.android.com/reference/android/media/MediaPlayer.html
 *              https://developer.android.com/guide/topics/media/media-formats.html
 *              https://github.com/aosp-mirror/platform_frameworks_base/blob/master/media/java/android/media/MediaPlayer.java
 */

public class MP4Activity extends AppCompatActivity {

    private VideoView mp4View;
    private MediaController mc;
    private ProgressBar pbLoad;
    private String mp4Url;
    private int start;
    private boolean bIsPlaying;

    /**
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make any bars invisible - call before setContentView()
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // + XML def in manifest: android:theme="@style/AppTheme.NoActionBar"

        setContentView(R.layout.activity_mp4);

        pbLoad = (ProgressBar) findViewById(R.id.pb_mp4_loading);
        pbLoad.setVisibility(View.INVISIBLE);

        mp4View = (VideoView) findViewById(R.id.vv_mp4);
        // mp4View.setBackgroundColor(getResources().getColor(android.R.color.black));
        // https://stackoverflow.com/questions/22402746/setting-background-colour-for-videoview-hides-the-video
        // ... PorterDuff... // TODO check
        // My trick for black bg: use framelayout and put a black match parent imageview behind videoview
        // framelayout used for loading indicator anyway...

        mp4View.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                Log.e("HFCM", "Error on MediaPlayer: what = " + what + ", extra = " + extra);
                mediaPlayer.reset();
                return false;
            }
        });

        // Registering an info listener to get warnings on buffer status
        // what = 701: MEDIA_INFO_BUFFERING_START - MediaPlayer is temporarily pausing playback internally in order to buffer more data.
        //        702: MEDIA_INFO_BUFFERING_END - MediaPlayer is resuming playback after filling buffers.
        // also found:
        //  what = 703, extra = 1819
        // TODO: Just interesting, and i seem to have found an answer to question on stackoverflow
        // https://stackoverflow.com/questions/45317172/listener-when-videoview-pause-because-of-loading-buffer-and-play-again-in-androi
        // https://developer.android.com/guide/topics/media/exoplayer.html  (exoplayer / exomedia)
        // http://google.github.io/ExoPlayer/

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mp4View.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
                    Log.w("HFCM", "Info/Warning on MediaPlayer: what = " + what + ", extra = " + extra);
                    if (what == mediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        pbLoad.setVisibility(View.VISIBLE);
                    } else if (what == mediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        pbLoad.setVisibility(View.INVISIBLE);
                    }
                    return false;
                }
            });
        }

        mp4View.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                Log.i("HFCM", "OnPrepared() - video duration: " + mp4View.getDuration() + ", starting at:" + start);
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
                Log.i("HFCM", "OnPrepared() - playback started, now at " + mp4View.getCurrentPosition());
            }
        });

        mp4View.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                int pos = mediaPlayer.getCurrentPosition();
                Log.i("HFCM", "Video complete, pos = " + pos);
                //mp4View.seekTo(3000); // it automatically seeks to start...
                mc.show(3000);
            }
        });

        if (savedInstanceState != null) {
            mp4Url = savedInstanceState.getString("url");
            start = savedInstanceState.getInt("playpos");
            bIsPlaying = savedInstanceState.getBoolean("isplaying");
            Log.i("HFCM", "Restored instance: " + mp4Url + ", Pos: " + start + " ,Playing: " + bIsPlaying);
        } else {
            // Get video URI from intent
            Intent intent = getIntent();
            mp4Url = intent.getStringExtra("mp4url");
            start = 0;
            bIsPlaying = true;
        }
        mp4View.setVideoURI(Uri.parse(mp4Url));
        // Create mediacontroller
        //MediaController
        mc = new MediaController(this);
        mc.setAnchorView(mp4View);
        //mc.setMediaPlayer(mp4View);
        mp4View.setMediaController(mc);
        pbLoad.setVisibility(View.VISIBLE);
    }

    /**
     * Save current playback position and url string
     * @param outState
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("playpos", mp4View.getCurrentPosition());
        outState.putBoolean("isplaying", mp4View.isPlaying());
        outState.putString("url", mp4Url);
        Log.i("HFCM", "Saving instance: " + mp4Url + ", Pos: " + mp4View.getCurrentPosition() + ", Playing: " + mp4View.isPlaying());
    }
}
