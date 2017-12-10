package de.herb64.funinspace.helpers;

import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.preference.PreferenceManager;
import android.telecom.Call;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.util.Log;
import android.webkit.MimeTypeMap;

import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
// import java.time.ZoneId;             API26+ required
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.net.ssl.HttpsURLConnection;

import de.herb64.funinspace.MainActivity;
import de.herb64.funinspace.models.spaceItem;


/**
 * Created by herbert on 10/18/17.
 * Use this class as a utility class with static member functions. Former helpers class removed.
 * - static member functions called on the "class", NOT an "instance"
 */
public final class utils {

    private static final long MS_PER_DAY = 86400000;
    private static final long MAX_HDSIZE = 25000;
    private static final String LOG_TS_FORMAT = "yyyy.MM.dd-HH:mm:ss:SSS";

    public static final int NO_JSON = 0;
    public static final int JSON_OBJ = 1;
    public static final int JSON_ARRAY = 2;

    public static final int UNKNOWN = 0;
    public static final int YES = 1;
    public static final int NO = -1;

    /**
     * Read contents of a text file
     * @param ctx context
     * @param filename filename
     * @return String containing the file contents
     */
    public static String readf(Context ctx, String filename) {
        String line = null;
        // getApplicationContext() only exists in Activity, and context needed for openFileInput()
        try {
            FileInputStream fileInputStream = ctx.openFileInput(filename);
            InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
            BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
            StringBuilder stringBuilder = new StringBuilder();

            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line + System.getProperty("line.separator"));
            }
            fileInputStream.close();
            line = stringBuilder.toString();

