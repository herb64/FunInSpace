package de.herb64.funinspace;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.WallpaperManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
// import android.support.multidex.MultiDexApplication;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ListView;
import android.support.v7.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeStandalonePlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import de.herb64.funinspace.helpers.deviceInfo;
import de.herb64.funinspace.helpers.dialogDisplay;
import de.herb64.funinspace.helpers.utils;
import de.herb64.funinspace.models.spaceItem;
import de.herb64.funinspace.services.apodJobService;
import de.herb64.funinspace.services.shuffleJobService;
import de.herb64.funinspace.services.thumbLoaderJobService;

// TODO Log statements: Log.d etc.. should not be contained in final release - how to automate?
// see https://stackoverflow.com/questions/2446248/remove-all-debug-logging-calls-before-publishing-are-there-tools-to-do-this

// TODO: if missing or bad hires image url for an image, we need to handle this: fall back to lowres
// TODO: network check - timeouts, 404 etc..  --- UNMETERED NETWORKS - check this
// https://developer.android.com/reference/android/net/NetworkCapabilities.html

/*
  TODO Weak references... check..
https://github.com/googlesamples/android-JobScheduler/blob/master/Application/src/main/java/com/example/android/jobscheduler/MainActivity.java
/ Prevent possible leaks with a weak reference.
private WeakReference<MainActivity> mActivity;
IncomingMessageHandler(MainActivity activity) {
            super();
        this.mActivity = new WeakReference<>(activity);
        }
 */


/*
 * The MainActivity Class for FunInSpace
 */
