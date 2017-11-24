package de.herb64.funinspace;

// Need to use support Fragment/Transaction for youtube!
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

/**
 * Created by herbert on 11/24/17.
 * New Class to make fullscreen YouTube playback possible.
 *
 * some info:
 * https://stackoverflow.com/questions/45144574/how-to-play-any-youtube-video-play-in-full-screen-in-android-app
 * https://developers.google.com/youtube/android/player/reference/com/google/android/youtube/player/YouTubePlayerView
 * https://gist.github.com/takeshiyako2/e776bbaf2966c6501c4f
 *
 */

public class YouTubeFragment extends Fragment {

    private YouTubePlayer mPlayer;
    private String mApiKey;
    private String mVideoID;

    /**
     * @param savedInstanceState saved instance state
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mApiKey = getArguments().getString("api_key");
        mVideoID = getArguments().getString("video_id");
    }

    /**
     * @param inflater menu inflater
     * @param container container
     * @param savedInstanceState instance state
     * @return
     */
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             Bundle savedInstanceState) {
        // forgetting to inflate: java.lang.IllegalStateException: Fragment does not have a view
        View rootView = inflater.inflate(R.layout.youtube_layout, container, false);
        YouTubePlayerSupportFragment playerFragment = YouTubePlayerSupportFragment.newInstance();
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.add(R.id.youtube_fullscreen, playerFragment).commit();

        playerFragment.initialize(mApiKey, new YouTubePlayer.OnInitializedListener() {
            @Override
            public void onInitializationSuccess(YouTubePlayer.Provider provider,
                                                YouTubePlayer youTubePlayer,
                                                boolean bWasRestored) {
                if (!bWasRestored) {
                    mPlayer = youTubePlayer;
                    mPlayer.setPlayerStyle(YouTubePlayer.PlayerStyle.DEFAULT);
                    mPlayer.setFullscreen(true);
                    mPlayer.loadVideo(mVideoID);
                    mPlayer.play();
                }
            }

            @Override
            public void onInitializationFailure(YouTubePlayer.Provider provider,
                                                YouTubeInitializationResult result) {
                // YouTube error
                String errorMessage = result.toString();
                Log.e("HFCM", errorMessage);
            }
        });

        return rootView;
        //return super.onCreateView(inflater, container, savedInstanceState);
    }
}