            bufferedReader.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return line;
    }

    /**
     * Write file string content to a given file
     * @param ctx Context
     * @param filename filename
     * @param content Content to be written in string
     */
    public static void writef(Context ctx, String filename, String content) {
        FileOutputStream outputStream;
        try {
            outputStream = ctx.openFileOutput(filename, ctx.MODE_PRIVATE);
            //outputStream = openFileOutput(filename, getApplicationContext().MODE_WORLD_READABLE);
            outputStream.write(content.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Write contents of a JSON object to file
     * @param ctx context
     * @param filename filename
     * @param json JSON Object to be written
     */
    public static void writeJson(Context ctx, String filename, JSONObject json) {
        try {
            if (json != null) {
                writef(ctx, filename, json.toString(2));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write Content of a JSON Array to file
     * @param ctx context
     * @param filename filename
     * @param json JSON Array Object
     */
    public static void writeJson(Context ctx, String filename, JSONArray json) {
        try {
            if (json != null) {
                writef(ctx, filename, json.toString(2));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Append content with timestamp to a given logfile
     * @param ctx context
     * @param filename filename to append to
     * @param content the string to be appended
     */
    public static void logAppend(Context ctx, String filename, String content) {
        if (ctx == null) {
            return;
        }
        String timestamp = new SimpleDateFormat(LOG_TS_FORMAT,Locale.getDefault()).
                format(System.currentTimeMillis());
        File logFile = new File(ctx.getFilesDir(), filename);
        FileOutputStream outstream = null;
        try {
            outstream = new FileOutputStream(logFile, true);
            outstream.write((timestamp + " > " + content + "\n").getBytes());
            outstream.flush();
            outstream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Append content with timestamp and additional duration py passing a starttime
     * @param ctx context
     * @param filename filename to append to
     * @param content string to be appended
     * @param start epoch value in ms since 1.1.1970 - base for calculating time difference
     */
    public static void logAppend(Context ctx, String filename, String content, long start) {
        if (ctx == null) {
            return;
        }
        long end = System.currentTimeMillis();
        String timestamp = new SimpleDateFormat(LOG_TS_FORMAT, Locale.getDefault()).format(end);
        content += "(" + (end-start) + "ms)";
        File logFile = new File(ctx.getFilesDir(), filename);
        FileOutputStream outstream = null;
        try {
            outstream = new FileOutputStream(logFile, true);
            outstream.write((timestamp + " > " + content + "\n").getBytes());
            outstream.flush();
            outstream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get size of a bitmap object
     * @param bmp btmap object
     * @return size of bitmap object in bytes
     */
    public static int getBMPBytes(Bitmap bmp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            return bmp.getAllocationByteCount();
        } else {
            return bmp.getByteCount();
        }
    }

    /**
     * Write bitmap to jpeg file
     * @param ctx context
     * @param filename filename
     * @param bitmap bitmap object to be written
     */
    public static void writeJPG(Context ctx, String filename, Bitmap bitmap) {
        File thumbFile = new File(ctx.getFilesDir(), filename);
        FileOutputStream outstream = null;
        Log.i("HFCM", "Utils writeJPG for " + filename);
        try {
            outstream = new FileOutputStream(thumbFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outstream);
            outstream.flush();
            outstream.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("HFCM", e.getMessage());
        }
    }

    /**
     * Write bitmap to jpeg file
     * @param ctx context
     * @param filename filename
     * @param bitmap bitmap object to be written
     */
    public static void writeJPG(Context ctx, String filename, Bitmap bitmap, int quality) {
        File thumbFile = new File(ctx.getFilesDir(), filename);
        FileOutputStream outstream = null;
        int q = quality > 100 ? 100 : quality;
        q = q < 0 ? 0 : quality;
        Log.i("HFCM", "Utils writeJPG for " + filename);
        try {
            outstream = new FileOutputStream(thumbFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, q, outstream);
            outstream.flush();
            outstream.close();
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("HFCM", e.getMessage());
        }
    }

    /**
     * Calculate some epoch values based on TimeZone New York //TODO cleanup - lot of unused stuff..
     * TODO: checkout DateTimeFormatter as modern way in Java to handle dates/times
     * @param latestImgEpoch epoch from latest image or 0, if no delta to next img needed
     * @return ArrayList of 3 long values containing epoch values:
     * - epoch cut down to 00:00:00 (date only) - TODO why should we keep that? just for fun ??
     * - epoch full at current time
     * - milliseconds until next image fetch
     * */
    public static ArrayList<Long> getNASAEpoch(long latestImgEpoch) {
        Locale locale = Locale.getDefault();
        ArrayList<Long> epochs = new ArrayList<>();
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            HashSet<String> zoneids = (HashSet<String>) ZoneId.getAvailableZoneIds();
        }*/

        // Washington (NASA HQ) - matches with the observations, at which time new images appear in DE
        // https://www.timeanddate.de/stadt/info/usa/washington-dc
        // https://www.timeanddate.de/zeitzonen/deutschland
        // BUT: most of USA has daylight saving time, so how to know, when it switches to GMT-04:00?
        // API (google) or just use the scheduled plan within the app:
        // https://greenwichmeantime.com/time-zone/rules/usa/               - rules
        // https://developers.google.com/maps/documentation/timezone/intro  - API Google
        // https://www.timeanddate.com/services/api/                        - API

        // about date and time and java dependencies - DateTimeFormatter object
        // https://stackoverflow.com/questions/15360123/time-difference-between-two-times

        // http://tycho.usno.navy.mil/systime.html -- time can be quite complicated :)

        long epochFULL = System.currentTimeMillis();        // current milliseconds value
        // TimeZone tzNASA1 = TimeZone.getTimeZone("GMT-05:00");
        // Getting by ID returns a different object, than getting by GMT... Looks like this
        // implements dst already, so we need no API call..
        // Washington not available as ID, but New York is same zone and also uses DST
        // (only Arizona does not use DST)
        TimeZone tzSYS = TimeZone.getDefault();
        Log.w("HFCM", "DEVICE running on timezone " +
                tzSYS.getDisplayName() +
                " / " + tzSYS.getID());
        Calendar cSYS = Calendar.getInstance(tzSYS);
//        Log.i("HFCM", "SYS TZ offset for epoch (hours): " +
//                (float)tzSYS.getOffset(epochFULL)/3600000f);

        // NASA Server seems to be in New York timezone (deduced from delay when images appear)
        TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
//        Log.w("HFCM", "NASA timezone " +
//                tzNASA.getDisplayName() +
//                " / " + tzNASA.getID());
        Calendar cNASA = Calendar.getInstance(tzNASA);
//        Log.i("HFCM", "NASA TZ offset for epoch (hours): " +
//                (float)tzNASA.getOffset(epochFULL)/3600000f);
//        Log.w("HFCM", "SYS ahead of NASA (hours): " +
//                ((float)tzSYS.getOffset(epochFULL) - (float)tzNASA.getOffset(epochFULL))/3600000f);

        // The only way to get the date/time as string as seen in other timezone from the calendar
        // is to query the elements single calendar fields themselves and create a formatted string
        // Calling calendar.getTime() returns our local time values
        // Log.e("HFCM", "TEST: " + cNASA.getTime().toString());   // gets our local time!!
        String yyyymmddNASA = String.format(locale, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
//        Log.i("HFCM", "NASA Date only string: " + yyyymmddNASA);
        String yyyymmddhhmmssNASA = String.format(locale, "%04d-%02d-%02d_%02d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH),
                cNASA.get(Calendar.HOUR_OF_DAY),
                cNASA.get(Calendar.MINUTE),
                cNASA.get(Calendar.SECOND));
//        Log.i("HFCM", "NASA Date+time string: " + yyyymmddhhmmssNASA);
        String yyyymmddSYS = String.format(locale, "%04d-%02d-%02d",
                cSYS.get(Calendar.YEAR),
                cSYS.get(Calendar.MONTH) + 1,
                cSYS.get(Calendar.DAY_OF_MONTH));
//        Log.i("HFCM", "SYS  Date onlystring: " + yyyymmddSYS);
        String yyyymmddhhmmssSYS = String.format(locale, "%04d-%02d-%02d_%02d-%02d-%02d",
                cSYS.get(Calendar.YEAR),
                cSYS.get(Calendar.MONTH) + 1,
                cSYS.get(Calendar.DAY_OF_MONTH),
                cSYS.get(Calendar.HOUR_OF_DAY),
                cSYS.get(Calendar.MINUTE),
                cSYS.get(Calendar.SECOND));
//        Log.i("HFCM", "SYS Date+time string: " + yyyymmddhhmmssSYS);

        // Use SimpleDateFormat to get the epoch value reduced to date only (no hh:mm:ss...)
        SimpleDateFormat dF00_00_00 = new SimpleDateFormat("yyyy-MM-dd", locale);
        long epoch_00_00_00 = 0;
        try {
            // IMPORTANT:
            // https://developer.android.com/reference/java/text/DateFormat.html#setTimeZone(java.util.TimeZone)
            // The TimeZone set by this method may be overwritten as a result of a call to the parse method.!!!!
            dF00_00_00.setCalendar((Calendar) cNASA.clone()); // clone to avoid timezone overwrite
            //dFTEST.setTimeZone(tzNASA);
            epoch_00_00_00 = dF00_00_00.parse(yyyymmddNASA).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
//        Log.i("HFCM", "FULL: epoch=" + epochFULL);
//        Log.i("HFCM", "00_00_00: epoch= " + epoch_00_00_00);
//        Log.i("HFCM", "Latest image: epoch=" + latestImgEpoch);

        // Calculate the delta time to next required image load.
        long timeToNext = 0;
        if (latestImgEpoch > 0) {
            Date datefull = new Date(epochFULL);
            Date dateLatestImg = new Date(latestImgEpoch);  // seen in current TimeZone!
            //Date date00_00_00 = new Date(epoch_00_00_00);
//            Log.i("HFCM", "Date latest image: " + dateLatestImg.toString());
            long diff = (datefull.getTime() - dateLatestImg.getTime());
//            Log.i("HFCM", "Diff full - imagelatest: " + diff + " seconds");
            if (diff < MS_PER_DAY) {
                timeToNext = MS_PER_DAY - diff;
            }
        }

        // Getting the time/date for any other given timezone (i.e. the local time there)
        // create a formatter for this and assign a calendar or timezone object of the requested
        SimpleDateFormat fmtCHECK = new SimpleDateFormat("dd. MMM yyyy, HH:mm:ss", locale);
        //fmtNASA.setCalendar(cNASA);
        fmtCHECK.setTimeZone(tzNASA);
        Log.i("HFCM", String.format(locale, "NASA local time: %s", fmtCHECK.format(epochFULL)));

        // Some other test:
        fmtCHECK.setTimeZone(TimeZone.getTimeZone("Europe/Brussels"));
//        Log.i("HFCM", String.format(locale, "Brussels local time: %s", fmtCHECK.format(epochFULL)));

        // Prepare return values: NASA epoch at 00:00:00, NASA epoch full, our own epoch
        epochs.add(epoch_00_00_00);
        epochs.add(epochFULL);
        epochs.add(timeToNext);

        // Testing and debugging
        SimpleDateFormat formatter = new SimpleDateFormat("dd. MMM yyyy, HH:mm:ss", locale);
        formatter.setTimeZone(tzNASA);
        //formatter.setCalendar(cNASA);
//        Log.i("HFCM", "Have values " + epochs +
//                "\nNASA time 00:00:00: " + formatter.format(epochs.get(0)) +
//                "\nNASA time full: " + formatter.format(epochs.get(1)) +
//                "\nNASA time next: " + formatter.format(epochs.get(1) + epochs.get(2)));

        // It seems to be sufficient to have the timezone object
        //formatter.setTimeZone(tzLOC);
        formatter.setCalendar(cSYS);
//        Log.i("HFCM", "Local time on device: " + formatter.format(epochs.get(1)) + " (" + tzSYS.getDisplayName() + ")");
//        Log.i("HFCM", "Local time for next image fetch: " + formatter.format(epochs.get(1) + epochs.get(2)));

        // we do not need to use a Date object for formatting, can pass epoch as well
        //Date testdate = new Date(epochNASA_00_00_00);
        //String formatted = formatter.format(testdate);

        // Just get all defined timezones printed
        /*String[] tzIDs = TimeZone.getAvailableIDs();
        for (String id : tzIDs) {
            TimeZone tzTEST = TimeZone.getTimeZone(id);
            formatter.setTimeZone(tzTEST);
            Log.i("HFCM", "Converted time: " + formatter.format(epochs.get(2)) +
                    " (" + id + " / " + tzTEST.getDisplayName() + ")");
        }*/
        return epochs;
    }

    /**
     * Get the milliseconds from current time to next full hour.
     * @param locale locale
     * @return long ms to wait for full hour
     */
    public static long getMsToNextFullHour(Locale locale) {
        TimeZone tzSYS = TimeZone.getDefault();
        Calendar cSYS = Calendar.getInstance(tzSYS);
        String yyyymmddhh = String.format(locale, "%04d-%02d-%02d_%02d",
                cSYS.get(Calendar.YEAR),
                cSYS.get(Calendar.MONTH) + 1,
                cSYS.get(Calendar.DAY_OF_MONTH),
                cSYS.get(Calendar.HOUR_OF_DAY));
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd_HH", locale);
        long epoch = 0;
        long epochFULL = System.currentTimeMillis();
        try {
            dF.setCalendar((Calendar) cSYS.clone()); // clone to avoid timezone overwrite
            epoch = dF.parse(yyyymmddhh).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return epoch + 3600000 - epochFULL;
    }

    /**
     * Get informations on existing files (thumbs, wallpapers, hires) and their memory use. This
     * can be displayed in the about dialog.
     * @return  formatted string containing the infos for display
     */
    public static String getFileStats(Context ctx, Locale loc) {
        File dir = new File(ctx.getFilesDir().getPath());
        File[] files = dir.listFiles();
        int nTh = 0;
        long sTh = 0;
        int nWp = 0;
        long sWp = 0;
        int nHd = 0;
        long sHd = 0;
        for (File file : files) {
            if (file.getName().startsWith("th_")) {
                nTh++;
                sTh +=  file.length();
            } else if (file.getName().startsWith("wp_")) {
                nWp++;
                sWp +=  file.length();
            } else if(file.getName().startsWith("hd_")) {
                nHd++;
                sHd +=  file.length();
            }
        }
        return String.format(loc, "\n\nUsed image memory:\nThumbnails: %d files, %.2f MB" +
                        "\nWallpapers: %d files, %.2f MB" +
                        "\nHires: %d files, %.2f MB",
                nTh, (float)sTh/1048576f,
                nWp, (float)sWp/1048576f,
                nHd, (float)sHd/1048576f);
    }

    /**
     *  Cleanup: - remove any orphaned wallpaper/thumbnail/hires image files
     *           - remove hires images if max space exceeded (size and rating dependent)
     * @param ctx context
     * @param items arraylist with spaceitems
     * @param maxHdMem memory Limit in KiB that triggers cleanup
     * @param skip index in item list to skip from deletion (-1: none
     * @return formatted string with results on cleanup
     */
    public static ArrayList<Integer> cleanupFiles(Context ctx, ArrayList<spaceItem> items, long maxHdMem, int skip) {
        // filenamefilter does not allow wildcard... could use java.nio.file... but not avail..

        //String logString = "";
        logAppend(ctx, MainActivity.DEBUG_LOG, "Starting file cleanup");
        // Create array lists of all th/wp/hd files in filesystem - potential orphans list
        ArrayList<String> orphans = new ArrayList<>();
        File dir = new File(ctx.getFilesDir().getPath());
        String[] names = dir.list();
        for (String name : names) {
            if (name.startsWith("th_") || name.startsWith("wp_") || name.startsWith("hd_")) {
                orphans.add(name);
            }
        }
        String filebase;
        long usedHdMem = 0;
        for (spaceItem item : items) {
            filebase = item.getThumb().replace("th_", "");
            //if (orphans.contains("th_" + filebase)) {
            orphans.remove("th_" + filebase);
            orphans.remove("wp_" + filebase);
            orphans.remove("hd_" + filebase);
            File hd = new File(ctx.getFilesDir(), "hd_" + filebase);
            usedHdMem += hd.length();
        }
        usedHdMem /= 1024;

        if (orphans.size() > 0) {
            logAppend(ctx, MainActivity.DEBUG_LOG, orphans.size() + " orphaned files to remove");
        }
        for (String fn : orphans) {
            File todelete = new File(ctx.getFilesDir(), fn);
            Log.i("HFCM", "Deleting orphaned file: " + fn);
            //logString += fn + "\n";
            if (!todelete.delete()) {
                Log.e("HFCM", "Error deleting orphaned file: " + todelete);
            }
        }

        // Delete hires images if space is exceeded: depend on size and rating
        // TreeMap is sorted: https://developer.android.com/reference/java/util/TreeMap.html
        Log.i("HFCM", "About to cleanup hd images space, currently in use: " + usedHdMem);
        TreeMap<Long, File> sorted = new TreeMap<>();
        TreeMap<Long, Integer> indices = new TreeMap<>();
        ArrayList<Integer> del_idx = new ArrayList<>();
        if (usedHdMem > maxHdMem) {
            Log.i("HFCM", "Used hd mem " + usedHdMem + " exceeds max hd mem " + maxHdMem);
            int idx = 0;
            for (spaceItem item : items) {
                File hd = new File(ctx.getFilesDir(), item.getThumb().replace("th_", "hd_"));
                if (hd.exists()) {
                    int rating = item.getRating();
                    long size = hd.length() / 1024;
                    long key = MAX_HDSIZE - size + rating * MAX_HDSIZE;
                    Log.i("HFCM", "Adding " + hd.getName() + " - key=" + key +
                            " (size=" + size + ", rating=" + rating + ")");
                    sorted.put(key, hd);
                    indices.put(key, idx);
                }
                idx++;
            }

            logAppend(ctx, MainActivity.DEBUG_LOG, "Hires images: (Used: " + usedHdMem + "KB, Limit: " + maxHdMem + "KB)");
            for(Map.Entry<Long,File> entry : sorted.entrySet()) {
                if (indices.get(entry.getKey()) == skip) {
                    continue;
                }
                usedHdMem -= entry.getValue().length() / 1024;
                Log.i("HFCM", "Deleting Hires file: " + entry.getValue().getName() + " > " +
                        entry.getKey() + " - used mem now " + usedHdMem);
                logAppend(ctx,
                        MainActivity.DEBUG_LOG,
                        "delete: " + entry.getValue().getName() + " (" + entry.getValue().length()/1024 + "KB)");
                del_idx.add(indices.get(entry.getKey()));
                if (!entry.getValue().delete()) {
                    Log.e("HFCM", "Error deleting file: " + entry.getValue().getName());
                }
                if (usedHdMem < maxHdMem) {
                    logAppend(ctx, MainActivity.DEBUG_LOG, "Used " + usedHdMem + "KB after cleanup");
                    break;
                }
            }
        }
        return del_idx;
    }

    // TODO - backup function. Restore will not restore hires images unchecked, because these are
    // possibly lower res than on new device, where restore takes place...
    // need to backup info about phone as well to do checks
    public static void backupToSdCard(Context ctx, ArrayList<spaceItem> items) {

    }

    /**
     * @param ctx
     */
    public static void cancelAllJobs(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler sched = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            sched.cancelAll();
        }
    }

    /**
     * Create a formatted string in default shared preferences "WP_SELECT_LIST" to be used by
     * wallpaper shuffle job to determine a new random filename to be set as new wallpaper.
     * This is called if user does add/change wallpapers or changes rating for images that are
     * marked as WPs
     * TODO: building the string: what about that "unflattenFromString stuff to be used?"
     * @param ctx context
     * @param items arraylist of space items
     */
    public static void setWPShuffleCandidates(Context ctx, ArrayList<spaceItem> items) {
        //Set<String> wpSet = new HashSet<>(); > HashSet is not randomly selectable!
        String wpString = "";
        //logAppend(ctx, MainActivity.DEBUG_LOG, "WP_SELECT_LIST update ...");
        for (spaceItem item : items) {
            if (item.getWpFlag() >= MainActivity.WP_EXISTS) {
                // Rating increases chance to be selected - added to each filename as prefix
                wpString += String.format(Locale.getDefault(),
                        "%d:%s*",
                        item.getRating(),
                        item.getThumb().replace("th_", "wp_"));
            }
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("WP_SELECT_LIST", wpString);
        editor.apply();
    }

    /**
     * Return a random wallpaper filename from the information contained in default shared prefs
     * in WP_SELECT_LIST key. This is a special formatted string to be parsed, including information
     * on rating.
     * @param ctx
     * @return
     */
    public static String getRandomWpFileName(Context ctx) {
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        String wpselections = sharedPref.getString("WP_SELECT_LIST", "");
        String[] wps = wpselections.split("[*]");
        ArrayList<String> wplist = new ArrayList<>();
        for (String wp : wps) {
            if (!wp.isEmpty()) {
                String[] v = wp.split(":");
                wplist.add(v[1]);
                // todo: a better logic for rating influence on selection might be needed!!!
                for (int i = 1; i < Integer.parseInt(v[0]); i++) {
                    wplist.add(v[1]);
                }
            }
        }
        if (wplist.size() < 2) {
            return "";
        }
        String current = sharedPref.getString("CURRENT_WALLPAPER_FILE", "");
        Random r = new Random();
        int i;
        // Do not repeat previous image again
        do {
            i = r.nextInt(wplist.size());
            Log.i("HFCM", "Testing index for wp:" + i);
        } while (wplist.get(i).equals(current));

        Log.i("HFCM", "Random index for wp: " + i + " out of " + wplist.size() + ", File: " + wplist.get(i));
        return wplist.get(i);
    }

    /**
     * @param items
     * @return
     */
    public static int getNumWP(ArrayList<spaceItem> items) {
        int count = 0;
        for (spaceItem item : items) {
            if (item.getWpFlag() >= MainActivity.WP_EXISTS) {   // EXIST + ACTIVE!!!
                count++;
            }
        }
        return count;
    }

    /**
     * Parse a nasa json object - just flat object with strings.
     * @param s
     * @return
     */
    public static HashMap<String, String> parseNASAJson(String s) {
        HashMap<String, String> map = new HashMap<>();
        try {
            JSONObject parent = new JSONObject(s);
            Iterator<String> iterator = parent.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                map.put(key, (String) parent.get(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("HFCM", e.getMessage());
        }
        return map;
    }

    /**
     * Check, if network is connected.
     * @param ctx context
     * @return true if connected, false if not connection or no ConnectivityService found
     */
    public static boolean isNetworkConnected(Context ctx) {
        ConnectivityManager connManager = (ConnectivityManager) ctx.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            //LinkProperties props = connManager.getLinkProperties(connManager.getActiveNetwork());
            // DOCU: java.lang.NoSuchMethodError: No virtual method getActiveNetwork()
            //       one hint: invalidate caches - does not help, tested it
            //       https://www.jetbrains.com/help/idea/2016.3/cleaning-system-cache.html
            //List<RouteInfo> routes = props.getRoutes();
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            return networkInfo != null && networkInfo.isConnectedOrConnecting();    // see google
        }
        return false;
    }

    /**
     * Check internet connectivity without running into possible DNS timeouts.
     * TODO: check how this works with open WLAN and redirect to a credentials page (e.g. hotel)
     *       It might be, that the test fails in this case..
     * TODO - maybe ask alternative dns
     * 208.67.222.222 - openDNS
     * 208.67.220.220 - openDNS
     * 8.8.8.8 - google dns
     * 8.8.4.4 - google dns
     * @param timeout integer with max timeout allowed
     * @return long value with connection time, if fails: timeout value
     */
    public static long testSocketConnect(final int timeout) {
        // using a class derived from Callable
        //netChecker checker = new netChecker();
        // static class netChecker implements Callable<Boolean>.. old stuff
        final long ts = System.currentTimeMillis();
        final Callable<Long> netCheck = new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                try {
                    Socket sock = new Socket();
                    SocketAddress sockaddr = new InetSocketAddress("8.8.8.8", 53);
                    sock.connect(sockaddr, timeout);
                    long connecttime = System.currentTimeMillis() - ts;
                    if (sock.isConnected()) {
                        Log.i("HFCM", "netCheck socket connected after " +
                                connecttime + "ms");
                    }
                    sock.close();
                    return connecttime;
                } catch (IOException e) {
                    // exception gets called after timeout from sock.connect()
                    e.printStackTrace();
                    Log.e("HFCM", "netCheck socket IOException: " + e.getMessage());
                    return (long)timeout;
                }
            }
        };
        Log.i("HFCM", "isInternetReachable about to call netCheck.call()");
        try {
            // callable.call() does not start a thread!!! and a Thread(callable) cannot be created
            // use FutureTask
            // https://stackoverflow.com/questions/25231149/can-i-use-callable-threads-without-executorservice
            //boolean ttt = checker.call();

            FutureTask<Long> futureTask = new FutureTask<>(netCheck);
            Thread t=new Thread(futureTask);
            t.start();
            long ttt = futureTask.get();
            Log.e("HFCM", "isInternetReachable, netCheck returned: " + ttt);
            return ttt;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("HFCM", "isInternetReachable: " + e.getMessage());
            return timeout;
        }
    }

    /**
     * Check, if we can reach our server, because isConnected() from networkInfo does not tell use
     * anything about real connectivity. E.g. DNS might fail, no route from wifi router to outside
     * because DSL disconnected. 10 secs for each resolved IP - takes 40 seconds to exception!
     * @param ctx
     * @param url2test
     * @return
     */
    public static boolean isInternetReachable(Context ctx, String url2test) {

        HttpsURLConnection conn = null;
        int status;

        // testing a bad hack: https://8.8.8.8 - to avoid dns timeout 10sec * times of resolved ips
        // this one could help to run into other exception
        // leads to Hostname '8.8.8.8' was not verified - but does not work in my test case - hangs

        try {
            URL url = new URL(url2test);
            conn = (HttpsURLConnection) url.openConnection();
            //conn.setConnectTimeout(500);  // ignored, could use external timer thread
            status = conn.getResponseCode();
            if (status == HttpsURLConnection.HTTP_OK) {
                try {
                    conn.disconnect();
                } catch (Exception e){
                    e.printStackTrace();
                    logAppend(ctx, MainActivity.DEBUG_LOG, e.toString());
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logAppend(ctx, MainActivity.DEBUG_LOG, e.toString());
            return false;
        } finally {
            if(conn != null) {
                try {
                    conn.disconnect();
                } catch (Exception e) {
                    e.printStackTrace();
                    logAppend(ctx, MainActivity.DEBUG_LOG, e.toString());
                }
            }
        }
        return false;
    }

    /**
     * @param ctx context
     * @return integer
     */
    public static int getActiveNetworkType(Context ctx) {
        ConnectivityManager connManager = (ConnectivityManager) ctx.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                return networkInfo.getType();
            }
        }
        return -1;
    }

    /**
     * Get status
     * @param ctx context
     * @return ConnectivityManager.TYPE
     */
    public static String getActiveNetworkTypeName(Context ctx) {
        ConnectivityManager connManager = (ConnectivityManager) ctx.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                //int subtype = networkInfo.getSubtype();
                //String ttt = networkInfo.getDetailedState().toString();
                //NetworkInfo.DetailedState.CAPTIVE_PORTAL_CHECK.toString();
                /*String networkdetails = String.format(Locale.getDefault(),
                        "Network: ExtraInfo: %s, SubType: %s, Detailed State: %s",
                        networkInfo.getExtraInfo(),
                        networkInfo.getSubtypeName(),
                        networkInfo.getDetailedState().toString());
                logAppend(ctx, MainActivity.DEBUG_LOG, networkdetails);*/
                return networkInfo.getTypeName();
            }
        }
        return "";
        /*for (Network net : connManager.getAllNetworks()) {
            Log.i("HFCM", "Network: " + net.toString());
        }*/

        /*HashMap<String, Boolean> status = new HashMap<>();
        if (networkInfo != null && networkInfo.isConnected()) {
            status.put("WIFI", networkInfo.getType() == ConnectivityManager.TYPE_WIFI);
            status.put("MOBILE", networkInfo.getType() == ConnectivityManager.TYPE_MOBILE);
            status.put("BLUETOOTH", networkInfo.getType() == ConnectivityManager.TYPE_BLUETOOTH);
        }*/
    }

    /**
     * TODO: remove - is useless, this is just the state within wifi state machine
     * But important:
     * https://stackoverflow.com/questions/26982762/get-wifi-captive-portal-info
     * " However, new to Lollipop is that the mobile data connection is used when WiFi does
     * not have connectivity. This means my ping method will still return results, and
     * that a redirect would not happen, as the request would be routed over the mobile data."
     *
     * Check, if a captive portal is present, which redirected us to a special page before granting
     * access to the network, as often found on airports and in hotels.
     * https://en.wikipedia.org/wiki/Captive_portal
     * See also infos about captive portal detection
     * https://android.stackexchange.com/questions/100657/how-to-disable-captive-portal-detection-how-to-remove-exclamation-mark-on-wi-fi
     * @param ctx Context
     * @return YES, NO or UNKNOWN
     */
    public static int isActiveNetworkCaptivePortal(Context ctx) {
        ConnectivityManager connManager = (ConnectivityManager) ctx.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connManager != null) {
            NetworkInfo networkInfo = connManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                if (networkInfo.getDetailedState() == NetworkInfo.DetailedState.CAPTIVE_PORTAL_CHECK) {
                    return YES;
                } else {
                    return NO;
                }
            } else {
                return UNKNOWN;
            }
        } else {
            return UNKNOWN;
        }
    }


    public static void getAllNetworksInfo(Context ctx) {
        // see also https://www.androidhive.info/2012/07/android-detect-internet-connection-status/
        ConnectivityManager connManager = (ConnectivityManager) ctx.
                getSystemService(Context.CONNECTIVITY_SERVICE);
        //CaptivePortal cp =
        if (connManager == null) {
            Log.e("HFCM", "getAllNetworksInfo: no connectionmanager found");
            return;
        }
        //NetworkInfo act = connManager.getActiveNetworkInfo();
        //String flag;
        //Log.i("HFCM", "ACT" + act.toString());
        for (Network net : connManager.getAllNetworks()) {
            /*NetworkInfo inf = connManager.getNetworkInfo(net);
            String extra = inf.getExtraInfo();
            String type = inf.getTypeName();
            String subtype = inf. getSubtypeName();
            String detailedstate = inf.getDetailedState().toString();
            Log.i("HFCM", "INF" + inf.toString());
            flag = act.toString().equals(inf.toString()) ? " (*)" : "";
            String networkdetails = String.format(Locale.getDefault(),
                    "Network: ExtraInfo: %s, Type/SubType: %s/%s, Detailed State: %s%s",
                    extra,
                    type,
                    subtype,
                    detailedstate,
                    flag);*/
            logAppend(ctx, MainActivity.DEBUG_LOG, "NETINFO: " +
                    connManager.getNetworkInfo(net).toString());
            //connManager.bindProcessToNetwork(net);
        }
    }

    /**
     * Get internal memory available to application
     * @return internal memory in bytes
     */
    public static long getAvailableInternalMem() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        /* Just for comparing results
        long blockSize = stat.getBlockSizeLong();
        long availBlocks = stat.getAvailableBlocksLong();
        long a = stat.getAvailableBytes();
        long b = availBlocks * blockSize;*/
        return stat.getAvailableBytes();
    }

    /**
     * Get overall internal memory size
     * @return internal memory in bytes
     */
    public static long getTotalInternalMem() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        /* Just for comparing results
        long blockSize = stat.getBlockSizeLong();
        long totalBlocks = stat.getBlockCountLong();
        long a = stat.getTotalBytes();
        long b = totalBlocks * blockSize;*/
        return stat.getTotalBytes();
    }

    /**
     * Ís the given string a valid json and if so, is it an ARRAY or an OBJECT?
     * @param string
     * @return
     */
    public static int isJson(String string) {
        try {
            new JSONObject(string);
            return JSON_OBJ;
        } catch (JSONException a) {
            try {
                new JSONArray(string);
                return JSON_ARRAY;
            } catch (JSONException b) {
                return NO_JSON;
            }
        }
        /* As reference: some code from my DROPBOX_REFRESH in processFinish() MainActivity:
           also a way to get the infos... this create the json array in this case
        Object json;
        try {
            json = new JSONTokener((String) output).nextValue();
        } catch (JSONException e) {
            e.printStackTrace();
            return;
        }
        String hhh = json.getClass().toString();    // class.org.MYTYPE
        if (json != null && json instanceof JSONArray) {
            refreshFromDropbox((JSONArray) json);
        }
        */
    }


    ///////////////// J U S T   S O M E   J U N K  /////////////////////////////////////////////////
    // stuff to check
    /* hmmm, strange behaviour when checking for installed packages
        boolean flashInstalled = false;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo("com.adobe.flashplayer", 0);
            if (ai != null)
                flashInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            flashInstalled = false;
        }*/
}
