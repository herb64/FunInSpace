package de.herb64.funinspace;

import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;

/**
 * Created by herbert on 10/22/17.
 * Now using ViewHolder for our ListView Adapter to avoid overhead by findViewById() calls.
 * This is my own simple class used as a view holder with my listview adapter.
 */

class ViewHolder {
    ImageView ivThumb;
    ImageView ivYoutube;
    ImageView ivCached;
    ImageView ivWallpaper;
    RatingBar rbRating;
    TextView tvTitle;
    TextView tvExplanation;
    TextView tvDate;
    TextView tvCopyright;
    TextView tvLowSize;
    TextView tvHiSize;
    ProgressBar lbThumb;
}
