package de.herb64.funinspace.models;

import android.graphics.Bitmap;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.ProgressBar;

import de.herb64.funinspace.MainActivity;

/**
 * Created by herbert on 7/16/17.
 */

// https://stackoverflow.com/questions/36947709/how-do-i-fix-or-correct-the-default-file-template-warning-in-intellij-idea
// TODO: include option for rating

/* 18.09.2017 - implement Parcelable to be able to add to savedInstanceSTate in MainActivity
 * see lalatex doc and
 * https://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate
 * 12.11.2017 - add wpFlag flag: 0: no wallpaper, 1: defined, 3: active
 */
public class spaceItem implements Parcelable {

    /**
     * This constructor was found to be needed, if the other constructor with parcel is present
     */
    public spaceItem() {
    }

    /**
     * constructor needed to be called from Parcelable.creator...
     * @param in
     */
    private spaceItem(Parcel in) {
        bmpThumb = CREATOR.createFromParcel(in).getBmpThumb();
    }
    // the spaceItem model has the following contents
    private String Title;
    private long DateTime;
    private String Copyright;
    private String Explanation;
    private String Lowres;
    private String Hires;
    private String Thumb;
    private Bitmap bmpThumb = null;     // set at runtime !!
    private int rating = 0;
    private String Media;
    private String HiSize = "";
    private String LowSize = "";
    private Integer ThumbLoadingState = View.INVISIBLE;
    private int maxLines = MainActivity.MAX_ELLIPSED_LINES;
    private boolean isSelected = false;
    private int wpFlag = 0;
    private boolean isCached = false;

    public String getTitle() {
        return Title;
    }

    public void setTitle(String title) {
        Title = title;
    }

    public long getDateTime() {
        return DateTime;
    }

    public void setDateTime(long dateTime) {
        DateTime = dateTime;
    }

    public String getCopyright() {
        return Copyright;
    }

    public void setCopyright(String copyright) {
        Copyright = copyright;
    }

    public String getExplanation() {
        return Explanation;
    }

    public void setExplanation(String explanation) {
        Explanation = explanation;
    }

    public String getLowres() {
        return Lowres;
    }

    public void setLowres(String lowres) {
        Lowres = lowres;
    }

    public String getHires() {
        return Hires;
    }

    public void setHires(String hires) {
        Hires = hires;
    }

    public String getThumb() {
        return Thumb;
    }

    public void setThumb(String thumb) {
        Thumb = thumb;
    }

    public Bitmap getBmpThumb() {
        return bmpThumb;
    }

    public void setBmpThumb(Bitmap bmpThumb) {
        this.bmpThumb = bmpThumb;
    }

    public int getRating() {
        return rating;
    }

    public void setRating(int rating) {
        this.rating = rating;
    }

    public String getMedia() {
        return Media;
    }

    public void setMedia(String media) {
        Media = media;
    }

    public String getHiSize() {
        return HiSize;
    }

    public void setHiSize(String hiSize) {
        HiSize = hiSize;
    }

    public String getLowSize() {
        return LowSize;
    }

    public void setLowSize(String lowSize) {
        LowSize = lowSize;
    }

    public Integer getThumbLoadingState() {
        return ThumbLoadingState;
    }

    public void setThumbLoadingState(Integer thumbLoadingState) {
        ThumbLoadingState = thumbLoadingState;
    }

    public int getMaxLines() {
        return maxLines;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }

    public int getWpFlag() {
        return wpFlag;
    }

    public void setWpFlag(int wpFlag) {
        this.wpFlag = wpFlag;
    }

    public boolean isCached() {
        return isCached;
    }

    public void setCached(boolean cached) {
        isCached = cached;
    }

    // -------------------------------------------------------
    // Functions needed to implement for Parcelable interface
    // BUT: nice to have learnt this, but this leads to transaction too large errors on Android 8.0
    // so we do not store any more the arraylist on the saved instance state.. 03.10.2017
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeParcelable(bmpThumb, 0);     // Bitmap implements parcelable
    }

    // and this one comes up as well
    public static final Parcelable.Creator<spaceItem> CREATOR = new Parcelable.Creator<spaceItem>() {
        public spaceItem createFromParcel(Parcel in) {
            return new spaceItem(in);
        }

        public spaceItem[] newArray(int size) {
            return new spaceItem[size];
        }
    };
}
