package de.herb64.funinspace;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
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
import android.content.pm.LabeledIntent;
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
// import android.support.multidex.MultiDexApplication;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
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

import java.io.File;
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

// TODO Log statements: Log.d etc.. should not be contained in final release - how to automate?
// see https://stackoverflow.com/questions/2446248/remove-all-debug-logging-calls-before-publishing-are-there-tools-to-do-this

// TODO: if missing or bad hires image url for an image, we need to handle this: fall back to lowres
// TODO: network check - timeouts, 404 etc..  --- UNMETERED NETWORKS - check this
// https://developer.android.com/reference/android/net/NetworkCapabilities.html


/*
 * The MainActivity Class for FunInSpace
 */
public class MainActivity extends AppCompatActivity
        implements ratingDialog.RatingListener, asyncLoad.AsyncResponse, confirmDialog.ConfirmListener {

    private ArrayList<spaceItem> myList;
    //private LinkedHashMap<String, spaceItem> myMap; // abandoned, old class file still present
    private spaceAdapter adp;
    private JSONArray parent;
    private String jsonData;
    //private String localJson = "nasatest.json";
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
    private boolean wpShuffleTimesChanged;

    // APOD Filter view related -- hmm, is stored in the adapter, do we need these variables at all?
    private boolean filterCaseSensitive;    // shared pref filter_case_sensitive
    private boolean filterFullText;         // shared pref filter_full

    // APOD Archive Search related
    private boolean isSearch;               // TODO: remove, use search string content instead
    private boolean restoreDeletedApodsOnSearch;
    private boolean archiveSearchCaseSensitive;
    private boolean archiveSearchFullText;
    private String archiveSearchString;     // Search String for NASA archive search
    private String archiveSearchBeginDate;
    private String archiveSearchEndDate;

    //private boolean bgApodLoadChanged;    // new, simple logic, was bad before... TODO: more of that
    private boolean bg_apod_load;

    private String hd_cachelimit;
    private int hd_maxmempct;

    private int currentWallpaperIndex = -1;
    protected TimeZone tzNASA;
    protected Calendar cNASA;
    protected SimpleDateFormat formatter;
    // static is bad for tts variable (possible leaks)
    // https://www.androiddesignpatterns.com/2013/04/retaining-objects-across-config-changes.html
    // http://www.vogella.com/tutorials/AndroidFragments/article.html#headlessfragments
    //private TextToSpeech tts = null;
    private TextReader reader;
    private BroadcastReceiver receiver = null;
    private NetworkBcastReceiver networkReceiver = null;
    private int unfinishedApods = 0;
    private int versionCodeCurrent = 0;
    //private int versionCodeInPrefs = 0;
    private ArrayList<Integer> selected;    // keep track of selected items

    private ActionMode mActionMode;

    // Using JNI for testing with NDK and C code in a shared lib .so file
    static {System.loadLibrary("hfcmlib");}
    public native String yT();
    public native String nS();
    public native String vE();
    public native String dPJ();
    public native String dAS();
    public native String dPR();

    // ========= CONSTANTS =========
    //public static String TAG = MainActivity.class.getSimpleName();

    //private static final String ABOUT_VERSION = "0.5.8 (alpha)\nBuild Date 2017-12-19\n";
    // infos now only in build.gradle - utils.getVersionInfo() grabs data for about dialog

    public static final String localJson = "nasatest.json";

    private static final int INIT_DAYS_TO_LOAD = 9;

    // for logcat wrapper - utils.java
    public static final boolean LOGCAT_INFO = false;
    public static final boolean LOGCAT_ERROR = false;

    private static final int HIRES_LOAD_REQUEST = 1;
    private static final int GL_MAX_TEX_SIZE_QUERY = 2;
    private static final int SETTINGS_REQUEST = 3;
    private static final int APOD_SEARCH_REQUEST = 4;

    //private static final int KIB = 1024;
    //private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;

    // strings for media type classification
    public static final String M_IMAGE = "image";
    public static final String M_YOUTUBE = "youtube";
    public static final String M_VIMEO = "vimeo";
    public static final String M_MP4 = "mp4";      // first nasa mp4 on 13.11.2017
    public static final String M_HTML_VIDEO = "html-video";    // first nasa html on 05.03.2018
    public static final String M_VIDEO_UNKNOWN = "unknown-video";

    // dealing with the number of displayed lines in the Explanation text view
    public static final int MAX_ELLIPSED_LINES = 2;
    protected static final int MAX_LINES = 1000;    // ridiculous, but safe
    protected static final int MAX_ITEMS = 10000;   // theoretic limit of items - for id handling

    // wallpaper related stuff
    private static final String WP_CURRENT_BACKUP = "w_current.jpg";
    protected static final int WP_NONE = 0;
    public static final int WP_EXISTS = 1;
    protected static final int WP_ACTIVE = 3;   // just in case of bitmap interpretation
    //private static final int DEFAULT_MAX_STORED_WP = 20;
    public static final int JOB_ID_SHUFFLE = 85407;
    public static final String BCAST_SHUFFLE = "SHUFFLE";
    public static final String BCAST_APOD = "APOD";
    public static final String BCAST_THUMB = "THUMB";
    public static final String DEBUG_LOG = "funinspace.log";
    private static final String FILE_PROVIDER = "de.herb64.funinspace.fileprovider";

    /**
     * @param savedInstanceState saved instance data
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState); // before own code for create

        // TODO - fix problems with this, for now it is not used!  -  see terminateApp()
        if (getIntent().getExtras() != null && getIntent().getExtras().getBoolean("EXIT", false)) {
            finishAndRemoveTask();
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //https://developer.android.com/design/patterns/actionbar.html
        ActionBar actbar = getSupportActionBar();
        if (actbar != null) {
            actbar.setIcon(R.mipmap.ic_launcher);
            actbar.setDisplayShowTitleEnabled(false);
            // We could set our own custom view here as well - not tested... TODO
            //actbar.setDisplayShowCustomEnabled(true);
            //actbar.setCustomView();
        }

        loc = Locale.getDefault();  // see also lalatex docu
        versionCodeCurrent = utils.getVersionCode(getApplicationContext());

        long starttime = System.currentTimeMillis();
        utils.logAppend(getApplicationContext(), DEBUG_LOG,
                "**************  ONCREATE (" + versionCodeCurrent + ") **************");

        // Clear APOD notification when opening the app
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.cancel(apodJobService.APOD_NOTIFY_ID);
        }

        // Create text to speech reader
        FragmentManager fm = getSupportFragmentManager();
        reader = (TextReader) fm.findFragmentByTag(TextReader.TAG_READ_FRAGMENT);
        if (reader == null) {
            Log.i("HFCM", "Creating new reader object");
            reader = new TextReader();
            fm.beginTransaction().add(reader, TextReader.TAG_READ_FRAGMENT).commit();
        }

        // TODO - improve algorithm for new shuffle wp - low #images vs. high rating
        //String hugo = utils.getRandomWpFileName(getApplicationContext());

        // Prepare a device information class to be used during testing and debugging.
        // Query GL_MAX_TEXTURE_SIZE by creating an OpenGL context within a separate Activity.
        // Important: returned value maxTextureSize not yet available at onCreate(), although
        // onActivityResult is called and values are correct at later times after finishing
        // onCreate() and even the Toast within onActivityResult shows the correct values

        devInfo = new deviceInfo(getApplicationContext());
        if (savedInstanceState != null) {
            selected = savedInstanceState.getIntegerArrayList("selecteditems");
            maxTextureSize = savedInstanceState.getInt("maxtexsize");
            restoreDeletedApodsOnSearch = savedInstanceState.getBoolean("restoreDeletedApodsOnSearch");
            archiveSearchFullText = savedInstanceState.getBoolean("archiveSearchFullText");
            archiveSearchCaseSensitive = savedInstanceState.getBoolean("archiveSearchCaseSensitive");
            isSearch = savedInstanceState.getBoolean("isSearch");
            archiveSearchString = savedInstanceState.getString("archiveSearchString");
            devInfo.setGlMaxTextureSize(maxTextureSize);
        } else {
            SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
            maxTextureSize = shPref.getInt("maxtexsize", 0);
            restoreDeletedApodsOnSearch = false;
            archiveSearchFullText = false;
            archiveSearchCaseSensitive = true;
            isSearch = false;
            archiveSearchString = "";
            devInfo.setGlMaxTextureSize(maxTextureSize);
            selected = new ArrayList<>();
        }
        utils.logAppend(getApplicationContext(), DEBUG_LOG, devInfo.getLogInfo());

        // READ PREFERENCE SETTINGS FROM DEFAULT SHARED PREFERENCES
        // (shared_prefs/de.herb64.funinspace_preferences.xml)
        // TODO check, if this is rotation proof, or if we better should get prefs each time
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        // Initialize flags to true, so that first open of preferences dialog, which triggers the
        // prefChangeListener, does change it back to false in this case.
        dateFormatChanged = !sharedPref.contains("date_format");
        thumbQualityChanged = !sharedPref.contains("rgb565_thumbs");
        wpShuffleChanged = !sharedPref.contains("wallpaper_shuffle");
        wpShuffleTimesChanged = !sharedPref.contains("wp_shuffle_times");

        //bgApodLoadChanged = !sharedPref.contains("apod_bg_load");
        bg_apod_load = sharedPref.getBoolean("apod_bg_load", true);

        hd_cachelimit = sharedPref.getString("hd_cachelimit", "20");
        hd_maxmempct = Integer.decode(hd_cachelimit);

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
                // Changed to not collapse when scrolling out. Also fixed collapsing when doing
                // refresh of adapter when loading images at first time install
                TextView v = myItemsLV.findViewWithTag(position + MAX_ITEMS);
                if (v.getMaxLines() == MAX_ELLIPSED_LINES) {
                    v.setMaxLines(MAX_LINES);
                    v.setEllipsize(null);
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
                    myList.get((int)id).setMaxLines(MAX_LINES);
                } else {
                    v.setEllipsize(TextUtils.TruncateAt.END);
                    v.setMaxLines(MAX_ELLIPSED_LINES);
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null, expl_points);
                    myList.get((int)id).setMaxLines(MAX_ELLIPSED_LINES);
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
            //private ArrayList<Integer> selected;    // keep track of selected items

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
             * Create Action Mode
             * @param actionMode actionmode
             * @param menu menu
             * @return true
             */
            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.menu_cab_main, menu);
                mActionMode = actionMode;
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
                MenuItem item_cache_remove = menu.findItem(R.id.cab_cached_remove);
                if (selected.size() == 1) {
                    // For a single selection: non images cannot be selected at all
                    if (!myList.get(selected.get(0)).getMedia().equals(M_IMAGE)) {
                        // setVisible is better than setEnabled in this case!
                        item_setwp.setVisible(false);
                        item_reselect.setVisible(false);
                        item_remove.setVisible(false);
                        return true;
                    }
                    if (myList.get(selected.get(0)).isCached()) {
                        item_cache_remove.setVisible(true);
                    } else {
                        item_cache_remove.setVisible(false);
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
                    for (int idx : selected) {
                        if (myList.get(idx).isCached()) {
                            item_cache_remove.setVisible(true);
                            break;
                        }
                        item_cache_remove.setVisible(false);
                    }
                }

                // Change Read Icon depending on current state
                MenuItem item_read = menu.findItem(R.id.cab_read);
                FragmentManager fm = getSupportFragmentManager();
                reader = (TextReader) fm.findFragmentByTag(TextReader.TAG_READ_FRAGMENT);
                if (reader != null && reader.isReading()) {
                    Log.i("HFCM", "CAB init: tts is reading");
                    item_read.setIcon(R.drawable.ic_menu_stop_read);
                } else {
                    if (reader == null) {
                        Log.i("HFCM", "CAB init: reader is null");
                    }
                    item_read.setIcon(R.drawable.ic_menu_start_read);
                }

                return true;        // ???
            }

            /**
             * Handle click on action items in contextual action bar to process all selected space
             * items in the listview
             * @param actionMode action mode
             * @param menuItem the clicked menu item
             * @return boolean
             */
            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.cab_delete:
                        deleteItems(selected);
                        // activity_main.xml has CoordinatorLayout as base!
                        final View coordinatorLayoutView = findViewById(R.id.id_coord);
                        Snackbar.make(coordinatorLayoutView, "does not work yet!!!", Snackbar.LENGTH_LONG)
                                .setAction("UNDO", undoListener)
                                .show();
                        new dialogDisplay(MainActivity.this, "Undo snackbar not yet active...", "Herbert TODO");
                        actionMode.finish();
                        return true;
                    case R.id.cab_share:
                        // https://faq.whatsapp.com/en/android/28000012
                        new dialogDisplay(MainActivity.this, "Sharing not yet implemented", "Herbert TODO");
                        actionMode.finish();
                        return true;
                    case R.id.cab_rating:
                        // We use a DialogFragment that displays the Rating dialog and our main
                        // activity implements an interface for rating listener
                        FragmentManager fm = getSupportFragmentManager();
                        ratingDialog dlg = new ratingDialog();
                        // Just pass the indices to the rating fragment, so that they get returned
                        // by our interface implementation. This avoids a global def. of "selected"
                        // and potential problems with phone rotation
                        // AGAIN: do NOT use non default constructor with fragments - use arguments
                        Bundle fragArguments = new Bundle();
                        // FIX: just passing selected as arraylist will result in zero-length list
                        //      in ratingDialog.java - need to use new()
                        fragArguments.putIntegerArrayList("indices", new ArrayList<>(selected));
                        int current = 0;
                        for (int i : selected) {
                            if (myList.get(i).getRating() > current) {
                                current = myList.get(i).getRating();
                            }
                        }
                        fragArguments.putInt("current_rating", current);
                        dlg.setArguments(fragArguments);
                        // dlg.setTargetFragment(); // this only works when calling from fragment,
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
                                                    Log.w("HFCM", "File delete for wallpaper did not return true");
                                                }
                                                myList.get(selected.get(0)).setWpFlag(WP_NONE);
                                                int numwp = utils.setWPShuffleCandidates(getApplicationContext(), myList);
                                                if (numwp < 2) {
                                                    utils.logAppend(getApplicationContext(), DEBUG_LOG,
                                                            "Wallpaper remove - cancel shuffle due to <2 remaining wallpapers");
                                                    utils.cancelJob(getApplicationContext(), JOB_ID_SHUFFLE);
                                                }
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

                        // This is reached for multiple selections. There's only one option: delete
                        // all wallpapers, if status EXISTS for selected elements. No removal of the
                        // active one. This just removes wp files, updates wp status and job status
                        for (int idx : selected) {
                            if (myList.get(idx).getWpFlag() == WP_EXISTS) {
                                File wpdel = new File(getApplicationContext().getFilesDir(),
                                        myList.get(idx).getThumb().replace("th_", "wp_"));
                                if (!wpdel.delete()) {
                                    Log.w("HFCM", "File delete for wallpaper did not return true");
                                }
                                myList.get(idx).setWpFlag(WP_NONE);
                            }
                        }
                        int numwp = utils.setWPShuffleCandidates(getApplicationContext(), myList);
                        if (numwp < 2) {
                            utils.logAppend(getApplicationContext(), DEBUG_LOG,
                                    "Wallpaper remove - cancel shuffle due to <2 remaining wallpapers");
                            utils.cancelJob(getApplicationContext(), JOB_ID_SHUFFLE);
                        }
                        adp.notifyDataSetChanged();
                        return true;
                    case R.id.cab_read:
                        fm = getSupportFragmentManager();
                        reader = (TextReader) fm.findFragmentByTag(TextReader.TAG_READ_FRAGMENT);
                        if (reader != null) {
                            if (reader.read(myList.get(selected.get(0)).getTitle(),
                                    myList.get(selected.get(0)).getExplanation())) {
                                menuItem.setIcon(R.drawable.ic_menu_stop_read);
                            } else {
                                menuItem.setIcon(R.drawable.ic_menu_start_read);
                            }
                        }
                        return true;
                    case R.id.cab_cached_remove:
                        for (int idx : selected) {
                            if (myList.get(idx).isCached()) {
                                File cachedel = new File(getApplicationContext().getFilesDir(),
                                        myList.get(idx).getThumb().replace("th_", "hd_"));
                                if (!cachedel.delete()) {
                                    Log.w("HFCM", "File delete for cached hires did not return true");
                                }
                                myList.get(idx).setCached(false);
                            }
                        }
                        adp.notifyDataSetChanged();
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
                selected.clear();
                for (spaceItem item : myList) {
                    item.setSelected(false);
                }
                if (reader != null) {
                    reader.stop();
                }
            }
        });

        // Create a listeners for handling clicks on the thumbnail image and for rating changes
        myThumbClickListener = new thumbClickListener();

        // Create space item list and adapter
        myList = new ArrayList<> ();
        adp = new spaceAdapter(getApplicationContext(), MainActivity.this,
                R.layout.space_item, myList);
        adp.setFullSearch(sharedPref.getBoolean("filter_full", false));
        adp.setCaseSensitive(sharedPref.getBoolean("filter_case_sensitive", true));
        myItemsLV.setAdapter(adp);

        // SearchView - this had been defined in XML, but set to "GONE" by default. When search is
        // requested, this was originally shown above the listview. This has been changed to show
        // the SearchView within the App Bar.
        // See code in onCreateOptionsMenu() for SearchView as a menu item
        // https://www.youtube.com/watch?v=c9yC8XGaSv4
        // https://www.youtube.com/watch?v=YnNpwk_Q9d0
        // https://www.youtube.com/watch?v=9OWmnYPX1uc

        // Detect, if an updated version of the app has been started. This allows to have special
        // code to be run, e.g. handle stuff like json format changes etc...
        processAppUpdates();

        jsonData = null;
        File jsonFile = new File(getApplicationContext().getFilesDir(), localJson);
        if (jsonFile.exists()) {
            jsonData = utils.readf(getApplicationContext(), localJson);
        }

        // No longer check for saved instance state here, we always reconstruct jsonData, myList
        // and parent array on config changes - only check to avoid unneeded calls to getLatestAPODs
        // On Android 8.0, saving spaceItem ArrayList and JSON data string to savedInstanceState
        // failed with "Transaction Too Large error".
        // myList = savedInstanceState.getParcelableArrayList("myList");
        // jsonData = savedInstanceState.getString("jsonData");
        if (jsonData != null) {
            try {
                parent = new JSONArray(jsonData);
                addItems();
            } catch (JSONException e) {
                e.printStackTrace();
                parent = new JSONArray();
                jsonData = "[]";
            }
            if (savedInstanceState == null) {
                getLatestAPODs();
            }
        } else {
            // FIRST LAUNCH if jsonData is null, because it is only filled, if localJson file exists
            // TODO: offer restore from sd card...

            // query gl texture size, but do not call again, if rotation of phone during init launch
            SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
            if (shPref.getInt("maxtexsize", -1) == -1) {
                Intent texSizeIntent = new Intent(this, TexSizeActivity.class);
                startActivityForResult(texSizeIntent, GL_MAX_TEX_SIZE_QUERY);
                devInfo.setGlMaxTextureSize(maxTextureSize);
            }

            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP) {
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("apod_bg_load", false); // by default it is true
                editor.apply();
            }

            parent = new JSONArray();
            // at initial run, do a network test which does not fall into DNS wait...
            long conntime = utils.testSocketConnect(1500);
            if (conntime == 1500) {
                Bundle fragArguments = new Bundle();
                fragArguments.putString("TITLE",
                        "Welcome to FunInSpace");
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "INITIAL_LAUNCH - testSocketConnect TIMEOUT (" + conntime + "ms)");
                fragArguments.putString("MESSAGE",
                        getString(R.string.init_no_network));
                fragArguments.putString("NEG",
                        getString(R.string.hfcm_ok));
                fm = getSupportFragmentManager();
                confirmDialog confirmdlg = new confirmDialog();
                confirmdlg.setArguments(fragArguments);
                confirmdlg.show(fm, "INITLAUNCH");
            } else {
                getLatestAPODs();
                initialLaunch();
            }
        }

        utils.setWPShuffleCandidates(getApplicationContext(), myList);

        // Adding our first test service here for JobScheduler testing fails in Android 4.1 with
        // java.lang.NoClassDefFoundError: de.herb64.funinspace.services.shuffleJobService
        //     at de.herb64.funinspace.MainActivity.onCreate(MainActivity.java:845)
        // Finding on web: multidex enable as solution
        // https://stackoverflow.com/questions/31829350/app-crashes-with-noclassdeffounderror-only-on-android-4-x
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            serviceComponent = new ComponentName(this, shuffleJobService.class);
        }*/

        // Test for network changes broadcast receiver
        // https://developer.android.com/training/basics/network-ops/managing.html
        // TODO: better one receiver with multiple filters or multiple receivers with one filter?
        // the latter allows for multiple files, which do not grow too large and allow to deregister
        // single receivers, while others can remain active.
        /*IntentFilter netFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        netFilter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        networkReceiver = new NetworkBcastReceiver();
        registerReceiver(networkReceiver, netFilter);*/

        // Broadcast Receiver to handle events, mostly to update the UI immediately (e.g. wallpaper
        // symbol in thumbnail)
        // TODO - own class for receiver - for now add airplane mode here:
        IntentFilter filter = new IntentFilter();
        filter.addAction(BCAST_SHUFFLE);
        filter.addAction(BCAST_APOD);
        filter.addAction(BCAST_THUMB);
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        //filter.addCategory();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BCAST_SHUFFLE:
                        String newwp = intent.getStringExtra("NEWWP");
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
                    case BCAST_APOD:
                        // APOD background loader found valid json while ui running: refresh list
                        getLatestAPODs();
                        adp.notifyDataSetChanged();
                        break;
                    case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                        // If airplane mode switched on, test network first, then load data if ok
                        if (!utils.isAirPlaneMode(getApplicationContext())) {
                            long conntime = utils.testSocketConnect(2000);
                            if (conntime == 2000) {
                                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                                        "Airplane mode switched on, testSocketConnect TIMEOUT (" + conntime + "ms)");
                                break;
                            }
                            getLatestAPODs();
                            adp.notifyDataSetChanged();
                        }
                        break;
                    case BCAST_THUMB:
                        // This one is not used...
                        break;
                    default:
                        break;
                }
            }
        };
        registerReceiver(receiver, filter);

        // Reschedule jobs, that are expected to be active but not running at app start
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            JobScheduler jobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            List<JobInfo> allPendingJobs = jobScheduler.getAllPendingJobs();
            if (sharedPref.getBoolean("wallpaper_shuffle", false) &&
                    utils.getNumWP(myList) >= 2) {
                boolean needReload = true;
                for (JobInfo pending : allPendingJobs) {
                    if (pending.getId() == JOB_ID_SHUFFLE) {
                        needReload = false;
                    }
                }
                if (needReload) {
                    scheduleShuffle(utils.getMsToNextShuffle(getApplicationContext()));
                    utils.logAppend(getApplicationContext(),
                            DEBUG_LOG,
                            "Shuffle schedule found inactive although enabled in settings, restarting...");
                }
            }
            if (sharedPref.getBoolean("apod_bg_load", true)) {
                boolean needReload = true;
                for (JobInfo pending : allPendingJobs) {
                    if (pending.getId() == apodJobService.JOB_ID_APOD) {
                        needReload = false;
                    }
                }
                if (needReload) {
                    long ms2NextApod = utils.getMsToNextApod(getApplicationContext());
                    scheduleApod(ms2NextApod, apodJobService.DEADLINE_DELAY);
                    utils.logAppend(getApplicationContext(),
                            DEBUG_LOG,
                            "APOD schedule found inactive ... starting...");
                }
            }
        }

        // just to add some log data about networks - could be removed
        utils.getAllNetworksInfo(getApplicationContext());

        utils.logAppend(getApplicationContext(),
                DEBUG_LOG,
                "onCreate() finished... ",
                starttime);
    }

    /**
     * Todo - checkout this
     */
    /*@Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }*/

    @Override
    protected void onDestroy() {
        // https://stackoverflow.com/questions/18821481/what-is-the-correct-order-of-calling-superclass-methods-in-onpause-onstop-and-o
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268

        // interesting: why is this not working, if reader stop at end of onDestroy()? Seems that
        // calls to tts.stop/shutdown and unregister calls are async, so onDestroy continues, so
        // that last statements are not run any more...

        // from https://stackoverflow.com/questions/21136464/when-to-unregister-broadcastreceiver-in-onpause-ondestroy-or-onstop
        // The last lifecycle event handler that is guaranteed to be called before an app is
        // terminated (if you’re supporting pre-HoneyComb devices) is onPause. If you're only
        // supporting Post-HoneyComb devices, then onStop is the last guaranteed handler.

        // so this code is not yet perfect!! Will need to move some of the code to onstart/resume
        // from oncreate

        if (reader != null) {
            //reader.stop();
            if (isFinishing()) {
                Log.i("HFCM", "is finishing, shutdown tts...");
                reader.stop();
                reader.shutdown();
            }
        }
        utils.info("onDestroy() .....");
        if (receiver != null) {
            Log.i("HFCM", "unregister receiver");
            unregisterReceiver(receiver);
            receiver = null;
        }
        if (networkReceiver != null) {
            Log.i("HFCM", "unregister networkreceiver");
            unregisterReceiver(networkReceiver);
            networkReceiver = null;
        }
        Log.i("HFCM", "end of onDestroy, now calling super...");
        //if (isFinishing()) {
        //}
        super.onDestroy(); // after own code for destroy
    }

    /**
     * Test: starting our apod Service to be used with JobScheduler
     */
    @Override
    protected void onStart() {
        super.onStart(); // before own code for start
        utils.info("onStart() .....");
    }

    /**
     * Stop text to speech, if any active playback is running.
     */
    @Override
    protected void onStop() {
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        utils.info("onStop() .....");
        /*if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }*/
        //utils.stopRead(getSupportFragmentManager());
        // java.lang.IllegalStateException: Can not perform this action after onSaveInstanceState
        // see https://www.androiddesignpatterns.com/2013/08/fragment-transaction-commit-state-loss.html

        super.onStop(); // after own code for stop
    }

    /**
     *
     */
    @Override
    protected void onPause() {
        // actions here TODO: unregisterOnSharedPreferenceChangeListener
        // https://developer.android.com/guide/topics/ui/settings.html
        utils.info("onPause() .....");
        if (isFinishing()) {
            Log.i("HFCM", "onPause() - is finishing");
        }
        super.onPause();
    }

    /**
     *
     */
    @Override
    protected void onResume() {
        super.onResume();
        utils.info("onResume() .....");
        // actions here TODO: registerOnSharedPreferenceChangeListener
        // https://developer.android.com/guide/topics/ui/settings.html
    }

    /**
     * Save the instance state
     * @param outState Bundle to be saved
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
        //outState.putString("jsonData",jsonData);
        outState.putInt("maxtexsize", maxTextureSize);
        outState.putIntegerArrayList("selecteditems", selected);
        outState.putBoolean("restoreDeletedApodsOnSearch", restoreDeletedApodsOnSearch);
        outState.putBoolean("archiveSearchFullText", archiveSearchFullText);
        outState.putBoolean("archiveSearchCaseSensitive", archiveSearchCaseSensitive);
        outState.putBoolean("isSearch", isSearch);
        outState.putString("archiveSearchString", archiveSearchString);
        Log.i("HFCM", "Saving instance state, selected = " + selected);
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
                        if (!wpfile.equals("")) {
                            // update index values
                            if (currentWallpaperIndex >= 0) {
                                myList.get(currentWallpaperIndex).setWpFlag(WP_EXISTS);
                            }
                            myList.get((int)o).setWpFlag(WP_ACTIVE);
                            currentWallpaperIndex = (int)o;
                            changeWallpaper(wpfile);
                            // Enable shuffle if more than 2 WPs present and settings switch is on
                            if (sharedPref.getBoolean("wallpaper_shuffle", false) &&
                                    utils.getNumWP(myList) >=2 ) {
                                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                                        "processConfirmation(pos) - start WP shuffle due to >=2 wallpapers");
                                scheduleShuffle(utils.getMsToNextShuffle(getApplicationContext()));
                            }
                        }
                        break;
                    case "INITLAUNCH":
                        // initial launch now uses the date range query and gets some days back
                        long testEpoch = utils.getNASAEpoch();
                        long endEpoch = utils.getDaysBeforeEpoch(testEpoch, 1);
                        long beginEpoch = utils.getDaysBeforeEpoch(endEpoch, INIT_DAYS_TO_LOAD - 1);
                        String beginDate = utils.getNASAStringFromEpoch(beginEpoch);
                        String endDate = utils.getNASAStringFromEpoch(endEpoch);
                        // https://api.nasa.gov/planetary/apod?api_key=nnn&start_date=2018-02-05&end_date=2018-02-07
                        new asyncLoad(MainActivity.this,
                                "APOD_LOAD_RANGE",
                                sharedPref.getBoolean("enable_tls_pre_lollipop", true)).
                                execute(nS()+
                                        "&start_date=" + beginDate +
                                        "&end_date=" + endDate);
                        break;

                    case "NOWIFI-ACCEPT":
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putBoolean("wifi_switch", false);
                        editor.apply();
                        processThumbClick((int)o);
                        break;

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
                        // Enable shuffle if more than 2 WPs present and settings switch is on
                        if (sharedPref.getBoolean("wallpaper_shuffle", false) &&
                                utils.getNumWP(myList) >=2 ) {
                            utils.logAppend(getApplicationContext(), DEBUG_LOG,
                                    "processConfirmation(neg) - start WP shuffle due to >=2 wallpapers");
                            scheduleShuffle(utils.getMsToNextShuffle(getApplicationContext()));
                        }
                        break;
                    case "INITLAUNCH":
                        // TODO - task close does not yet work, if there comes some return from asyncload
                        // Negative button: only load image for today.
                        // this is called also if not network found at initial launch - neg button
                        // is the ok button in this case - we could use the neutral button here
                        //terminateApp();
                        break;
                    case "SHOW_LOG":
                        File logFile = new File(getFilesDir(), MainActivity.DEBUG_LOG);
                        if (logFile.exists()) {
                            if (!logFile.delete()) {
                                // it might fail if email client is still accessing contents
                                Log.e("HFCM", "Error deleting file: " + logFile);
                            }
                        }
                        break;
                    default:
                        break;
                }
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                switch (tag) {
                    case "SHOW_LOG":
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

            // Decide, if we want to proceed if WiFi required bot not not active - user now can
            // select to change the setting
            if (sharedPref.getBoolean("wifi_switch", false)
                    && !(utils.getActiveNetworkType(getApplicationContext()) == ConnectivityManager.TYPE_WIFI)
                    && hiresUrl.startsWith("http")) {
                Bundle fragArguments = new Bundle();
                fragArguments.putString("TITLE",
                        "No Wifi");
                fragArguments.putString("MESSAGE",
                        getString(R.string.hires_no_wifi));
                fragArguments.putString("NEG",
                        getString(R.string.hfcm_cancel));
                fragArguments.putString("POS",
                        getString(R.string.hfcm_yes));
                // We might use neutral for one-time loading as well
                fragArguments.putInt("IDX", idx);
                FragmentManager fm = getSupportFragmentManager();
                confirmDialog confirmdlg = new confirmDialog();
                confirmdlg.setArguments(fragArguments);
                confirmdlg.show(fm, "NOWIFI-ACCEPT");
                return;
            }
            processThumbClick(idx);
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
                true,   // autoplay
                true); // lightbox mode

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
        mp4Intent.putExtra("showstats", true);
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
     * 1. image Activity for hires size (no longer valid, size is determined in advance)
     * 2. GL max texture size query at very first run
     * 3. Settings dialog
     * Returned resultCode = 0 (RESULT_CANCELED) after having rotated the phone while
     * displaying the image in hires ImageActivity
     * https://stackoverflow.com/questions/32803497/incorrect-activity-result-code-after-rotating-activity
     * same problem in above post - this was because activity was gone underneath
     * @param requestCode   request code
     * @param resultCode    result code
     * @param data          intent returned by activity
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
                // String hUrl = data.getStringExtra("hiresurl");
                int listidx = data.getIntExtra("lstIdx", 0);

                // If a new cache file has been written, we trigger a file cleanup.
                if (data.getBooleanExtra("new_hd_cached_file", false)) {
                    ArrayList<Integer> del = utils.cleanupFiles(getApplicationContext(),
                            myList,
                            hd_maxmempct,
                            listidx);
                    for (int i : del) {
                        myList.get(i).setCached(false);
                    }
                    adp.notifyDataSetChanged();     // better call at end only once
                    Log.i("HFCM", "hd cached file was returned in intent, update...");
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
                    Log.d("HFCM", "Low quality thumbs enabled: " + sharedPref.getBoolean("rgb565_thumbs", false));
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
                        if (shuffle) {
                            utils.logAppend(getApplicationContext(),
                                    DEBUG_LOG,
                                    "Enabled wallpaper shuffle in settings");
                            if (utils.getNumWP(myList) < 2) {
                                new dialogDisplay(MainActivity.this,
                                        getString(R.string.wp_shuffle_less_than_two), "DEBUG ONLY!");
                            } else {
                                new dialogDisplay(MainActivity.this,
                                        getString(R.string.wp_shuffle_enabled), "DEBUG ONLY!");
                                scheduleShuffle(utils.getMsToNextShuffle(getApplicationContext()));
                            }
                        } else {
                            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
                            scheduler.cancel(JOB_ID_SHUFFLE);
                            utils.logAppend(getApplicationContext(),
                                    DEBUG_LOG,
                                    "Disabled wallpaper shuffle in settings");
                            Toast.makeText(MainActivity.this, "Wallpaper shuffle switched off",
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        new dialogDisplay(MainActivity.this,
                                "Not yet implemented for Versions below 5 (Lollipop)", "DEBUG ONLY!");
                    }
                }
                if (wpShuffleTimesChanged) {
                    wpShuffleTimesChanged = false;
                    //long test = utils.getMsToNextShuffle(getApplicationContext());
                    //Log.i("HFCM", "Shuffle times change..." + test);
                    if (sharedPref.getBoolean("wallpaper_shuffle", false) &&
                            utils.getNumWP(myList) >= 2) {
                        scheduleShuffle(utils.getMsToNextShuffle(getApplicationContext()));
                        utils.logAppend(getApplicationContext(),
                                DEBUG_LOG,
                                "Shuffle reschedule due to times reselect");
                    }
                }

                if (sharedPref.getBoolean("apod_bg_load", true) ^ bg_apod_load) {
                    bg_apod_load ^= true;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        //boolean bgload = sharedPref.getBoolean("apod_bg_load", false);
                        if (bg_apod_load) {
                            long ms2NextApod = utils.getMsToNextApod(getApplicationContext());
                            scheduleApod(ms2NextApod, apodJobService.DEADLINE_DELAY);
                            utils.logAppend(getApplicationContext(),
                                    DEBUG_LOG,
                                    "Enabled APOD Loader in settings, next apod in " +
                                            ms2NextApod/1000 + " seconds"
                            );

                        } else {
                            if (utils.cancelJob(getApplicationContext(),
                                    apodJobService.JOB_ID_APOD)) {
                                utils.logAppend(getApplicationContext(),
                                        DEBUG_LOG,
                                        "Disabled APOD background load in settings");
                                Toast.makeText(MainActivity.this, "APOD Background loader switched off",
                                        Toast.LENGTH_LONG).show();
                            } else {
                                utils.logAppend(getApplicationContext(),
                                        DEBUG_LOG,
                                        "Error disabling APOD background loader schedule");
                            }
                        }
                    } else {
                        new dialogDisplay(MainActivity.this,
                                "Not yet implemented for Versions below 5 (Lollipop)", "DEBUG ONLY!");
                    }
                }

                // Cache memory limit for hires images: if this value is lowered, we should run
                // a cleanup to reflect the new value - maybe warn the user before...
                if (!sharedPref.getString("hd_cachelimit", "20").equals(hd_cachelimit)) {
                    int hd_current = hd_maxmempct;
                    hd_cachelimit = sharedPref.getString("hd_cachelimit", "20");
                    hd_maxmempct = Integer.decode(hd_cachelimit);
                    if (hd_maxmempct < hd_current) {
                        //Log.i("HFCM", "Running cleanupFiles (hdmax lowered from " +
                        //        hd_current + " to " + hd_maxmempct + ")");
                        utils.cleanupFiles(getApplicationContext(), myList, hd_maxmempct, -1);
                    }
                }
            }
        } else if (requestCode == APOD_SEARCH_REQUEST) {
            // We get the following infos back from ApodSearchActivity:
            // - search string (might be empty)
            // - date range: begin and end date as strings
            // - if we should restore previously deleted images
            // - if case sensitive search should be used
            if (resultCode == RESULT_OK) {
                archiveSearchString = data.getStringExtra("search");
                archiveSearchBeginDate = data.getStringExtra("beginDate");
                archiveSearchEndDate = data.getStringExtra("endDate");
                restoreDeletedApodsOnSearch = data.getBooleanExtra("reloadDeleted", false);
                archiveSearchCaseSensitive = data.getBooleanExtra("archiveSearchCaseSensitive", true);
                archiveSearchFullText = data.getBooleanExtra("archiveSearchFullText", false);
                Log.i("HFCM", "returned from apod search activity: " + archiveSearchString);
                isSearch = true;
                // https://api.nasa.gov/planetary/apod?api_key=nnn&start_date=2018-02-05&end_date=2018-02-07
                new asyncLoad(MainActivity.this,
                        "APOD_SEARCH_LOAD_RANGE",
                        sharedPref.getBoolean("enable_tls_pre_lollipop", true)).
                        execute(nS()+
                                "&start_date=" + archiveSearchBeginDate +
                                "&end_date=" + archiveSearchEndDate);

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
     * TODO: better make switch-case or else-if!
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
                    if (changed.equals("wp_shuffle_times")) {
                        wpShuffleTimesChanged ^= true;
                    }
                    /*if (changed.equals("apod_bg_load")) {
                        bgApodLoadChanged ^= true;
                    }*/
                    if (changed.equals("filter_full")) {
                        adp.setFullSearch(sharedPref.getBoolean("filter_full", false));
                    }
                    if (changed.equals("filter_case_sensitive")) {
                        adp.setCaseSensitive(sharedPref.getBoolean("filter_case_sensitive", true));
                    }
                    if (changed.equals("wallpaper_quality")) {
                        Log.d("HFCM", "Changed wall paper quality to " +
                                sharedPref.getString("wallpaper_quality", "80"));
                    }
                    if (changed.equals("hires_save")) {
                        boolean hs = sharedPref.getBoolean("hires_save", false);
                    }
                }
            };

    /**
     * The options menu is the primary Application menu. Do not confuse with "settings" dialog.
     * It is called during startup of the activity once.
     * Note the code to make icons visible in overflow menu.
     * TODO: document how to make icons for search AND filter disappear, if any of these actions
     * is actually expanded. Otherwise, for example, filter could be clicked, while search action
     * was expaned, getting ugly overlap of graphics... -> SupportMenuItem!!!
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

        // 20.11.2017 Switch MenuItem to SupportMenuItem and use noinspection RestrictedApi                             TODO DOCU
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
                adp.setHiLight(s);
                adp.getFilter().filter(s);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                if (s.length() == 0) {
                    adp.cleanMap();
                }
                adp.setHiLight(s);
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
                //adp.getFilter().filter("");                                                               // TODO RE-TEST
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
                return true;
            }
        });
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        return super.onCreateOptionsMenu(menu);
        //return true;
    }

    /**
     * Prepare main menu depending on current state
     * 1. no dropbox sync, while still reloading unfinished apods
     * @param menu menu
     * @return return value
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        //MenuItem dbResync = menu.findItem(R.id.dropbox_sync);
        MenuItem search = menu.findItem(R.id.action_search);
        if (unfinishedApods > 0) {
            //dbResync.setEnabled(false);
            search.setEnabled(false);
        } else {
            //dbResync.setEnabled(true);
            search.setEnabled(true);
        }
        // https://stackoverflow.com/questions/9625920/should-the-call-to-the-superclass-method-be-the-first-statement/9626268#9626268
        return super.onPrepareOptionsMenu(menu);
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
            // USED FOR EXPERIMENTS - will not be needed in final version any more

            //scheduleApod(5000, 5000);

            // Highlighting tests for filter / archive search. This is implemented in the adapter
            //adp.setHiLight("moon");
            //adp.notifyDataSetChanged();

            /*new dialogDisplay(this, devInfo.getDeviceInfo() +
                    devInfo.getActMgrMemoryInfo(true) +
                    devInfo.getGLInfo() +
                    devInfo.getNetworkInfo(true));*/

            //utils.logWrapped(getApplicationContext(), "wrapped", "This is an entry for wrapper test");
            //utils.getWrappedLogContents(getApplicationContext(), "wrapped", "testout");

            // https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY&start_date=2018-02-05&end_date=2018-02-07
            /*new asyncLoad(MainActivity.this,
                    "APOD_LOAD_RANGE",
                    sharedPref.getBoolean("enable_tls_pre_lollipop", true)).
                    execute(nS()+"&start_date=2018-01-03&end_date=2018-01-04");*/

            return true;
        }
        if (id == R.id.action_slideshow) {
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
            Intent helpIntent = new Intent(this, HelpActivity.class);
            helpIntent.putExtra("help_start_url", getString(R.string.html_help_basepage));
            startActivity(helpIntent);
            return true;
        }
        /*if (id == R.id.action_crash) {
            Toast.makeText(getApplicationContext(),"will crash soon :)", Toast.LENGTH_LONG).show();
            SystemClock.sleep(2000);
            ArrayList<Integer> crash_me = new ArrayList<>();
            int t = crash_me.get(10);
        }*/
        if (id == R.id.action_privacy) {
            Intent privacyIntent = new Intent(this, PrivacyActivity.class);
            privacyIntent.putExtra("privacyurl", dPR());
            startActivity(privacyIntent);
            return true;
        }
        if (id == R.id.action_about) {
            // NEW: create a layout for this and a new class AboutDialog
            // https://www.youtube.com/watch?v=rsKHeuBKnNc
            // <div>Icons made by <a href="https://www.flaticon.com/authors/gregor-cresnar" title="Gregor Cresnar">Gregor Cresnar</a> from <a href="https://www.flaticon.com/" title="Flaticon">www.flaticon.com</a> is licensed by <a href="http://creativecommons.org/licenses/by/3.0/" title="Creative Commons BY 3.0" target="_blank">CC 3.0 BY</a></div>
            // see https://file000.flaticon.com/downloads/license/license.pdf
            // MP4 movie icon designed by Gregor Cresnar from www.flaticon.com
            //AboutDialog ad = new AboutDialog(getApplicationContext());
            AboutDialog ad = new AboutDialog(MainActivity.this);
            ad.requestWindowFeature(Window.FEATURE_NO_TITLE);   // TODO: docu avoid space on top of window and check more features!!
            //ad.setTitle("About Fun In Space");
            //ad.setCancelable(true);
            ad.show();
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
        if (id == R.id.restore_wallpaper) {
            new dialogDisplay(MainActivity.this,
                    getString(R.string.dlg_revert_wp),
                    getString(R.string.dlg_title_info));
            changeWallpaper("");
        }
        if (id == R.id.action_search_archive) {
            // new code using date range picker library - see also build.gradle
            Intent apodSearchIntent = new Intent(this, ApodSearchActivity.class);
            startActivityForResult(apodSearchIntent, APOD_SEARCH_REQUEST);
            return true;
        }
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

            if (!strThumb.isEmpty()) {
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
                // Wallpaper filename is just derived from thumb filename
                String wpName = strThumb.replace("th_", "wp_");
                File wpFile = new File(getApplicationContext().getFilesDir(), wpName);
                if (wpFile.exists()) {
                    if (wpName.equals(wpFileCurrent)) {
                        newitem.setWpFlag(WP_ACTIVE);
                        currentWallpaperIndex = count;
                    } else {
                        newitem.setWpFlag(WP_EXISTS);
                    }
                } else {
                    newitem.setWpFlag(WP_NONE);
                }
                // Hires cache image filename derived from thumb filename, th_ > hd_
                String hdName = strThumb.replace("th_", "hd_");
                File hdFile = new File(getApplicationContext().getFilesDir(), hdName);
                newitem.setCached(hdFile.exists());
            }
            myList.add(newitem);
            count++;
        }
        if (selected != null) {
            for (int i : selected) {
                myList.get(i).setSelected(true);
            }
        }
    }

    /**
     * Get any missing informations for items in the apod list. This includes the following:
     * 1. hires image: width, height
     * 2. vimeo infos: thumbnail url, duration
     * 3. thumbnail image (in any case)
     * The function triggers a chain of async operations, depending on type of item.
     * Note: MediaMetadataRetriever might also be able to retrieve an image from a stream, but it is
     * better and faster to retrieve the "offical" thumb...
     * TODO: check options for parallel execution... (executeOnExecutor...)
     */
    public void getMissingApodInfos() {
        long conntime = utils.testSocketConnect(2000);
        if (conntime == 2000) {
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "getMissingApodInfos() - testSocketConnect TIMEOUT (" + conntime + "ms)");
            new dialogDisplay(MainActivity.this,
                    getString(R.string.no_network_for_apod),
                    getString(R.string.no_network));
            return;
        } else {
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "getMissingApodInfos() - testSocketConnect OK (" + conntime + "ms)");
        }
        for(int i=0; i<myList.size(); i++) {
            // TODO change with later possible glide implementation
            if (myList.get(i).getBmpThumb() == null &&
                    !myList.get(i).getThumb().equals("th_UNKNOWN.jpg")) {
                unfinishedApods++;
                myList.get(i).setThumbLoadingState(View.VISIBLE);
                String sMediaType = myList.get(i).getMedia();
                String sHiresUrl = myList.get(i).getHires();
                String imgUrl = myList.get(i).getLowres();
                String imgSize = myList.get(i).getHiSize();
                if (sMediaType.equals(M_VIMEO) && imgUrl.isEmpty()) {   // why check for imgurl empty?
                    // Query vimeo thumbnail URL via oembed API
                    Log.d("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG VIM_" + i + ": " + sHiresUrl);
                    new asyncLoad(MainActivity.this, "VIM_" + i).
                            execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
                } else if (sMediaType.equals(M_IMAGE) && imgSize.isEmpty()) {
                    Log.d("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG SIZE_" + i + ": " + sHiresUrl);
                    new asyncLoad(MainActivity.this, "SIZE_" + i).execute(sHiresUrl);
                } else if (sMediaType.equals(M_MP4)) {
                    Log.d("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG DUR_" + i + ": " + sHiresUrl);
                    new asyncLoad(MainActivity.this, "DUR_" + i).execute(sHiresUrl);
                } else if (sMediaType.equals(M_HTML_VIDEO)) {
                    // 05.03.2018 - NASA url links to html page for interactive image...
                    Log.d("HFCM", "getMissingApodInfos() - media_type video with html link!");
                    if (imgUrl != null && !imgUrl.isEmpty()) {
                        Log.d("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG THUMB_" + i + ": " + imgUrl);
                        new asyncLoad(MainActivity.this, "THUMB_" + i).execute(imgUrl);
                    } else {
                        myList.get(i).setThumbLoadingState(View.INVISIBLE);
                        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                                "getMissingApodInfos() - imgUrl is null or empty");
                    }
                } else {  // TODO double code as with html video link... check how to handle best
                    // DOCU see "short circuit evaluation" as keyword
                    if (imgUrl != null && !imgUrl.isEmpty()) {
                        Log.d("HFCM", "getMissingApodInfos() - launching asyncLoad for TAG THUMB_" + i + ": " + imgUrl);
                        new asyncLoad(MainActivity.this, "THUMB_" + i).execute(imgUrl);
                    } else {
                        myList.get(i).setThumbLoadingState(View.INVISIBLE);
                        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                                "getMissingApodInfos() - imgUrl is null or empty");
                    }
                }
            }
        }

        if (unfinishedApods > 0) {
            Toast.makeText(MainActivity.this,
                    getString(R.string.load_miss_thumbs, unfinishedApods),
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Get newest APODs since last launch of the app UI. This includes the check for any APODs
     * already catched by the scheduler.
     * The additional infos on thumbails, image size and (if applicable) vimeo thumbnail url
     * are added later using function getMissingApodInfos()
     */
    private void getLatestAPODs() {
        // Network connection is needed to load additional infos. Else skip and keep s___ file(s)
        // for later processing - we might use bcast services as trigger

        long conntime = utils.testSocketConnect(2000);
        if (conntime == 2000) {
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "getLatestAPODs() - testSocketConnect TIMEOUT (" + conntime + "ms)");
            return;
        }

        unfinishedApods = 0;
        // No wildcard file filtering - a subdirectory for scheduled files could be used to avoid
        // too many files in names list, or just putting thumbnails in separate directory, as the
        // local nasa json original files are no longer stored.
        File dir = new File(getApplicationContext().getFilesDir().getPath());
        String[] names = dir.list();

        long timeToNext = 0;
        if (!myList.isEmpty()) {
            // FIRST item in myList is latest available item. This is the LAST item in JSON.
            ArrayList<Long> epochs = utils.getNASAEpoch(myList.get(0).getDateTime());
            timeToNext = epochs.get(2);
        }

        // Get information about any missing images since last successful App GUI launch, which
        // have not been covered by job scheduler. Run a date range query.
        long check = sharedPref.getLong("LAST_UI_LAUNCH_EPOCH", 0);

        // Fill in epochsToLoad array based on the current date and the last UI launch
        long current = utils.getNASAEpoch();
        ArrayList<Long> epochsToLoad = new ArrayList<>();   // epochs to be checked
        if (check != 0) {
            if (check == current) {
                if (timeToNext != 0) {
                    Toast.makeText(MainActivity.this,
                            String.format(loc, getString(R.string.apod_already_loaded),
                                    (float) timeToNext / (float) 3600000),
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            do {
                check = utils.getNextDaysEpoch(check);
                epochsToLoad.add(check);
                //Log.i("HFCM", "Have epoch to load:" + check);
            } while (check < current);
        } else {
            epochsToLoad.add(current);
        }

        // Create ArrayList of epoch values from existing s___ files. Any s___ file means that this
        // one is available from scheduler output, so remove from potential missing epochs.
        // after this is done, potentialMissing only has those, that need to be loaded, because
        // scheduler did not provide any data. first one: begindate, last one: end date for query
        ArrayList<Long> schedEpochs = new ArrayList<>();
        for (String name : names) {
            if (name.startsWith(apodJobService.APOD_SCHED_PREFIX)) {
                long ep = Long.parseLong(name.replaceAll("\\D+",""));
                schedEpochs.add(ep);
            }
        }

        // Insert all items from s___ files into the spaceitem list first.
        for (long schedEpoch : schedEpochs) {
            String name = apodJobService.APOD_SCHED_PREFIX + schedEpoch + ".json";
            spaceItem item = utils.createSpaceItemFromJsonFile(getApplicationContext(), name);
            if (item != null && !item.getTitle().isEmpty()) {
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "getLatestAPODs(): file = '" + name + "', title ='" + item.getTitle() + "'");
                utils.insertSpaceItem(myList, item);
                epochsToLoad.remove(schedEpoch);    // remove from epoch list to be checked
            } else {
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "getLatestAPODs(): '" + name + "' - null item or missing title");
            }
        }

        // epochsToLoad now contains the remaining epochs, for which no s___ file has been found.
        // The values are in sorted order, so the first and last represent the start and end time to
        // be queried - for this range, we trigger a NASA API Query to retrieve a JSON Array.
        // If no elements remain to load, getMissingApodInfos() is called immediately.
        int nEpochs = epochsToLoad.size();
        if (nEpochs == 0) {
            getMissingApodInfos();
        } else {
            // At least one apod needs to be loaded which not in the scheduled apods list.
            // This might be the daily one (because it is not yet available) and/or some of the
            // earlier apods, which were missed by the scheduler (phone switched off, no network)
            long beginEpoch = epochsToLoad.get(0);
            long endEpoch = epochsToLoad.get(nEpochs-1);
            String beginDate = utils.getNASAStringFromEpoch(beginEpoch);
            String endDate = utils.getNASAStringFromEpoch(endEpoch);
            // example format for query:
            // https://api.nasa.gov/planetary/apod?api_key=nnn&start_date=2018-02-05&end_date=2018-02-07
            new asyncLoad(MainActivity.this,
                    "APOD_LOAD_RANGE",
                    sharedPref.getBoolean("enable_tls_pre_lollipop", true)).
                    execute(nS()+
                            "&start_date=" + beginDate +
                            "&end_date=" + endDate);
        }
    }

    /**
     * Interface implementation of processFinish() for asyncLoad class. This is called when the
     * asyncLoad task has finished retrieving the requested data. The asyncLoad class is used for
     * different tasks.
     * Note: for parallel asynctask execution (executeonexecutor), see also my lalatex document...
     * @param status   Return status. This can either be
     *                 - HttpURLConnection.HTTP_n - http connection status, 200, 404 etc..
     *                 - aysncLoad.IOEXCEPTION or similar: connection problems BEFORE http..
     * @param tag      the tag string set by caller to identify the calling procedure. Some tags
     *                 include the list index: TAG_nnn, with nnn = index
     * @param output   the returned output (e.g. json string, bitmap, exception string)
     */
    @Override
    public void processFinish(int status, String tag, Object output) {
        // DOCU switch-case for String type since Java version 7, but not with String.startsWith()
        // Status returned from asyncload: everything not HTTP OK 200 is assumed to be bad
        // TODO: e.g. on 20.03.2018  503 service unavailable during testing - does not show anything
        //       shouldn't we have some display on that? otherwise only log entry, but no user interaction
        if (status != HttpURLConnection.HTTP_OK) {
            Log.e("HFCM", "processFinish(), tag = " + tag + " > '" + output + "'");
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    tag + " > '" + output + "'");
            new dialogDisplay(MainActivity.this,
                    (String) output,
                    "NASA Error");
            return;
        }

        if (tag.equals("APOD_LOAD")) {
            String h = (String) output;
            // TODO: test 4.1 with tls off - need to handle return code...
            if (utils.isJson(h) == utils.JSON_OBJ) {
                spaceItem item = utils.createSpaceItemFromJsonString(getApplicationContext(), (String) output);
                // TODO: we should check, if this is a real apod json, and not some http500 etc...
                utils.insertSpaceItem(myList, item);

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
                //return;
            }

        } else if (tag.equals("APOD_SEARCH_LOAD_RANGE")) {
            // Search function using the LOAD_RANGE instead resyncWithDropbox() logic
            String h = (String) output;
            try {
                JSONArray newarray = new JSONArray(h);
                Set<String> deleted_items = null;
                Set<String> deleted_work = null;
                if (isSearch) {
                    deleted_items = sharedPref.getStringSet("DELETED_ITEMS",
                            new HashSet<String>());
                    deleted_work = new HashSet<>(deleted_items);  // USE A WORK COPY!!!
                }
                Boolean recoveredAnyDeleted = false;
                int lastInsertIdx = -1;
                long lastInsertEpoch = 0;
                int numRecoveredDeleted = 0;
                int numSkippedNonMatched = 0;
                int numSkippedAsDeleted = 0;
                int numOverallItems = newarray.length();
                int numInsertedItems = 0;
                for (int i = 0; i < numOverallItems; i++) {
                    // new logic - filtering stuff if called from within a search context. If not
                    // in search context, all items are just added
                    if (isSearch) {
                        String title = newarray.getJSONObject(i).getString("title");
                        String searchtitle;
                        if (archiveSearchCaseSensitive) {
                            searchtitle = title;
                        } else {
                            searchtitle = title.toLowerCase();
                        }
                        if (!archiveSearchString.isEmpty() && !searchtitle.contains(archiveSearchString)) {
                            // 1. FIRST filter should be string filter for search string, independent
                            //    from deleted items recovery
                            Log.i("HFCM", "Skipping because '" + archiveSearchString + "' not in title: " + title);
                            numSkippedNonMatched++;
                            continue;
                        }
                        if (!restoreDeletedApodsOnSearch) {
                            // 2. skip item, if this item had already been deleted by the user and those
                            //    deleted items should not be restored.
                            if (deleted_items != null && deleted_items.contains(title)) {
                                Log.i("HFCM", "Skipping as deleted before with no recovery: " + title);
                                numSkippedAsDeleted++;
                                continue;
                            }
                        } else {
                            if (deleted_work != null) {
                                deleted_work.remove(title);
                                recoveredAnyDeleted = true;
                                numRecoveredDeleted++;
                            }
                        }
                    }
                    // if getting here: add the item. Double items are skipped by insertSpaceItem()
                    spaceItem item = utils.createSpaceItemFromJsonString(getApplicationContext(),
                            newarray.getJSONObject(i).toString());
                    int lastSuccessfulInsert = utils.insertSpaceItem(myList, item);
                    if (lastSuccessfulInsert != -1) {
                        numInsertedItems++;
                        lastInsertIdx = lastSuccessfulInsert;
                        lastInsertEpoch = item.getDateTime();
                    }
                }
                // update shared prefs deleted items by removing any recovered items from this list
                if (recoveredAnyDeleted) {
                    Log.i("HFCM", "Reloaded " + numRecoveredDeleted + " previously deleted items");
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putStringSet("DELETED_ITEMS", deleted_work);
                    editor.apply();
                }
                getMissingApodInfos();
                adp.notifyDataSetChanged();
                // scroll to the newest image postion that has been successfully inserted
                if (lastInsertIdx != -1) {
                    myItemsLV.setSelection(lastInsertIdx);
                    Log.i("HFCM", "Setting selection index to " + lastInsertIdx);
                    SharedPreferences.Editor editor = sharedPref.edit();
                    editor.putString("NEXT_ARCHIVE_SEARCH_BEGIN_DATE",
                            utils.getNASAStringFromEpoch(utils.getNextDaysEpoch(lastInsertEpoch)));
                    editor.apply();
                    // TODO: check, if some items are omitted
                    if (numInsertedItems != numOverallItems) {
                        new dialogDisplay(MainActivity.this,
                                "Only " + numInsertedItems + " of " + numOverallItems + " have been loaded",
                                "NASA Archive search");
                    }
                } else {
                    // No items added by this search at all, check counters for reason
                    new dialogDisplay(MainActivity.this,
                            String.format(Locale.getDefault(),
                                    getString(R.string.archive_search_no_match),
                                    archiveSearchString, archiveSearchBeginDate, archiveSearchEndDate, numSkippedNonMatched, numSkippedAsDeleted),
                            "NASA Archive search");
                }
            } catch (JSONException e) {
                e.printStackTrace();
                String first100 = h.substring(0, h.length() > 100 ? 100 : h.length());
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "processFinish() - APOD_SEARCH_LOAD_RANGE: JSON exception: " + first100);
                // TODO - now display a dialog - we might trigger email log sender
                new dialogDisplay(MainActivity.this,
                        "processFinish() - No JSON Array received\nContent: '" +
                                first100 + "......'", "APOD_SEARCH_LOAD_RANGE debug");
            }

        } else if (tag.equals("APOD_LOAD_RANGE")) {
            // TODO: this can be done by apod_search_load_range as well with isSearch = false
            String h = (String) output;
            try {
                JSONArray newarray = new JSONArray(h);
                for (int i = 0; i < newarray.length(); i++) {
                    spaceItem item = utils.createSpaceItemFromJsonString(getApplicationContext(),
                            newarray.getJSONObject(i).toString());
                    // we could filter here. if item.title contains string....
                    utils.insertSpaceItem(myList, item);
                }
                getMissingApodInfos();
                adp.notifyDataSetChanged();
            } catch (JSONException e) {
                e.printStackTrace();
                String first100 = h.substring(0, h.length() > 100 ? 100 : h.length());
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "processFinish() - APOD_LOAD_RANGE: JSON exception: " + first100);
                // TODO - now display a dialog - we might trigger email log sender
                new dialogDisplay(MainActivity.this,
                        "processFinish() - No JSON Array received\nContent: '" +
                                first100 + "......'", "APOD_LOAD_RANGE");
            }

        } else if (tag.startsWith("THUMB_")) {
            // Thumbnail loading is the last step in the apod processing chain
            int idx = Integer.parseInt(tag.replace("THUMB_",""));
            Log.d("HFCM", "processFinish(), tag = " + tag);
            myList.get(idx).setThumbLoadingState(View.INVISIBLE);
            if (output instanceof Bitmap) {
                // 13.03.2018 -> annoying stuff all the time, finally do it !!!!!!!!!!!!!!!!!!!!!!!!
                // CORRECTION FOR INTERCHANGED HIRES/LOWRES LINKS. Sure, we might have already used
                // the hires image to create the thumbnail, but this is the first point in time, we
                // have BOTH sizes available to be able to exchange the links in case NASA did not
                // provide the correct links, which unfortunately happens quite often. This is the
                // point in time to correct this in our local database (currently local json file,
                // which might be changed to sqlite database approach later (search funtion))
                int nasaLowWidth = ((Bitmap) output).getWidth();
                int nasaLowHeight = ((Bitmap)output).getHeight();
                String sz = myList.get(idx).getHiSize();
                // take care of 'java.lang.NumberFormatException: Invalid int: ""'
                if (sz.matches("\\d+x\\d+")) {
                    String[] hs = sz.split("x");
                    int nasaHighWidth = Integer.valueOf(hs[0]);
                    int nasaHighHeight = Integer.valueOf(hs[1]);
                    // get the pixels count to rule out aspect ratio - good idea???
                    int nasaLow = nasaLowWidth * nasaLowHeight;
                    int nasaHigh = nasaHighWidth * nasaHighHeight;
                    if (nasaHigh >= nasaLow) {
                        // highres >= lowres - no correction needed
                        myList.get(idx).setLowSize(String.valueOf(nasaLowWidth) + "x" +
                                String.valueOf(nasaLowHeight));
                    } else {
                        // highres < lowres - need correction
                        utils.logAppend(getApplicationContext(),
                                MainActivity.DEBUG_LOG,
                                "NASA hires/lowres correction required...");
                        myList.get(idx).setLowSize(String.valueOf(nasaHighWidth) + "x" +
                                String.valueOf(nasaHighHeight));
                        myList.get(idx).setHiSize(String.valueOf(nasaLowWidth) + "x" +
                                String.valueOf(nasaLowHeight));
                        // exchange hires/lowres url strings as well for the correction case only
                        String tmpHigh = myList.get(idx).getHires();
                        myList.get(idx).setHires(myList.get(idx).getLowres());
                        myList.get(idx).setLowres(tmpHigh);
                    }
                }

                // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
                //thumbFile = new File(getApplicationContext().getFilesDir(), wkItem.getThumb());
                Bitmap thumbnail = ThumbnailUtils.extractThumbnail((Bitmap) output, 120, 120);
                myList.get(idx).setBmpThumb(thumbnail);
                utils.writeJPG(getApplicationContext(), myList.get(idx).getThumb(), thumbnail);
            } else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        tag + ": no Bitmap returned");
                // maybe set a "not available" image instead of leaving black
            }
            adp.notifyDataSetChanged();

            unfinishedApods--;
            Log.d("HFCM", "processFinish() - remaining unfinished APODs: " + unfinishedApods);
            if (unfinishedApods == 0) {
                parent = new JSONArray(); // other way to cleanup the array?
                for (int i = myList.size()-1; i >= 0; i--) {
                    JSONObject apodObj = utils.createJsonObjectFromSpaceItem(myList.get(i));
                    parent.put(apodObj);
                    jsonData = parent.toString();   // FIX MISSING NEW APOD AFTER ROTATE
                }
                utils.writeJson(getApplicationContext(), localJson, parent);
                File dir = new File(getFilesDir().getPath());
                String[] names = dir.list();
                for (String name : names) {
                    if (name.startsWith(apodJobService.APOD_SCHED_PREFIX)) {
                        File todelete = new File(getFilesDir(), name);
                        Log.d("HFCM", "Deleting scheduled apod: " + name);
                        if (!todelete.delete()) {
                            Log.e("HFCM", "Error deleting file: " + name);
                        }
                    }
                }
                // Once all items are in, get the newest date and set into sharedprefs
                long latest = utils.getNASAEpoch();
                utils.logAppend(getApplicationContext(),
                        DEBUG_LOG,
                        "Setting LAST_UI_LAUNCH_EPOCH to " + latest + " (" + utils.getNASAStringFromEpoch(latest) + ")");
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putLong("LAST_UI_LAUNCH_EPOCH", latest);
                editor.apply();
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
            // TODO - good idea to refresh after VIM reload?
            adp.notifyDataSetChanged();
            // TODO Docu: json = new JSONTokener((String) output).nextValue();

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
     * Schedule the next wallpaper shuffle event. If 0 is passed, this means, that shuffle option is
     * enabled, but no hour is selected. In this case, the shuffle job is cancelled.
     * @param minLatency time to next in milliseconds (0 for no times selected)
     */
    private void scheduleShuffle(long minLatency) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            if (minLatency == 0) {
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "scheduleShuffle() - no shuffle times set, disabling shuffle");
                utils.cancelJob(getApplicationContext(), JOB_ID_SHUFFLE);
                return;
            }
            utils.logAppend(getApplicationContext(), DEBUG_LOG,
                    "scheduleShuffle() - scheduling SHUFFLE for " + minLatency/1000 + " seconds");
            ComponentName serviceComponent = new ComponentName(this, shuffleJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_SHUFFLE, serviceComponent);
            builder.setMinimumLatency(minLatency);
            builder.setOverrideDeadline(minLatency + 15000);
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", 1);
            builder.setExtras(extras);
            JobInfo jobInfo = builder.build();
            //Log.i("HFCM", "SHUFFLE jobinfo: " + jobInfo.toString());
            JobScheduler sched = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (sched != null) {
                sched.schedule(jobInfo);
            }
        }
    }

    /**
     * Schedule the background loading of daily APOD json metadata.
     */
    private void scheduleApod(long minLatency, long add_deadline) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, apodJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(apodJobService.JOB_ID_APOD,
                    serviceComponent);
            builder.setMinimumLatency(minLatency);
            builder.setOverrideDeadline(minLatency + add_deadline);
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", 0);
            //extras.putString("URL", nS());
            extras.putString("URL", sharedPref.getBoolean("get_apod_simulate", false) ? dAS() : nS());
            // java.lang.IllegalAccessError: Method 'void android.os.BaseBundle.putBoolean(java.lang.String, boolean)' is inaccessible to class 'de.herb64.funinspace.MainActivity'
            //extras.putBoolean("PRE_LOLLOPOP_TLS", sharedPref.getBoolean("enable_tls_pre_lollipop", true));
            builder.setExtras(extras);
            JobInfo jobInfo = builder.build();
            Log.i("HFCM", "APOD jobinfo: " + jobInfo.toString());
            JobScheduler sched = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (sched != null) {
                sched.schedule(jobInfo);
            }
        }
    }

    /**
     * Called if app is launched the first time at installation.
     */
    private void initialLaunch() {
        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                "initialLaunch()");
        Bundle fragArguments = new Bundle();
        fragArguments.putString("TITLE",
                "Welcome to FunInSpace");
        long connecttime = utils.testSocketConnect(1000);
        int type = utils.getActiveNetworkType(getApplicationContext());
        String tname = utils.getActiveNetworkTypeName(getApplicationContext());
        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                "initialLaunch() - network '" + tname + "' (" + connecttime + "ms)");
        switch (type) {
            case ConnectivityManager.TYPE_MOBILE:
                fragArguments.putString("MESSAGE",
                        String.format(Locale.getDefault(),
                                getString(R.string.init_mobile),
                                INIT_DAYS_TO_LOAD));
                fragArguments.putString("POS",
                        getString(R.string.hfcm_yes));
                fragArguments.putString("NEG",
                        getString(R.string.hfcm_no));
                break;
            case ConnectivityManager.TYPE_WIFI:
                fragArguments.putString("MESSAGE",
                        String.format(Locale.getDefault(),
                                getString(R.string.init_wifi),
                                INIT_DAYS_TO_LOAD));
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

        /*SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean("INITIAL_LAUNCH", false);
        editor.apply();*/
        FragmentManager fm = getSupportFragmentManager();
        confirmDialog confirmdlg = new confirmDialog();
        confirmdlg.setArguments(fragArguments);
        confirmdlg.show(fm, "INITLAUNCH");
    }

    /**
     * Delete items specified in array list of indices.
     * TODO: prepare for undo provided by snackbar.
     * - no immediate image delete, rename to del___ or similar to flag them for later deletion
     * - keep deleted entries to be able to add them again
     * - keep a backup of the previous json to add again
     * - DELETED_ITEMS shared pref must be undone as well
     * @param selected Arraylist of items indices to be deleted
     */
    private void deleteItems(ArrayList<Integer> selected) {
        Collections.sort(selected, Collections.<Integer>reverseOrder()); // BACK TO FRONT!!!
        boolean needShuffleListRefresh = false;
        for (int idx : selected) {
            // spaceAdapter.remove() has been overwritten, so that it deletes the image in the full
            // ArrayList plus (if filtered view) in the currently presented view as well.
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
                    Log.w("HFCM", "File delete for wallpaper did not return true");
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
            int numwp = utils.setWPShuffleCandidates(getApplicationContext(), myList);
            if (numwp < 2) {
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "deleteItems() - cancel shuffle due to <2 remaining wallpapers");
                utils.cancelJob(getApplicationContext(), JOB_ID_SHUFFLE);
            }
        }
    }

    /**
     * Undo deletion listener called by SnackBar
     */
    final View.OnClickListener undoListener = new View.OnClickListener() {

        @Override
        public void onClick(View view) {
            Log.i("HFCM", "Undo clicked...");
            // TODO: implement - see comments in deleteItems()
        }
    };


    /**
     * Try to terminate the app, which might be useful if starting without network at initial launch
     */
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
     * TODO: attachment does not work with some apps, e.g. inbox (markus) - seems to be solved
     *       https://stackoverflow.com/questions/15946297/sending-email-with-attachment-using-sendto-on-some-devices-doesnt-work
     *       might be because ACTION_SENDTO is bad and we should use ACTION_SEND
     * TODO: investigate Telekom mail app issue reported by Heiko - no mail content, only topic
     *       and attachments - seems to be caused by the Telekom app... see extra infos
     */
    private void emailLogFileToHerbert() {
        // TODO AVD failures - seems to be known
        // https://stackoverflow.com/questions/27528236/mailto-android-unsupported-action-error
        //Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto",vE(), null));
//        Intent u = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", vE(), null));
//        u.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.test_mail_subject));
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

        // ADD A SINGLE FILE ATTACHMENT TO THE MAIL
        // E-mail apps do not have access to my storage - prohibited by Android security
        // - use external storage
        // - create a provider - this is what we do here - see lalatex docu
        ArrayList<Uri> attachmentUris = new ArrayList<>();
        File log_file = new File(getApplicationContext().getFilesDir(), DEBUG_LOG);
        File nasa_file = new File(getApplicationContext().getFilesDir(), localJson);

        //Uri contentUri = FileProvider.getUriForFile(MainActivity.this,
        //        FILE_PROVIDER,
        //        log_file);

        if (log_file.exists()) {
            attachmentUris.add(FileProvider.getUriForFile(MainActivity.this,
                    FILE_PROVIDER,
                    log_file));
        }
        if (nasa_file.exists()) {
            attachmentUris.add(FileProvider.getUriForFile(MainActivity.this,
                    FILE_PROVIDER,
                    nasa_file));
        }
        StringBuilder logbuilder = new StringBuilder("emailLogFileToHerbert(): Attachments:");
        for (Uri uri : attachmentUris) {
            logbuilder.append(String.format(" %s", uri.toString()));
        }
        utils.logAppend(getApplicationContext(), DEBUG_LOG, logbuilder.toString());

        /*if (log_file.exists()) {
            i.putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(MainActivity.this,
                            FILE_PROVIDER,
                            log_file));
            //utils.logAppend(getApplicationContext(), DEBUG_LOG,
            //        "emailLogFileToHerbert(): Attachment URI: '" + contentUri.toString() + "'");
        }*/

        //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    // TODO check

        // Finding matching apps for the intent - see more details in lalatex docu
        // a) if (i.resolveActivity(getPackageManager()) != null)
        // b) try {startActivity(Intent.createChooser(i, "Send mail..."));}
        //    catch (android.content.ActivityNotFoundException ex) {}
        // c) List<ResolveInfo> pkgs = getPackageManager().queryIntentActivities(i, 0);

        List<ResolveInfo> pkgs = getPackageManager().queryIntentActivities(i, 0);   // flags?
        List<LabeledIntent> intents = new ArrayList<>();
        if(pkgs.size() == 0) {
            new dialogDisplay(MainActivity.this,
                    getString(R.string.no_email_client));
        } else {
            for (ResolveInfo pkg : pkgs) {

                // send multiple should also allow for multiple files
                Intent intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.setComponent(new ComponentName(pkg.activityInfo.packageName,
                        pkg.activityInfo.name));
                intent.putExtra(Intent.EXTRA_EMAIL,
                        new String[]{vE()});
                intent.putExtra(Intent.EXTRA_SUBJECT,
                        getString(R.string.test_mail_subject));
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,
                        attachmentUris);
                intents.add(new LabeledIntent(intent,
                        pkg.activityInfo.packageName,
                        pkg.loadLabel(getPackageManager()),
                        pkg.icon));

                // see more infos in lalatex -
                // TODO: how to restrict grant to one selected app? / default app
                // interesting: getting fallback on my 5.1 avd:
                // 2018.01.24-12:08:00:008 > emailLogFileToHerbert(): Granting shared rights for package: com.android.fallback
                // this seems to be caused by the AVD, so test, if happens on real device
                // com.google.email was not found - which is on my elephone as well and worked
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "emailLogFileToHerbert(): Granting shared rights for package: " +
                                pkg.activityInfo.packageName);
                for (Uri uri : attachmentUris) {
                    grantUriPermission(pkg.activityInfo.packageName,
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }

            Intent chosenIntent = Intent.createChooser(intents.remove(intents.size() - 1),
                    "Choose email client:");
            chosenIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                    intents.toArray(new LabeledIntent[intents.size()]));
            startActivity(chosenIntent);

            // While the intent has been created using SENDTO (to filter out whatsapp etc..), the
            // actual action used to send the e-mail is set to ACTION_SEND. This should allow for
            // attachments also with other mail clients, e.g. "inbox"
            //i.setAction(Intent.ACTION_SEND_MULTIPLE);
            //startActivity(Intent.createChooser(i, "Send mail by"));
            //startActivity(i);
        }
    }


    /**
     * Called by thumbClickListener. This has been put into a separate function to be called by
     * Comfirm Dialog in case no wifi is available but required for loading hires/video data.
     * @param idx index into space item list
     */
    private void processThumbClick(int idx) {
        String hiresFileBase = myList.get(idx).getThumb().replace("th_", "");
        File hiresFile = new File(getApplicationContext().getFilesDir(), "hd_" + hiresFileBase);
        String hiresUrl;
        if (hiresFile.exists()) {
            hiresUrl = hiresFile.getAbsolutePath();
        } else {
            hiresUrl = myList.get(idx).getHires();
        }
        String media = myList.get(idx).getMedia();

        if (!utils.isNetworkConnected(getApplicationContext())) {
            new dialogDisplay(MainActivity.this,
                    getString(R.string.no_network_for_hd),
                    getString(R.string.no_network));
            return;
        }

        if (mActionMode != null) {
            mActionMode.finish();
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
                hiresIntent.putExtra("explanation", myList.get(idx).getExplanation());
                hiresIntent.putExtra("title", myList.get(idx).getTitle());
                // forResult now ALWAYS to get logstring returned for debugging
                startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                break;
            case M_YOUTUBE:
                String thumb = myList.get(idx).getThumb();
                if (reader != null) {
                    reader.stop();
                }
                // We get the ID from thumb name - hmmm, somewhat dirty ?
                if (sharedPref.getBoolean("youtube_fullscreen", false)) {
                    playYouTubeFullScreen(thumb.replace("th_", "").replace(".jpg", ""));
                } else {
                    playYouTube(thumb.replace("th_", "").replace(".jpg", ""));
                }
                break;
            case M_VIMEO:
                if (reader != null) {
                    reader.stop();
                }
                playVimeo(hiresUrl);
                break;
            case M_MP4:
                if (reader != null) {
                    reader.stop();
                }
                playMP4(hiresUrl);
                break;
            default:
                new dialogDisplay(MainActivity.this, "Unknown media: " + media, "Warning");
                break;
        }
    }


    /**
     * Used for debugging date issues
     * TODO: investigate 03 + 04.11. clock is 1 hour off... ( https://www.epochconverter.com/ )
     * This could be put into utils.java...
     */
    private void listItemsDateInfo() {

        SimpleDateFormat fmtCHECK = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss", loc);
        fmtCHECK.setTimeZone(tzNASA);
        for (spaceItem item : myList) {
            Log.i("HFCM", String.format(loc, "%s - %s",
                    fmtCHECK.format(item.getDateTime()),
                    item.getTitle()));
        }
    }

    /**
     * Handle any updates found at app startup. This allows to do any reformatting etc.. Currently,
     * no activities are needed, but at later times, this might be useful
     */
    private void processAppUpdates() {
        // prefs contain the version that has been present at last run of the app
        int versionCodeInPrefs = sharedPref.getInt("versioncode", -1);

        // set these for testing only
        //versionCodeInPrefs = 201801242;
        //versionCodeCurrent = 201802031;

        if (versionCodeInPrefs < versionCodeCurrent) {
            utils.logAppend(getApplicationContext(), DEBUG_LOG,
                    "Version Update: " + versionCodeInPrefs + " to " + versionCodeCurrent);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putInt("versioncode", versionCodeCurrent);
            editor.apply();
            // -1: this is initial inst, just return
            if (versionCodeInPrefs == -1) {
                return;
            }
            String updateString = "";
            // CHANGES DONE FOR VERSION UPDATES
            // 21.01.2018
            // Changes from 201801192 > 201801241: read_out now for image viewing, default "true"
            if (versionCodeInPrefs <= 201801192 && versionCodeCurrent >= 201801242) {
                //editor = sharedPref.edit();
                //editor.putBoolean("read_out", true);
                //editor.apply();
            }
            // Revert change from 201801192 > 201801241 in default option
            if (versionCodeInPrefs == 201801241 && versionCodeCurrent >= 201801242) {
                editor = sharedPref.edit();
                editor.putBoolean("read_out", false);   // revert change from 201801241
                editor.apply();
                updateString += getString(R.string.version_update_201801242) + "\n";
            }
            // Enable APOD load by default
            if (versionCodeInPrefs <= 201801242 && versionCodeCurrent >= 201802031) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    editor = sharedPref.edit();
                    editor.putBoolean("apod_bg_load", true); // change default to true
                    editor.apply();
                    long ms2NextApod = utils.getMsToNextApod(getApplicationContext());
                    scheduleApod(ms2NextApod, apodJobService.DEADLINE_DELAY);
                    updateString += getString(R.string.version_update_201801281) + "\n";
                }
            }
            // Fixing for 05.03.2018 bug with html and archive search
            if (versionCodeInPrefs <= 201802031 && versionCodeCurrent >= 201803261) {
                // TODO also delete the logfile
                updateString += getString(R.string.version_update_201803261) + "\n";
            }

            // Fixing for 02.06.2019 bug with ustream.tv lifestream url
            if (versionCodeInPrefs < 201906111 && versionCodeCurrent >= 201006111) {
                updateString += getString(R.string.version_update_201906111) + "\n";
            }

            // Next version:
            // 1. delete logfile or have it shortened with turnaround
            // 2. delete local copies of nasa json files - no longer needed
            // 3. introduce a thumbnail sub-directory ?

            if (!updateString.isEmpty()) {
                new dialogDisplay(this,
                        updateString,
                        getString(R.string.version_update_title));
            }
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
    //  O L D    C O D E   F R A G M E N T S
    ////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Retired function to get the latest APOD.
     * Check the age of the current latest image and avoid any NASA query, if it is already up to
     * date. The time to next apod is calculated, and if this is found to be larger than 0, we do
     * not need to load a new item from NASA.
     */
    /*private void getLatestAPOD() {
        long timeToNext = 0;

        if (!myList.isEmpty()) {
            // FIRST item in myList is latest available item. This is the LAST item in JSON.
            ArrayList<Long> epochs = utils.getNASAEpoch(myList.get(0).getDateTime());
            timeToNext = epochs.get(2);
        }
        if (timeToNext > 0) {
            // No APOD needs to be loaded (we are done with myList). Trigger apod info completion
            // in case we have some infos stored by scheduler
            Toast.makeText(MainActivity.this,
                    String.format(loc, getString(R.string.apod_already_loaded),
                            (float)timeToNext/(float)3600000),
                    Toast.LENGTH_LONG).show();


            //SharedPreferences.Editor editor = sharedPref.edit();
            //editor.putLong("LATEST_APOD_EPOCH", myList.get(0).getDateTime());
            //editor.apply();

            getMissingApodInfos();
        } else {
//            if (sharedPref.getBoolean("get_apod", true)) {
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
//            } else {
//                new dialogDisplay(this, getString(R.string.warn_apod_disable),
//                        getString(R.string.reminder));
//            }
        }
    }*/

    /* RETIRED processFinish() TAG for archive search with syncing via old dropbox sync function
        } else if (tag.equals("APOD_SEARCH_DB_SYNC_VERSION")) {
            //        add the search string and only add items matching the search term into the
            //        new list. This even might be done by load_range and can be retired at all ?
            String h = (String) output;
            ArrayList<spaceItem> newList = new ArrayList<>();
            if (utils.isJson(h) == utils.JSON_ARRAY) {
                try {
                    JSONArray newarray = new JSONArray(h);
                    Set<String> deleted_items = sharedPref.getStringSet("DELETED_ITEMS",
                            new HashSet<String>());
                    Set<String> deleted_set = new HashSet<>(deleted_items);  // USE A COPY!!!
                    Boolean recoveredAnyDeleted = false;
                    for (int i = 0; i < newarray.length(); i++) {
                        spaceItem item = utils.createSpaceItemFromJsonString(getApplicationContext(),
                                newarray.getJSONObject(i).toString());
                        // we can filter here: if item.title contains a search string - see new global variable for that

                        // handle items on deleted list
                        if (deleted_items.contains(item.getTitle())) {
                            if (restoreDeletedApodsOnSearch) {
                                // insertSpaceItem does not insert existing ones again
                                utils.insertSpaceItem(newList, item);
                                deleted_set.remove(item.getTitle());
                                recoveredAnyDeleted = true;
                                Log.i("HFCM", "Recovering deleted item: " + item.getTitle());
                            }
                        } else {
                            utils.insertSpaceItem(newList, item);
                        }
                    }
                    // update shared prefs deleted items
                    if (recoveredAnyDeleted) {
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putStringSet("DELETED_ITEMS", deleted_set);
                        editor.apply();
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                String first100 = h.substring(0, h.length() > 100 ? 100 : h.length());
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "APOD_SEARCH_DB_SYNC_VERSION: JSON exception: " + first100);
                new dialogDisplay(MainActivity.this,
                        "No JSON object received\nContent: '" +
                                first100 + "......'", "APOD_SEARCH_DB_SYNC_VERSION Debug");
            }
            JSONArray testarray = new JSONArray();
            //String jsoncontent;
            for (int i = newList.size()-1; i >= 0; i--) {
                JSONObject testObj = utils.createJsonObjectFromSpaceItem(newList.get(i));
                testarray.put(testObj);
                //jsoncontent = testObj.toString();   // FIX MISSING NEW APOD AFTER ROTATE
            }
            Log.i("HFCM", "Have loaded " + newList.size() + " new item(s) via search");
            if (testarray != null && testarray instanceof JSONArray) {
                resyncWithDropbox((JSONArray) testarray, restoreDeletedApodsOnSearch);
            }
            */

    /* Retired processFinish() tags...
    } else if (tag.equals("DROPBOX_REFRESH")) {
            Object json;
            try {
                json = new JSONTokener((String) output).nextValue();
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            if (json != null && json instanceof JSONArray) {
                //resyncWithDropbox((JSONArray) json, false);
            }

        } else if (tag.equals("DROPBOX_FORCE_ REFRESH")) {
            Object json;
            try {
                json = new JSONTokener((String) output).nextValue();
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
            if (json != null && json instanceof JSONArray) {
                //resyncWithDropbox((JSONArray) json, true);
            }*/


    /**
     * Retired function for dropbox sync
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
    /*private void resyncWithDropbox(JSONArray dropbox, boolean forceFull) {
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
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "resyncWithDropbox() - " + e.getMessage());
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
                    Log.d("HFCM", "DB end - adding local '" + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    continue;
                }
                // Dropbox matches local title - add into new json array
                if (ltitle.equals(dbtitle)) {
                    Log.d("HFCM", "DB idx: " + dbidx + " - Adding local (A): " + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    dbidx++;
                } else if (!dropbox_items.contains(ltitle)) {
                    Log.d("HFCM", "Info - DB idx: " + dbidx + " - Current local title '" + ltitle + "' not on dropbox");
                    while (dbepoch <= lepoch) {
                        if (!deleted_items.contains(dbtitle) || forceFull) {
                            Log.d("HFCM", "DB idx: " + dbidx + " - Adding from dropbox(B) '" + dbtitle + "'");

                            // BBBBBBBBBBBBBBBB
                            if (forceFull) {
                                deleted_items.remove(dbtitle);
                            }

                            newjson.put(dropbox.getJSONObject(dbidx));
                            db_add++;
                        } else {
                            Log.d("HFCM", "DB idx: " + dbidx + " - Skipping deleted from dropbox (B): '" + dbtitle + "'");
                            db_skip++;
                        }
                        dbidx++;
                        if (dbidx == dropbox.length()) {
                            break;
                        }
                        dbepoch = dropbox.getJSONObject(dbidx).getJSONObject("Content").getLong("DateTime");
                        dbtitle = dropbox.getJSONObject(dbidx).getJSONObject("Content").getString("Title");
                    }
                    Log.d("HFCM", "DB idx: " + dbidx + " - Adding local (B): " + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    dbidx++;
                } else {
                    // local title is on dropbox, just fill any possible missing items
                    while (!dbtitle.equals(ltitle)) {
                        if (!deleted_items.contains(dbtitle) || forceFull) {
                            Log.d("HFCM", "DB idx: " + dbidx + " - Adding from dropbox(C) '" + dbtitle + "'");

                            // BBBBBBBBBBBBBBBB
                            if (forceFull) {
                                deleted_items.remove(dbtitle);
                            }

                            newjson.put(dropbox.getJSONObject(dbidx));
                            db_add++;
                        } else {
                            Log.d("HFCM", "DB idx: " + dbidx + " - Skipping deleted from dropbox (C): '" + dbtitle + "'");
                            db_skip++;
                        }
                        dbidx++;
                        dbtitle = dropbox.getJSONObject(dbidx).getJSONObject("Content").getString("Title");
                    }
                    Log.d("HFCM", "DB idx: " + dbidx + " - Adding local (C): '" + ltitle + "'");
                    newjson.put(lobj);
                    l_add++;
                    dbidx++;
                }

            } catch (JSONException e) {
                e.printStackTrace();
                Log.e("HFCM", "resyncWithDropbox() - " + e.getMessage());
                utils.logAppend(getApplicationContext(), DEBUG_LOG,
                        "resyncWithDropbox() - " + e.getMessage());
            }
        }
        // Add remaining items not in local json from dropbox
        for (int didx = dbidx; didx < dropbox.length(); didx ++) {
            String title;
            try {
                title = dropbox.getJSONObject(didx).getJSONObject("Content").getString("Title");
                Log.d("HFCM", "DB idx: " + dbidx + " - Adding from dropbox (D): '" + title + "'");
                newjson.put(dropbox.getJSONObject(didx));
                db_add++;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        utils.logAppend(getApplicationContext(),
                DEBUG_LOG,
                String.format(loc, "resyncWithDropbox(): LOCAL ADD=%d, DB ADD=%d, DB SKIP=%d",
                        l_add, db_add, db_skip));

        // how to handle the shared prefs DELETED_ITEMS info?
        //SharedPreferences.Editor editor = sharedPref.edit();
        //editor.putStringSet("DELETED_ITEMS", deleted_items);
        //editor.apply();

        // Activate the contents of the new json array
        parent = newjson;
        utils.writeJson(getApplicationContext(), localJson, parent);
        myList.clear();
        addItems();
        getMissingApodInfos();
        utils.cleanupFiles(getApplicationContext(), myList, hd_maxmempct, -1);
        adp.notifyDataSetChanged();
    }*/
}
