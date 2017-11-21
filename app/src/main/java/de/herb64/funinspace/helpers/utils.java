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
     * @param locale the locale, used for string formatting
     * @return ArrayList of 3 long values containing epoch values:
     * - epoch-nasa at 00:00:00 (date only)
     * - epoch-nasa at current time
     * - epoch-device
     * TODO: THIS IS ALL STILL IN A VERY BAD SHAPE !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
     */
    public static ArrayList<Long> getNASAEpoch(Locale locale) {
        ArrayList<Long> epochs = new ArrayList<>();
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            HashSet<String> zoneids = (HashSet<String>) ZoneId.getAvailableZoneIds();
        }*/

        // The local system info
        final TimeZone tzSYS = TimeZone.getDefault();
        Log.i("HFCM", "Local timezone: " + tzSYS.toString());
        final Calendar cSYS = Calendar.getInstance(tzSYS);
        Log.i("HFCM", "Calendar info LOC: " + cSYS.toString());
        long epochSYS = System.currentTimeMillis();
        Log.i("HFCM", "SYS Epoch: " + epochSYS);

        // This one uses timezone New York, creating a different calendar object
        final TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
        Log.i("HFCM", "NASA timezone: " + tzNASA.toString());
        final Calendar cNASA = Calendar.getInstance(tzNASA);
        Log.i("HFCM", "Calendar info NASA: " + cNASA.toString());
        long epochNASA = cNASA.getTimeInMillis();
        Log.i("HFCM", "NASA Epoch: " + epochNASA);

        String yyyymmddNASA1 = String.format(locale, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
        Log.i("HFCM", "NASA Date string: " + yyyymmddNASA1);
        String yyyymmddSYS = String.format(locale, "%04d-%02d-%02d",
                cSYS.get(Calendar.YEAR),
                cSYS.get(Calendar.MONTH) + 1,
                cSYS.get(Calendar.DAY_OF_MONTH));
        Log.i("HFCM", "SYS Date string: " + yyyymmddSYS);

        // No matter, which calendar is used, it produces the same epoch value
        //long epochLOC = cLOC.getTimeInMillis();
        //long epochDEF = cDEF.getTimeInMillis();
        //long epochNASA = cNASA.getTimeInMillis();

        // Running this code BEFORE epochNASA is calculated, does change epoch NASA as well
        // WHY DOES THIS CHANGE cNASA obviously?? - because parse() changes timezone!!
        SimpleDateFormat dF00_00_00 = new SimpleDateFormat("yyyy-MM-dd", locale);
        long epochNASA_00_00_00 = 0;
        long epochSYS_00_00_00 = 0;
        try {
            // https://developer.android.com/reference/java/text/DateFormat.html#setTimeZone(java.util.TimeZone)
            // The TimeZone set by this method may be overwritten as a result of a call to the parse method.!!!!
            dF00_00_00.setCalendar((Calendar) cNASA.clone());       // use clone object, otherwise cNASA Timezone gets overwritten!!!
            //dFTEST.setTimeZone(tzNASA);
            epochNASA_00_00_00 = dF00_00_00.parse(yyyymmddNASA1).getTime();   // PARSE CHANGES THE TIMEZONE!!!
            dF00_00_00.setCalendar((Calendar) cSYS.clone());       // use clone object, otherwise cNASA Timezone gets overwritten!!!
            //dFTEST.setTimeZone(tzSYS);
            epochSYS_00_00_00 = dF00_00_00.parse(yyyymmddSYS).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        // Why does this get different results than same call before?? - parse is responsible!!
        // using a calendar clone for parse operations solves this
        long epochNASA2 = cNASA.getTimeInMillis();      // just verify, that parse did not change cNASA

        // No matter, which calendar object is used, the same time string is produced reflecting the
        // local time on the system for its timezone.
        //Log.i("HFCM", "LOC: epoch=" + epochLOC + " > " + cLOC.getTime().toString());
        //Log.i("HFCM", "DEF: epoch=" + epochDEF + " > " + cDEF.getTime().toString());
        Log.i("HFCM", "SYS: epoch=" + epochSYS + " > " + cSYS.getTime().toString());
        Log.i("HFCM", "NASA: epoch=" + epochNASA + " > " + cNASA.getTime().toString());
        Log.i("HFCM", "NASA 00_00_00: epoch=" + epochNASA_00_00_00 );
        Log.i("HFCM", "Local 00_00_00: epoch =" + epochSYS_00_00_00 );
        Log.i("HFCM", "NASA: epoch2=" + epochNASA2 + " > " + cNASA.getTime().toString());

        // Getting the time/date for any other given timezone (i.e. the local time there)
        // create a formatter for this and assign a calendar or timezone object of the requested
        SimpleDateFormat fmtNASA = new SimpleDateFormat("dd. MMM yyyy, HH:mm:ss", locale);
        //fmtNASA.setCalendar(cNASA);
        fmtNASA.setTimeZone(tzNASA);
        float deltaHours = (float) (epochNASA - epochNASA_00_00_00) / 3600000f;
        Log.i("HFCM", String.format(locale, "NASA local time: %s, %2f", fmtNASA.format(epochSYS), deltaHours));

        // Some other test:
        fmtNASA.setTimeZone(TimeZone.getTimeZone("Europe/Brussels"));
        Log.i("HFCM", String.format(locale, "Brussels local time: %s", fmtNASA.format(epochSYS)));

        // Get time difference between system and nasa in hours
        // NASA local 00 epoch is larger than local value (for timezone tokyo with 14h) - this makes
        // sense, because 1.1.1970 happened in Japan BEFORE the US

        // Message for display: new image not yet available
        // our date might be already ahead of nasa date


        // ____________________________________________________

        // TimeZone tzNASA1 = TimeZone.getTimeZone("GMT-05:00");
        // Getting by ID returns a different object, than getting by GMT... Looks like this
        // implements dst already, so we need no API call..
        // Washington not available as ID, but New York is same zone and also uses DST
        // (only Arizona does not use DST)

        String yyyymmddNASA = String.format(locale, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
        /*String hhmmssNASA = String.format (locale, "%02d:%02d:%02d",
                cNASA.get(Calendar.HOUR_OF_DAY),
                cNASA.get(Calendar.MINUTE),
                cNASA.get(Calendar.SECOND));*/

        // Get NASA epoch value for current date with time = 00:00:00
        //long epochNASA_00_00_00;
        long epochLocal;
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", locale);

        dF.setTimeZone(tzSYS);
        //dF.setCalendar(cLOC);
        try {
            epochLocal = dF.parse(yyyymmddNASA).getTime();
        } catch (ParseException e) {
            epochLocal = -1;
            e.printStackTrace();
        }

        dF.setTimeZone(tzNASA);
        //dF.setCalendar(cNASA);        // if setting this, it makes 00:00:00 time !!!
        try {
            epochNASA_00_00_00 = dF.parse(yyyymmddNASA).getTime();
        } catch (ParseException e) {
            epochNASA_00_00_00 = -1;
            e.printStackTrace();
        }
        // Prepare return values: NASA epoch at 00:00:00, NASA epoch full, our own epoch
        epochs.add(epochNASA_00_00_00);
        epochs.add(cNASA.getTimeInMillis());
        epochs.add(epochLocal);
        //epochs.add(Calendar.getInstance().getTimeInMillis());   // actually same as value above

        // Testing and debugging
        SimpleDateFormat formatter = new SimpleDateFormat("dd. MMM yyyy, HH:mm:ss", locale);
        formatter.setTimeZone(tzNASA);
        //formatter.setCalendar(cNASA);
        Log.i("HFCM", "Have epochs " + epochs +
                "\nLocal time converted to nasa: " + formatter.format(epochs.get(2)) +
                "\nNASA time 00:00:00: " + formatter.format(epochs.get(0)) +
                "\nNASA time full: " + formatter.format(epochs.get(1)));

        // It seems to be sufficient to have the timezone object
        //formatter.setTimeZone(tzLOC);
        formatter.setCalendar(cSYS);
        Log.i("HFCM", "Local time on device: " + formatter.format(epochs.get(2)) + " (" + tzSYS.getDisplayName() + ")");

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
