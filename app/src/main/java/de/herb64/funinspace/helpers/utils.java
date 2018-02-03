package de.herb64.funinspace.helpers;

import android.support.v4.app.FragmentManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.net.CaptivePortal;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
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
import java.lang.reflect.Array;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

import javax.net.ssl.HttpsURLConnection;

import de.herb64.funinspace.BuildConfig;
import de.herb64.funinspace.MainActivity;
import de.herb64.funinspace.TextReader;
import de.herb64.funinspace.models.spaceItem;
import de.herb64.funinspace.services.apodJobService;


/**
 * Created by herbert on 10/18/17.
 * Use this class as a utility class with static member functions. Former helpers class removed.
 * - static member functions called on the "class", NOT an "instance"
 */
public final class utils {

    private static final long MS_PER_DAY = 86400000;
    private static final long MAX_HDSIZE = 25000;
    private static final String LOG_TS_FORMAT = "yyyy.MM.dd-HH:mm:ss:SSS";

    // random delay to avoid DDOS kind of flooding NASA server with many app instances
    private static final int MIN_DELAY = 900000;
    private static final int MAX_DELAY = 2700000;

    public static final int NO_JSON = 0;
    public static final int JSON_OBJ = 1;
    public static final int JSON_ARRAY = 2;

    public static final int UNKNOWN = 0;
    public static final int YES = 1;
    public static final int NO = -1;

