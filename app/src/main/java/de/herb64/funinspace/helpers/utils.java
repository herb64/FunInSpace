package de.herb64.funinspace.helpers;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
// import java.time.ZoneId;             API26+ required
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentNavigableMap;

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
     * @param locale the locale, used for string formatting
     * @param latestImgEpoch epoch from latest image or 0, if no delta to next img needed
     * @return ArrayList of 3 long values containing epoch values:
     * - epoch cut down to 00:00:00 (date only) - TODO why should we keep that? just for fun ??
     * - epoch full at current time
     * - milliseconds until next image fetch
     * */
    public static ArrayList<Long> getNASAEpoch(Locale locale, long latestImgEpoch) {
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
        Log.i("HFCM", "SYS TZ offset for epoch (hours): " +
                (float)tzSYS.getOffset(epochFULL)/3600000f);

        // NASA Server seems to be in New York timezone (deduced from delay when images appear)
        TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
        Log.w("HFCM", "NASA timezone " +
                tzNASA.getDisplayName() +
                " / " + tzNASA.getID());
        Calendar cNASA = Calendar.getInstance(tzNASA);
        Log.i("HFCM", "NASA TZ offset for epoch (hours): " +
                (float)tzNASA.getOffset(epochFULL)/3600000f);
        Log.w("HFCM", "SYS ahead of NASA (hours): " +
                ((float)tzSYS.getOffset(epochFULL) - (float)tzNASA.getOffset(epochFULL))/3600000f);

        // The only way to get the date/time as string as seen in other timezone from the calendar
        // is to query the elements single calendar fields themselves and create a formatted string
        // Calling calendar.getTime() returns our local time values
        // Log.e("HFCM", "TEST: " + cNASA.getTime().toString());   // gets our local time!!
        String yyyymmddNASA = String.format(locale, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
        Log.i("HFCM", "NASA Date only string: " + yyyymmddNASA);
        String yyyymmddhhmmssNASA = String.format(locale, "%04d-%02d-%02d_%02d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH),
                cNASA.get(Calendar.HOUR_OF_DAY),
                cNASA.get(Calendar.MINUTE),
                cNASA.get(Calendar.SECOND));
        Log.i("HFCM", "NASA Date+time string: " + yyyymmddhhmmssNASA);
        String yyyymmddSYS = String.format(locale, "%04d-%02d-%02d",
                cSYS.get(Calendar.YEAR),
                cSYS.get(Calendar.MONTH) + 1,
                cSYS.get(Calendar.DAY_OF_MONTH));
        Log.i("HFCM", "SYS  Date onlystring: " + yyyymmddSYS);
        String yyyymmddhhmmssSYS = String.format(locale, "%04d-%02d-%02d_%02d-%02d-%02d",
                cSYS.get(Calendar.YEAR),
                cSYS.get(Calendar.MONTH) + 1,
                cSYS.get(Calendar.DAY_OF_MONTH),
                cSYS.get(Calendar.HOUR_OF_DAY),
                cSYS.get(Calendar.MINUTE),
                cSYS.get(Calendar.SECOND));
        Log.i("HFCM", "SYS Date+time string: " + yyyymmddhhmmssSYS);

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
        Log.i("HFCM", "FULL: epoch=" + epochFULL);
        Log.i("HFCM", "00_00_00: epoch= " + epoch_00_00_00);
        Log.i("HFCM", "Latest image: epoch=" + latestImgEpoch);

        // Calculate the delta time to next required image load.
        long timeToNext = 0;
        if (latestImgEpoch > 0) {
            Date datefull = new Date(epochFULL);
            Date dateLatestImg = new Date(latestImgEpoch);  // seen in current TimeZone!
            //Date date00_00_00 = new Date(epoch_00_00_00);
            Log.i("HFCM", "Date latest image: " + dateLatestImg.toString());
            long diff = (datefull.getTime() - dateLatestImg.getTime());
            Log.i("HFCM", "Diff full - imagelatest: " + diff + " seconds");
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
        Log.i("HFCM", String.format(locale, "Brussels local time: %s", fmtCHECK.format(epochFULL)));

        // Prepare return values: NASA epoch at 00:00:00, NASA epoch full, our own epoch
        epochs.add(epoch_00_00_00);
        epochs.add(epochFULL);
        epochs.add(timeToNext);

        // Testing and debugging
        SimpleDateFormat formatter = new SimpleDateFormat("dd. MMM yyyy, HH:mm:ss", locale);
        formatter.setTimeZone(tzNASA);
        //formatter.setCalendar(cNASA);
        Log.i("HFCM", "Have values " + epochs +
                "\nNASA time 00:00:00: " + formatter.format(epochs.get(0)) +
                "\nNASA time full: " + formatter.format(epochs.get(1)) +
                "\nNASA time next: " + formatter.format(epochs.get(1) + epochs.get(2)));

        // It seems to be sufficient to have the timezone object
        //formatter.setTimeZone(tzLOC);
        formatter.setCalendar(cSYS);
        Log.i("HFCM", "Local time on device: " + formatter.format(epochs.get(1)) + " (" + tzSYS.getDisplayName() + ")");
        Log.i("HFCM", "Local time for next image fetch: " + formatter.format(epochs.get(1) + epochs.get(2)));

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
     * @param maxHdMem maximum allowed memory for hires images in bytes
     * @return formatted string with results on cleanup
     */
    public static String cleanupFiles(Context ctx, ArrayList<spaceItem> items, long maxHdMem) {
        // TODO For testing, it is currently on "R.id.action_search_apod"
        // filenamefilter does not allow wildcard... could use java.nio.file... but not avail..

        String logString = "";
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
            logString += "Orphans:\n";
        }
        for (String fn : orphans) {
            File todelete = new File(ctx.getFilesDir(), fn);
            Log.i("HFCM", "Deleting orphaned file: " + fn);
            logString += fn + "\n";
            /*if (!todelete.delete()) {
                Log.e("HFCM", "Error deleting orphaned file: " + todelete);
            }*/
        }

        // Delete hires images if space is exceeded: depend on size and rating
        // TreeMap is sorted: https://developer.android.com/reference/java/util/TreeMap.html
        Log.i("HFCM", "About to cleanup hd images space, currently in use: " + usedHdMem);
        TreeMap<Long, File> sorted = new TreeMap<>();
        if (usedHdMem > maxHdMem) {
            Log.i("HFCM", "Used hd mem " + usedHdMem + " exceeds max hd mem " + maxHdMem);
            for (spaceItem item : items) {
                File hd = new File(ctx.getFilesDir(), item.getThumb().replace("th_", "hd_"));
                if (hd.exists()) {
                    int rating = item.getRating();
                    long size = hd.length() / 1024;
                    long key = MAX_HDSIZE - size + rating * MAX_HDSIZE;
                    Log.i("HFCM", "Adding " + hd.getName() + " - key=" + key +
                            " (size=" + size + ", rating=" + rating + ")");
                    sorted.put(key, hd);
                }
            }
            logString += "Hires images: (" + usedHdMem + "KB of " + maxHdMem + "KB)\n";
            for(Map.Entry<Long,File> entry : sorted.entrySet()) {
                usedHdMem -= entry.getValue().length() / 1024;
                Log.i("HFCM", "Deleting Hires file: " + entry.getValue().getName() + " > " +
                        entry.getKey() + " - used mem now " + usedHdMem);
                logString += entry.getValue().getName() + " (" + entry.getValue().length()/1024 + ")\n";
                /*if (!entry.getValue().delete()) {
                    Log.e("HFCM", "Error deleting file: " + entry.getValue().getName());
                }*/
                if (usedHdMem < maxHdMem) {
                    logString += "Now used " + usedHdMem + "KB after cleanup";
                    logString += "\nNO REAL DELETES DURING TEST PHASE FOR NOW!!!!";
                    break;
                }
            }
        }
        return logString;
    }

    // TODO - backup function. Restore will not restore hires images unchecked, because these are
    // possibly lower res than on new device, where restore takes place...
    // need to backup info about phone as well to do checks
    public static void backupToSdCard(Context ctx, ArrayList<spaceItem> items) {

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
