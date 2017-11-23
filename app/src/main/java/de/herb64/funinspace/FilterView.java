package de.herb64.funinspace;

import android.content.Context;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.support.v7.view.CollapsibleActionView;
import android.support.v7.widget.LinearLayoutCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import java.util.Locale;

/**
 * Created by herbert on 11/18/17.
 *
 * This class provides an alternative search for the space item list. Instead of searching for text,
 * we can now use other constraints.
 *
 * Code is heavily inspired by the Android SearchView class
 * https://android.googlesource.com/platform/frameworks/support.git/+/jb-mr2-release/v7/appcompat/src/android/support/v7/widget/SearchView.java
 *
 * See
 * https://developer.android.com/training/appbar/action-views.html
 */

public class FilterView extends LinearLayoutCompat implements CollapsibleActionView{

    private OnQueryConstraintListener mOnQueryConstraintListener;
    private checkBoxClickListener cbClickListener;
    private Context mCtx;
    //private HorizontalScrollView sv_scroll;
    //private LinearLayout ll_scroll;
    private ImageView iv_rating1;
    private ImageView iv_rating2;
    private ImageView iv_rating3;
    private ImageView iv_rating4;
    private ImageView iv_rating5;
    private ImageView iv_youtube;
    private ImageView iv_vimeo;
    private ImageView iv_video;     // MP4
    private ImageView iv_wallpaper;

    // Current selection status information, from which the filter string is built
    private int wp_select_state;
    private int video_select_state;
    private int rating_select_state;
    private int width_select_state;
    private int height_select_state;

    // C O N S T A N T S
    protected static final int FILTER_VIDEO_NONE = 0;
    protected static final int FILTER_YOUTUBE = 1;
    protected static final int FILTER_VIMEO = 1 << 1;
    protected static final int FILTER_MP4 = 1 << 2;


    /**
     * Implementation for CollapsibleActionView
     */
    @Override
    public void onActionViewExpanded() {
        //updateViewsVisibility(true);
    }

    /**
     * Implementation for CollapsibleActionView
     */
    @Override
    public void onActionViewCollapsed() {
        //clearFocus();
        //updateViewsVisibility(true);
        resetFilter();
        /*rating_select_state = 0;
        video_select_state = 0;
        wp_select_state = 0;
        width_select_state = 0;
        height_select_state = 0;
        iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_youtube.setImageResource(R.drawable.youtube_social_icon_dark);
        iv_vimeo.setImageResource(R.drawable.vimeo_icon_dark);
        iv_video.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        mOnQueryConstraintListener.onQueryConstraintChange(makeFilterString());*/
    }

    /*@Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }*/

    /**
     * Callbacks for changes to the filter constraints
     */
    public interface OnQueryConstraintListener {
        /**
         * @return true if the query has been handled by the listener, false to let the
         * FilterView perform the default action.
         * // TODO how is submit triggered in this filter view?
         */
        boolean onQueryConstraintSubmit(String s);

        /**
         * @return false if the FilterView should perform the default action of showing any
         * suggestions if available, true if the action was handled by the listener.
         */
        boolean onQueryConstraintChange(String test);

    }

    /**
     * Constructor
     * @param context
     */
    public FilterView(Context context) {
        super(context);
        this.mCtx = context;
        final LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(R.layout.filter, this, true);

        //TextView tv_test = findViewById(R.id.tv_filter);
        //tv_test.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));

        cbClickListener = new checkBoxClickListener();

        // The horizontal scroll view containing all elementes for the filterview...
        // TODO; no reaction on long click...
        /*sv_scroll = findViewById(R.id.sv_filter_scroll);
        ll_scroll = findViewById(R.id.ll_filter_scroll);
        // android:longClickable="true" in xml
        sv_scroll.setOnLongClickListener(cbClickListener);
        ll_scroll.setOnLongClickListener(cbClickListener);*/


        // Rating: just use 5 stars  - clicking on one of them sets rating (unfortnately, small
        // ratingbar does not support interaction, and large one does not fit into our view
        // This looks like to be a nice workaround (full star selects only)
        iv_rating1 = findViewById(R.id.iv_filter_star1);
        iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating1.setOnClickListener(cbClickListener);
        iv_rating1.setOnLongClickListener(cbClickListener);
        iv_rating2 = findViewById(R.id.iv_filter_star2);
        iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating2.setOnClickListener(cbClickListener);
        iv_rating2.setOnLongClickListener(cbClickListener);
        iv_rating3 = findViewById(R.id.iv_filter_star3);
        iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating3.setOnClickListener(cbClickListener);
        iv_rating3.setOnLongClickListener(cbClickListener);
        iv_rating4 = findViewById(R.id.iv_filter_star4);
        iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating4.setOnClickListener(cbClickListener);
        iv_rating4.setOnLongClickListener(cbClickListener);
        iv_rating5 = findViewById(R.id.iv_filter_star5);
        iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating5.setOnClickListener(cbClickListener);
        iv_rating5.setOnLongClickListener(cbClickListener);
        rating_select_state = 0;

        iv_youtube = findViewById(R.id.iv_filter_youtube);
        iv_youtube.setOnClickListener(cbClickListener);
        iv_youtube.setOnLongClickListener(cbClickListener);

        iv_vimeo = findViewById(R.id.iv_filter_vimeo);
        iv_vimeo.setOnClickListener(cbClickListener);
        iv_vimeo.setOnLongClickListener(cbClickListener);

        iv_video = findViewById(R.id.iv_filter_video);
        iv_video.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_video.setOnClickListener(cbClickListener);
        iv_video.setOnLongClickListener(cbClickListener);
        video_select_state = FILTER_VIDEO_NONE;

        iv_wallpaper = findViewById(R.id.iv_filter_wallpaper);
        iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_wallpaper.setOnClickListener(cbClickListener);
        iv_wallpaper.setOnLongClickListener(cbClickListener);
        wp_select_state = MainActivity.WP_NONE;
    }

