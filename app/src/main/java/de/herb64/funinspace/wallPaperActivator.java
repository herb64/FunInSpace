package de.herb64.funinspace;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.IOException;


/**
 * Created by herbert on 11/11/17.
 * Setting the wallpaper image in main activity did cause a noticable delay when returning from
 * the image activity. 3 Seconds on Elephone P9000, even more on Klaus' Samsung S4
 * This class allows to run this asynchronously.
 * Had to make it public for access by shuffleJobService in conjunction with wallpaper shuffle
 */

public class wallPaperActivator implements Runnable {

    private String wpFileName;
    private Context ctx;

    /**
     * Constructor
     * @param ctx       Context
     * @param filename  Filename to be set
     */
    public wallPaperActivator(Context ctx, String filename) {
        this.wpFileName = filename;
        this.ctx = ctx;
    }

    /**
     * Set the bitmap contained in the given filename
     */
    @Override
    public void run() {
        WallpaperManager wpm = WallpaperManager.getInstance(ctx);
        if (wpFileName != null) {
            File wFile = new File(ctx.getFilesDir(), wpFileName);
            if (wFile.exists()) {
                Bitmap bmp = (BitmapFactory.decodeFile(wFile.getAbsolutePath()));
                try {
                    /* Android N - allows to set a croprect
                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                      wpm.setBitmap(bmp, croprect, true);
                       */
                    wpm.setBitmap(bmp);
                } catch (IOException e) {
                    e.printStackTrace();
                    Log.e("HFCM", e.toString());
                }
            }
        }
    }
}