    //public static final String TAG_READ_FRAGMENT = "text_reader_fragment";

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
        info(content);
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
        info(content);
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
     * Wrapper for logcat - allows to switch off all statements by constant
     * @param content string to be written to log
     */
    public static void info(String content) {
        if (BuildConfig.DEBUG) {
        //if (MainActivity.LOGCAT_INFO) {
            //String hugo = this.getClass().getSimpleName().toString();
            Log.i("HFCM", content);
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
     * Get simple value of NASA epoch for current day at 00:00:00
     * @return
     */
    public static long getNASAEpoch() {
        Locale loc = Locale.getDefault();
        TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
        Calendar cNASA = Calendar.getInstance(tzNASA);
        String yyyymmddNASA = String.format(loc, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", loc);
        long epoch = 0;
        try {
            dF.setCalendar((Calendar) cNASA.clone()); // clone to avoid timezone overwrite
            epoch = dF.parse(yyyymmddNASA).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return epoch;
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

        // from code for docu:
        // SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", loc);
        // normally we would use code as below for correct locale, but we parse a specific
        // format here :_ "date": "2017-09-17", as returned by NASA in their json
        // DateFormat dF = SimpleDateFormat.getDateInstance();

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
     * Create epoch value from datestring passed in APOD. This is used to set value in spaceItem
     * @param ds date string from APOD
     * @return epoch value or 0 for error
     */
    public static long getEpochFromDatestring(String ds) {
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
        try {
            dF.setCalendar(Calendar.getInstance(tzNASA));
            return dF.parse(ds).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
            return 0;
        }
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
     * Get the milliseconds to next shuffle full hour based on configured times in preferences
     * The returned value is rounded to full seconds.
     * @param ctx context
     * @return milliseconds to next shuffle, 0 if no times are set
     */
    public static long getMsToNextShuffle(Context ctx) {
        TimeZone tzSYS = TimeZone.getDefault();
        Calendar cSYS = Calendar.getInstance(tzSYS);
        String[] hhmmss = String.format(Locale.getDefault(), "%d-%d-%d",
                cSYS.get(Calendar.HOUR_OF_DAY),
                cSYS.get(Calendar.MINUTE),
                cSYS.get(Calendar.SECOND)).split("-");
        Log.i("HFCM", "Having hour string of '" + hhmmss[0] + "'");
        int currentHour = Integer.decode(hhmmss[0]);
        int nextHour = currentHour < 23 ? currentHour + 1 : 0;
        // this does not take current milliseconds into account, it is rounded to full seconds
        long msToNextFull = 3600000 -
                Integer.decode(hhmmss[1]) * 60000 -
                Integer.decode(hhmmss[2]) * 1000;

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        Set<String> shtimes = sharedPref.getStringSet("wp_shuffle_times", new HashSet<String>());
        if (shtimes.size() == 0) {
            return 0;
        }
        ArrayList<Integer> ints = new ArrayList<>();
        for (String s : shtimes) {
            ints.add(Integer.parseInt(s));
        }
        Collections.sort(ints, new Comparator<Integer>() {
            @Override
            public int compare(Integer a, Integer b) {
                return a.compareTo(b);
            }
        });

        for (int start : ints) {
            if (nextHour == start) {
                return msToNextFull;
            } else if (nextHour < start) {
                return msToNextFull + 3600000 * (start - nextHour);
            }
        }
        return msToNextFull + 3600000 * (ints.get(0) - nextHour + 24);

        /*for (int i = 0; i<act.length; i++) {
            int start = Integer.parseInt(act[i]);
            if (nextHour == start) {
                return msToNextFull;
            } else if (nextHour < start) {
                return msToNextFull + 3600000 * (start - nextHour);
            }
        }
        return msToNextFull + 3600000 * (Integer.decode(act[0]) - nextHour + 24);*/
    }

    /**
     * Get the milliseconds to the next APOD including a random delay to avoid flooding NASA server
     * with many installed apps at same time
     * @return milliseconds to next APOD
     */
    public static long getMsToNextApod(Context ctx) {
        long epoch = getNASAEpoch();
        ArrayList<Long> data = utils.getNASAEpoch(epoch);
        int random = MIN_DELAY + new Random().nextInt((MAX_DELAY - MIN_DELAY));
        String logentry = String.format(Locale.getDefault(),
                "getMsToNextApod() -  %.1f hours + random delay of %d seconds",
                (float)data.get(2)/3600000f, random/1000);
        logAppend(ctx, MainActivity.DEBUG_LOG, logentry);
        return data.get(2) + random;
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
        return String.format(loc, "Thumbnails: %d files, %.2f MB" +
                        "\nWallpapers: %d files, %.2f MB" +
                        "\nHires: %d files, %.2f MB",
                nTh, (float)sTh/1048576f,
                nWp, (float)sWp/1048576f,
                nHd, (float)sHd/1048576f);
    }

    /**
     *  Cleanup: - remove any orphaned wallpaper/thumbnail/hires image files
     *           - remove hires images if max space exceeded (size and rating dependent)
     *           - TODO: removing local json copies of nasa data (maybe those older than 100 days)
     *                   note, that these are lost forever after deletion. But on any user device
     *                   they are not needed at all...
     *           - TODO: handle too many wallpaper files eating up memory
     * @param ctx context
     * @param items arraylist with spaceitems
     * @param maxPercent memory Limit in percent of free available internal memory
     * @param skip index in item list to skip from deletion (-1: none
     * @return formatted string with results on cleanup
     */
    public static ArrayList<Integer> cleanupFiles(Context ctx, ArrayList<spaceItem> items, int maxPercent, int skip) {
        // filenamefilter does not allow wildcard... could use java.nio.file... but not avail..
        long maxHdMem = getAvailableInternalMem() * maxPercent / 102400;
        Log.i("HFCM", "avail bytes: " + getAvailableInternalMem() + ", percent: " + maxPercent + ", resulting max KIB:" + maxHdMem);

        //String logString = "";
        logAppend(ctx, MainActivity.DEBUG_LOG, "Starting file cleanup with maxHdMem " + maxHdMem + "KiB");
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
            logAppend(ctx, MainActivity.DEBUG_LOG, "cleanupFiles(): " + orphans.size() + " orphaned files to remove");
        }
        for (String fn : orphans) {
            File todelete = new File(ctx.getFilesDir(), fn);
            Log.i("HFCM", "Deleting orphaned file: " + fn);
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
     * Cancel a running job
     * @param ctx context
     * @param jobId job id
     * @return true, if job was cancelled
     */
    public static boolean cancelJob(Context ctx, int jobId) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler sched = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            List<JobInfo> allPendingJobs = sched.getAllPendingJobs();
            for (JobInfo pending : allPendingJobs) {
                if (pending.getId() == jobId) {
                    sched.cancel(jobId);
                    utils.logAppend(ctx,
                            MainActivity.DEBUG_LOG,
                            "cancelJob() - jobid: " + jobId);
                    return true;
                }
            }
            return false;
        }
        return false;
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
     * @return number of existing wallpapers
     */
    public static int setWPShuffleCandidates(Context ctx, ArrayList<spaceItem> items) {
        //Set<String> wpSet = new HashSet<>(); > HashSet is not randomly selectable!
        String wpString = "";
        //logAppend(ctx, MainActivity.DEBUG_LOG, "WP_SELECT_LIST update ...");
        int count = 0;
        for (spaceItem item : items) {
            if (item.getWpFlag() >= MainActivity.WP_EXISTS) {
                // Rating increases chance to be selected - added to each filename as prefix
                wpString += String.format(Locale.getDefault(),
                        "%d:%s*",
                        item.getRating(),
                        item.getThumb().replace("th_", "wp_"));
                count++;
            }
        }
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("WP_SELECT_LIST", wpString);
        editor.apply();
        return count;
    }

    /**
     * Return a random wallpaper filename from the information contained in default shared prefs
     * in WP_SELECT_LIST key. This is a special formatted string to be parsed, including information
     * on rating.
     * @param ctx context
     * @return string with filename of selected random wallpaper
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
        // Do not repeat previous image again - we force a change
        do {
            i = r.nextInt(wplist.size());
            Log.i("HFCM", "Testing index for wp:" + i);
        } while (wplist.get(i).equals(current));

        Log.i("HFCM", "Random index for wp: " + i + " out of " + wplist.size() + ", File: " + wplist.get(i));
        return wplist.get(i);
    }

    /**
     * Get number of items which are set was wallpaper (including the active one!)
     * @param items space item list
     * @return number of items
     */
    public static int getNumWP(ArrayList<spaceItem> items) {
        int count = 0;
        for (spaceItem item : items) {
            if (item.getWpFlag() >= MainActivity.WP_EXISTS) {
                count++;
            }
        }
        return count;
    }

    /**
     * Create a hashmap from a JSON object string
     * TODO - keep this as some utility function, but currently no longer used - also needs recheck
     * @param s String to be parsed...
     * @return HashMap with key / value pairs from object
     */
    public static HashMap<String, String> parseStringAsJsonObject(Context ctx, String s) {
        HashMap<String, String> map = new HashMap<>();
        JSONObject parent;
        try {
            parent = new JSONObject(s);
            Iterator<String> iterator = parent.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                map.put(key, (String) parent.get(key));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("HFCM", e.getMessage());
            return map; // empty
        }
        return map;
    }

    /**
     * Create a spaceItem with null thumbnail image from a given json file
     * @param ctx context
     * @param filename JSON file
     * @return spaceItem (empty if error)
     */
    public static spaceItem createSpaceItemFromJsonFile(Context ctx, String filename) {
        spaceItem item = new spaceItem();
        String content = readf(ctx, filename);
        // TODO - check with isJson and check, if it is a NASA json (e.g. has correct attr)
        if (content != null) {
            item = createSpaceItemFromJsonString(ctx, content);
        }
        return item;
    }

    /**
     * Create a spaceItem from a given JSON string, which must be one of NASA provided type.
     * The returned item is not yet complete
     * - null as thumbbitmap
     * - no hires size information
     * - for vimeo: no URL for Lowres (thumbnail link)
     * @param ctx context
     * @param s string containing json delivered by NASA
     * @return spaceItem (empty item if json parsing does not work)
     */
    public static spaceItem createSpaceItemFromJsonString(Context ctx, String s) {
        String imgUrl = "";
        spaceItem item = new spaceItem();
        String sTitle = "n/a";
        String sCopyright = "";
        String sExplanation = "";
        String sMediaType = "";
        String sHiresUrl = "";
        String sDate = "";
        JSONObject parent = null;
        Uri resource_uri = null;
        try {
            parent = new JSONObject(s);
            /* TODO: handle possible errors returned, e.g. 02.08.2017 - demo key exhausted??
            {
                "code": 500,
                "msg": "Internal Service Error",
                "service_version": "v1"
            }*/
            // also [503] Service Unavailable (15.12.2017)
            sMediaType = parent.getString("media_type");
            sDate = parent.getString("date");
            // TODO copyright not yet finalized - replace newline or not, limit length
            if (parent.has("copyright")) {
                sCopyright = "Copyright: " + parent.getString("copyright").
                        replaceAll(System.getProperty("line.separator"), " ");
            }
            sTitle = parent.getString("title");
            imgUrl = parent.getString("url"); // TODO: missing url handling? see 11.09.2017
            resource_uri = Uri.parse(imgUrl);
            if (parent.has("hdurl")) {
                sHiresUrl = parent.getString("hdurl");
            } else {
                // actually, we should not get here again after apod is already loaded.
                if (sMediaType.equals("image")) {
                    logAppend(ctx, MainActivity.DEBUG_LOG,
                            "Missing hires image link - todo: fallback to lowres???");
                }
            }
            sExplanation = parent.getString("explanation");

            // Keep local copy of original NASA JSON as reference for debugging. This content can
            // only be loaded on the specific day, else never again (see also cleanupFiles)
            if (sDate != null) {
                long epoch = getEpochFromDatestring(sDate);
                utils.writeJson(ctx, String.valueOf(epoch) + ".json", parent);
            }
        } catch (JSONException e) {
            e.printStackTrace();
            logAppend(ctx, MainActivity.DEBUG_LOG,
                    "createSpaceItemFromJsonString() JSON exception: " +
                            s.substring(0, s.length() > 50 ? 50 : s.length()));
            return item;
        }

        // At this point sMediaType contains the NASA delivered string. This gets changed now to
        // a more specific media type information. It's a little bit messy, because NASA delivered
        // an MP4 stream as media type "image" on 13.11.2017... need to catch such error situations
        if (resource_uri != null) {
            String hiresPS = Uri.parse(sHiresUrl).getLastPathSegment();
            if (sMediaType.equals("video")) {
                // note, that this rewrites sMediaType variable!
                String host = resource_uri.getHost();
                List<String> path = resource_uri.getPathSegments();
                item.setHires(resource_uri.toString());
                if (host.equals("www.youtube.com")) {
                    // TODO: now we just assume 'embed' link - might not be true always ??
                    // url> https://www.youtube.com/embed/" + path.get(1) + "?rel=0
                    // img> https://img.youtube.com/vi/9Vp2jUQ4rNM/0.jpg
                    // TODO : MediaMetadataRetriever - also can retrieve image from video - FYI
                    // it's not sure, if this works with youtube, but what about vimeo?
                    sMediaType = MainActivity.M_YOUTUBE;
                    item.setThumb("th_" + path.get(1) + ".jpg");
                    sHiresUrl = imgUrl;
                    imgUrl = "https://img.youtube.com/vi/" + path.get(1) + "/0.jpg";
                    item.setLowres(imgUrl);
                } else if (host.endsWith("vimeo.com")) { // vimeo.com / player.vimeo.com
                    // Important: video link is in "url", NOT "hdurl"
                    sMediaType = MainActivity.M_VIMEO;
                    item.setThumb("th_VIMEO_unknown.jpg");
                    sHiresUrl = imgUrl;
                    // URL is the link that can be played in a WebView, just not using
                    // iframe embed at all... doing it like this for now
                    // TODO: extract video ID and thumbnail url from given video url
                    // is solved now, but what about "MediaMetadataRetriever"
                    // thumbnail image name: th_<video-id>.jpg
                    // Note: this needs a REST API call to gather the required infos..
                    item.setLowres("");     // to be filled with thumbnail URL later
                } else {
                    // TODO: MP4 handling for correct media type video
                    if (hiresPS.endsWith(".mp4") || hiresPS.endsWith(".MP4")) {
                        sMediaType = MainActivity.M_MP4;
                        item.setThumb("th_" + resource_uri.getLastPathSegment());
                        //apodItem.setHires(sHiresUrl);
                        item.setLowres(imgUrl);
                    } else {
                        sMediaType = MainActivity.M_VIDEO_UNKNOWN;
                        item.setThumb("th_UNKNOWN.jpg");
                    }
                }
            } else {
                // FIX for NASA sending wrong media type 'image' for MP4 video (13.11.2017)
                if (hiresPS.endsWith(".mp4") || hiresPS.endsWith(".MP4")) {
                    sMediaType = MainActivity.M_MP4;
                }
                item.setThumb("th_" + resource_uri.getLastPathSegment());
                //apodItem.setHires(sHiresUrl);
                item.setLowres(imgUrl);
            }
        }
        // TODO shouldn't super be executed first ??? might have done this bad
        //super.onPostExecute(s);
        item.setTitle(sTitle);
        item.setCopyright(sCopyright);
        item.setExplanation(sExplanation);
        item.setHires(sHiresUrl);
        //long epoch = utils.getNASAEpoch(0).get(0);
        long epoch = getEpochFromDatestring(sDate);   // FIX to use "date" string from apod
        item.setDateTime(epoch);
        item.setMedia(sMediaType);
        item.setRating(0);
        item.setLowSize("");
        item.setHiSize("");
        return item;
    }

    /**
     * Create JSON object in funinspace specific format to be added into nasatest.json
     * @param item spaceItem object
     * @return the JSON object
     */
    public static JSONObject createJsonObjectFromSpaceItem(spaceItem item) {
        JSONObject apodObj = new JSONObject();
        JSONObject contentObj = new JSONObject();
        try {
            contentObj.put("Title", item.getTitle());
            contentObj.put("DateTime", item.getDateTime());
            contentObj.put("Copyright", item.getCopyright());
            contentObj.put("Explanation", item.getExplanation());
            contentObj.put("Lowres", item.getLowres());
            contentObj.put("Hires", item.getHires());
            contentObj.put("Thumb", item.getThumb());
            contentObj.put("Rating", item.getRating());
            contentObj.put("Media", item.getMedia());
            contentObj.put("HiSize", item.getHiSize());
            contentObj.put("LowSize", item.getLowSize());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            apodObj.put("Type", "APOD");
            apodObj.put("Content", contentObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return apodObj;
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
           also a way to get the infos... this creates the json array in this case
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

    public static boolean todaysImageAlreadyInList(Context ctx, long epoch) {
        String jsonData = null;
        long latestEpoch = 0;
        File jsonFile = new File(ctx.getFilesDir(), MainActivity.localJson);
        if (jsonFile.exists()) {
            jsonData = readf(ctx, MainActivity.localJson);
            try {
                JSONArray parent = new JSONArray(jsonData);
                latestEpoch = parent.getJSONObject(parent.length()-1).
                        getJSONObject("Content").
                        getLong("DateTime");
                if (epoch == latestEpoch) {
                    return true;
                }
            } catch (JSONException e) {
                logAppend(ctx, MainActivity.DEBUG_LOG,
                        "todaysImageAlreadyInList() - JSONException " + MainActivity.localJson);
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

    /**
     * Check, if we have a valid scheduled NASA Json file for the current day
     * @param ctx context
     * @param epoch epoch
     * @return true, if file is found
     */
    public static boolean haveValidScheduledJson(Context ctx, long epoch) {
        String name = apodJobService.APOD_SCHED_PREFIX + epoch + ".json";
        String content = readf(ctx, name);
        if (content == null) {
            return false;
        }
        try {
            JSONObject parent = new JSONObject(content);
            String sDate = parent.getString("date");
            if (sDate != null) {
                long epoch2 = getEpochFromDatestring(sDate);
                if (epoch2 == epoch) {
                    return true;
                }
            }
            // here, the file seems to exist, but does not contain a valid json - delete the file?
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Insert a space item into the current list at position matching epoch value. Double titles
     * are skipped.
     * @param list The arraylist, into which items get inserted
     * @param newitem The new item to be inserted
     * @return true if success - currently not used
     */
    public static boolean insertSpaceItem(ArrayList<spaceItem> list, spaceItem newitem) {
        // Iterate through the list. Most likely the item is to be added at the beginning of the
        // list, because this is called for items with current epoch values - so iteration should
        // be very short. ArrayList is sorted by descending epochs.
        long newepoch = newitem.getDateTime();
        String newtitle = newitem.getTitle();
        int idx = 0;
        for (spaceItem item : list) {
            if (item.getTitle().equals(newtitle)) {
                Log.i("HFCM", "insertSpaceItem() - title already in list, index " + idx + " - " + newtitle);
                return false;
            }
            if (newepoch > item.getDateTime()) {
                list.add(idx, newitem);
                Log.i("HFCM", "insertSpaceItem() - insert at index " + idx + " - " + newtitle);
                return true;
            }
            idx++;
        }
        Log.i("HFCM", "insertSpaceItem() - append at end - " + newtitle);
        list.add(newitem);
        return true;
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
        // see:
        // https://github.com/aosp-mirror/platform_frameworks_base/blob/marshmallow-release/core/java/android/net/CaptivePortal.java
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


    /**
     * @param ctx context
     */
    public static void getAllNetworksInfo(Context ctx) {
        // see also https://www.androidhive.info/2012/07/android-detect-internet-connection-status/
        // Note: getAllNetworks: since API 21
        // Problem: getAllNetworks does not return all networks, just the active one
        // On 4.1 AVD with old getAllNetworkInfo, log is much nicer :)
        //2017.12.11-07:38:51:377 > NetworkInfo: type: mobile[LTE], state: CONNECTED/CONNECTED, reason: simLoaded, extra: epc.tmobile.com, roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:377 > NetworkInfo: type: wifi[], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: false
        //2017.12.11-07:38:51:377 > NetworkInfo: type: mobile_mms[LTE], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:377 > NetworkInfo: type: mobile_supl[LTE], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:377 > NetworkInfo: type: mobile_hipri[LTE], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:378 > NetworkInfo: type: mobile_fota[LTE], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:378 > NetworkInfo: type: mobile_ims[LTE], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:378 > NetworkInfo: type: mobile_cbs[LTE], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: true
        //2017.12.11-07:38:51:378 > NetworkInfo: type: wifi_p2p[], state: UNKNOWN/IDLE, reason: (unspecified), extra: (none), roaming: false, failover: false, isAvailable: false

        // But found, that on another wifi both networks are shown on Elephone using Android 6
        // are shown, while my own wifi does not appear
        // 2017.12.12-09:37:40:736 > NETINFO: [type: MOBILE[LTE], state: CONNECTED/CONNECTED, reason: connected, extra: internet, roaming: true, failover: false, isAvailable: true]
        //2017.12.12-09:37:40:738 > NETINFO: [type: WIFI[], state: CONNECTED/CONNECTED, reason: (unspecified), extra: "Sengelxxx42", roaming: false, failover: false, isAvailable: true]


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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Network[] nets = connManager.getAllNetworks();
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
        } else {
            NetworkInfo[] netinfos = connManager.getAllNetworkInfo();
            for (NetworkInfo netinfo : netinfos) {
                logAppend(ctx, MainActivity.DEBUG_LOG, netinfo.toString());
            }
        }
    }

    /**
     * Get internal memory available to application - API18 introduced changes/deprectations
     * @return internal memory in bytes
     */
    public static long getAvailableInternalMem() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return stat.getAvailableBytes();
        } else {
            /*Just for comparing results
            long blockSize = stat.getBlockSizeLong();
            long availBlocks = stat.getAvailableBlocksLong();
            long a = stat.getAvailableBytes();
            long b = availBlocks * blockSize;*/
            //noinspection deprecation
            int blockSize = stat.getBlockSize();
            //noinspection deprecation
            int availBlocks = stat.getAvailableBlocks();
            return blockSize * availBlocks;
        }

    }

    /**
     * Get overall internal memory size
     * @return internal memory in bytes
     */
    public static long getTotalInternalMem() {
        File path = Environment.getDataDirectory();
        StatFs stat = new StatFs(path.getPath());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return stat.getTotalBytes();
        } else {
            /* Just for comparing results
            long blockSize = stat.getBlockSizeLong();
            long totalBlocks = stat.getBlockCountLong();
            long a = stat.getTotalBytes();
            long b = totalBlocks * blockSize;*/
            //noinspection deprecation
            int blockSize = stat.getBlockSize();
            //noinspection deprecation
            int totalBlocks = stat.getBlockCount();
            return blockSize * totalBlocks;
        }
    }

    /**
     * Get Version infos as defined in build.gradle
     * @param ctx context
     * @return String with version and build info
     */
    public static String getVersionInfo(Context ctx) {
        String pkgName = ctx.getPackageName();
        PackageInfo pkgInfo;
        try {
            pkgInfo = ctx.getPackageManager().getPackageInfo(pkgName, 0);
            //ApplicationInfo inf = pkgInfo.applicationInfo;
            //String infos = "Version: " + pkgInfo.versionName + ", Build: " + pkgInfo.versionCode;
            return "Version: " + pkgInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "Missing version infos";
        }
    }

    /**
     * Get version Code as defined in build.gradle
     * @param ctx context
     * @return Integer with version code
     */
    public static int getVersionCode(Context ctx) {
        String pkgName = ctx.getPackageName();
        PackageInfo pkgInfo;
        try {
            pkgInfo = ctx.getPackageManager().getPackageInfo(pkgName, 0);
            return pkgInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Read out the title and the explanation text for the image. This is done at 2 occasions:
     * 1. Via CAB menu for a selected item
     * 2. When watching the image in fullscreen and having this option enabled (default)
     * If the reader fragment already exists (speaking), the speaker is stopped
     * @param fm FragmentManager
     * @param title Title text
     * @param text Explanation text
     * @return TextReader object
     */
    /*public static TextReader readText(FragmentManager fm, String title, String text) {
        TextReader readerFragment = (TextReader) fm.findFragmentByTag(TAG_READ_FRAGMENT);
        if (readerFragment == null) {
            readerFragment = new TextReader();
            Bundle fragArguments = new Bundle();
            fragArguments.putString("explanation", text);
            fragArguments.putString("title", title);
            readerFragment.setArguments(fragArguments);
            fm.beginTransaction().add(readerFragment, TAG_READ_FRAGMENT).commit();
        } else {
            Bundle args = readerFragment.getArguments();
            String currenttitle = args.getString("title");
            readerFragment.stop();
            fm.beginTransaction().remove(readerFragment).commit();
            if (currenttitle != null && !currenttitle.equals(title)) {
                args.putString("explanation", text);
                args.putString("title", title);
                readerFragment.setArguments(args);
                fm.beginTransaction().add(readerFragment, TAG_READ_FRAGMENT).commit();
            }

            int u = 0;
            return null;
        }
        return readerFragment;
    }

    public static void stopRead(FragmentManager fm) {
        TextReader readerFragment = (TextReader) fm.findFragmentByTag(TAG_READ_FRAGMENT);
        if (readerFragment != null) {
            readerFragment.stop();
            fm.beginTransaction().remove(readerFragment).commit();
        }
    }

    public static boolean isReading(FragmentManager fm) {
        TextReader readerFragment = (TextReader) fm.findFragmentByTag(TAG_READ_FRAGMENT);
        if (readerFragment != null) {
            if (readerFragment.isReading()) {
                return true;
            }
        }
        return false;
    }*/

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