public class MainActivity extends AppCompatActivity
        implements ratingDialog.RatingListener, asyncLoad.AsyncResponse, confirmDialog.ConfirmListener {

    private spaceItem apodItem;                     // TODO: no longer needed
    private ArrayList<spaceItem> myList;
    //private LinkedHashMap<String, spaceItem> myMap; // abandoned, old class file still present
    private spaceAdapter adp;
    private JSONArray parent;
    private String jsonData;
    private String localJson = "nasatest.json";
    protected thumbClickListener myThumbClickListener;
    //private ratingChangeListener myRatingChangeListener;
    private ListView myItemsLV;
    private deviceInfo devInfo;
    private int maxTextureSize = 999;   // TODO clean this, just to check the 999 was ok - still seems to be found, see Nathan
    private String lastImage;           // for log dialog title
    private Locale loc;
    protected Drawable expl_points;
    private SharedPreferences sharedPref;
    private boolean thumbQualityChanged;    // indicate preference setting change
    private boolean dateFormatChanged;      // important: need true after installation
    private boolean wpShuffleChanged;
    private int currentWallpaperIndex = -1;
    protected TimeZone tzNASA;
    protected Calendar cNASA;
    protected SimpleDateFormat formatter;
    private TextToSpeech tts;
    private BroadcastReceiver receiver = null;
    private NetworkBcastReceiver networkReceiver = null;
    private int unfinishedApods = 0;

    // Using JNI for testing with NDK and C code in a shared lib .so file
    static {System.loadLibrary("hfcmlib");}
    public native String yT();
    public native String nS();
    public native String vE();
    public native String dPJ();
    public native String dAS();

    // ========= CONSTANTS =========
    //public static String TAG = MainActivity.class.getSimpleName();

    private static final String ABOUT_VERSION = "0.5.6 (alpha)\nBuild Date 2017-12-17\n";

    // for logcat wrapper - utils.java
    public static final boolean LOGCAT_INFO = false;
    public static final boolean LOGCAT_ERROR = false;

    private static final int HIRES_LOAD_REQUEST = 1;
    private static final int GL_MAX_TEX_SIZE_QUERY = 2;
    private static final int SETTINGS_REQUEST = 3;

    //private static final int KIB = 1024;
    //private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;

    // strings for media type classification
    public static final String M_IMAGE = "image";
    public static final String M_YOUTUBE = "youtube";
    public static final String M_VIMEO = "vimeo";
    public static final String M_MP4 = "mp4";      // first nasa mp4 on 13.11.2017
    public static final String M_VIDEO_UNKNOWN = "unknown-video";

    // dealing with the number of displayed lines in the Explanation text view
    protected static final int MAX_ELLIPSED_LINES = 2;
    private static final int MAX_LINES = 1000;      // ridiculous, but safe
    protected static final int MAX_ITEMS = 10000;   // theoretic limit of items - for id handling

    // wallpaper related stuff
    private static final String WP_CURRENT_BACKUP = "w_current.jpg";
    protected static final int WP_NONE = 0;
    public static final int WP_EXISTS = 1;
    protected static final int WP_ACTIVE = 3;   // just in case of bitmap interpretation
    //private static final int DEFAULT_MAX_STORED_WP = 20;      // limit number of stored wallpapers - better use memory constraint ??

    public static final String APOD_SCHED_PREFIX = "s___";
    protected static final int MAX_HIRES_MB = 100;  // limit (MB) for hires img on internal stg         // TODO - rework with percentage below
    protected static final int MAX_HIRES_PERCENT = 10;  // percentage..                                 // TODO - config option
    public static final int JOB_ID_SHUFFLE = 85407;
    public static final String BCAST_SHUFFLE = "SHUFFLE";
    public static final int JOB_ID_APOD = 3124;
    public static final String BCAST_APOD = "APOD";
    public static final int JOB_ID_THUMB = 739574;
    public static final String BCAST_THUMB = "THUMB";
    public static final String DEBUG_LOG = "funinspace.log";

    /**
     * @param savedInstanceState saved instance data
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // before own code for create

        if (getIntent().getExtras() != null && getIntent().getExtras().getBoolean("EXIT", false)) {
            finishAndRemoveTask();
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //https://developer.android.com/design/patterns/actionbar.html
        ActionBar actbar = getSupportActionBar();
        actbar.setIcon(R.mipmap.ic_launcher);
        actbar.setDisplayShowTitleEnabled(false);
        // We could set our own custom view here as well - not tested... TODO
        //actbar.setDisplayShowCustomEnabled(true);
        //actbar.setCustomView();

        utils.info("onCreate()...........");

        // get the locale - using default leads to several warnings with String.format()
        // https://stackoverflow.com/questions/14389349/android-get-current-locale-not-default
        /*if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = getResources().getConfiguration().locale;
        }*/
        loc = Locale.getDefault();

        long starttime = System.currentTimeMillis();
        utils.logAppend(getApplicationContext(), DEBUG_LOG,
                "**************  APP START **************");
        Log.d("HFCM", "**************************  APP START **************************");

        // READ PREFERENCE SETTINGS FROM DEFAULT SHARED PREFERENCES
        // (shared_prefs/de.herb64.funinspace_preferences.xml)
        // TODO check, if this is rotation proof, or if we better should get prefs each time
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        // Initialize flags to true, so that first open of preferences dialog, which triggers the
        // prefChangeListener, does change it back to false in this case.
        dateFormatChanged = !sharedPref.contains("date_format");
        thumbQualityChanged = !sharedPref.contains("rgb565_thumbs");
        wpShuffleChanged = !sharedPref.contains("wallpaper_shuffle");
        sharedPref.registerOnSharedPreferenceChangeListener(prefChangeListener);

        // Timezone and Calendar objects used to base our timestamps on current NASA TimeZone
        // We must interpret the stored epoch value as seen from NASA time!
        tzNASA = TimeZone.getTimeZone("America/New_York"); // NASA server is within this TZ
        cNASA = Calendar.getInstance(tzNASA);
        // TODO: shared prefs are mixed up for date format
        String fmt = sharedPref.getString("date_format", getString(R.string.df_dd_mm_yyyy));
        formatter = new SimpleDateFormat(fmt, loc);
        formatter.setTimeZone(tzNASA);
        formatter.setCalendar(cNASA);

        // TODO solve issue with vectorgraphics on 4.1 (4.x?) - for now, just dirty workaround...
        // arrow_down_float image only - don't like that, but do it now
        // Drawable for textview - use builtin in "android.R...." - now use SVG graphic via xml
        // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
        // https://medium.com/@chrisbanes/appcompat-v23-2-age-of-the-vectors-91cbafa87c88
        // - vectorDrawables.useSupportLibrary = true >> already in build.gradle
        // - setting "app:srcCompat="@drawable/hfcm" in spaceitem textview xml also does not help
        // android.content.res.Resources$NotFoundException: File res/drawable/hfcm.xml from drawable resource ID #0x7f02005b
        // on 4.1 AVD....
        // https://stackoverflow.com/questions/39091521/vector-drawables-flag-doesnt-work-on-support-library-24
        // https://developer.android.com/topic/libraries/support-library/features.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            expl_points = ContextCompat.getDrawable(this, R.drawable.hfcm);
        } else {
            expl_points = ContextCompat.getDrawable(this, android.R.drawable.arrow_down_float);
        }

        /*FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });*/

        // Prepare a device information class to be used during testing and debugging.
        // Query GL_MAX_TEXTURE_SIZE by creating an OpenGL context within a separate Activity.
        // Important: returned value maxTextureSize not yet available at onCreate(), although
        // onActivityResult is called and values are correct at later times after finishing
        // onCreate() and even the Toast within onActivityResult shows the correct values

        devInfo = new deviceInfo(getApplicationContext());
        myItemsLV = (ListView) findViewById(R.id.lv_content);

        // --------  Option 1 for  contextual action mode (non multi select) ---------
        // LONG CLICK MENU - Option 1 for "non multi select"...
        // needs ActionMode.Callback as well, see code at end of this listing...
        //myItemsLV.setLongClickable(true);
        // We also need an action bar
        // https://developer.android.com/design/patterns/actionbar.html
        // https://material.io/guidelines/patterns/selection.html
        // https://www.youtube.com/watch?v=blJMA9CHkyc  09:45 about xml creation of menu itself
        /*myItemsLV.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> parent, View v,
                                           int position, long id) {

                if (mActionMode != null) {      // avoid unnecessary recreation...
                    return false;
                }
                // Start the CAB using the ActionMode.Callback defined above
                //mActionMode = MainActivity.this.startActionMode((android.view.ActionMode.Callback) mActionModeCallback);
                // better do not cast but use "Support" version of the function
                mActionMode = MainActivity.this.startSupportActionMode(mActionModeCallback);
                v.setSelected(true);
                return true;
            }});*/

        // Allow click on items in listview without disrupting MultiChoiceModeListener for
        // contextual action bar handling. This is a better solution for expanding explanation text.
        // Important: id reports what is set in my space adapter via overwritten getItemId()
        myItemsLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
                // Just reset the maxlines to a larger limit and remove the ellipse stuff. This
                // is only temporarily and automatically disappears when scrolling or when clicking
                // again.
                // TODO - at background load of thumbs, the full text always disappears on refreshs
                // TODO - do not ellipsize if explanation matches into minimum lines
                TextView v = myItemsLV.findViewWithTag(position + MAX_ITEMS);
                boolean read = sharedPref.getBoolean("read_out", false);
                if (v.getMaxLines() == MAX_ELLIPSED_LINES) {
                    v.setMaxLines(MAX_LINES);
                    v.setEllipsize(null);
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
                    if (read && tts != null) {
                        tts.speak(myList.get((int)id).getExplanation(),
                                TextToSpeech.QUEUE_FLUSH,
                                null);
                    }
                } else {
                    v.setEllipsize(TextUtils.TruncateAt.END);
                    v.setMaxLines(MAX_ELLIPSED_LINES);
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,expl_points);
                    if (read && tts != null) {
                        tts.stop();
                    }
                }
                myItemsLV.setSelection(position);
            }
        });

        // --------  Option 2 for  contextual action mode with multiple selections ---------
        // Handling multiple choices - add another listener. Note, that this responds to long clicks
        // without using the longclick listener explicitly...
        // https://www.youtube.com/watch?v=kyErynku-BM  (Prabeesh R K)
        myItemsLV.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        myItemsLV.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            private ArrayList<Integer> selected;    // keep track of selected items

            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode actionMode,
                                                  int position,
                                                  long id,
                                                  boolean checked) {
                // ID:       always contains index in FULL dataset. See spaceAdapter.getItemId()
                // POSITION: index within the CURRENTLY presented view, which may be filtered
                if (checked) {
                    selected.add((int) id);
                } else {
                    // Either object or position as parameter? here, both are of type int :)
                    // https://stackoverflow.com/questions/4534146/properly-removing-an-integer-from-a-listinteger
                    selected.remove(Integer.valueOf((int) id));
                }
                myList.get((int) id).setSelected(checked);

                // Dynamically change action bar depending on selections
                MenuItem readitem = actionMode.getMenu().findItem(R.id.cab_read);
                readitem.setEnabled(!(selected.size() > 1));
                readitem.setVisible(!(selected.size() > 1));
                // TODO: change action bar to "delete cached file" or "force reload from NASA", if a cached file currently exists...
                adp.notifyDataSetChanged();
            }

            /**
             * @param actionMode
             * @param menu
             * @return
             */
            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.menu_cab_main, menu);
                selected = new ArrayList<>();
                /* Just for keeping track of actions
                // Trying to display the RatingBar within CAB, similar to SearchView in main bar
                // this fails, see lalatex doc for details: RatingBar within Contextual Action Bar*/
                return true; // important, otherwise no selection of items!
            }

            /**
             * Dynamically enable/disable menu items depending on current state of selection. E.g.
             * disable the wallpaper menu item, if more than one space item is selected.
             * @param actionMode the action mode
             * @param menu the menu
             * @return return true
             */
            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                //TODO Trick for menu to show the icons within overflow menu does not work for CAB
                /*if(menu instanceof MenuBuilder){  // false
                    MenuBuilder m = (MenuBuilder) menu;
                    //noinspection RestrictedApi
                    m.setOptionalIconsVisible(true);
                }*/
                //item.setIcon(android.R.drawable.btn_plus); // also does not work

                // By default, all menu items are visible
                MenuItem item_setwp = menu.findItem(R.id.cab_wallpaper);
                //item_setwp.setEnabled(!(selected.size() > 1));
                MenuItem item_reselect = menu.findItem(R.id.cab_wp_reselect);
                MenuItem item_remove = menu.findItem(R.id.cab_wp_remove);
                if (selected.size() == 1) {
                    // For a single selection: non images cannot be selected at all
                    if (!myList.get(selected.get(0)).getMedia().equals(M_IMAGE)) {
                        // setVisible is better than setEnabled in this case!
                        item_setwp.setVisible(false);
                        item_reselect.setVisible(false);
                        item_remove.setVisible(false);
                        return true;
                    }
                    int wpflag = myList.get(selected.get(0)).getWpFlag();
                    if (wpflag == WP_ACTIVE) {
                        item_setwp.setVisible(false);
                        item_remove.setVisible(false);
                        item_reselect.setVisible(true);
                    } else if (wpflag == WP_EXISTS) {
                        item_setwp.setVisible(true);
                        item_remove.setVisible(true);
                        item_reselect.setVisible(false);
                    } else {
                        item_setwp.setVisible(true);
                        item_remove.setVisible(false);
                        item_reselect.setVisible(false);
                    }
                } else if (selected.size() > 1) {
                    for (int idx : selected) {
                        if (myList.get(idx).getWpFlag() == WP_EXISTS) {
                            item_setwp.setVisible(false);
                            item_remove.setVisible(true);
                            item_reselect.setVisible(false);
                            break;
                        }
                        item_setwp.setVisible(false);
                        item_remove.setVisible(false);
                        item_reselect.setVisible(false);
                    }
                }
                return true;        // ???
                //return false;
            }

            // Now we did click on an action item in our CAB and need to process actions on all
            // selected SpaceItems in our listview
            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.cab_delete:
                        // Within ArrayList - removal from back to front is essential!!
                        Collections.sort(selected, Collections.<Integer>reverseOrder());
                        boolean needShuffleListRefresh = false;
                        for (int idx : selected) {
                            // spaceAdapter.remove() has been overwritten, so that it deletes the
                            // image in the full ArrayList plus (if filtered view) in the currently
                            // presented view as well.
                            if (parent.length() != myList.size()) {
                                new dialogDisplay(MainActivity.this, "Some problem with json (" +
                                        parent.length() + ") vs. list (" + myList.size() +
                                        ") size", "Info for Herbert");
                            }
                            // Remove images BEFORE indexed item gets deleted from list. If the item
                            // holds the active wallpaper, deletion is skipped
                            Log.i("HFCM", "Attempting to delete: " + myList.get(idx).getThumb() +
                                    " for '" + myList.get(idx).getTitle() + "'");
                            if (myList.get(idx).getWpFlag() == WP_EXISTS) {
                                File wpdel = new File(getApplicationContext().getFilesDir(),
                                        myList.get(idx).getThumb().replace("th_", "wp_"));
                                needShuffleListRefresh = true;
                                if (!wpdel.delete()) {
                                    Log.i("HFCM", "File delete for wallpaper did not return true");
                                }
                            } else if (myList.get(idx).getWpFlag() == WP_ACTIVE) {
                                Log.i("HFCM", "File delete skipped for active wallpaper");
                                new dialogDisplay(MainActivity.this,
                                        getString(R.string.wp_no_delete_active,
                                                myList.get(idx).getTitle()), "Warning");
                                continue;
                            }
                            // Remove the thumbnail image
                            File thdel = new File(getApplicationContext().getFilesDir(),
                                    myList.get(idx).getThumb());
                            if (!thdel.delete()) {
                                Log.e("HFCM", "File delete for thumbnail did not return true");
                            }
                            // Remove existing hires image
                            File hddel = new File(getApplicationContext().getFilesDir(),
                                    myList.get(idx).getThumb().replace("th_", "hd_"));
                            if (hddel.exists()) {
                                if (!hddel.delete()) {
                                    Log.e("HFCM", "File delete for hires cached image did not return true");
                                }
                            }

                            // Deleted items - keep track to avoid dropbox sync to get these again
                            Set<String> deleted_items = sharedPref.getStringSet("DELETED_ITEMS",
                                    new HashSet<String>());
                            // IMPORTANT: Need a copy, do NOT edit the gathered object itself!!!
                            Set<String> newset = new HashSet<>(deleted_items);
                            newset.add(myList.get(idx).getTitle());
                            SharedPreferences.Editor editor = sharedPref.edit();
                            editor.putStringSet("DELETED_ITEMS", newset);
                            editor.apply();
                            utils.logAppend(getApplicationContext(),
                                    DEBUG_LOG,
                                    "Delete item: '" + myList.get(idx).getTitle() + "'");

                            // delete the json object from json array
                            try {
                                JSONObject obj = (JSONObject) parent.get(parent.length() - 1 - idx);
                                String title = obj.getJSONObject("Content").getString("Title");

                                // json array remove: parent.remove(index) requires API level 19
                                // https://gist.github.com/emmgfx/0f018b5acfa3fd72b3f6
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                    Log.i("HFCM", "json remove: " + title);
                                    parent.remove(parent.length() - 1 - idx);
                                } else {
                                    Log.i("HFCM", "Doing PRE KITKAT json remove for " + title);
                                    // no nice code here.... we should improve that, for example
                                    // do only one iteration for ALL elements in "selected"
                                    JSONArray tempjson = new JSONArray();
                                    for (int i = 0; i < parent.length(); i++) {
                                        JSONObject tobj = (JSONObject) parent.get(i);
                                        if (!tobj.getJSONObject("Content").getString("Title").equals(title)) {
                                            tempjson.put(tobj);
                                        }
                                    }
                                    parent = tempjson;
                                }
                                // Make changes permanent by rewriting json file
                                utils.writeJson(getApplicationContext(), localJson, parent);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            adp.remove(myList.get(idx));
                            // adp.setNotifyOnChange(true); // TODO - test this for auto notify ??
                        }
                        adp.notifyDataSetChanged();
                        if (needShuffleListRefresh) {
                            utils.setWPShuffleCandidates(getApplicationContext(), myList);
                        }
                        actionMode.finish();
                        return true;
                    case R.id.cab_share:
                        new dialogDisplay(MainActivity.this, "Sharing not yet possible", "Herbert TODO");
                        actionMode.finish();
                        return true;
                    case R.id.cab_rating:
                        // AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                        // builder.setView(R.layout.rating_dialog);  // REQUIRES 21 minimum :(
                        // We use a DialogFragment that displays the Rating dialog and our main
                        // activity implements an interface for rating listener
                        FragmentManager fm = getSupportFragmentManager();
                        ratingDialog dlg = new ratingDialog();
                        // Just pass the indices to the rating fragment, so that they get returned
                        // by our interface implementation. This avoids a global def. of "selected"
                        // and potential problems with phone rotation
                        // AGAIN: do NOT use non default constructor with fragments - use arguments
                        Bundle fragArguments = new Bundle();
                        fragArguments.putIntegerArrayList("indices", selected);
                        int current = 0;
                        for (int i : selected) {
                            if (myList.get(i).getRating() > current) {
                                current = myList.get(i).getRating();
                            }
                        }
                        fragArguments.putInt("current_rating", current);
                        dlg.setArguments(fragArguments);
                        // dlg.setTargetFragment(); // this only works when calling from fragment,
                        // not from activity as here
                        dlg.show(fm, "RATINGTAG");
                        actionMode.finish();
                        return true;
                    case R.id.cab_wallpaper:
                    case R.id.cab_wp_remove:
                    case R.id.cab_wp_reselect:
                        if (selected.size() == 1) {
                            switch (myList.get(selected.get(0)).getWpFlag()) {
                                case WP_NONE:
                                case WP_ACTIVE:
                                    String basefn = myList.get(selected.get(0)).getThumb().replace("th_", "");
                                    int maxAlloc = devInfo.getMaxAllocatable();
                                    Intent hiresIntent = new Intent(getApplication(), ImageActivity.class);
                                    // Check, if a local copy for hires image exists. If yes, pass filepath to URL
                                    // TODO: duplicate code here... do this better - also need to check for wifi flag to avoid load for wallpaper purpose as well
                                    //String hiresFileBase = myList.get(idx).getThumb().replace("th_", "");
                                    File hiresFile = new File(getApplicationContext().getFilesDir(), "hd_" + basefn);
                                    String hiresUrl;
                                    if (hiresFile.exists()) {
                                        hiresUrl = hiresFile.getAbsolutePath();
                                    } else {
                                        hiresUrl = myList.get(selected.get(0)).getHires();
                                    }
                                    hiresIntent.putExtra("hiresurl", hiresUrl);
                                    hiresIntent.putExtra("listIdx", selected.get(0));
                                    hiresIntent.putExtra("maxAlloc", maxAlloc);
                                    hiresIntent.putExtra("maxtexturesize", maxTextureSize);
                                    hiresIntent.putExtra("wallpaper_quality",
                                            sharedPref.getString("wallpaper_quality", "80"));
                                    lastImage = myList.get(selected.get(0)).getTitle();
                                    // base filename now as imagename
                                    hiresIntent.putExtra("imagename", basefn);
                                    // call image activity with wallpaper selection mode at startup
                                    hiresIntent.putExtra("wpselect", true);
                                    Log.i("HFCM", "Changing wallpaper 'wp_" + basefn + "'");
                                    startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                                    break;
                                case WP_EXISTS:
                                    switch (menuItem.getItemId()) {
                                        case R.id.cab_wallpaper:
                                            // Confirm dialog - set the wallpaper in callback
                                            fragArguments = new Bundle();
                                            fragArguments.putInt("IDX", selected.get(0));
                                            fragArguments.putString("TITLE",
                                                    getString(R.string.wp_confirm_dlg_title));
                                            fragArguments.putString("MESSAGE",
                                                    getString(R.string.wp_confirm_dlg_msg,
                                                    myList.get(selected.get(0)).getTitle()));
                                            fragArguments.putString("POS",
                                                    getString(R.string.wp_confirm_dlg_pos_button));
                                            fragArguments.putString("NEG",
                                                    getString(R.string.wp_confirm_dlg_neg_button));
                                            fm = getSupportFragmentManager();
                                            confirmDialog confirmdlg = new confirmDialog();
                                            confirmdlg.setArguments(fragArguments);
                                            confirmdlg.show(fm, "WP");
                                            break;
                                        case R.id.cab_wp_remove:
                                            if (myList.get(selected.get(0)).getWpFlag() == WP_EXISTS) {
                                                File wpdel = new File(getApplicationContext().getFilesDir(),
                                                        myList.get(selected.get(0)).getThumb().replace("th_", "wp_"));
                                                if (!wpdel.delete()) {
                                                    Log.i("HFCM", "File delete for wallpaper did not return true");
                                                }
                                                myList.get(selected.get(0)).setWpFlag(WP_NONE);
                                                adp.notifyDataSetChanged();
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                    break;
                                default:
                                    break;

                            }
                            return true;
                        }

                        // This is reached for multiple selections, there's only one option: delete
                        // all wallpapers, if existing for selected elements. No removal of the
                        // active one. This just removes wp files and updates wp status
                        // TODO; confirm dialog
                        for (int idx : selected) {
                            if (myList.get(idx).getWpFlag() == WP_EXISTS) {
                                File wpdel = new File(getApplicationContext().getFilesDir(),
                                        myList.get(idx).getThumb().replace("th_", "wp_"));
                                if (!wpdel.delete()) {
                                    Log.i("HFCM", "File delete for wallpaper did not return true");
                                }
                                myList.get(idx).setWpFlag(WP_NONE);
                            }
                        }
                        adp.notifyDataSetChanged();
                        return true;
                    case R.id.cab_read:
                        // deprecations
                        // https://stackoverflow.com/questions/27968146/texttospeech-with-api-21
                        if (tts != null && !tts.isSpeaking()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                tts.speak("Title",
                                        TextToSpeech.QUEUE_ADD,
                                        null, null);
                            } else {
                                tts.speak("Title",
                                        TextToSpeech.QUEUE_ADD,
                                        null);
                            }
                            tts.playSilence(500L,
                                    TextToSpeech.QUEUE_ADD,
                                    null);
                            tts.speak(myList.get(selected.get(0)).getTitle(),
                                    TextToSpeech.QUEUE_ADD,
                                    null);
                            tts.playSilence(500L,
                                    TextToSpeech.QUEUE_ADD,
                                    null);
                            tts.speak("Explanation",
                                    TextToSpeech.QUEUE_ADD,
                                    null);
                            tts.playSilence(500L,
                                    TextToSpeech.QUEUE_ADD,
                                    null);
                            tts.speak(myList.get(selected.get(0)).getExplanation(),
                                    TextToSpeech.QUEUE_ADD,
                                    null);
                        } else if (tts != null) {
                            tts.stop();
                        }
                        return true;
                    default:
                        return false;
                }
            }

            /**
             * @param actionMode mode
             */
            @Override
            public void onDestroyActionMode(android.view.ActionMode actionMode) {
                // reset selection status for all items
                for (spaceItem item : myList) {
                    item.setSelected(false);
                }
                if (tts != null) {
                    tts.stop();
                }
            }
        });

        // Create a listeners for handling clicks on the thumbnail image and for rating changes
        myThumbClickListener = new thumbClickListener();
        //myRatingChangeListener = new ratingChangeListener();

        myList = new ArrayList<> ();
        adp = new spaceAdapter(getApplicationContext(), MainActivity.this,
                R.layout.space_item, myList);
        adp.setFullSearch(sharedPref.getBoolean("full_search", false));
        myItemsLV.setAdapter(adp);

        // SearchView - this had been defined in XML, but set to "GONE" by default. When search is
        // requested, this was originally shown above the listview. This has been changed to show
        // the SearchView within the App Bar.
        // See code in onCreateOptionsMenu() for SearchView as a menu item
        // https://www.youtube.com/watch?v=c9yC8XGaSv4
        // https://www.youtube.com/watch?v=YnNpwk_Q9d0
        // https://www.youtube.com/watch?v=9OWmnYPX1uc

        if (savedInstanceState != null) {
            // The spaceItem internal structure and the json data string are restored.
            // On Android 8.0 this fails with Transaction Too Large error, so we do not put this
            // structure onto the saved instance state and instead recreate it using addItems()
            //myList = savedInstanceState.getParcelableArrayList("myList");
            jsonData = savedInstanceState.getString("jsonData");
            if (jsonData != null) {
                try {
                    parent = new JSONArray(jsonData);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            addItems();
            maxTextureSize = savedInstanceState.getInt("maxtexsize");
            devInfo.setGlMaxTextureSize(maxTextureSize);
        } else {
            jsonData = null;
            File jsonFile = new File(getApplicationContext().getFilesDir(), localJson);
            if (jsonFile.exists()) {
                jsonData = utils.readf(getApplicationContext(), localJson);
            }

            // -------------------------------------------------------------------
            // DECIDE, IF WE ARE IN "FIRST LAUNCH" BY PRESENCE OF jsonData CONTENT
            // jsonData != null: we have a file, so not first successful launch
            // jsonData == null: no file, first launch most likely
            // -------------------------------------------------------------------
            if (jsonData != null) {
                SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
                maxTextureSize = shPref.getInt("maxtexsize", 0);
                devInfo.setGlMaxTextureSize(maxTextureSize);
                // TODO: if shared preferences are lost for some reason, run texsize check again
                // following can be skipped, is tested below
                //if (jsonData.equals("")) {
                //    jsonData = "[]";
                //}
                parent = null;

                try {
                    parent = new JSONArray(jsonData);
                } catch (JSONException e) {
                    e.printStackTrace();
                    jsonData = "[]";
                }
                // Parse the local json file contents and add these to ArrayList
                addItems();
                getScheduledAPODs();        // NEW: first add any scheduled apods
                getLatestAPOD();
            } else {
                // It looks like we do not have a local json file yet - FIRST LAUNCH most likely
                // TODO: later offer a restore option from sd card...
                Intent texSizeIntent = new Intent(this, TexSizeActivity.class);
                startActivityForResult(texSizeIntent, GL_MAX_TEX_SIZE_QUERY);
                devInfo.setGlMaxTextureSize(maxTextureSize);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("INITIAL_LAUNCH", true);
                editor.apply();
                parent = new JSONArray();
                // at initial run, do a network test which does not fall into DNS wait...
                long conntime = utils.testSocketConnect(2000);
                if (conntime == 2000) {
                    Bundle fragArguments = new Bundle();
                    fragArguments.putString("TITLE",
                            "Welcome to FunInSpace");
                    utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                            "INITIAL_LAUNCH - testSocketConnect TIMEOUT (" + conntime + "ms)");
                    fragArguments.putString("MESSAGE",
                            getString(R.string.dropbox_init_no_network));
                    fragArguments.putString("NEG",
                            getString(R.string.hfcm_ok));
                    FragmentManager fm = getSupportFragmentManager();
                    confirmDialog confirmdlg = new confirmDialog();
                    confirmdlg.setArguments(fragArguments);
                    confirmdlg.show(fm, "INITLAUNCH");
                } else {
                    getLatestAPOD();
                }
            }
            // JUST A CONVERSION UTILITY USED DURING DEVELOPMENT - CONVERTED EPOCH VALUES INTO A NEW
            // JSON FILE FOR UPLOAD TO DROPBOX - NEW TIMEZONE HANDLING NEEDS THIS CHANGE
            //updateEpochsInJsonDEVEL();
        }

        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    tts.setLanguage(Locale.UK);
                }
            }
        });

        utils.setWPShuffleCandidates(getApplicationContext(), myList);

        // Adding our first test service here for JobScheduler testing
        // Fails in Android 4.1 with
        // java.lang.NoClassDefFoundError: de.herb64.funinspace.services.shuffleJobService
        //     at de.herb64.funinspace.MainActivity.onCreate(MainActivity.java:845)
        // Web shows: multidex enable as solution
        // https://stackoverflow.com/questions/31829350/app-crashes-with-noclassdeffounderror-only-on-android-4-x
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            serviceComponent = new ComponentName(this, shuffleJobService.class);
        }*/

        // Test for network changes broadcast receiver
        // https://developer.android.com/training/basics/network-ops/managing.html
        // TODO: better one receiver with multiple filters or multiple receivers with one filter?
        // the latter allows for multiple files, which do not grow too large and allow to deregister
        // single receivers, while others can remain active.
        IntentFilter netFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        netFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        networkReceiver = new NetworkBcastReceiver();
        registerReceiver(networkReceiver, netFilter);

        // Broadcast Receiver to handle events, mostly to update the UI immediately (e.g. wallpaper
        // symbol in thumbnail)
        // TODO - own class for receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BCAST_SHUFFLE);
        filter.addAction(BCAST_THUMB);
        //filter.addCategory();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BCAST_SHUFFLE:
                        String newwp = intent.getStringExtra("NEWWP");
                        Log.i("HFCM", "Broadcast received, new WP: " + newwp);
                        // TODO - better way than just iterating? at least some cut if met condition
                        // or change order of check - to avoid string comparison
                        for (spaceItem item : myList) {
                            if (item.getThumb().equals(newwp.replace("wp_", "th_"))) {
                                item.setWpFlag(WP_ACTIVE);
                            } else if (item.getWpFlag() == WP_ACTIVE) {
                                item.setWpFlag(WP_EXISTS);
                            }
                        }
                        adp.notifyDataSetChanged();
                        break;
                    case BCAST_THUMB:
                        String thname = intent.getStringExtra("THUMBNAIL");
                        Log.i("HFCM", "Broadcast received, THUMBFILE: " + thname);
                        // ugly code here...
                        /*int i = 0;
                        boolean match = false;
                        for (spaceItem item : myList) {
                            if (item.getThumb().equals(thname)) {
                                File thumbFile = new File(getApplicationContext().getFilesDir(), thname);
                                if (thumbFile.exists()) {
                                    Bitmap thumb = null;
                                    // we use this option to save some memory - experimental
                                    if (sharedPref.getBoolean("rgb565_thumbs", false)) {
                                        BitmapFactory.Options options = new BitmapFactory.Options();
                                        options.inPreferredConfig = Bitmap.Config.RGB_565;
                                        thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), options);
                                    } else {
                                        thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                                    }
                                    myList.get(i).setBmpThumb(thumb);
                                } else {
                                    myList.get(i).setBmpThumb(null);
                                }
                                myList.get(i).setThumbLoadingState(View.INVISIBLE);
                                adp.notifyDataSetChanged();
                                break;
                            }
                            i++;
                        }*/
                        break;
                    default:
                        break;
                }
            }
        };
        registerReceiver(receiver, filter);

        // Reschedule jobs, that are expected to be active but not running at app start
        // TODO rework and investigate
        // TODO: deal with reboot safety as well.. for now it is not set...
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (sharedPref.getBoolean("wallpaper_shuffle", false)) {
                JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                List<JobInfo> allPendingJobs = jobScheduler.getAllPendingJobs();
                boolean needReload = true;
                for (JobInfo pending : allPendingJobs) {
                    if (pending.getId() == JOB_ID_SHUFFLE) {
                        needReload = false;
                    }
                }
                if (needReload) {
                    new dialogDisplay(MainActivity.this,
                            "Wallpaper Shuffle has been terminated, although it should be active. Reactivating now...");
                    scheduleShuffle();
                    utils.logAppend(getApplicationContext(),
                            DEBUG_LOG,
                            "Shuffle schedule found inactive although enabled in settings, restarting...");
                }
            }
        }
        //utils.logAppend(getApplicationContext(), DEBUG_LOG, "Current active network type: " +
        //        utils.getActiveNetworkTypeName(getApplicationContext()));
        utils.getAllNetworksInfo(getApplicationContext());
        utils.logAppend(getApplicationContext(),
                DEBUG_LOG,
                "onCreate() finished... ",
                starttime);
    }

    @Override
    protected void onDestroy() {
        // https://stackoverflow.com/questions/18821481/what-is-the-correct-order-of-calling-superclass-methods-in-onpause-onstop-and-o
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
        if (networkReceiver != null) {
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
        Log.i("HFCM", "onDestroy()...........");
        super.onDestroy(); // after own code for destroy
    }

    /**
     * Test: starting our apod Service to be used with JobScheduler
     */
    @Override
    protected void onStart() {
        super.onStart(); // before own code for start
        Log.i("HFCM", "onStart()...........");
    }

    /**
     * Stop text to speech, if any active playback is running.
     */
    @Override
    protected void onStop() {
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
        Log.i("HFCM", "onStop()...........");
        super.onStop(); // after own code for stop
    }

    /**
     *
     */
    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * @param outState
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // 18.09.2017 TODO important for docu, but code had to be removed
        // https://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate
        // outState.putParcelableArrayList("myList", myList);
        // 03.10.2017 - remove that, because it causes Transaction too large error. Why this only
        // happens on Android 8.0 (Markus Hilger) is not clear, because there seems to be a limit
        // on 1 MB in general.
        // see also
        // https://stackoverflow.com/questions/33182309/passing-bitmap-to-another-activity-ends-in-runtimeexception
        // unfortunately, JSONarray does not implement Parcelable, so we cannot put this. But we
        // add the jsonData String, from which we then regenerate the JSONArray parent object
        outState.putString("jsonData",jsonData);
        outState.putInt("maxtexsize", maxTextureSize);
    }

    /*@Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        if (s.equals("item_order")) {
            new dialogDisplay(this, "Prefs changed");
        }
        // TODO: https://developer.android.com/guide/topics/ui/settings.html
        //       onResume/onPause: register/deregister listener!!
    }*/

    /**
     * Interface implementation for Rating Dialog result: return from DialogFragment to Activity
     * This is called, if Rating Dialog OK button has been pressed
     * @param rating    the rating value to be set
     * @param selected  the item index, for which the rating has to be set
     */
    @Override
    public void updateRating(int rating, ArrayList<Integer> selected) {
        HashSet<String> titles = new HashSet<>();
        boolean needShuffleListRefresh = false;
        for (int idx : selected) {
            myList.get(idx).setRating(rating);
            titles.add(myList.get(idx).getTitle());
            if (!needShuffleListRefresh && myList.get(idx).getWpFlag() == WP_EXISTS) {
                needShuffleListRefresh = true;
            }
        }
        if (needShuffleListRefresh) {
            utils.setWPShuffleCandidates(getApplicationContext(), myList);
        }

        adp.notifyDataSetChanged();
        JSONObject obj = null;
        JSONObject content = null;
        for (int i = 0; i < parent.length(); i++) {
            try {
                obj = parent.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, "Rating JSON Exception:\n" + e.getMessage());
            }
            // get the "Content" object, not yet checking type, currently only "APOD" is expected
            try {
                if (obj != null) {
                    content = obj.getJSONObject("Content");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, "Rating JSON Exception:\n" + e.getMessage());
            }
            // get the fields - we know, that the keys exist, because we have written this ourselves
            // ha, not always :( bad with 11.09.2017 - no lowres key present, because "url" missing
            // in json from NASA TODO 11.09.2017
            try {
                if (content != null) {
                    String strTitle = content.getString("Title");
                    if (titles.contains(strTitle)) {
                        content.put("Rating", rating);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, "Rating JSON Exception:\n" + e.getMessage());
            }
        }
        // Rewrite local json file
        utils.writeJson(getApplicationContext(), localJson, parent);
    }

    /*
     * GET APOD JSON INFOS FROM NASA. THIS STARTS ANOTHER THREAD TO LOAD THE LOWRES IMAGE
     * TODO: VERY IMPORTANT!!! bad asynctask use here, need to fix for memory leaks!!!
     * also in terms of splitting large code with inner classes into smaller segments, as with my spaceItemFilter (22.10.2017)
     * https://medium.com/freenet-engineering/memory-leaks-in-android-identify-treat-and-avoid-d0b1233acc8
     * making apodTask static to eliminate implicit reference does not allow access to apodItem
     * any more. So we need a constructor
     * https://stackoverflow.com/questions/10864853/when-exactly-is-it-leak-safe-to-use-anonymous-inner-classes
     * https://blog.androidcafe.in/android-memory-leak-part-1-context-85cebdc97ab3
     * http://simonvt.net/2014/04/17/asynctask-is-bad-and-you-should-feel-bad/
     */


    // TODO - return values from asynctask - seems to work with execute().get()  but it blocks!!! - search topic: wait for asynctask to complete
    // ++++   >>>  https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
    //        >>>  asyncLoad.java is one of the results of this - now still make sure about memory leaks!!!!
    // private class ImgLowresTask extends AsyncTask<String, String, Bitmap> {
    // .. 26.20.2017 - now also retired and replaced by asyncLoad with bitmap support


    /**
     * Implementation of interface ConfirmListener for class confirmDialog
     * @param button button (pos/neg/neutral)
     * @param tag TAG
     * @param o some object that may be returned
     */
    @Override
    public void processConfirmation(int button, String tag, Object o) {
        switch (button) {
            case DialogInterface.BUTTON_POSITIVE:
                switch (tag) {
                    case "WP":
                        String wpfile = myList.get((int)o).getThumb().replace("th_", "wp_");
                        Log.i("HFCM", "Reached confirmation: " + wpfile);
                        if (!wpfile.equals("")) {
                            // update index values
                            if (currentWallpaperIndex >= 0) {
                                myList.get(currentWallpaperIndex).setWpFlag(WP_EXISTS);
                            }
                            myList.get((int)o).setWpFlag(WP_ACTIVE);
                            currentWallpaperIndex = (int)o;
                            changeWallpaper(wpfile);
                        }
                        break;
                    case "INITLAUNCH":
                    case "DROPBOX_REFRESH":
                        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                                tag + " - Kick off asyncLoad: DROPBOX_REFRESH");
                        //new asyncLoad(MainActivity.this, "DROPBOX_INIT").execute(dPJ());
                        new asyncLoad(MainActivity.this, "DROPBOX_REFRESH").execute(dPJ());
                        break;

                    //case "DROPBOX_REFRESH":
                    //    new asyncLoad(MainActivity.this, "DROPBOX_REFRESH").execute(dPJ());
                    //    break;
                    default:
                        break;
                }

                break;
            case DialogInterface.BUTTON_NEGATIVE:
                switch (tag) {
                    case "WP":
                        // wp file was created, but not set as active - update ui !!
                        myList.get((int)o).setWpFlag(WP_EXISTS);
                        adp.notifyDataSetChanged();
                        utils.setWPShuffleCandidates(getApplicationContext(), myList);
                        break;
                    case "INITLAUNCH":
                        // TODO - task close does not yet work, if there comes some return from asyncload
                        //terminateApp();
                        break;
                    case "SHOW_LOG":
                        Log.i("HFCM", "Clear log");
                        File logFile = new File(getFilesDir(), MainActivity.DEBUG_LOG);
                        if (logFile.exists()) {
                            logFile.delete();
                        }
                        break;
                    default:
                        break;
                }
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                switch (tag) {
                    case "SHOW_LOG":
                        Log.i("HFCM", "Email to herbert");
                        emailLogFileToHerbert();
                        break;
                    default:
                        break;
                }

                break;
            default:
                break;
        }
    }

    /* N O T E:   T H I S   I S   N O T   A C T I V E
     * Listener for rating bar changes - useless for me, because I use "small" bars
     * style="@style/Widget.AppCompat.RatingBar"       > works
     * style="@style/Widget.AppCompat.RatingBar.Small" > fails
     * >> "small" versions are designed to ignore any interaction, see Android docs
     */
    /*private class ratingChangeListener implements RatingBar.OnRatingBarChangeListener {
        @Override
        public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
            Toast.makeText(MainActivity.this, "Have rating" + v, Toast.LENGTH_SHORT).show();
        }
    }*/

    /**
     * Listener for clicks on items in the list. This is registered in spaceAdapter.
     * The adapter sets a Tag, which can be read here and used for further actions.
     */
    private class thumbClickListener implements View.OnClickListener {
        /**
         * onClick() gets passed the view in which we store the URL to be opened in a TAG. This
         * happens in getView() in our adapter...
         * Note: cannot receive events from rating bar below thumb, because the "small" RatingBar
         *       does not support interaction at all.
         * passing multiple tags? we could just combine this in a single string of special format
         * see https://developer.android.com/training/basics/firstapp/starting-activity.html
         * @param view The view
         */
        @Override
        public void onClick(View view) {
            // if (view.getTag() instanceof View) {}  // TODO DOCU - tag can be a view as well
            int idx = (int) view.getTag();  // for "filtered" view - index into full list!
            // If a local copy for hires image exists, "misuse" hiresUrl to pass filename instead
            String hiresFileBase = myList.get(idx).getThumb().replace("th_", "");
            File hiresFile = new File(getApplicationContext().getFilesDir(), "hd_" + hiresFileBase);
            String hiresUrl;
            if (hiresFile.exists()) {
                hiresUrl = hiresFile.getAbsolutePath();
            } else {
                hiresUrl = myList.get(idx).getHires();
            }
            String media = myList.get(idx).getMedia();
            // Decide, if we want to proceed if no WiFi is active (local image load no problem)
            // TODO maybe exchange with utils getActiveNetworkType() for wifi check - but is ok!!
            if (sharedPref.getBoolean("wifi_switch", true)
                    //&& !devInfo.isWifiActive()
                    && !(utils.getActiveNetworkType(getApplicationContext()) == ConnectivityManager.TYPE_WIFI)
                    && hiresUrl.startsWith("http")) {
                new dialogDisplay(MainActivity.this, getString(R.string.hires_no_wifi), "No Wifi");
                return;
            }

            if (!utils.isNetworkConnected(getApplicationContext())) {
                new dialogDisplay(MainActivity.this,
                        getString(R.string.no_network_for_hd),
                        getString(R.string.no_network));
                return;
            }

            // get maximum allocatable heap mem at time of pressing the button
            int maxAlloc = devInfo.getMaxAllocatable();

            // Check our own media type, which has been set in createApodFromJson()
            switch (media) {
                case M_IMAGE:
                    Intent hiresIntent = new Intent(getApplication(), ImageActivity.class);
                    hiresIntent.putExtra("hiresurl", hiresUrl);
                    hiresIntent.putExtra("listIdx", idx);
                    hiresIntent.putExtra("maxAlloc", maxAlloc);
                    hiresIntent.putExtra("maxtexturesize", maxTextureSize);
                    hiresIntent.putExtra("wallpaper_quality",
                            sharedPref.getString("wallpaper_quality", "80"));
                    lastImage = myList.get(idx).getTitle();
                    hiresIntent.putExtra("imagename", hiresFileBase);
                    // forResult now ALWAYS to get logstring returned for debugging
                    startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                    break;
                case M_YOUTUBE:
                    String thumb = myList.get(idx).getThumb();
                    if (tts != null && tts.isSpeaking()) {
                        tts.stop();
                    }
                    // We get the ID from thumb name - hmmm, somewhat dirty ?
                    if (sharedPref.getBoolean("youtube_fullscreen", false)) {
                        playYouTubeFullScreen(thumb.replace("th_", "").replace(".jpg", ""));
                    } else {
                        playYouTube(thumb.replace("th_", "").replace(".jpg", ""));
                    }
                    break;
                case M_VIMEO:
                    if (tts != null && tts.isSpeaking()) {
                        tts.stop();
                    }
                    playVimeo(hiresUrl);
                    break;
                case M_MP4:
                    if (tts != null && tts.isSpeaking()) {
                        tts.stop();
                    }
                    playMP4(hiresUrl);
                    break;
                default:
                    new dialogDisplay(MainActivity.this, "Unknown media: " + media, "Warning");
                    break;
            }
        }
    }

    /**
     * Play a youtube video by id  - first quick shot for basic test, which uses the autoplay in
     * lightbox mode.
     * NOTE: this might be completely replaced by the fullscreen code...
     * @param id The String containing the YouTube Video ID
     */
    private void playYouTube(String id) {
        Intent youtube_intent = YouTubeStandalonePlayer.createVideoIntent(MainActivity.this,
                yT(),
                id,
                0,
                true,
                true);

        // try to use autoplay and lightbox mode (boolean parms to true)
        try {
            startActivity(youtube_intent);
        } catch(Exception e) {
            new dialogDisplay(this, getString(R.string.no_youtube),
                    getString(R.string.sorry));
        }
    }

    /**
     * Play a youtube video by id in fullscreen using YouTubePlayerSupportFragment
     * https://developers.google.com/youtube/android/player/reference/com/google/android/youtube/player/YouTubePlayerSupportFragment
     * TODO: needs much more testing (phone rotation, buttons press like fullscreen etc..)
     * @param id video id
     */
    private void playYouTubeFullScreen(String id) {
        FragmentManager fm = getSupportFragmentManager();
        YouTubeFragment ytFragment = (YouTubeFragment) fm.findFragmentByTag("YOUTUBE-FULLSCREEN");
        if (ytFragment == null) {
            ytFragment = new YouTubeFragment();
            // setArguments is the way to go, do NOT use non default constructor with fragments!
            Bundle fragArguments = new Bundle();
            fragArguments.putString("api_key", yT());
            fragArguments.putString("video_id", id);
            ytFragment.setArguments(fragArguments);
            fm.beginTransaction().add(ytFragment, "YOUTUBE-FULLSCREEN").commit();
        }
    }

    /**
     * Play a vimeo video based on the url passed as string. A lot of experiments have been done.
     * See lalatex document, chapter "Playing Vimeo videos". Quite some code and remarks have
     * been moved from this source code to that chapter.
     * @param url The video url to be played
     */
    private void playVimeo(String url) {
        Intent vimeoIntent = new Intent(this, VideoActivity.class);
        vimeoIntent.putExtra("vimeourl", url);
        startActivity(vimeoIntent);
        /* COMMENT
         * My code for testing with Vimeo API video objects. Actually, this has been abandoned,
         * because embedding iframes in WebView caused quite some trouble.. to be checked again.
         * For now, just use the NASA provided link in a webview. See lalatex doc for removed code
         */
    }

    /**
     * TODO: still issues with some type of videos
     * Play an MP4 stream from the given URL. This might cause real trouble, if having quicktime
     * video type as found with NASA mp4 on 13.11.2017. Other test videos play perfectly. Although
     * the NASA video also reports MIME type video/mp4, there seems to be some problems. The
     * NASA video reports 54Mbit/s bitrate! But this is not the only issue, also in local network,
     * it does not render ok.
     * @param url The video mp4 url to be played
     */
    private void playMP4(String url) {
        Intent mp4Intent = new Intent(MainActivity.this, MP4Activity.class);
        mp4Intent.putExtra("mp4url", url);
        startActivity(mp4Intent);

        // D O C U    S T U F F !!!
        // TODO: check https://developer.android.com/reference/android/media/MediaCodec.html
        /*
         * TEST: also starting nasa video via system intent does not work
         * Intent test = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
         * test.setDataAndType(Uri.parse(url), "video/mp4");
         * startActivity(test);
        */
        /*
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(Uri.parse(url).toString(), new HashMap<String, String>());
        Bitmap bmp = retriever.getFrameAtTime(0);  // TODO; other option to create thumbnail
        */

        /*
        ContentResolver cR = getApplicationContext().getContentResolver();
        String mime = cR.getType(Uri.parse(url));
        File test = new File("/data/data/de.herb64.funinspace/files/nasatest.json");
        String mime2 = cR.getType(Uri.fromFile(test));
        */

        /*
         * Trying a load with my asyncLoad class, which checks mime type of the stream:
         * new asyncLoad(MainActivity.this, "TTT").execute(url);
         * Called asyncLoad() with a single URL
         * URL: https://apod.nasa.gov/apod/image/1711/CometMachholz2017_SOHO_big.mp4,
         * Content type: video/mp4, Typemap info - NASA video reports video/mp4, which is ok.
        */
    }

    /**
     * Get results from Activities started with startActivityForResult()
     * 1. image Activity for hires size
     * 2. GL max texture size query at very first run
     * 3. Settings dialog
     * Returned resultCode = 0 (RESULT_CANCELED) after having rotated the phone while
     * displaying the image in hires ImageActivity
     * https://stackoverflow.com/questions/32803497/incorrect-activity-result-code-after-rotating-activity
     * same problem in above post - this was because activity was gone underneath
     * @param requestCode   request code
     * @param resultCode    result code
     * @param data          intent
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == HIRES_LOAD_REQUEST) {
            if (resultCode == RESULT_OK) {
                // Display a log dialog for image loader statistics (image memory / scaling)
                if (sharedPref.getBoolean("show_debug_infos", true)) {
                    new dialogDisplay(this, data.getStringExtra("logString"), lastImage);
                }

                // Get the wallpaper info from returned intent (returning bmp is bad - trx size)
                // String testsize = data.getStringExtra("sizeHires");
                //String hUrl = data.getStringExtra("hiresurl");
                int listidx = data.getIntExtra("lstIdx", 0);

                // If a new cache file has been written, we trigger a file cleanup.
                if (data.getBooleanExtra("new_hd_cached_file", false)) {
                    ArrayList<Integer> del = utils.cleanupFiles(getApplicationContext(),
                            myList,
                            MAX_HIRES_MB * 1024,
                            listidx);
                    for (int i : del) {
                        myList.get(i).setCached(false);
                    }
                    adp.notifyDataSetChanged();     // better call at end only once
                }

                // todo - is the following needed on each return?
                File hdFile = new File(getApplicationContext().getFilesDir(),
                        myList.get(listidx).getThumb().replace("th_", "hd_"));
                myList.get(listidx).setCached(hdFile.exists());

                // User dialog OK processConfirmation() kicks off changer thread later
                if (data.getStringExtra("wallpaperfile") != null) {
                    Bundle fragArguments = new Bundle();
                    //fragArguments.putString("RESULT", data.getStringExtra("wallpaperfile"));
                    fragArguments.putInt("IDX", listidx);
                    fragArguments.putString("TITLE",
                            getString(R.string.wp_confirm_dlg_title));
                    fragArguments.putString("MESSAGE",
                            getString(R.string.wp_confirm_dlg_msg,
                                    myList.get(listidx).getTitle()));
                    fragArguments.putString("POS", getString(R.string.wp_confirm_dlg_pos_button));
                    fragArguments.putString("NEG", getString(R.string.wp_confirm_dlg_neg_button));
                    FragmentManager fm = getSupportFragmentManager();
                    confirmDialog dlg = new confirmDialog();
                    dlg.setArguments(fragArguments);
                    // dlg.setTargetFragment(); // only if calling from fragment, not from activity!
                    dlg.show(fm, "WP");
                }

                /* SKIP THIS, WE ALREADY GET HIRES IMAGE WIDTH/HEIGHT AT APOD LOAD TIME NOW
                if (!(myList.get(listidx).getHiSize().equals(testsize) ||
                        testsize.equals("no-change"))) {
                    //return;     // no action needed if hires size already in data
                    // TODO DOCU MetaDataRetriever  did not work for jpg in my tests (only video)
                    try {
                        // data in json is reverse ordered as in list
                        JSONObject content = parent.getJSONObject(parent.length() - 1 - listidx).
                                getJSONObject("Content");
                        Log.i("HFCM", "HIRES_LOAD: index=" + listidx +
                                ", list: " + myList.get(listidx).getTitle() +
                                ", json: " + content.getString("Title"));
                        content.put("HiSize", testsize);
                        utils.writeJson(getApplicationContext(), localJson, parent);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e("HFCM", e.toString());
                    }
                }
                myList.get(listidx).setHiSize(testsize);*/
                adp.notifyDataSetChanged();
            } else if (resultCode == RESULT_CANCELED) {
                Log.w("HFCM", "Returning from imageactivity with RESULT_CANCELLED");
                utils.logAppend(getApplicationContext(),
                        MainActivity.DEBUG_LOG,
                        "HIRES_LOAD > " + lastImage + ": " + data.getStringExtra("logString"));
            }
        } else if (requestCode == GL_MAX_TEX_SIZE_QUERY) {
            if (resultCode == RESULT_OK) {
                maxTextureSize = data.getIntExtra("maxTextureSize",0);
                SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = shPref.edit();
                editor.putInt("maxtexsize", maxTextureSize);
                editor.apply(); // apply is recommended by inspection instead of commit()
            }
        } else if (requestCode == SETTINGS_REQUEST) {
            if (resultCode == RESULT_OK) {
                // This gets called after the preferences Activity has been closed. While the
                // onSharedPreferenceChangeListener immediately gets called when a switch is changed
                // this one allows to react after user has closed the dialog. Toggling of a switch
                // might result in trouble, if that switch would trigger long run tasks in bg.
                // BASIC IDEA: have the change listener just keep a HashSet of preference
                // HERE: do any actions that need immediate reaction on changed settings, e.g.
                //       e.g. ordering of elements in list...
                // Check for any changed preference items, which have been marked by the listener.
                if (thumbQualityChanged) {
                    //SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(this);
                    //Log.i("HFCM", "Low quality thumbs enabled: " + shPref.getBoolean("rgb565_thumbs", false));
                    Log.i("HFCM", "Low quality thumbs enabled: " + sharedPref.getBoolean("rgb565_thumbs", false));
                    thumbQualityChanged = false;
                    myList.clear();
                    addItems();
                    adp.notifyDataSetChanged();
                }
                if (dateFormatChanged) {
                    dateFormatChanged = false;
                    // SimpleDateFormat object does not allow to CHANGE format string - recreate
                    String fmt = sharedPref.getString("date_format", getString(R.string.df_dd_mm_yyyy));
                    formatter = new SimpleDateFormat(fmt, loc); // "dd. MMMM yyyy"
                    formatter.setTimeZone(tzNASA);
                    formatter.setCalendar(cNASA);
                    adp.notifyDataSetChanged();
                }
                if (wpShuffleChanged) {
                    wpShuffleChanged = false;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        boolean shuffle = sharedPref.getBoolean("wallpaper_shuffle", false);
                        Log.i("HFCM", "WP Shuffle has changed to " + shuffle);
                        if (shuffle) {
                            scheduleShuffle();
                            utils.logAppend(getApplicationContext(),
                                    DEBUG_LOG,
                                    "Enabled wallpaper shuffle in settings");
                            new dialogDisplay(MainActivity.this,
                                    getString(R.string.wp_shuffle_enabled), "DEBUG ONLY!");
                        } else {
                            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                            scheduler.cancel(JOB_ID_SHUFFLE);
                            utils.logAppend(getApplicationContext(),
                                    DEBUG_LOG,
                                    "Disabled wallpaper shuffle in settings");
                            File shufflelog= new File(getApplicationContext().getFilesDir(),
                                    DEBUG_LOG);
                            String loginfo = "No logfile data available";
                            if (shufflelog.exists()) {
                                loginfo = "\nFunInSpace log data:\nYou may send this to Herbert using  the email function.\n";
                                loginfo += utils.readf(getApplicationContext(), DEBUG_LOG);
                            }
                            new dialogDisplay(MainActivity.this, loginfo, "Shuffle Job cancelled", 10f);
                        }
                    } else {
                        new dialogDisplay(MainActivity.this,
                                "Not yet implemented for Versions below 5 (Lollipop)", "DEBUG ONLY!");
                    }
                }
            }
        }
    }

    /**
     * Listener for changed preferences. This is called after each single change IMMEDIATELY!
     * This means, if changing a preference that might trigger a more expensive action, it could
     * get bad if user toggles that switch and in the end closes the dialog without any effective
     * change.
     * The onActivityResult() method reacting on SETTINGS_REQUEST is called AFTER the settings
     * dialog activity is CLOSED and might be a better place to handle REAL changes. The following
     * code solves this by using "change flags", checked in onActivityResult() later on.
     * IMPORTANT: Need to have set the "Changed" flags to TRUE, if no shared prefs exist, because
     * this listener gets called when first opening the preferences after installation. This is
     * done on sharedPreferences preparation in onCreate()
     */
    SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener = new
            SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String changed) {
                    if (changed.equals("rgb565_thumbs")) {
                        thumbQualityChanged ^= true;
                    }
                    if (changed.equals("date_format")) {
                        dateFormatChanged ^= true;
                    }
                    if (changed.equals("wallpaper_shuffle")) {
                        wpShuffleChanged ^= true;
                    }
                    if (changed.equals("full_search")) {
                        // this one: just set on each single toggle
                        adp.setFullSearch(sharedPref.getBoolean("full_search", false));
                    }
                    if (changed.equals("wallpaper_quality")) {
                        Log.i("HFCM", "Changed wall paper quality to " +
                                sharedPref.getString("wallpaper_quality", "80"));
                    }
                }
            };

    /**
     * The options menu is the primary Application menu. Do not confuse with "settings" dialog.
     * It is called during startup of the activity once.
     * Note the code to make icons visible in overflow menu.
     * TODO: document how to make icons for search AND filter disappear, if any of these actions
     * is actually expanded. Otherwise, for example, filter could be clicked, while search action
     * was expaned, getting bad overlay of graphics... -> SupportMenuItem!!!
     * @param menu the menu item
     * @return boolean return value
     */
    @SuppressLint("RestrictedApi")
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // Make menu icons in overflow menu visible as well
        // https://stackoverflow.com/questions/18374183/how-to-show-icons-in-overflow-menu-in-actionbar
        // TODO: how to do that for contextual action bar overflow menu as well?
        if(menu instanceof MenuBuilder){
            MenuBuilder m = (MenuBuilder) menu;
            //noinspection RestrictedApi
            m.setOptionalIconsVisible(true);
        }

        // Test menu for apod scheduler debugging
        JobScheduler jobScheduler = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            List<JobInfo> allPendingJobs = jobScheduler.getAllPendingJobs();
            MenuItem scheditem = menu.findItem(R.id.action_schedule_apod_json_load);
            scheditem.setTitle("Start Schedule APOD (Debug)");
            for (JobInfo pending : allPendingJobs) {
                if (pending.getId() == JOB_ID_APOD) {
                    scheditem.setTitle("Stop Schedule APOD (Debug)");
                    continue;
                }
            }
        }

        // 20.11.2017 Switch MenuItem to SupportMenuItem and use noinspection RestrictedApi  TODO DOCU
        // to allow for registering expand listeners for action view
        final SupportMenuItem searchItem = (SupportMenuItem) menu.findItem(R.id.action_search);
        //noinspection RestrictedApi
        SearchView sv = (SearchView) searchItem.getActionView();
        // on close listener does not work at all - see also discussions on web. I now use the
        // current search string length = 0 to detect, that the search is closed and that the list
        // is not filtered again.
        // Todo: this can be solved by using the actionexpand listeners instead!!!                                                                          !!!!!!!!!!!!!
        // Todo: indeed, we need a reset of our filter anyway!
        /*sv.setOnCloseListener(new SearchView.OnCloseListener() {
            @Override
            public boolean onClose() {
            }
        });*/
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                if (s.length() == 0) {
                    adp.cleanMap();
                }
                adp.getFilter().filter(s);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.length() == 0) {
                    adp.cleanMap();
                }
                adp.getFilter().filter(s);
                return false;
            }
        });

        // === FILTER FOR OTHER CONSTRAINTS ===

        // see also https://developer.android.com/training/appbar/action-views.html
        // TODO: actually, this looks like the way to go: actionprovider - hmmm, really?
        // https://developer.android.com/reference/android/support/v4/view/ActionProvider.html
        // https://www.grokkingandroid.com/adding-actionviews-to-your-actionbar/
        // https://gist.github.com/f2prateek/3982054
        final SupportMenuItem filterItem = (SupportMenuItem) menu.findItem(R.id.action_filter);
        // getting inspection error:
        // "SupportMenuItem.getActionView can only be called from within the same library group (groupId=com.android.support)
        //noinspection RestrictedApi
        FilterView fv = (FilterView) filterItem.getActionView();    // define in menu_main.xml

        // TODO !!!!!!!!!!!!!!! see lalatex doc for some more details and work out history
        // Problems adding expand listener (to disable/enable other menu items)
        // ....

        /* deprecated, see https://developer.android.com/reference/android/support/v4/view/MenuItemCompat.html
        MenuItemCompat.setOnActionExpandListener(filterItem,
                new MenuItemCompat.OnActionExpandListener() ....
                });
        FilterView fv =
                (FilterView) MenuItemCompat.getActionView(filterItem);*/

        // not implemented
        /*fv.setOnCloseListener(new FilterView.OnCloseListener() {
            @Override
            public boolean onClose() {
            }
        });*/

        fv.setOnQueryConstraintListener(new FilterView.OnQueryConstraintListener() {
            @Override
            public boolean onQueryConstraintSubmit(String s) {
                if (s.endsWith("0:0:0:0:0")) {
                    adp.cleanMap();
                }
                adp.getFilter().filter(s);
                return false;
            }

            @Override
            public boolean onQueryConstraintChange(String s) {
                if (s.endsWith("0:0:0:0:0")) {
                    adp.cleanMap();
                }
                adp.getFilter().filter(s);
                return false;
            }
        });

        // We either use a text search OR a filter (e.g. by video). The respectively other option
        // needs to be removed from the menu items to avoid bad overlays. We can achieve this by
        // registering a listener to the expansion of either type. This was quite a bit annoying to
        // find out, how to get this work.

        // Make "textsearch" menu item unavailable, if "filter" is currently used
        //noinspection RestrictedApi
        filterItem.setSupportOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                searchItem.setVisible(false);
                // TODO - what about inactivating other items as well (those in bar)
                //menu.findItem(R.id.action_settings).setVisible(false);
                // this just makes the next from overflow to appear... not saving space
                // well, we could overwrite the complete bar... but GROUPS could be used (removegroup, set group visible...)
                return true;
            }
            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                searchItem.setVisible(true);
                //adp.cleanMap();
                //adp.getFilter().filter("");                                  // TODO RE-TEST
                return true;
            }
        });

        // Make "filter" menu item unavailable, if "textsearch" is currently used
        //noinspection RestrictedApi
        searchItem.setSupportOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem menuItem) {
                filterItem.setVisible(false);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem menuItem) {
                filterItem.setVisible(true);
                //adp.cleanMap();
                return true;
            }
        });
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        return super.onCreateOptionsMenu(menu);
        //return true;
    }

    /**
     * This code handles selections from the menu bar. Contents are defined in menu_main.xml.
     * Note, that action_search is defined in menu_main.xml as well, with following parameters:
     * app:showAsAction="ifRoom|collapseActionView"
     * app:actionViewClass="android.support.v7.widget.SearchView"
     * This is handled by actionViewClass
     * Same applies to the new class "FilterView", that has been created on 18.11.2017
     * @param item  the menuitem that has been selected
     * @return      return true, if we handled the event, else false to forward
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivityForResult(settingsIntent, SETTINGS_REQUEST);
            return true;
        }
        if (id == R.id.action_debuginfo) {
            new dialogDisplay(this, devInfo.getDeviceInfo() +
                    devInfo.getActMgrMemoryInfo(true) +
                    devInfo.getGLInfo() +
                    devInfo.getNetworkInfo(true));
            return true;
        }
        if (id == R.id.action_xfer) {
            // File Transfer: do not open socket in main thread :)
            FragmentManager fm = getSupportFragmentManager();
            fileTransferDialog dlg = new fileTransferDialog();
            dlg.show(fm, "XFERTAG");
            return true;
        }
        if (id == R.id.action_help) {
            new dialogDisplay(MainActivity.this, "Help page not yet available, to be done hopefully soon...", "TODO");
            return true;
        }
        if (id == R.id.action_about) {
            new dialogDisplay(MainActivity.this, "Version: " + ABOUT_VERSION +
                    "\nTODO credits:\nflaticons.com for movie_other_64.png\n\n" +
                    getString(R.string.credits_first_testers) +
                    utils.getFileStats(getApplicationContext(), loc) +
                    "\nLimit: " + MAX_HIRES_MB + "MB (no active cleanup yet)",
                    "Infos (in development)");
            return true;
        }
        if (id == R.id.action_mail) {
            emailLogFileToHerbert();
            return true;
        }
        if (id == R.id.action_showlog) {
            File shufflelog= new File(getApplicationContext().getFilesDir(),
                    DEBUG_LOG);
            Bundle fragArguments = new Bundle();
            String loginfo = "No logfile data available";
            if (shufflelog.exists()) {
                loginfo = "\nFunInSpace log data:\nYou may send this to Herbert using the email function.\n";
                loginfo += utils.readf(getApplicationContext(), DEBUG_LOG);
                fragArguments.putString("NEG", "CLEAR");
                fragArguments.putString("NEU", "EMAIL");
            }
            fragArguments.putString("TITLE", "FunInSpace debug log");
            fragArguments.putString("MESSAGE", loginfo);
            fragArguments.putString("POS", getString(R.string.hfcm_ok));
            fragArguments.putFloat("MSGSIZE", 10f);
            FragmentManager fm = getSupportFragmentManager();
            confirmDialog dlg = new confirmDialog();
            dlg.setArguments(fragArguments);
            dlg.show(fm, "SHOW_LOG");
            return true;
        }
        if (id == R.id.dropbox_sync) {
            // Refresh metadata with dropbox. This allows to sync personal contents with dropbox
            // comment for old code in lalatex
            Bundle fragArguments = new Bundle();
            fragArguments.putString("TITLE",
                    getString(R.string.refresh_dropbox_title));
            //fragArguments.putInt("ICON_ID", R.drawable.vimeo_icon); // just a test for icon
            fragArguments.putString("MESSAGE",
                    getString(R.string.refresh_dropbox_message));
            fragArguments.putString("POS", getString(R.string.hfcm_yes));
            fragArguments.putString("NEG", getString(R.string.hfcm_no));
            FragmentManager fm = getSupportFragmentManager();
            confirmDialog dlg = new confirmDialog();
            dlg.setArguments(fragArguments);
            // dlg.setTargetFragment(); // only if calling from fragment, not from activity!
            dlg.show(fm, "DROPBOX_REFRESH");
            return true;
        }
        if (id == R.id.restore_wallpaper) {
            new dialogDisplay(MainActivity.this,
                    getString(R.string.dlg_revert_wp),
                    getString(R.string.dlg_title_info));
            changeWallpaper("");
        }
        if (id == R.id.action_search_apod) {
            new dialogDisplay(MainActivity.this,
                    "Searching the NASA APOD Archive is not yet available.", "TODO");
        }
        if (id == R.id.action_schedule_apod_json_load) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                List<JobInfo> allPendingJobs = scheduler.getAllPendingJobs();
                for (JobInfo pending : allPendingJobs) {
                    if (pending.getId() == JOB_ID_APOD) {
                        // we are in pending state, so this means that this action should cancel
                        // this job
                        scheduler.cancel(JOB_ID_APOD);
                        utils.logAppend(getApplicationContext(),
                                DEBUG_LOG,
                                "Cancelled APOD Load scheduler");
                        item.setTitle("Start Schedule APOD (Debug)");
                        return true;
                    }
                }
                // Reaching this point, job id was not in the pending job list, so start it
                scheduleApod();
                utils.logAppend(getApplicationContext(),
                        DEBUG_LOG,
                        "Started APOD Load scheduler");
                item.setTitle("Stop Schedule APOD (Debug)");
            } else {
                new dialogDisplay(MainActivity.this,
                        "Scheduling not yet implemented for Versions below 5 (Lollipop)", "DEBUG ONLY!");
            }
        }
        // if (id == R.id.action_filter)  //no longer needed
        // TIP: calling 'return super.onOptionsItemSelected(item);' made menu icons disappear after
        //      using overflow menu while having the SearchView open - this was really nasty
        return false;
    }

    // TODO - memory trim
    // https://developer.android.com/topic/performance/memory.html
    /*@Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }*/

    /**
     * Fill ArrayList of spaceItems with contents described in local JSON object
     */
    public void addItems() {
        JSONObject content = null;
        String strTitle = "";
        long dateTime = 0;
        String strCopyright = "";
        String strExplanation = "";
        String strLowres = "";
        String strHires = "";
        String strThumb = "";
        int rating = 0;
        String strMedia = "";
        String strHiSize = "";
        String strLowSize = "";

        // Get current wallpaper filename, if any has been set (shared_prefs/MainActivity.xml)
        //SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
        String wpFileCurrent = sharedPref.getString("CURRENT_WALLPAPER_FILE", "");

        int count = 0;
        for (int i = parent.length()-1; i >=0 ; i--)
        {
            try {
                // we do not check "Type" field here, only APOD is present now
                content = parent.getJSONObject(i).getJSONObject("Content");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            // 11.09.2017 - no lowres key present, because "url" missing in NASA json TODO recheck
            try {
                strTitle = content.getString("Title");
                dateTime = content.getLong("DateTime");
                // TODO - revisit: long copyright string (2017-09-11) and newline removal
                // images... Remove later and only use the code already in new apod loading
                strCopyright = content.getString("Copyright").
                        replaceAll(System.getProperty("line.separator"), " ");
                strExplanation = content.getString("Explanation");
                strLowres = content.getString("Lowres");
                strHires = content.getString("Hires");
                strThumb = content.getString("Thumb");
                rating = content.getInt("Rating");
                strMedia = content.getString("Media");
                strHiSize = content.getString("HiSize");
                strLowSize = content.getString("LowSize");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // add info to our list of spaceItems
            spaceItem newitem = new spaceItem();
            newitem.setTitle(strTitle);
            newitem.setDateTime(dateTime);
            newitem.setCopyright(strCopyright);
            newitem.setExplanation(strExplanation);
            newitem.setLowres(strLowres);
            newitem.setHires(strHires);
            newitem.setThumb(strThumb); // this is just the thumb image filename!
            newitem.setRating(rating);
            newitem.setMedia(strMedia);
            newitem.setHiSize(strHiSize);
            newitem.setLowSize(strLowSize);
            newitem.setMaxLines(MAX_ELLIPSED_LINES); // # of explanation lines in ellipsized view
            // Load the corresponding bitmap for thumbail from th_xxx.jpg and set into
            // the list item. If the file is not found, set bitmap to null.
            File thumbFile = new File(getApplicationContext().getFilesDir(), strThumb);
            if (thumbFile.exists()) {
                Bitmap thumb = null;
                // we use this option to save some memory - experimental
                if (sharedPref.getBoolean("rgb565_thumbs", false)) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath(), options);
                } else {
                    thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                }
                newitem.setBmpThumb(thumb);
            } else {
                newitem.setBmpThumb(null);
            }
            // Wallpaper filename is just derived from thumb filename, no extra storage space used
            String wpName = strThumb.replace("th_", "wp_");
            File wpFile = new File(getApplicationContext().getFilesDir(), wpName);
            if (wpFile.exists()) {
                if (wpName.equals(wpFileCurrent)) {
                    //Log.w("HFCM", "Wallpaper file " + strThumb.replace("th_", "wp_") + " found AS ACTIVE");
                    newitem.setWpFlag(WP_ACTIVE);
                    currentWallpaperIndex = count;
                } else {
                    //Log.w("HFCM", "Wallpaper file " + strThumb.replace("th_", "wp_") + " found");
                    newitem.setWpFlag(WP_EXISTS);
                }
            } else {
                newitem.setWpFlag(WP_NONE);
            }
            // Hires cache image filename derived from thumb filename, th_ > hd_
            String hdName = strThumb.replace("th_", "hd_");
            File hdFile = new File(getApplicationContext().getFilesDir(), hdName);
            newitem.setCached(hdFile.exists());
            myList.add(newitem);
            count++;
        }
    }

    /**
     * After filling the array list with all space items, this function is called to check for
     * any missing data. It does not only get missing thumbs, but does:
     * 1. get hires size information (for images)
     * 2. get vimeo infos thumbnail url / duration (for vimeo videos)
     * 3. get thumbnail image (in any case)
     * The function triggers a chain of async operations, depending on type of item.
     * MediaMetadataRetriever might also be able to retrieve an image from a stream, but it is
     * better and faster to retrieve the "offical" thumb...
     * TODO: check parallel execution option... (executeOnExectuor...)
     */
    public void getMissingApodInfos() {
        int count = 0;
        for(int i=0; i<myList.size(); i++) {
            if (myList.get(i).getBmpThumb() == null) {      // TODO change with glide implementation
                count++;
                unfinishedApods++;
                myList.get(i).setThumbLoadingState(View.VISIBLE);
                String sMediaType = myList.get(i).getMedia();
                String sHiresUrl = myList.get(i).getHires();
                String imgUrl = myList.get(i).getLowres();
                String imgSize = myList.get(i).getHiSize();
                if (sMediaType.equals(M_VIMEO) && imgUrl.isEmpty()) {
                    // Query vimeo thumbnail URL via oembed API
                    Log.i("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG VIM_" + i + ": " + sHiresUrl);
                    new asyncLoad(MainActivity.this, "VIM_" + i).
                            execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
                } else if (sMediaType.equals(M_IMAGE) && imgSize.isEmpty()) {
                    Log.i("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG SIZE_" + i + ": " + sHiresUrl);
                    new asyncLoad(MainActivity.this, "SIZE_" + i).execute(sHiresUrl);
                } else if (sMediaType.equals(M_MP4)) {
                    Log.i("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG DUR_" + i + ": " + sHiresUrl);
                    new asyncLoad(MainActivity.this, "DUR_" + i).execute(sHiresUrl);
                } else {
                    if (imgUrl.isEmpty()) {
                        myList.get(i).setThumbLoadingState(View.INVISIBLE);
                        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                                "getMissingApodInfos() - missing lowres URL");
                        // TODO: keep null image thumb (black) or add missing image or try to use hires to create a thumb...
                        //       this could be done while loading hires first... think about options
                        continue;
                    }
                    Log.i("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG THUMB_" + i + ": " + imgUrl);
                    new asyncLoad(MainActivity.this, "THUMB_" + i).execute(imgUrl);
                }
            }
        }
        if (count > 0) {
            Toast.makeText(MainActivity.this, getString(R.string.load_miss_thumbs, count),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * TODO: must not be used while thumbs are still loaded in bg... check!!
     * Resync local json data with dropbox json data. A merge is done so that
     * - Existing local items on the device are not deleted or changed
     * - Items found on dropbox which do not exist in local array are copied only if
     *   they have not been previously deleted by user explicitly. These are typically missed APOD
     *   images (app not used for some time)
     * - A full reload may be forced by option - this will reload even deleted items
     * - Items keep their order by timestamp
     * @param dropbox The JSON Array from dropbox, that has been loaded
     * @param forceFull if set to true, this forces to reload even previously deleted images
     */
    private void resyncWithDropbox(JSONArray dropbox, boolean forceFull) {
        Set<String> deleted_items= sharedPref.getStringSet("DELETED_ITEMS", new HashSet<String>());
        Set<String> dropbox_items = new HashSet<>();
        JSONArray newjson = new JSONArray();
        int db_skip = 0;
        int db_add = 0;
        int l_add = 0;

        for (int i = 0; i < dropbox.length(); i++) {
            try {
                String title = dropbox.getJSONObject(i).getJSONObject("Content").getString("Title");
                dropbox_items.add(title);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        int dbidx = 0;
        for (int lidx = 0; lidx < parent.length(); lidx++) {
            try {
                JSONObject lobj = parent.getJSONObject(lidx);
                JSONObject lcontent = lobj.getJSONObject("Content");
                String ltitle = lcontent.getString("Title");
                long lepoch = lcontent.getLong("DateTime");
                String dbtitle = "";
                long dbepoch = 0;
                // Dropbox items done, only local items remain to be added to new json array
                if (dbidx < dropbox.length()) {
                    JSONObject dbobj = dropbox.getJSONObject(dbidx);
                    JSONObject dbcontent = dbobj.getJSONObject("Content");
                    dbtitle = dbcontent.getString("Title");
                    dbepoch = dbcontent.getLong("DateTime");
                } else {
                    Log.i("HFCM", "DB end - adding local '" + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    continue;
                }
                // Dropbox matches local title - add into new json array
                if (ltitle.equals(dbtitle)) {
                    Log.i("HFCM", "DB idx: " + dbidx + " - Adding local (A): " + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    dbidx++;
                } else if (!dropbox_items.contains(ltitle)) {
                    Log.i("HFCM", "Info - DB idx: " + dbidx + " - Current local title '" + ltitle + "' not on dropbox");
                    while (dbepoch <= lepoch) {
                        if (!deleted_items.contains(dbtitle) || forceFull) {
                            Log.i("HFCM", "DB idx: " + dbidx + " - Adding from dropbox(B) '" + dbtitle + "'");
                            newjson.put(dropbox.getJSONObject(dbidx));
                            db_add++;
                        } else {
                            Log.i("HFCM", "DB idx: " + dbidx + " - Skipping deleted from dropbox (B): '" + dbtitle + "'");
                            db_skip++;
                        }
                        dbidx++;
                        if (dbidx == dropbox.length()) {
                            break;
                        }
                        dbepoch = dropbox.getJSONObject(dbidx).getJSONObject("Content").getLong("DateTime");
                        dbtitle = dropbox.getJSONObject(dbidx).getJSONObject("Content").getString("Title");
                    }
                    Log.i("HFCM", "DB idx: " + dbidx + " - Adding local (B): " + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    dbidx++;
                } else {
                    // local title is on dropbox, just fill any possible missing items
                    while (!dbtitle.equals(ltitle)) {
                        if (!deleted_items.contains(dbtitle) || forceFull) {
                            Log.i("HFCM", "DB idx: " + dbidx + " - Adding from dropbox(C) '" + dbtitle + "'");
                            newjson.put(dropbox.getJSONObject(dbidx));
                            db_add++;
                        } else {
                        Log.i("HFCM", "DB idx: " + dbidx + " - Skipping deleted from dropbox (C): '" + dbtitle + "'");
                        db_skip++;
                        }
                        dbidx++;
                        dbtitle = dropbox.getJSONObject(dbidx).getJSONObject("Content").getString("Title");
                    }
                    Log.i("HFCM", "DB idx: " + dbidx + " - Adding local (C): '" + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    dbidx++;
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", "resyncWithDropbox() - " + e.getMessage());
            }
        }

        utils.logAppend(getApplicationContext(),
                MainActivity.DEBUG_LOG,
                String.format(loc, "Dropbox resync: LOCAL ADD=%d, DB ADD=%d, DB SKIP=%d",
                        l_add, db_add, db_skip));

        // TODO how to handle the shared prefs DELETED_ITEMS info?

        // Activate the contents of the new json array
        parent = newjson;
        utils.writeJson(getApplicationContext(), localJson, parent);
        myList.clear();
        addItems();
        getMissingApodInfos();
        utils.cleanupFiles(getApplicationContext(), myList, MAX_HIRES_MB * 1024, -1);
        adp.notifyDataSetChanged();
    }

    /**
     * Get APODs, which might have been catched by scheduler daily run. These are inserted first
     * into the list. This is run BEFORE getting the daily apod and does not involve any network
     * and async processing.
     * The additional infos on thumbails, image size and (if applicable) vimeo thumbnail url
     * are added later, in function getMissingApodInfos()
     * TODO: integrate with getLatestAPOD
     */
    private void getScheduledAPODs() {
        unfinishedApods = 0;
        // There's no wildcard filter possible - subdirectories for less files per type ??
        File dir = new File(getApplicationContext().getFilesDir().getPath());
        String[] names = dir.list();
        for (String name : names) {
            if (name.startsWith(APOD_SCHED_PREFIX)) {
                spaceItem item = utils.createSpaceItemFromJsonFile(getApplicationContext(), name);
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "getScheduledAPODs() - '" + name + "'");
                if (!item.getTitle().isEmpty()) {
                    Log.i("HFCM", "getScheduledAPODs() - '" + item.getTitle() + "'");
                    utils.logAppend(getApplicationContext(), DEBUG_LOG,
                            "getScheduledAPODs() - '" + item.getTitle() + "'");
                    utils.insertSpaceItem(myList, item);
                } else {
                    utils.logAppend(getApplicationContext(), DEBUG_LOG,
                            "getScheduledAPODs() - missing item title: " + name);
                }
            }
        }
    }

    /**
     * Get the latest APOD. This is called on each start of the app AFTER getScheduledAPODs().
     * Check the age of the current latest image and avoid any NASA query, if it is already up to
     * date. The time to next apod is calculated, and if this is found to be larger than 0, we do
     * not need to load a new item from NASA.
     * TODO: integrate with getScheduledAPODs
     */
    private void getLatestAPOD() {
        long timeToNext = 0;

        if (!myList.isEmpty()) {
            // FIRST item in myList is latest available item. This is the LAST item in JSON.
            ArrayList<Long> epochs = utils.getNASAEpoch(myList.get(0).getDateTime());
            timeToNext = epochs.get(2);
        }
        if (timeToNext > 0) {
            Toast.makeText(MainActivity.this,
                    String.format(loc, getString(R.string.apod_already_loaded),
                            (float)timeToNext/(float)3600000),
                    Toast.LENGTH_LONG).show();
            // If no apod needs to be loaded, we are done with myList - trigger apod info completion
            // in case we have some infos stored by scheduler
            getMissingApodInfos();
        } else {
            if (sharedPref.getBoolean("get_apod", true)) {
                // Check for active connection: e.g. AVD with wifi connected on host but no external
                // access (DSL unplugged): Failure is NOT detected by isConnected() - this means,
                // we run into DNS timeouts - we catch them, but it takes 40 seconds (depending on
                // number of ip's resolved for the name) - If called at initial launch - very bad!
                //if (!utils.isNetworkConnected(getApplicationContext())) {
                long conntime = utils.testSocketConnect(2000);
                if (conntime == 2000) {
                    utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                            "getLatestAPOD() - testSocketConnect TIMEOUT (" + conntime + "ms)");
                    new dialogDisplay(MainActivity.this,
                            getString(R.string.no_network_for_apod),
                            getString(R.string.no_network));
                    return;
                } else {
                    utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                            "getLatestAPOD() - testSocketConnect OK (" + conntime + "ms)");
                }
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "getLatestAPOD() - Kick off asyncLoad: APOD_LOAD");
                new asyncLoad(MainActivity.this,
                        "APOD_LOAD",
                        sharedPref.getBoolean("enable_tls_pre_lollipop", true)).
                        execute(sharedPref.getBoolean("get_apod_simulate", false) ? dAS() : nS());
            } else {
                new dialogDisplay(this, getString(R.string.warn_apod_disable),
                        getString(R.string.reminder));
            }
        }
    }

    /**
     * This is the interface implementation of processFinish() for asyncLoad class. This is called
     * when the asyncLoad task has finished retrieving the requested data. The asyncLoad class is
     * currently used for different tasks.
     * Note: for parallel asynctask execution (executeonexecutor), see also my lalatex document...
     * @param status   Return status. This can either be
     *                 - HttpURLConnection.HTTP_x - http connection status, 200, 404 etc..
     *                 - aysncLoad.IOEXCEPTION or similar: connection problems BEFORE http..
     * @param tag      the tag string set by caller to identify the calling procedure. Some tags
     *                 include the list index: TAG_nnn, with nnn = index
     * @param output   the returned output (e.g. json string, bitmap, exception string)
     */
    @Override
    public void processFinish(int status, String tag, Object output) {
        // DOCU switch-case for String type since Java version 7, but not with String.startsWith()
        // Status returned from asyncload: everything not HTTP OK 200 is assumed to be bad
        if (status != HttpURLConnection.HTTP_OK) {
            Log.e("HFCM", "processFinish(), tag = " + tag + " > '" + output + "'");
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    tag + " > '" + output + "'");
        }

        if (tag.equals("APOD_LOAD")) {
            String h = (String)output;
            // TODO: test 4.1 with tls off - need to handle return code...
            if (h.startsWith("Connection")) {       // BAD
                new dialogDisplay(MainActivity.this, h + "\n" + getString(R.string.enable_tls), "NASA Connect");
                return;
            }
            if (utils.isJson(h) == utils.JSON_OBJ) {
                spaceItem item = utils.createSpaceItemFromJsonString(getApplicationContext(), (String) output);
                utils.insertSpaceItem(myList, item);

                // Add latest epoch into default shared prefs - used by scheduler service
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong("LATEST_APOD_EPOCH", item.getDateTime());
                editor.apply();

                getMissingApodInfos();
                adp.notifyDataSetChanged();

                // from old finalize - do we need it?
                Toast.makeText(MainActivity.this, R.string.apod_load,
                            Toast.LENGTH_LONG).show();
                myItemsLV.setSelection(0);


            } else {
                String first100 = h.substring(0, h.length() > 100 ? 100 : h.length());
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "APOD_LOAD: JSON exception: " + first100);
                new dialogDisplay(MainActivity.this,
                        "No JSON object received - possible captive portal?\nContent: '" +
                                first100 + "......'", "APOD_LOAD Debug");
                return;
            }

        } else if (tag.startsWith("THUMB_")) {
            int idx = Integer.parseInt(tag.replace("THUMB_",""));
            Log.i("HFCM", "processFinish(), tag = " + tag);
            if (output instanceof Bitmap) {
                myList.get(idx).setLowSize(String.valueOf(((Bitmap) output).getWidth()) + "x" +
                        String.valueOf(((Bitmap)output).getHeight()));
                // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
                //thumbFile = new File(getApplicationContext().getFilesDir(), wkItem.getThumb());
                Bitmap thumbnail = ThumbnailUtils.extractThumbnail((Bitmap) output, 120, 120);
                myList.get(idx).setBmpThumb(thumbnail);
                // Write the thumbnail as small jpeg file to internal storage
                utils.writeJPG(getApplicationContext(), myList.get(idx).getThumb(), thumbnail);
                myList.get(idx).setThumbLoadingState(View.INVISIBLE);
                adp.notifyDataSetChanged();

                unfinishedApods--;
                Log.i("HFCM", "processFinish() - remaining unfinished APODs: " + unfinishedApods);
                if (unfinishedApods == 0) {
                    parent = new JSONArray(); // other way to cleanup the array?
                    for (int i = myList.size()-1; i >= 0; i--) {
                        JSONObject apodObj = utils.createJsonObjectFromSpaceItem(myList.get(i));
                        parent.put(apodObj);
                    }
                    // TODO: write file content
                    utils.writeJson(getApplicationContext(), localJson, parent);

                    File dir = new File(getFilesDir().getPath());
                    String[] names = dir.list();
                    for (String name : names) {
                        if (name.startsWith(APOD_SCHED_PREFIX)) {
                            File todelete = new File(getFilesDir(), name);
                            Log.i("HFCM", "Deleting schduled apod: " + name);
                            if (!todelete.delete()) {
                                Log.e("HFCM", "Error deleting file: " + name);
                            }
                        }
                    }
                }

                // this only get's called ONCE, if just single apod is present
                if (sharedPref.getBoolean("INITIAL_LAUNCH", false)) {
                    initialLaunch();
                }
            } else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        tag + ": no Bitmap returned");
            }

        } else if (tag.startsWith("SIZE_")) {
            int idx = Integer.parseInt(tag.replace("SIZE_", ""));
            if (status == HttpURLConnection.HTTP_OK) {
                String size = (String) output;
                Log.i("HFCM", "processFinish(), tag = " + tag + ", SIZE = " + size);
                myList.get(idx).setHiSize(size);
            }
            String lowres = myList.get(idx).getLowres();
            Log.i("HFCM", "processFinish() - launching asyncLoad for TAG THUMB_" + idx + ": " + lowres);
            new asyncLoad(MainActivity.this, "THUMB_" + idx).execute(lowres);
            // TODO - good idea to refresh after size reload?
            adp.notifyDataSetChanged();

        } else if (tag.startsWith("DUR_")) {
            int idx = Integer.parseInt(tag.replace("DUR_", ""));
            if (status == HttpURLConnection.HTTP_OK) {
                String dur = (String) output;
                Log.i("HFCM", "processFinish(), tag = " + tag + ", DUR = " + dur);
                myList.get(idx).setHiSize(dur);
            }
            String lowres = myList.get(idx).getLowres();
            Log.i("HFCM", "processFinish() - launching asyncLoad for TAG THUMB_" + idx + ": " + lowres);
            new asyncLoad(MainActivity.this, "THUMB_" + idx).execute(lowres);
            // TODO - good idea to refresh after duration reload?
            adp.notifyDataSetChanged();

        } else if (tag.startsWith("VIM_")) {
            // String "output" contains a json object returned for the specified video url
            // We only use "video_id" and "thumbnail_url"
            // duration is also available, as well as a description.. and more..
            int idx = Integer.parseInt(tag.replace("VIM_", ""));
            Log.i("HFCM", "processFinish(), tag = VIM_" + idx);
            String thumbUrl = "n/a";
            String videoId = null;
            int duration = 0;
            try {
                JSONObject vimeoobj = new JSONObject((String) output);
                videoId = vimeoobj.getString("video_id");
                thumbUrl = vimeoobj.getString("thumbnail_url");
                duration = vimeoobj.getInt("duration");
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, getString(R.string.no_vimeo_thumb),
                        "VIMEO INFO JSON ERROR");
            }
            if (videoId != null) {
                myList.get(idx).setThumb("th_" + videoId + ".jpg");   // thumb filename
                myList.get(idx).setHiSize(String.valueOf(duration));
            }
            myList.get(idx).setLowres(thumbUrl);
            Log.i("HFCM", "processFinish() - launching asyncLoad for TAG THUMB_" + idx + ": " + thumbUrl);
            new asyncLoad(MainActivity.this, "THUMB_" + idx).execute(thumbUrl);
            // TODO - good idea to refresh after size reload?
            adp.notifyDataSetChanged();

        } else if (tag.equals("DROPBOX_REFRESH")) {
            // TODO - phone rotation and verify, if network checks are done / needed
            Object json;
            try {
                json = new JSONTokener((String) output).nextValue();
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            if (json != null && json instanceof JSONArray) {
                resyncWithDropbox((JSONArray) json,
                        sharedPref.getBoolean("force_full_dropbox_sync", false));
            }
        } else {
            new dialogDisplay(MainActivity.this, "Unknown Tag '" + tag + "' from processFinish()", "Info for Herbert");
        }
    }

    /**
     * Implementation for progress update for asyncLoad run with multiple urls
     * @param status status
     * @param tag tag
     * @param output output
     */
    @Override
    public void processProgressUpdate(int status, String tag, Object output) {
        switch (tag) {
            /*case "MISSING_THUMBS_RELOAD":
                Log.i("HFCM", "Returned progess update");
                break;*/
            default:
                new dialogDisplay(MainActivity.this, "Unknown Tag '" + tag + "' from processProgressUpdate()", "Info for Herbert");
                break;
        }
    }

    /**
     * Set wallpaper image to the given image. If the filename is empty, we revert to the wallpaper
     * image, that has been saved on very first setting of a FunInSpace wallpaper.
     * @param filename  JPG file to be set as wallpaper
     */
    public void changeWallpaper(String filename) {
        // https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/app/WallpaperManager.java
        // below: api23+ required
        String wpToSet = filename;
        //WallpaperManager wpm = (WallpaperManager)getSystemService(WallpaperManager.class);
        //wpm.getWallpaperId(FLAG...);
        WallpaperManager wpm = WallpaperManager.getInstance(getApplicationContext());

        // If empty filename is passed, we reset to the current wallpaper, that was present on the
        // device before setting our first apod image.
        if (filename.equals("")) {
            File cFile = new File(getApplicationContext().getFilesDir(), WP_CURRENT_BACKUP);
            if (cFile.exists()) {
                wpToSet = "w_current.jpg";
            } else {
                new dialogDisplay(MainActivity.this,
                        getString(R.string.wallpaper_nothing_to_revert), "Info");
                return;
            }
        } else {
            // Keep a backup of the current wallpaper if not yet present (only on first time change)
            // "builtin" drawable - needs api19+ (KITKAT)
            // "which" parameter - needs api24+ (NOUGAT)
            // note: in Android 4.1, we only get the "current" bitmap returned
            Drawable current = wpm.getDrawable();
            if (current != null) {
                File cFile = new File(getApplicationContext().getFilesDir(), WP_CURRENT_BACKUP);
                if (!cFile.exists()) {
                    Log.i("HFCM", "Saving current wallpaper bitmap");
                    utils.writeJPG(getApplicationContext(),
                            WP_CURRENT_BACKUP,
                            ((BitmapDrawable) current).getBitmap());
                    new dialogDisplay(MainActivity.this,
                            getString(R.string.wallpaper_backup_current), "Info");
                }
            } else {
                // actually, we should never reach this point...
                new dialogDisplay(MainActivity.this,
                        "No current wallpaper image could be found to save for later revert...");
                return;
            }
        }

        // Kick off thread which runs the wallpaper change in background
        wallPaperActivator wpact = new wallPaperActivator(getApplicationContext(), wpToSet);
        Thread activator = new Thread(wpact);
        Log.w("HFCM", "Starting wallpaper thread...");
        activator.start();  // of course, we do not join() :)

        // Store filename of current wallpaper into shared preferences
        //SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
        //SharedPreferences.Editor editor = shPref.edit();
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("CURRENT_WALLPAPER_FILE", wpToSet);
        editor.apply(); // apply is recommended by inspection instead of commit()
        // Refresh adapter (to update wp symbols on thumbnails)
        adp.notifyDataSetChanged();
        utils.setWPShuffleCandidates(getApplicationContext(), myList);

        // Get these as well, if present - actually, we do not need this...
        Drawable sysWall = null;
        Drawable lckWall = null;
        Drawable builtin = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            sysWall = wpm.getBuiltInDrawable(WallpaperManager.FLAG_SYSTEM);
            lckWall = wpm.getBuiltInDrawable(WallpaperManager.FLAG_LOCK);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                builtin = wpm.getBuiltInDrawable();
            }
        }
        if (sysWall != null) {
            utils.writeJPG(getApplicationContext(),
                    "w_system.jpg", ((BitmapDrawable)sysWall).getBitmap());
        }
        if (lckWall != null) {
            // this one was never encountered during my testing so far
            utils.writeJPG(getApplicationContext(),
                    "w_lock.jpg", ((BitmapDrawable)lckWall).getBitmap());
        }
        if (builtin != null) {
            utils.writeJPG(getApplicationContext(),
                    "w_builtin.jpg", ((BitmapDrawable)builtin).getBitmap());
        }
    }

    /**
     * Schedule the initial wallpaper shuffle. This one is run within a short deadline, just to
     * be able to see if it works.
     */
    private void scheduleShuffle() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, shuffleJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_SHUFFLE, serviceComponent);
            builder.setMinimumLatency(5000);
            builder.setOverrideDeadline(10000);
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", 1);
            builder.setExtras(extras);
            JobInfo jobInfo = builder.build();
            Log.i("HFCM", "SHUFFLE jobinfo: " + jobInfo.toString());
            JobScheduler sched = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            sched.schedule(jobInfo);
        }
    }

    /**
     * Schedule the background loading of daily APOD json metadata.
     */
    private void scheduleApod() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, apodJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_APOD, serviceComponent);
            builder.setMinimumLatency(5000);
            builder.setOverrideDeadline(10000);
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", 1);
            //extras.putString("URL", nS());
            extras.putString("URL", sharedPref.getBoolean("get_apod_simulate", false) ? dAS() : nS());
            // java.lang.IllegalAccessError: Method 'void android.os.BaseBundle.putBoolean(java.lang.String, boolean)' is inaccessible to class 'de.herb64.funinspace.MainActivity'
            //extras.putBoolean("PRE_LOLLOPOP_TLS", sharedPref.getBoolean("enable_tls_pre_lollipop", true));
            builder.setExtras(extras);
            JobInfo jobInfo = builder.build();
            Log.i("HFCM", "APOD jobinfo: " + jobInfo.toString());
            JobScheduler sched = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            sched.schedule(jobInfo);
        }
    }

    private void scheduleThumbLoader(String[] urls) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, thumbLoaderJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_THUMB, serviceComponent);
            builder.setMinimumLatency(2000);
            builder.setOverrideDeadline(4000);
            PersistableBundle extras = new PersistableBundle();
            extras.putStringArray("URLS", urls);
            builder.setExtras(extras);
            JobInfo jobInfo = builder.build();
            Log.i("HFCM", "THUMBLOADER jobinfo: " + jobInfo.toString());
            JobScheduler sched = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            sched.schedule(jobInfo);
        }
    }

    /**
     * Called) at end of the "APODchain", if the initial
     * launch flag is set to true in shared prefs "INITIAL_LAUNCH"
     */
    private void initialLaunch() {
        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                "initialLaunch()");
        Bundle fragArguments = new Bundle();
        fragArguments.putString("TITLE",
                "Welcome to FunInSpace");
        long connecttime = utils.testSocketConnect(1000);
        // we skip the actual test, just getting the info about connect time
        //if (connecttime < 1000) {
        //if (utils.isNetworkConnected(getApplicationContext())) {
        int type = utils.getActiveNetworkType(getApplicationContext());
        String tname = utils.getActiveNetworkTypeName(getApplicationContext());
        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                "initialLaunch() - network '" + tname + "' (" + connecttime + "ms)");
        switch (type) {
            case ConnectivityManager.TYPE_MOBILE:
                fragArguments.putString("MESSAGE",
                        getString(R.string.dropbox_init_mobile));
                fragArguments.putString("POS",
                        getString(R.string.hfcm_yes));
                fragArguments.putString("NEG",
                        getString(R.string.hfcm_no));
                break;
            case ConnectivityManager.TYPE_WIFI:
                fragArguments.putString("MESSAGE",
                        getString(R.string.dropbox_init_wifi));
                fragArguments.putString("POS",
                        getString(R.string.hfcm_proceed));
                fragArguments.putString("NEG",
                        getString(R.string.hfcm_cancel));
                break;
            default:
                // handle unknown network - e.g. bluetooth... should not be reached at all
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "Network '" + tname + "' cannot be used");
                fragArguments.putString("MESSAGE",
                        "Cannot use network " + utils.getActiveNetworkTypeName(getApplicationContext()));
                fragArguments.putString("NEG",
                        getString(R.string.hfcm_ok));
                break;
        }
        /*} else {
            // should not be reached...
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "NO network access at initialLaunch()");
            fragArguments.putString("MESSAGE",
                    getString(R.string.dropbox_init_no_network));
            fragArguments.putString("NEG",
                    getString(R.string.hfcm_ok));
        }*/
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("INITIAL_LAUNCH", false);
        editor.apply();
        FragmentManager fm = getSupportFragmentManager();
        confirmDialog confirmdlg = new confirmDialog();
        confirmdlg.setArguments(fragArguments);
        confirmdlg.show(fm, "INITLAUNCH");
    }

    public void terminateApp() {
        // see also
        // https://developer.android.com/guide/components/activities/tasks-and-back-stack.html
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        // from android docu about Intent: FLAG_ACTIVITY_CLEAR_TOP
        // If set, and the activity being launched is already running in the current task, then
        // instead of launching a new instance of that activity, all of the other activities on
        // top of it will be closed and this Intent will be delivered to the (now on top) old
        // activity as a new Intent.
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("EXIT", true);
        startActivity(intent);
    }

    /**
     * Send logfile via email to funinspace mail account
     */
    private void emailLogFileToHerbert() {
        // TODO AVD failures - seems to be known
        // https://stackoverflow.com/questions/27528236/mailto-android-unsupported-action-error
        //Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto",vE(), null));
        Intent i = new Intent(Intent.ACTION_SENDTO);  // ACTION_SEND - also shows whatsapp etc..
        // just info
        // i.setPackage("com.pkgname"); // see https://faq.whatsapp.com/en/android/28000012
        i.setData(Uri.parse("mailto:" + vE()));
        //i.setType("message/rfc822");    // had to be removed
        //String to[] = {"user@domain.com","user2@domain.com"};
        //i.putExtra(Intent.EXTRA_EMAIL, new String[]{"user@domain.com"});
        //String cc[] = {vKE() + "," + vHH()};
        //i.putExtra(Intent.EXTRA_EMAIL, cc);
        i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.test_mail_subject));
        i.putExtra(Intent.EXTRA_TEXT, getString(R.string.test_mail_text));

        // == ADDING A SINGLE FILE ATTACHMENT TO THE MAIL ==  // TODO multiple
        // E-mail apps do not have access to my storage - prohibited by Android security
        // - use external storage
        // - create a provider - this is what we do here - see lalatex docu
        // we just send out the
        //File attachment_file = new File(getApplicationContext().getFilesDir(), localJson);
        File attachment_file = new File(getApplicationContext().getFilesDir(), DEBUG_LOG);
        Uri contentUri = FileProvider.getUriForFile(MainActivity.this,
                "de.herb64.funinspace.fileprovider",
                attachment_file);
        if (attachment_file.exists()) {
            i.putExtra(Intent.EXTRA_STREAM, contentUri);
        }

        //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    // TODO check

        // Finding matching apps for the intent - see more details in lalatex docu
        // a) if (i.resolveActivity(getPackageManager()) != null)
        // b) try {startActivity(Intent.createChooser(i, "Send mail..."));}
        //    catch (android.content.ActivityNotFoundException ex) {}
        // c) List<ResolveInfo> pkgs = getPackageManager().queryIntentActivities(i, 0);

        List<ResolveInfo> pkgs = getPackageManager().queryIntentActivities(i, 0);   // flags ?
        if(pkgs.size() == 0) {
            new dialogDisplay(MainActivity.this,
                    getString(R.string.no_email_client));
        } else {
            for (ResolveInfo pkg : pkgs) {
                // see more infos in lalatex -
                // TODO: how to restrict grant to one selected app? / default app
                Log.i("HFCM", "Granting shared rights for package: " +
                        pkg.activityInfo.packageName);
                grantUriPermission(pkg.activityInfo.packageName,
                        contentUri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            startActivity(i);
        }
    }




    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////




    /**   DEVEL HELPER ONLY!!!
     * CHANGE IN EPOCH VALUE CALCULATION - JUST TO BE RUN ONCE ON A WELL PREAPARED JSON FILE
     * Helper function for updating epoch values - 15.11.2017 - need to change all values for stored
     * items to the new epoch value. Getting base infos in 'epoch-update' file, derived from NASA
     * https://apod.nasa.gov/apod/archivepix.html, in one line per entry, e.g.:
     * 2017 July 24: A Hybrid Solar Eclipse over Kenya
     * This function now iterates all items in the local json and rewrites epoch values based on the
     * information in 'epoch-update'
     */
    @SuppressWarnings("unused")
    public void updateEpochsInJsonDEVEL() {
        HashMap<String, Long> epochMap = new HashMap<>();
        String filecontents = utils.readf(getApplicationContext(),"epoch-update");
        if (filecontents == null) {
            return;
        }
        String[] contents = filecontents.split("\n");
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy MMM dd", loc);
        formatter.setTimeZone(tzNASA);
        formatter.setCalendar(cNASA);
        for (String line : contents) {
            String[] splt = line.split(":", 2);
            String datum = splt[0];
            String title = splt[1].trim();
            try {
                long epoch = formatter.parse(datum).getTime();
                epochMap.put(title, epoch);
                Log.i("HFCM", epoch + " (" + formatter.format(epoch) + ") >> " + title);
            } catch (ParseException e) {
                e.printStackTrace();
            }
        }
        JSONObject obj = null;
        JSONObject content = null;
        for (int i = 0; i < parent.length(); i++) {
            try {
                obj = parent.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, "Rating JSON Exception:\n" + e.getMessage());
            }
            try {
                if (obj != null) {
                    content = obj.getJSONObject("Content");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, "Rating JSON Exception:\n" + e.getMessage());
            }
            // update the values
            try {
                if (content != null) {
                    String strTitle = content.getString("Title");
                    if (epochMap.containsKey(strTitle)) {
                        long newEpoch = epochMap.get(strTitle);
                        Log.i("HFCM", "Exchanging: " + content.getLong("DateTime") +
                                " > " + newEpoch + " (" + strTitle + ")");
                        content.put("DateTime", newEpoch);
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.getMessage());
                new dialogDisplay(this, "Rating JSON Exception:\n" + e.getMessage());
            }
        }
        // Rewrite local json file
        utils.writeJson(getApplicationContext(), localJson, parent);
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  O L D    C O D E   F R A G M E N T S -- 24.11.2017 Cleanup - GIT Commit
    ////////////////////////////////////////////////////////////////////////////////////////////////



    /**
     * Create a new APOD from information returned in daily NASA JSON
     * @params String
     */
    void createApodFromJsonString(String s) {

        // Step 1: create the spaceItem object (no hires size, no vimeo thumb url, no thumb)
        // this needs no async processing at all
        apodItem = utils.createSpaceItemFromJsonString(getApplicationContext(), s);
        // CODE MOVED INTO UTILS - createSpaceItemFromJsonString - see end of file as reference

        String sMediaType = apodItem.getMedia();
        String sHiresUrl = apodItem.getHires();
        String imgUrl = apodItem.getLowres();

        // Local copy of original apod nasa json file now in utils.createSpaceItemFromJsonString()

        // TODO: check, if the following could be moved into checkMissingThumbs - this would allow
        // to use checkMissingThumbs to be called after all json/list activities to get details
        if (sMediaType.equals(M_VIMEO)) {
            // Insert query for vimeo thumbnail URL via oembed API which then triggers img_lowres load
            // MediaMetadataRetriever might also be able to retrieve a thumbnail, but it is better
            // and also faster to retrieve the "offical" thumb...
            Log.i("HFCM", "Launching asyncLoad for VIMEO info: " + sHiresUrl);
            new asyncLoad(MainActivity.this, "VIMEO_INFO").
                    execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
        } else if (sMediaType.equals(M_IMAGE)) {
            // Insert query for hires image size, which then triggers img_lowres_load
            new asyncLoad(MainActivity.this, "IMG_HIRES_SIZE_QUERY").execute(sHiresUrl);
        } else {
            // actually, lowres is just for thumbnail
            new asyncLoad(MainActivity.this, "IMG_LOWRES_LOAD").execute(imgUrl);
        }
    }


    /**
     * Check for any missing thumb images. We iterate all items and for those with no bitmap
     * contained, we kick of a loader thread, which gets the image file from the given lowres
     * URL and saves a thumbnail file.
     */
    public void checkMissingThumbs() {
        int count = 0;
        for(int i=0; i<myList.size(); i++) {
            // TODO: handle VIMEO here - just do it as in apod, so vimeo info is extracted
            // TODO: also get the hires size information here.
            // here. checkMissingThumbs could be the way to go all in all

            if (myList.get(i).getBmpThumb() == null && !myList.get(i).getLowres().equals("")) {
                // TODO unknown not yet checked...
                myList.get(i).setThumbLoadingState(View.VISIBLE);
                // TODO: check parallel execution - seems to not load all images / and duplicates
                new asyncLoad(MainActivity.this,
                        "THUMB_" + i).
                        execute(myList.get(i).getLowres());
                        //executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, myList.get(i).getLowres());
                count ++;
            }
        }

        if (count > 0) {
            Toast.makeText(MainActivity.this, getString(R.string.load_miss_thumbs, count),
                    Toast.LENGTH_LONG).show();
        }
    }


    /**
     * @param bitmap bitmap object of the lowres image - used for thumbnail creation
     */
    void finalizeApodWithLowresImage(Bitmap bitmap) {
        // "Good old ugly" version just iterating all items in ArrayList. This is ok. The first
        // item should match anyway, because latest image is on top of the list.
        for (spaceItem item : myList) {
            if (apodItem.getTitle().equals(item.getTitle())) {
                // TODO here's the point to check, if the thumbnail file exists (and reload)
                Toast.makeText(MainActivity.this, R.string.already_loaded,
                        Toast.LENGTH_LONG).show();
                return;
            }
        }

        // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
        if (bitmap != null) {
            // moved this here...
            apodItem.setLowSize(String.valueOf(((Bitmap) bitmap).getWidth()) + "x" +
                    String.valueOf(((Bitmap) bitmap).getHeight()));
            //File thumbFile = new File(getApplicationContext().getFilesDir(), apodItem.getThumb());
            Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 120, 120);
            Log.i("HFCM", "Calling utils to write thumb for new APOD:" + apodItem.getTitle());
            utils.writeJPG(getApplicationContext(), apodItem.getThumb(), thumbnail);
            apodItem.setBmpThumb(thumbnail);
        } else {
            apodItem.setBmpThumb(null);     // just have a black image here TODO missing img?
        }

        // Create a complete nasatest json object that can be inserted in the json array parent.
        // JSON is an array of objects, which have a "Type" and a "Content". Type is just
        // a string, content is another JSON object.
        JSONObject apodObj = utils.createJsonObjectFromSpaceItem(apodItem);

        // add object to local json array and save json content to internal storage
        // TODO - better insert into parent and myList at position depending on epoch

        parent.put(apodObj);
        utils.writeJson(getApplicationContext(), localJson, parent);

        myList.add(0, apodItem);
        adp.notifyDataSetChanged();
        // Add latest epoch into default shared prefs - used by scheduler service
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putLong("LATEST_APOD_EPOCH", apodItem.getDateTime());
        editor.apply();

        Toast.makeText(MainActivity.this, R.string.apod_load,
                Toast.LENGTH_LONG).show();
        myItemsLV.setSelection(0);
    }





    /* from createApodFromJson()
    THE CODE THAT HAS BEEN MOVED INTO UTILS
        HashMap<String, String> map = utils.parseStringAsJsonObject(getApplicationContext(), s);
        // non-existent keys return null string
        String sCopyright = map.containsKey("copyright") ? map.get("copyright").
                replaceAll(System.getProperty("line.separator"), " "): "";
        String sDate = map.containsKey("date") ? map.get("date") : "";
        String sExplanation = map.containsKey("explanation") ? map.get("explanation") : "";
        String sHiresUrl = map.containsKey("hdurl") ? map.get("hdurl") : "";
        String sMediaType = map.containsKey("media_type") ? map.get("media_type") : "";
        //"service_version"
        String sTitle = map.containsKey("title") ? map.get("title") : "";
        String imgUrl = map.containsKey("url") ? map.get("url") : "";

        // at this point, we can check, if the json was some error json returned, see utils code
        apodItem = new spaceItem();

        JSONObject parent = null;
        Uri resource_uri = null;
        resource_uri = Uri.parse(imgUrl);

        // Handle a missing hires url!!
        if (sHiresUrl.equals("") && sMediaType.equals("image")) {
            new dialogDisplay(this,
                    "APOD does not have a link to hires image, will need to fallback to lowres",
                    "Herbert TODO");
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
                apodItem.setHires(resource_uri.toString());
                if (host.equals("www.youtube.com")) {
                    // TODO: now we just assume 'embed' link - might not be true always ??
                    // url> https://www.youtube.com/embed/" + path.get(1) + "?rel=0
                    // img> https://img.youtube.com/vi/9Vp2jUQ4rNM/0.jpg
                    // TODO : MediaMetadataRetriever - also can retrieve image from video - FYI
                    // it's not sure, if this works with youtube, but what about vimeo?
                    sMediaType = M_YOUTUBE;
                    apodItem.setThumb("th_" + path.get(1) + ".jpg");
                    sHiresUrl = imgUrl;
                    imgUrl = "https://img.youtube.com/vi/" + path.get(1) + "/0.jpg";
                    apodItem.setLowres(imgUrl);
                } else if (host.endsWith("vimeo.com")) { // vimeo.com / player.vimeo.com
                    // see example file from 11.09.2017: link is in url, NOT hdurl
                    sMediaType = M_VIMEO;
                    apodItem.setThumb("th_VIMEO_unknown.jpg");
                    sHiresUrl = imgUrl;
                    // URL is the link that can be played in a WebView, just not using
                    // iframe embed at all... doing it like this for now
                    // TODO: extract video ID and thumbnail url from given video url
                    // is solved now, but what about "MediaMetadataRetriever"
                    // thumbnail image name: th_<video-id>.jpg
                    // Note: this needs a REST API call to gather the required infos..
                    apodItem.setLowres("");     // will hold thumbnail url
                } else {
                    // TODO: MP4 handling for correct media type video
                    if (hiresPS.endsWith(".mp4") || hiresPS.endsWith(".MP4")) {
                        sMediaType = M_MP4;
                        apodItem.setThumb("th_" + resource_uri.getLastPathSegment());
                        //apodItem.setHires(sHiresUrl);
                        apodItem.setLowres(imgUrl);
                    } else {
                        sMediaType = M_VIDEO_UNKNOWN;
                        apodItem.setThumb("th_UNKNOWN.jpg");
                    }
                }
            } else {
                // FIX for NASA sending wrong media type 'image' for MP4 video (13.11.2017)
                if (hiresPS.endsWith(".mp4") || hiresPS.endsWith(".MP4")) {
                    sMediaType = M_MP4;
                }
                apodItem.setThumb("th_" + resource_uri.getLastPathSegment());
                //apodItem.setHires(sHiresUrl);
                apodItem.setLowres(imgUrl);
            }
        }
        // TODO shouldn't super be executed first ??? might have done this bad
        //super.onPostExecute(s);
        apodItem.setTitle(sTitle);
        apodItem.setCopyright(sCopyright);
        apodItem.setExplanation(sExplanation);
        apodItem.setHires(sHiresUrl);
        //long epoch = utils.getNASAEpoch(0).get(0);
        long epoch = utils.getEpochFromDatestring(sDate);   // FIX to use "date" string from apod
        apodItem.setDateTime(epoch);
        apodItem.setMedia(sMediaType);
        apodItem.setRating(0);
        apodItem.setLowSize("");
        apodItem.setHiSize("");
        // END OF MOVED CODE*/


    // OLD refreshFromDropbox() - code now at end of listing for reference only, resyncWithDropbox
    // is now active.

    /**
     * Refresh the local json array with contents from the dropbox. The current array is replaced
     * by the new array by brute force...
     * - Existing rating values in the local array are preserved
     * - Thumbnail files of images are deleted, if they are not referenced any more in the new array
     * //@param dropbox   a JSONArray returned from the call asyncLoad call to dropbox
     */
    /*private void refreshFromDropbox(JSONArray dropbox) {
        // 1. metadata on the device which is not on dropbox is lost
        // 2. deleted items are loaded again and reappear in the list
        JSONObject obj = null;
        JSONObject content = null;
        HashMap<String, Integer> ratings;
        HashMap<String, String> thumbsToDelete;
        Set<String> deleted_items= sharedPref.getStringSet("DELETED_ITEMS", new HashSet<String>());

        // Iterate the currently active json array to fill maps for rating / thumbfilenames
        ratings = new HashMap<>();
        thumbsToDelete = new HashMap<>();
        for (int i = 0; i < parent.length(); i++) {
            try {
                content = parent.getJSONObject(i).getJSONObject("Content");
                if (content != null) {
                    ratings.put(content.getString("Title"), content.getInt("Rating"));
                    // All thumbfiles are candidates for deletion
                    thumbsToDelete.put(content.getString("Title"), content.getString("Thumb"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", e.toString());
            }
        }

        content = null;
        obj = null;
        for (int i = 0; i < dropbox.length(); i++) {
            try {
                obj = dropbox.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            try {
                if (obj != null) {
                    content = obj.getJSONObject("Content");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (content == null) {
                continue;
            }

            try {
                if (deleted_items.contains(content.getString("Title"))) {
                    Log.i("HFCM", "Skipping dropbox load for user deleted item: '" + content.getString("Title") + "'");
                    // TODO - make skip active - we might use an option here to force overall load!
                    // remove the item from dropbox JSON array
                    //continue;
                }
                if (ratings.containsKey(content.getString("Title"))) {
                    if (ratings.get(content.getString("Title")) != content.getInt("Rating")) {
                        Log.i("HFCM", "Adjust rating for '" + content.getString("Title") +
                                "' from " + content.getInt("Rating") + " to " +
                                ratings.get(content.getString("Title")));
                    }
                    content.put("Rating", ratings.get(content.getString("Title")));
                    // remove existing item from thumb deletion candidate list
                    thumbsToDelete.remove(content.getString("Title"));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        // Remove remaining thumbs/wallpapers which have no reference any more in the new json array
        // thumbnails are always present, so use them as filename stem. For wallpapers, just check
        // by exchanging th_ by wp_.
        for (String key : thumbsToDelete.keySet()) {
            Log.i("HFCM", "Delete thumb: " + thumbsToDelete.get(key));
            new File(getApplicationContext().getFilesDir(), thumbsToDelete.get(key)).delete();
            // remove orphaned wallpapers as well...
            // TODO: maybe use more unique prefidx, e.g. th__ and wp__ just to avoid conflicts...
            //       new app version should clean old files, or just require new install
            String wpFileName = thumbsToDelete.get(key).replace("th_", "wp_");
            File wpFile = new File(wpFileName);
            if (wpFile.exists()) {
                Log.i("HCFM", "Removing wallpaper orphan " + wpFileName);
                // TODO: should we remove the active one as well?
                wpFile.delete();        // TODO check returncode
            }
            String hdFileName = thumbsToDelete.get(key).replace("th_", "hd_");
            File hdFile = new File(hdFileName);
            if (hdFile.exists()) {
                Log.i("HCFM", "Removing hires orphan " + hdFileName);
                hdFile.delete();        // TODO check returncode
            }
        }

        // Activate the contents of the new json array
        parent = dropbox;
        utils.writeJson(getApplicationContext(), localJson, parent);
        myList.clear();
        addItems();
        checkMissingThumbs();
        adp.notifyDataSetChanged();
        getLatestAPOD();
    }*/


    /* processFinish() retired sections...
    case "DROPBOX_INIT":        // RETIRED - ALL DONE WITH DROPBOX_REFRESH NOW
                try {
                    json = new JSONTokener((String) output).nextValue();
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                if (json != null && json instanceof JSONArray) {
                    parent = (JSONArray) json;
                    utils.writeJson(getApplicationContext(), localJson, parent);
                    addItems();
                    checkMissingThumbs();
                    adp.notifyDataSetChanged();
                    getLatestAPOD();
                }
                break;*/

    // processFinish()  - old switch-case sections, not active any more since 2017-12-15
    // now for the "exact matches"
    //switch (tag) {
            /*case "DROPBOX_REFRESH":
                // TODO - phone rotation and verify, if network checks are done / needed
                Object json;
                try {
                    json = new JSONTokener((String) output).nextValue();
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                if (json != null && json instanceof JSONArray) {
                    //refreshFromDropbox((JSONArray) json);
                    resyncWithDropbox((JSONArray) json,
                            sharedPref.getBoolean("force_full_dropbox_sync", false));
                }
                break;*/

            /*case "VIMEO_INFO":
                String thumbUrl = "n/a";
                String videoId = null;
                //if (status == asyncLoad.FILENOTFOUND) {  // bad status fails in beginning ...
                //    break;
                //}
                try {
                    JSONObject parent = new JSONObject((String) output);
                    videoId = parent.getString("video_id");
                    thumbUrl = parent.getString("thumbnail_url");
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("HFCM", e.getMessage());
                    new dialogDisplay(this, getString(R.string.no_vimeo_thumb),
                            "VIMEO INFO JSON ERROR");
                }
                if (videoId != null) {
                    apodItem.setThumb("th_" + videoId + ".jpg");   // thumb filename
                }
                apodItem.setLowres(thumbUrl);
                new asyncLoad(MainActivity.this, "IMG_LOWRES_LOAD").execute(thumbUrl);
                break;*/
            /*case "APOD_LOAD":
                String h = (String)output;
                // TODO: test 4.1 with tls off - need to handle return code...
                if (h.startsWith("Connection")) {
                    new dialogDisplay(MainActivity.this, h + "\n" + getString(R.string.enable_tls), "NASA Connect");
                    return;
                }
                if (utils.isJson(h) == utils.JSON_OBJ) {
                    //createApodFromJsonString((String) output);
                    spaceItem item = utils.createSpaceItemFromJsonString(getApplicationContext(), (String) output);
                    utils.insertSpaceItem(myList, item);
                } else {
                    String first100 = h.substring(0, h.length() > 100 ? 100 : h.length());
                    utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                            "APOD_LOAD: JSON exception: " + first100);
                    new dialogDisplay(MainActivity.this,
                            "No JSON object received - possible captive portal?\nContent: '" +
                                    first100 + "......'", "APOD_LOAD Debug");
                    return;
                }
                break;*/
            /*case "IMG_LOWRES_LOAD":
                if (output instanceof Bitmap) {
                    finalizeApodWithLowresImage((Bitmap) output);
                    if (sharedPref.getBoolean("INITIAL_LAUNCH", false)) {
                        initialLaunch();
                    }
                } else {
                    Log.e("HFCM", "asyncLoad for lowres image did not return a bitmap");
                    utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                            "IMG_LOWRES_LOAD - no bitmap returned");
                }
                break;
            case "IMG_HIRES_SIZE_QUERY":
                // Inserted into chain as with vimeo info query
                String size = (String)output;
                apodItem.setHiSize(size);
                new asyncLoad(MainActivity.this, "IMG_LOWRES_LOAD").execute(apodItem.getLowres());
                break;*/
    //default:
    //    break;
    //}
    // recursive call is a really bad idea :)
    // new asyncLoad(this).execute("some url");
}