    /**
     * Listener for the items in the FilterView. Implements click and longclick
     * TODO - if one star left for rating, cannot be cleared.... how to handle that?
     * TODO - rename class checkBoxClickListener ---
     */
    private class checkBoxClickListener implements OnClickListener, OnLongClickListener {

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.iv_filter_star1:
                    iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    rating_select_state = 1;
                    break;
                case R.id.iv_filter_star2:
                    iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    rating_select_state = 2;
                    break;
                case R.id.iv_filter_star3:
                    iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    rating_select_state = 3;
                    break;
                case R.id.iv_filter_star4:
                    iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                    rating_select_state = 4;
                    break;
                case R.id.iv_filter_star5:
                    iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, R.color.colorAccent));
                    rating_select_state = 5;
                    break;

                case R.id.iv_filter_youtube:
                    if ((video_select_state & FILTER_YOUTUBE) == FILTER_YOUTUBE) {
                        iv_youtube.setImageResource(R.drawable.youtube_social_icon_dark);
                        video_select_state -= FILTER_YOUTUBE;
                    } else {
                        iv_youtube.setImageResource(R.drawable.youtube_social_icon_red);
                        video_select_state += FILTER_YOUTUBE;
                    }
                    Log.i("HFCM", "Youtube filter clicked, new state=" + video_select_state);
                    break;

                case R.id.iv_filter_vimeo:
                    //Log.i("HFCM", "Vimeo filter clicked, state=" + video_select_state);
                    if ((video_select_state & FILTER_VIMEO) == FILTER_VIMEO) {
                        iv_vimeo.setImageResource(R.drawable.vimeo_icon_dark);
                        video_select_state -= FILTER_VIMEO;
                    } else {
                        iv_vimeo.setImageResource(R.drawable.vimeo_icon);
                        video_select_state += FILTER_VIMEO;
                    }
                    break;

                case R.id.iv_filter_video:
                    //Log.i("HFCM", "MP4 filter clicked, state=" + video_select_state);
                    if ((video_select_state & FILTER_MP4) == FILTER_MP4) {
                        iv_video.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                        video_select_state -= FILTER_MP4;
                    } else {
                        iv_video.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.holo_orange_light));
                        video_select_state += FILTER_MP4;
                    }
                    break;

                case R.id.iv_filter_wallpaper:
                    switch (wp_select_state) {
                        case MainActivity.WP_NONE:
                            iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.darker_gray));
                            wp_select_state = MainActivity.WP_EXISTS;
                            break;
                        case MainActivity.WP_EXISTS:
                            iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, R.color.color_hfcm_yellow_bright));
                            wp_select_state = MainActivity.WP_ACTIVE;
                            break;
                        case MainActivity.WP_ACTIVE:
                            iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
                            wp_select_state = MainActivity.WP_NONE;
                            break;
                        default:
                            break;
                    }
                    break;
                default:
                    break;
            }
            // update the filter string and pass
            mOnQueryConstraintListener.onQueryConstraintChange(makeFilterString());
        }

        /**
         * Long click resets the filters
         * @param view the clicked view
         * @return
         */
        @Override
        public boolean onLongClick(View view) {
            resetFilter();
            /*rating_select_state = 0;
            video_select_state = 0;
            wp_select_state = 0;
            width_select_state = 0;
            height_select_state = 0;
            iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            iv_youtube.setImageResource(R.drawable.youtube_social_icon_dark);
            iv_vimeo.setImageResource(R.drawable.vimeo_icon_dark);
            iv_video.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
            mOnQueryConstraintListener.onQueryConstraintChange(makeFilterString());*/
            return true;   // avoid simple click processing after
        }
    }

    public void setOnQueryConstraintListener(OnQueryConstraintListener listener) {
        mOnQueryConstraintListener = listener;
    }

    /**
     * Create a filter string to be passed to spaceItemFilter. This is created from the current
     * select_state variable contents with a special format.
     * @return String to be passed to filter
     */
    private  String makeFilterString() {
        Locale loc;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = getResources().getConfiguration().locale;
        }
        return String.format(loc,
                "%s%d:%d:%d:%d:%d",
                spaceItemFilter.FILTER_PREFIX,
                rating_select_state,
                video_select_state,
                wp_select_state,
                width_select_state,
                height_select_state
        );
    }

    /**
     * Reset filter state variables and icons in Filter View
     */
    private void resetFilter() {
        rating_select_state = 0;
        video_select_state = 0;
        wp_select_state = 0;
        width_select_state = 0;
        height_select_state = 0;
        iv_rating1.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating2.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating3.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating4.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_rating5.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_youtube.setImageResource(R.drawable.youtube_social_icon_dark);
        iv_vimeo.setImageResource(R.drawable.vimeo_icon_dark);
        iv_video.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        iv_wallpaper.setColorFilter(ContextCompat.getColor(mCtx, android.R.color.black));
        mOnQueryConstraintListener.onQueryConstraintChange(makeFilterString());
    }
}
