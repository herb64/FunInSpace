package de.herb64.funinspace;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.content.ContextCompat;
import android.util.AttributeSet;

/**
 * Created by herbert on 11/6/17.
 * This class allows to draw on top of the fullscreen image. This is used to position a rectangle
 * on the image to allow the user to choose the bitmap portion to be used as wallpaper.
 */

public class drawableImageView extends android.support.v7.widget.AppCompatImageView {

    //private static final String SELECTSTRING = .getString(R.string.wp_select_rect_string);
    private static final float STARTSIZE = 48f;
    private Rect mSelectRect = null;
    private Rect txtbounds = null;
    private Paint pnt = null;
    private Paint txtpaint;
    private float txtSize;
    private String selectString;

    /**
     * This  constructor is actually not used...
     * @param context
     */
    /*public drawableImageView(Context context) {
        super(context);
    }*/

    /**
     * Constructor with AttributeSet required to avoid "android.view.InflateException"
     * > Binary XML file line #24: Error inflating class de.herb64.funinspace.drawableImageView
     * > fix deprecation for getColor()
     * @param context   Context
     * @param attrs     AttributeSet
     */
    public drawableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        selectString = context.getString(R.string.wp_select_rect_string);
        pnt = new Paint();
        pnt.setColor(ContextCompat.getColor(getContext(), android.R.color.holo_green_light));
        pnt.setStyle(Paint.Style.STROKE);
        txtbounds = new Rect();
        txtpaint = new Paint();
        txtSize = 0f;
        txtpaint.setColor(ContextCompat.getColor(getContext(), android.R.color.holo_blue_bright));
        //txtpaint.setColor(getContext().getResources().getColor(android.R.color.holo_blue_bright));
        txtpaint.setTextSize(STARTSIZE);
        txtpaint.setAlpha(192);
    }


    /**
     * Draw our own elements on top of
     * @param canvas    The canvas we get passed for our drawing activities
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        pnt.setStrokeWidth(5);  // use resources!!
        if (mSelectRect != null) {
            canvas.drawRect(mSelectRect, pnt);
            // < Lollipop: double wallpaper width - draw 2 line separators to mark center region
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                pnt.setStrokeWidth(2);
                int tl = mSelectRect.left + mSelectRect.width()/4;
                int tr = mSelectRect.right - mSelectRect.width()/4;
                canvas.drawLine(tl, mSelectRect.top, tl, mSelectRect.bottom, pnt);
                canvas.drawLine(tr, mSelectRect.top, tr, mSelectRect.bottom, pnt);
            }
            canvas.drawText(selectString, mSelectRect.left + 10, mSelectRect.bottom - 10, txtpaint);
        }
    }

    /*@Override
    protected void onFinishInflate() {
        super.onFinishInflate();
    }*/

    /**
     * Set the selection Rectangle to be drawn on top of the bitmap. Passing a value of null means
     * that no rectangle is to be drawn (we are not in wallpaper select mode)
     *
     * @param mSelectRect The selection Rectangle in screen coordinates.
     */
    public void setSelectRect(Rect mSelectRect) {
        this.mSelectRect = mSelectRect;
        // If wallpaper select enabled and text size not yet set, do it once
        if (mSelectRect != null && txtSize == 0f) {
            txtpaint.setTextSize(STARTSIZE);
            txtpaint.getTextBounds(selectString, 0, selectString.length(), txtbounds);
            txtSize = STARTSIZE * (float) mSelectRect.width() / txtbounds.width() * 0.7f;
            txtpaint.setTextSize(txtSize);
        }
    }
}
