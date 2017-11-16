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
import java.util.Locale;
import java.util.TimeZone;


/**
 * Created by herbert on 10/18/17.
 * Use this class as a utility class with static member functions. Former helpers class removed.
 * - static member functions called on the "class", NOT an "instance"
 */
public final class utils {

    // Washington (NASA HQ) - matches with the observations, at which time new images appear in DE
    // https://www.timeanddate.de/stadt/info/usa/washington-dc
    // https://www.timeanddate.de/zeitzonen/deutschland
    // BUT: most of USA has daylight saving time, so how to know, when it switches to GMT-04:00?
    // API (google) or just use the scheduled plan within the app:
    // https://greenwichmeantime.com/time-zone/rules/usa/               - rules
    // https://developers.google.com/maps/documentation/timezone/intro  - API Google
    // https://www.timeanddate.com/services/api/                        - API

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
     * Calculate some epoch values based on TimeZone New York
     * @param locale
     * @return ArrayList of 3 long values containing epoch values
     */
    public static ArrayList<Long> getNASAEpoch(Locale locale) {
        ArrayList<Long> epochs = new ArrayList<>();
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            HashSet<String> zoneids = (HashSet<String>) ZoneId.getAvailableZoneIds();
        }*/

        // TimeZone tzNASA1 = TimeZone.getTimeZone("GMT-05:00");
        // Getting by ID returns a different object, than getting by GMT... Looks like this
        // implements dst already, so we need no API call..
        // Washington not available as ID, but New York is same zone and also uses DST
        // (only Arizona does not use DST)
        TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
        Calendar cNASA = Calendar.getInstance(tzNASA);
        String yyyymmddNASA = String.format(locale, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
        /*String hhmmssNASA = String.format (locale, "%02d:%02d:%02d",
                cNASA.get(Calendar.HOUR_OF_DAY),
                cNASA.get(Calendar.MINUTE),
                cNASA.get(Calendar.SECOND));*/

        // Get NASA epoch value for current date with time = 00:00:00
        long epochNASA_00_00_00;
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", locale);
        dF.setTimeZone(tzNASA);
        //dF.setCalendar(cNASA);
        try {
            epochNASA_00_00_00 = dF.parse(yyyymmddNASA).getTime();
        } catch (ParseException e) {
            epochNASA_00_00_00 = -1;
            e.printStackTrace();
        }
        // Prepare return values: NASA epoch at 00:00:00, NASA epoch full, our own epoch
        epochs.add(epochNASA_00_00_00);
        epochs.add(cNASA.getTimeInMillis());
        epochs.add(Calendar.getInstance().getTimeInMillis());   // actually same as value above

        // Testing and debugging
        SimpleDateFormat formatter = new SimpleDateFormat("dd. MMM yyyy, HH:mm:ss", locale);
        formatter.setTimeZone(tzNASA);
        formatter.setCalendar(cNASA);
        Log.i("HFCM", "Have epochs " + epochs +
                "\nLocal time converted to nasa: " + formatter.format(epochs.get(2)) +
                "\nNASA time 00:00:00: " + formatter.format(epochs.get(0)) +
                "\nNASA time full: " + formatter.format(epochs.get(1)));
        // It seems to be sufficient to have the timezone object
        TimeZone tzLOC = TimeZone.getDefault();
        //Calendar cLOC = Calendar.getInstance(tzLOC);
        formatter.setTimeZone(tzLOC);
        //formatter.setCalendar(cLOC);
        Log.i("HFCM", "Local time on device: " + formatter.format(epochs.get(2)) + " (" + tzLOC.getDisplayName() + ")");

        // we do not need to use a Date object for formatting, can pass epoch as well
        //Date testdate = new Date(epochNASA_00_00_00);
        //String formatted = formatter.format(testdate);

        // just get them all printed
        String[] tzIDs = TimeZone.getAvailableIDs();
        for (String id : tzIDs) {
            TimeZone tzTEST = TimeZone.getTimeZone(id);
            formatter.setTimeZone(tzTEST);
            //Log.i("HFCM", "Converted time: " + formatter.format(epochs.get(2)) +
            //        " (" + id + " / " + tzTEST.getDisplayName() + ")");
        }
        return epochs;
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
