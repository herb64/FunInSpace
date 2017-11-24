package de.herb64.funinspace;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.internal.view.SupportMenuItem;
import android.support.v4.view.MenuItemCompat;
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
import java.io.FileFilter;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import de.herb64.funinspace.helpers.deviceInfo;
import de.herb64.funinspace.helpers.dialogDisplay;
import de.herb64.funinspace.helpers.utils;
import de.herb64.funinspace.models.spaceItem;

// TODO Log statements: Log.d etc.. should not be contained in final release - how to automate?
// see https://stackoverflow.com/questions/2446248/remove-all-debug-logging-calls-before-publishing-are-there-tools-to-do-this

// TODO: handle possible errors returned: found on 02.08.2017 - led to null bitmap returned
// so the http return code seems to be passed in json...
/*
{
        "code": 500,
        "msg": "Internal Service Error",
        "service_version": "v1"
        }*/

// TODO: if missing or bad hires image url for an image, we need to handle this: fall back to lowres
//       might be an option. But take care, because
//       on 10.10.2017, it failed, although link was ok - network issue most likely, CHECK!!!

// TODO: network check - timeouts, 404 etc..  --- UNMETERED NETWORKS - check this
// https://developer.android.com/reference/android/net/NetworkCapabilities.html

/*
 * The MainActivity Class for FunInSpace
 */
public class MainActivity extends AppCompatActivity
        implements ratingDialog.RatingListener, asyncLoad.AsyncResponse, confirmDialog.ConfirmListener {

    private spaceItem apodItem;                     // the latest item to be fetched
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
    private int currentWallpaperIndex = -1;
    protected TimeZone tzNASA;
    protected Calendar cNASA;
    protected SimpleDateFormat formatter;

    private TextToSpeech tts;

    // Using JNI for testing with NDK and C code in a shared lib .so file
    static {
        System.loadLibrary("hfcmlib");
    }
    public native String yT();
    public native String nS();
    public native String vE();

    // ========= CONSTANTS =========
    //public static String TAG = MainActivity.class.getSimpleName();

    private static final String ABOUT_VERSION = "0.4.3 (alpha)\nBuild Date 2017-11-24\n\nFor special friends only :)\n";

    private static final int HIRES_LOAD_REQUEST = 1;
    private static final int GL_MAX_TEX_SIZE_QUERY = 2;
    private static final int SETTINGS_REQUEST = 3;

    private static final String DROPBOX_JSON = "https://dl.dropboxusercontent.com/s/3yqsmthlxth44w6/nasatest.json";
    // a simulated NASA json file for debugging and testing purpose - enabled as debug user option
    private static final String APOD_SIMULATE = "https://dl.dropboxusercontent.com/s/agfxia2f6or5plk/apod-simulate.json";
    // interesting: the name behind the link is not important, notfound.json worked as well, need to change the ID!!
    //private static final String APOD_NOTFOUND = "https://dl.dropboxusercontent.com/s/mzidejp3qfnosff/notfound.json";
    //private static final int KIB = 1024;
    //private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;

    // strings for media type classification
    protected static final String M_IMAGE = "image";
    protected static final String M_YOUTUBE = "youtube";
    protected static final String M_VIMEO = "vimeo";
    protected static final String M_MP4 = "mp4";      // first nasa mp4 on 13.11.2017
    private static final String M_VIDEO_UNKNOWN = "unknown-video";

    // dealing with the number of displayed lines in the Explanation text view
    protected static final int MAX_ELLIPSED_LINES = 2;
    private static final int MAX_LINES = 1000;      // hmm, ridiculous, but safe // TODO think
    protected static final int MAX_ITEMS = 10000;     // limit of items - for id handling

    private static final String WP_CURRENT_BACKUP = "w_current.jpg";
    protected static final int WP_NONE = 0;
    protected static final int WP_EXISTS = 1;// << 1;
    protected static final int WP_ACTIVE = 3;// << 2;
    //private static final int DEFAULT_MAX_STORED_WP = 20;      // limit number of stored wallpapers                  !!!!! TODO: used for cleanup and shuffle option

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //https://developer.android.com/design/patterns/actionbar.html
        ActionBar actbar = getSupportActionBar();
        actbar.setIcon(R.mipmap.ic_launcher);
        actbar.setDisplayShowTitleEnabled(false);
        // we can set our own custom view here...
        //actbar.setDisplayShowCustomEnabled(true);
        //actbar.setCustomView();

        // get the locale - using default leads to several warnings with String.format()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = getResources().getConfiguration().locale;
        }

        // READ PREFERENCE SETTINGS FROM DEFAULT SHARED PREFERENCES
        // TODO check, if this is rotation proof, or if we bettger should get prefs each time
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        // Initialize flags to true, so that first open of preferences dialog, which triggers the
        // prefChangeListener, does change it back to false in this case.
        dateFormatChanged = !sharedPref.contains("date_format");
        thumbQualityChanged = !sharedPref.contains("rgb565_thumbs");
        sharedPref.registerOnSharedPreferenceChangeListener(prefChangeListener);

        // Timezone and Calendar objects used to base our timestamps on current NASA TimeZone
        // We must interpret the stored epoch value as seen from NASA time!
        tzNASA = TimeZone.getTimeZone("America/New_York"); // NASA server is within this TZ
        cNASA = Calendar.getInstance(tzNASA);
        // TODO - make display format of date configurable in settings
        //String fmt = sharedPref.getString("date_format", getString(R.string.df_dd_mm_yyyy));
        //String fmt = sharedPref.getString("date_format", "dd.MMM.yyyy");
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
        // onCreate() and even the Toast within onActivityResult shows the correct value.
        devInfo = new deviceInfo(getApplicationContext());

        // Prepare the main ListView containing all our Space Items
        myItemsLV = (ListView) findViewById(R.id.lv_content);

        // 23.10.2017 - add another listener to the view which gets passed the ID as long as well
        // try to get the clicks within filtered view solved without the own mapping of indices
        // https://developer.android.com/reference/android/widget/AdapterView.OnItemClickListener.html
        /*myItemsLV.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Log.i("HFCM", "onItemClick: pos=" + i + " ID=" + l);
            }
        });*/

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
                // IMPORTANT: ID always contains index in FULL dataset. See spaceAdapter.getItemId()
                // POSITION is the index within the CURRENTLY presented view, which may be filtered
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
                // we might want to have a small disk symbol left down of thumb indicating the presence of a cached file...

                adp.notifyDataSetChanged();
            }

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
             * TODO: how could we show icons left to text in overflow, as done with main menu?
             * @param actionMode the action mode
             * @param menu the menu
             * @return return true
             */
            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                // Trick for menu to show the icons within overflow menu does not work
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
                            // adp.setNotifyOnChange(true); // TODO - test this for auto notify ?
                            adp.notifyDataSetChanged();
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


                                    // OLD - this one did always load the nasa remote image
                                    //hiresIntent.putExtra("hiresurl", myList.get(selected.get(0)).getHires());


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
                                    // 2 options: set existing as wp or remove existing wp
                                    switch (menuItem.getItemId()) {
                                        case R.id.cab_wallpaper:
                                            // Calling confirm dialog - this will set the wallpaper in callback
                                            fragArguments = new Bundle();
                                            fragArguments.putInt("IDX", selected.get(0));
                                            fragArguments.putString("TITLE", getString(R.string.wp_confirm_dlg_title));
                                            fragArguments.putString("MESSAGE", getString(R.string.wp_confirm_dlg_msg,
                                                    myList.get(selected.get(0)).getTitle()));
                                            fragArguments.putString("POS", getString(R.string.wp_confirm_dlg_pos_button));
                                            fragArguments.putString("NEG", getString(R.string.wp_confirm_dlg_neg_button));
                                            fm = getSupportFragmentManager();
                                            confirmDialog confirmdlg = new confirmDialog();
                                            confirmdlg.setArguments(fragArguments);
                                            confirmdlg.show(fm, "CONFIRMTAG");
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
                            /*File wp = new File(getApplicationContext().getFilesDir(),
                                    myList.get(selected.get(0)).getThumb().replace("th_", "wp_"));
                            if (!wp.exists()) { // TODO: could use wp status as well... ???
                                // Calling confirm dialog - this will set the wallpaper in callback
                                fragArguments = new Bundle();
                                fragArguments.putInt("IDX", selected.get(0));
                                fragArguments.putString("TITLE", getString(R.string.wp_confirm_dlg_title));
                                fragArguments.putString("MESSAGE", getString(R.string.wp_confirm_dlg_msg,
                                        myList.get(selected.get(0)).getTitle()));
                                fragArguments.putString("POS", getString(R.string.wp_confirm_dlg_pos_button));
                                fragArguments.putString("NEG", getString(R.string.wp_confirm_dlg_neg_button));
                                fm = getSupportFragmentManager();
                                confirmDialog confirmdlg = new confirmDialog();
                                confirmdlg.setArguments(fragArguments);
                                confirmdlg.show(fm, "CONFIRMTAG");
                            }*/
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

                        /*String wpfn = myList.get(selected.get(0)).getThumb().replace("th_", "wp_");
                        File wp = new File(getApplicationContext().getFilesDir(), wpfn);
                        if (wp.exists()) {
                            // Calling confirm dialog - this will set the wallpaper in callback
                            fragArguments = new Bundle();
                            fragArguments.putInt("IDX", selected.get(0));
                            fragArguments.putString("TITLE", getString(R.string.wp_confirm_dlg_title));
                            fragArguments.putString("MESSAGE", getString(R.string.wp_confirm_dlg_msg,
                                    myList.get(selected.get(0)).getTitle()));
                            fragArguments.putString("POS", getString(R.string.wp_confirm_dlg_pos_button));
                            fragArguments.putString("NEG", getString(R.string.wp_confirm_dlg_neg_button));
                            fm = getSupportFragmentManager();
                            confirmDialog confirmdlg = new confirmDialog();
                            confirmdlg.setArguments(fragArguments);
                            confirmdlg.show(fm, "CONFIRMTAG");
                        } else {
                            int maxAlloc = devInfo.getMaxAllocatable();
                            Intent hiresIntent = new Intent(getApplication(), ImageActivity.class);
                            hiresIntent.putExtra("hiresurl", myList.get(selected.get(0)).getHires());
                            hiresIntent.putExtra("listIdx", selected.get(0));
                            hiresIntent.putExtra("maxAlloc", maxAlloc);
                            hiresIntent.putExtra("maxtexturesize", maxTextureSize);
                            hiresIntent.putExtra("wallpaper_quality",
                                    sharedPref.getString("wallpaper_quality", "80"));
                            lastImage = myList.get(selected.get(0)).getTitle();
                            // wallpaper filename now as imagename
                            hiresIntent.putExtra("imagename", wpfn);
                            // call image activity with wallpaper selection mode at startup
                            hiresIntent.putExtra("wpselect", true);
                            Log.i("HFCM", "Changing wallpaper '" + wpfn + "'");
                            startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                        }*/
                        return true;
                    case R.id.cab_read:
                        // deprecations
                        // https://stackoverflow.com/questions/27968146/texttospeech-with-api-21
                        if (!tts.isSpeaking()) {
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
                        } else {
                            tts.stop();
                        }
                        return true;
                    default:
                        return false;
                }
            }

            @Override
            public void onDestroyActionMode(android.view.ActionMode actionMode) {
                // reset selection status for all items
                for (int idx = 0; idx < myList.size(); idx++) {
                    myList.get(idx).setSelected(false);
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
            // If local history json file does not exist, this is assumed to be a newly installed
            // app and we get some basic json data from dropbox for testing for now...
            jsonData = null;
            File jsonFile = new File(getApplicationContext().getFilesDir(), localJson);
            if (jsonFile.exists()) {
                jsonData = utils.readf(getApplicationContext(), localJson);
            }

            if (jsonData != null) {
                SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
                maxTextureSize = shPref.getInt("maxtexsize", 0);
                devInfo.setGlMaxTextureSize(maxTextureSize);
                // TODO: if shared preferences are lost for some reason, run texsize check again

                if (jsonData.equals("")) {
                    jsonData = "[]";
                }
                parent = null;

                try {
                    parent = new JSONArray(jsonData);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // Parse the local json file contents and add these to ArrayList
                addItems();
                checkMissingThumbs();
            } else {
                // jsonData is empty. It looks like we did not have a local json file yet, or it
                // is not a valid one. So we create a new empty parent array and attempt to load a
                // prefilled json file from our (hardcoded) testing dropbox link.

                // GL TEXTURE check only after installation, result written to shared preferences
                Intent texSizeIntent = new Intent(this, TexSizeActivity.class);
                startActivityForResult(texSizeIntent, GL_MAX_TEX_SIZE_QUERY);
                devInfo.setGlMaxTextureSize(maxTextureSize);

                parent = new JSONArray();
                Toast.makeText(MainActivity.this, "Initial installation - getting JSON base data " +
                                "from Herbert's DropBox and loading required images in background for " +
                                "thumbnail pictures generation to have some test data available...",
                        Toast.LENGTH_LONG).show();
                //new loadFromDropboxTask().execute(DROPBOX_JSON); // calls addItems() + adapter notify
                //asyncLoad loader = new asyncLoad(MainActivity.this, "DROPBOX_INIT");
                //loader.execute(DROPBOX_JSON);
                new asyncLoad(MainActivity.this, "DROPBOX_INIT").execute(DROPBOX_JSON);
            }

            // Get latest APOD item to append from NASA (or a simulation item from dropbox)
            getLatestAPOD();

            // JUST A CONVERSION UTILITY USED DURING DEVELOPMENT - CONVERTED EPOCH VALUES INTO A NEW
            // JSON FILE FOR UPLOAD TO DROPBOX - NEW TIMEZONE HANDLING NEEDS THIS CHANGE
            //updateEpochsInJsonDEVEL();

            tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if(status != TextToSpeech.ERROR) {
                        tts.setLanguage(Locale.UK);
                    }
                }
            });
        }
    }

    /**
     * Stop text to speech, if any active playback is running.
     */
    @Override
    protected void onStop() {
        super.onStop();
        if (tts != null && tts.isSpeaking()) {
            tts.stop();
        }
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
     *
     */
    @Override
    protected void onResume() {
        super.onResume();
    }


    /**
     * Interface implementation for Rating Dialog result: return from DialogFragment to Activity
     * This is called, if Rating Dialog OK button has been pressed
     * @param rating    the rating value to be set
     * @param selected  the item index, for which the rating has to be set
     */
    @Override
    public void updateRating(int rating, ArrayList<Integer> selected) {
        HashSet<String> titles = new HashSet<>();
        for (int idx : selected) {
            myList.get(idx).setRating(rating);
            titles.add(myList.get(idx).getTitle());
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
     *
     * Code has been moved to asyncLoad and orignal code now at end of this file... 26.10.2017
     */

    // CODE for processing apod json returned from asyncLoad...  former onpostexecute from apodtask
    /**
     * Create a new spaceItem from information returned in daily JSON
     * @params String
     */
    void createApodFromJson(String s) {
        String imgUrl = "";
        apodItem = new spaceItem();
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
            sMediaType = parent.getString("media_type");
            sDate = parent.getString("date");
            // TODO copyright not yet finalized - replace newline or not, limit length
            if (parent.has("copyright")) {
                sCopyright = "Copyright: " + parent.getString("copyright").
                        replaceAll(System.getProperty("line.separator"), " ");
            }
            sTitle = parent.getString("title"); // TODO: why not check here for already loaded ??? currently in lowresBitmapOnPostExecuteReplacement()
            imgUrl = parent.getString("url"); // TODO: 11.09.2017 - missing leads to strange effects of missing keys
            resource_uri = Uri.parse(imgUrl);
            if (parent.has("hdurl")) {
                sHiresUrl = parent.getString("hdurl");
            } else {
                // actually, we should not get here again after apod is already loaded.
                if (sMediaType.equals("image")) {
                    new dialogDisplay(this, "APOD does not have a link to hires image, will need to fallback to lowres", "Herbert TODO");
                }
            }
            sExplanation = parent.getString("explanation");
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("HFCM", e.getMessage());
            new dialogDisplay(this, "APOD JSON Exception:\n" + e.getMessage());
        }

        // At this point sMediaType contains the NASA delivered string. This gets changed now to
        // a more specific media type information. It's a little bit messy, because NASA delivered
        // an MP4 stream as media type "image" on 13.11.2017... need to catch that error situation
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
        // now using long int epoch value for date in spaceItem
        long epoch = utils.getNASAEpoch(loc).get(0);
        /*SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", loc);
        // normally we would use code as below for correct locale, but we parse a specific
        // format here :_ "date": "2017-09-17", as returned by NASA in their json
        // DateFormat dF = SimpleDateFormat.getDateInstance();
        long epoch = 0;
        try {
            dF.setTimeZone(tzNASA);
            dF.setCalendar(cNASA);      // not really needed here...
            epoch = dF.parse(sDate).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }*/
        apodItem.setDateTime(epoch);
        apodItem.setMedia(sMediaType);
        apodItem.setRating(0);
        apodItem.setLowSize("");
        apodItem.setHiSize("");

        // Keep a local copy of the original apod nasa json file returned by api
        // TODO: remove these as well on item deletion? They can never be recreated later on
        if (parent != null) {
            utils.writeJson(getApplicationContext(), String.valueOf(epoch) + ".json", parent);
        }

        // For VIMEO, "insert" an extra thread using oembed API to determine infos about the
        // thumbnail image URL before calling the imgLowResTask thread to load thumbnail bitmap
        // hmm, MediaMetadataRetriever might also be able to retrieve a thumbnail, but it is better
        // and also faster to retrieve the "offical" thumb...
        if (sMediaType.equals(M_VIMEO)) {   // TODO: test this
            //new vimeoInfoTask().execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
            Log.i("HFCM", "Launching asyncLoad for VIMEO info: " + sHiresUrl);
            new asyncLoad(MainActivity.this, "VIMEO_INFO").
                    execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
        } else {
            //new ImgLowresTask().execute(imgUrl);
            new asyncLoad(MainActivity.this, "IMG_LOWRES_LOAD").execute(imgUrl);
        }
    }

    // TODO - return values from asynctask - seems to work with execute().get()  but it blocks!!! - search topic: wait for asynctask to complete
    // ++++   >>>  https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
    //        >>>  asyncLoad.java is one of the results of this - now still make sure about memory leaks!!!!
    // private class ImgLowresTask extends AsyncTask<String, String, Bitmap> {
    // .. 26.20.2017 - now also retired and replaced by asyncLoad with bitmap support

    void finalizeApodWithLowresImage(Bitmap bitmap) {
        // "Good old ugly" version just iterating all items in ArrayList. This is ok. The first
        // item should match anyway, because latest image is on top of the list.
        for(int i=0; i<myList.size();i++) {
            if(apodItem.getTitle().equals(myList.get(i).getTitle())) {
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

        // JSON is an array of objects, which have a "Type" and a "Content". Type is just
        // a string, content is another JSON object.
        JSONObject apodObj = new JSONObject();
        JSONObject contentObj = new JSONObject();
        try {
            contentObj.put("Title", apodItem.getTitle());
            contentObj.put("DateTime", apodItem.getDateTime());
            contentObj.put("Copyright", apodItem.getCopyright());
            contentObj.put("Explanation", apodItem.getExplanation());
            contentObj.put("Lowres", apodItem.getLowres());
            contentObj.put("Hires", apodItem.getHires());
            contentObj.put("Thumb", apodItem.getThumb());
            contentObj.put("Rating", apodItem.getRating());
            contentObj.put("Media", apodItem.getMedia());
            contentObj.put("HiSize", apodItem.getHiSize());
            contentObj.put("LowSize", apodItem.getLowSize());
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            apodObj.put("Type", "APOD");
            apodObj.put("Content", contentObj);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        // add object to local json array and save json content to internal storage
        parent.put(apodObj);
        utils.writeJson(getApplicationContext(), localJson, parent);
        myList.add(0, apodItem);
        adp.notifyDataSetChanged();
        Toast.makeText(MainActivity.this, R.string.apod_load,
                Toast.LENGTH_LONG).show();
        myItemsLV.setSelection(0);
    }

    /**
     * Implementation of interface ConfirmListener in confirmDialog class. This class allows to
     * have a dialog's result to be processed only, if the Positive Button has been pressed.
     * For now, it's only used for wallpaper change confirmation.
     * @param idx index into list
     */
    @Override
    public void processConfirmation(int idx) {
        String wpfile = myList.get(idx).getThumb().replace("th_", "wp_");
        Log.i("HFCM", "Reached confirmation: " + wpfile);
        if (!wpfile.equals("")) {
            // update index values
            if (currentWallpaperIndex >= 0) {
                myList.get(currentWallpaperIndex).setWpFlag(WP_EXISTS);
            }
            myList.get(idx).setWpFlag(WP_ACTIVE);
            currentWallpaperIndex = idx;
            changeWallpaper(wpfile);
        }
    }

    /**
     * Implementation of interface ConfirmListener in confirmDialog class. This handles negative
     * button.
     * For now, it's only used for wallpaper change confirmation.
     * @param idx index
     */
    @Override
    public void processNegConfirm(int idx) {
        // The wp file was created during wp select (longpress) - so it exists and even if it is not
        // confirmed as active wp, the file exists, so immediately update wp status to be shown in
        // list
        myList.get(idx).setWpFlag(WP_EXISTS);
        adp.notifyDataSetChanged();
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
         * passing multiple tags? we could just combine this in a single string of special format
         * see https://developer.android.com/training/basics/firstapp/starting-activity.html
         * @param view The view
         */
        @Override
        public void onClick(View view) {
            // if (view.getTag() instanceof View) {}  // JUST FOR DOCU - tag can be a view as well
            int idx = (int) view.getTag();  // for "filtered" view - index into full list!
            /* Can't use this, because "small" ratingbars do not support interaction. So going
               for a separate dialog to set rating for items selected via contextual action mode
            if (idx >= 2*MAX_ITEMS) {
                RatingBar rb = (RatingBar) view;
            }*/
            // 11.11.2017 - moved ellipsized explanation text handling to OnItemClickListener()

            // Check, if a local copy for hires image exists. If yes, pass filepath to URL
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
            if (sharedPref.getBoolean("wifi_switch", false)
                    && !devInfo.isWifiActive()
                    && hiresUrl.startsWith("http")) {
                new dialogDisplay(MainActivity.this, getString(R.string.hires_no_wifi), "No Wifi");
                return;
            }

            // get maximum allocatable heap mem at time of pressing the button
            int maxAlloc = devInfo.getMaxAllocatable();
            Log.i("HFCM", "maximum alloc:" + String.valueOf(maxAlloc));

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
                    //hiresIntent.putExtra("imagename", lastImage);
                    // wallpaper filename now as imagename
                    //hiresIntent.putExtra("imagename", myList.get(idx).getThumb().replace("th_","wp_"));
                    //hiresIntent.putExtra("imagename", myList.get(idx).getThumb().replace("th_",""));
                    hiresIntent.putExtra("imagename", hiresFileBase);
                    // forResult now ALWAYS to get logstring returned for debugging
                    // if hires size is already
                    // TODO: how about running one extra thread to just query the hires image size, using MediaMetadataRetriever - would avoid startForResult...
                    startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                    break;
                case M_YOUTUBE:
                    String thumb = myList.get(idx).getThumb();
                    // We get the ID from thumb name - hmmm, somewhat dirty ?
                    if (sharedPref.getBoolean("youtube_fullscreen", false)) {
                        playYouTubeFullScreen(thumb.replace("th_", "").replace(".jpg", ""));
                    } else {
                        playYouTube(thumb.replace("th_", "").replace(".jpg", ""));
                    }
                    break;
                case M_VIMEO:
                    playVimeo(hiresUrl);
                    break;
                case M_MP4:
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
        // TODO - check other options - only first test with StandalonePlayer in lightbox mode
        // fullscreen: only in landscape, no rotate, mainactivity is recreated ... bad
        // lightbox_: better, but also recreates mainactivity
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
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == HIRES_LOAD_REQUEST) {
            if (resultCode == RESULT_OK) {
                // Get the wallpaper info from returned intent (returning bmp is bad - trx size)
                String testsize = data.getStringExtra("sizeHires");
                //String hUrl = data.getStringExtra("hiresurl");
                int listidx = data.getIntExtra("lstIdx",0);
                String logString = data.getStringExtra("logString");

                // Display a log dialog for image loader statistics (image memory / scaling)
                if (sharedPref.getBoolean("show_debug_infos", true)) {
                    new dialogDisplay(this, logString, lastImage);
                }

                // User dialog OK processConfirmation() kicks off changer thread later
                if (data.getStringExtra("wallpaperfile") != null) {
                    Bundle fragArguments = new Bundle();
                    //fragArguments.putString("RESULT", data.getStringExtra("wallpaperfile"));
                    fragArguments.putInt("IDX", listidx);
                    fragArguments.putString("TITLE", getString(R.string.wp_confirm_dlg_title));
                    fragArguments.putString("MESSAGE", getString(R.string.wp_confirm_dlg_msg,
                            myList.get(listidx).getTitle()));
                    fragArguments.putString("POS", getString(R.string.wp_confirm_dlg_pos_button));
                    fragArguments.putString("NEG", getString(R.string.wp_confirm_dlg_neg_button));
                    FragmentManager fm = getSupportFragmentManager();
                    confirmDialog dlg = new confirmDialog();
                    dlg.setArguments(fragArguments);
                    // dlg.setTargetFragment(); // only if calling from fragment, not from activity!
                    dlg.show(fm, "CONFIRMTAG");
                }

                if (myList.get(listidx).getHiSize().equals(testsize) ||
                        testsize.equals("no-change")) {
                    return;     // no action needed if hires size already in data // TODO maybe get during apod load using asyncload by testing from stream...
                }
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
                myList.get(listidx).setHiSize(testsize);
                adp.notifyDataSetChanged();
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
                    SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(this);
                    Log.i("HFCM", "Low quality thumbs enabled: " + shPref.getBoolean("rgb565_thumbs", false));
                    thumbQualityChanged = false;    // RESET !!!
                    // Just reload the complete list. This reloads all images with changed settings
                    // from the locally stored thumbnail images
                    myList.clear();
                    addItems();
                    adp.notifyDataSetChanged();
                }
                if (dateFormatChanged) {
                    dateFormatChanged = false;
                    // acutally, the SimpleDateFormat object does not seem to allow to change the
                    // format string, so we recreate the object...
                    String fmt = sharedPref.getString("date_format", getString(R.string.df_dd_mm_yyyy));
                    formatter = new SimpleDateFormat(fmt, loc); // "dd. MMMM yyyy"
                    formatter.setTimeZone(tzNASA);
                    formatter.setCalendar(cNASA);
                    adp.notifyDataSetChanged();
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
     * dialog activity is closed and might be a better place to handle changes. The following
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
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // 13.11.2017 - menu icons in overflow menu are visible now as well
        // https://stackoverflow.com/questions/18374183/how-to-show-icons-in-overflow-menu-in-actionbar
        // TODO: how to do that for contextual action bar overflow menu as well?
        if(menu instanceof MenuBuilder){
            MenuBuilder m = (MenuBuilder) menu;
            //noinspection RestrictedApi
            m.setOptionalIconsVisible(true);
        }

        // 20.11.2017 Switch MenuItem to SupportMenuItem and use noinspection RestrictedApi  TODO DOCU
        // to allow for registering expand listeners for action view
        final SupportMenuItem searchItem = (SupportMenuItem) menu.findItem(R.id.action_search); // 22.10.2017
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
                //adp.getFilter().filter("");                                                                                                         // TODO RE-TEST
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
                    utils.getFileStats(getApplicationContext(), loc),
                    "Infos (in development)");
            return true;
        }
        if (id == R.id.action_mail) {
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
            File attachment_file = new File(getApplicationContext().getFilesDir(), localJson);
            Uri contentUri = FileProvider.getUriForFile(MainActivity.this,
                    "de.herb64.funinspace.fileprovider",
                    attachment_file);
            i.putExtra(Intent.EXTRA_STREAM, contentUri);

            //i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);    // TODO check

            // Finding matching apps for the intent - see more details in lalatex docu
            // a) if (i.resolveActivity(getPackageManager()) != null)
            // b) try {startActivity(Intent.createChooser(i, "Send mail..."));}
            //    catch (android.content.ActivityNotFoundException ex) {}
            // c) List<ResolveInfo> pkgs = getPackageManager().queryIntentActivities(i, 0);

            List<ResolveInfo> pkgs = getPackageManager().queryIntentActivities(i, 0);   // flags ?
            if(pkgs.size() == 0) {
                new dialogDisplay(MainActivity.this, getString(R.string.no_email_client));
            } else {
                for (ResolveInfo pkg : pkgs) {
                    // see more infos in lalatex - TODO: how to restrict grant to one selected app? / default app
                    Log.i("HFCM", "Granting shared rights for package: " + pkg.activityInfo.packageName);
                    grantUriPermission(pkg.activityInfo.packageName,
                            contentUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivity(i);
            }
            return true;
        }
        if (id == R.id.dropbox_sync) {
            // Refresh metatata with dropbox. This allows to refresh without a new installation.
            //https://stackoverflow.com/questions/2115758/how-do-i-display-an-alert-dialog-on-android
            AlertDialog.Builder builder;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                //builder = new AlertDialog.Builder(getApplicationContext(), android.R.style.Theme_Material_Dialog_Alert);
                // java.lang.IllegalStateException: You need to use a Theme.AppCompat theme (or descendant) with this activity.
                // when show() is called. Using context was bad here..
                builder = new AlertDialog.Builder(MainActivity.this,
                        android.R.style.Theme_Material_Dialog_Alert);
            } else {
                builder = new AlertDialog.Builder(MainActivity.this);
            }

            // we could add an extended dialog with selection of different contents to load, e.g.
            // having data filtered by criteria on refresh
            // - only videos, no videos, only youtube or vimeo
            // - only images newer than, older than, timerange
            // - only images larger than given size
            builder.setTitle(R.string.refresh_dropbox_title)
                    .setMessage(R.string.refresh_dropbox_message)
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // asyncload requires an implementation of processFinish(), which is
                            // called after data has been loaded
                            new asyncLoad(MainActivity.this, "DROPBOX_REFRESH").execute(DROPBOX_JSON);
                            //new asyncLoad(MainActivity.this, "DROPBOX_REFRESH").execute(DROPBOX_MINI);
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // nothing done
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();
            return true;
        }

        if (id == R.id.restore_wallpaper) {
            new dialogDisplay(MainActivity.this, getString(R.string.dlg_revert_wp),
                    getString(R.string.dlg_title_info));
            changeWallpaper("");
        }
        if (id == R.id.action_search_apod) {
            new dialogDisplay(MainActivity.this,
                    "Searching the NASA APOD Archive is not yet available.", "TODO");
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
     * On startup, fill ArrayList of spaceItems with contents described in local JSON
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

        // Get current wallpaper filename, if any has been set
        SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
        String wpFileCurrent = shPref.getString("CURRENT_WALLPAPER_FILE", "");

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
            myList.add(newitem);
            count++;
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
     * Refresh the local json array with contents from the dropbox. The current array is replaced
     * by the new array, which might contain more ore less images as the current array.
     * - Existing rating values in the local array are preserved
     * - Thumbnail files of images are deleted, if they are not referenced any more in the new array
     * @param dropbox   a JSONArray returned from the call asyncLoad call to dropbox
     */
    private void refreshFromDropbox(JSONArray dropbox) {
        // TODO: filters for things to load and: KEEP NEWER IMAGES already present on device?? (now, apod is removed if not in dropbox and then reloaded)
        // for now, just overwrite existing data by brute force, which means
        // 1. metadata on the device which is not on dropbox is lost - could be restored from local nasa json info, if already stored (timestamp.json)
        // 2. deleted items are loaded again - no problem, because deletes are not yet persistent yet, but also later: how to determine, why not to restore an item?
        JSONObject obj = null;
        JSONObject content = null;
        HashMap<String, Integer> ratings;
        HashMap<String, String> thumbsToDelete;

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
        }

        // Activate the contents of the new json array
        parent = dropbox;
        utils.writeJson(getApplicationContext(), localJson, parent);
        myList.clear();
        addItems();
        checkMissingThumbs();
        adp.notifyDataSetChanged();
        getLatestAPOD();
    }

    /**
     * Try to get the latest APOD. This is called on each start of the app and after a refresh from
     * dropbox (while the latter should not be needed, if dropbox reload does a merge, which is not
     * (yet?) the case.
     * 14.11.2017 - this function has been created to put the load in a single place and to enable
     * a check based on nasa server time, if a reload is really needed...
     */
    private void getLatestAPOD() {
        // TODO: add code to check, if latest apod is already in, so no call is needed at all
        // utils function "needAPODRefresh" as base... doing the timezone stuff
        ArrayList<Long> epochs = utils.getNASAEpoch(loc);
        // we check the first image in list: if it's epoch is same as current NASA 00:00:00 epoch,
        // we do not call the apod loader at all.

        // Get current date and NASA date: if NASA date is behind, we need to wait, e.g. 04:00 DE

        // Get a message: New image not yet available, if our date is newer than current image date
        // but nasa did still not jump the date boundary (e.g. germany 03:00 -> still on prv. day in NEwYork

        // we need to compare date strings, not epochs!!
        /*if (myList.get(0).getDateTime() == epochs.get(0)) {
            Toast.makeText(MainActivity.this, "Image for today is already loaded",
                    Toast.LENGTH_LONG).show();
            return;
        }*/
        if (sharedPref.getBoolean("get_apod", true)) {
            new asyncLoad(MainActivity.this,
                    "APOD_LOAD",
                    sharedPref.getBoolean("enable_tls_pre_lollipop", true)).
                    execute(sharedPref.getBoolean("get_apod_simulate", false) ? APOD_SIMULATE : nS());
        } else {
            new dialogDisplay(this, getString(R.string.warn_apod_disable),
                    getString(R.string.reminder));
        }
    }

    /**
     * This is the interface implementation of processFinish() for asyncLoad class. This is called
     * when the asyncLoad task has finished retrieving the requested data.
     * Note: for parallel asynctask execution (executeonexecutor), see also my lalatex document...
     * @param status    return status
     * @param tag       the tag string set by caller to identify the calling procedure
     * @param output    the returned output (e.g. json string, bitmap)
     */
    @Override
    public void processFinish(int status, String tag, Object output) {
        // switch-case for String type available since Java version 7

        // no switch-case with string "segments", so first check this...
        if (tag.startsWith("THUMB_")) {
            int idx = Integer.parseInt(tag.replace("THUMB_",""));
            Log.i("HFCM", "Returned from asyncLoad() THUMB with index " + idx);
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
            }
            return;
        }

        switch (tag) {
            case "DROPBOX_REFRESH":
                Object json;
                try {
                    json = new JSONTokener((String) output).nextValue();
                } catch (JSONException e) {
                    e.printStackTrace();
                    return;
                }
                if (json != null && json instanceof JSONArray) {
                    refreshFromDropbox((JSONArray) json);
                }
                break;
            case "DROPBOX_INIT":
                // Todo: we might use refreshFromDropbox Code as well, maybe with parm "init" ?
                // On 25.10.2017, loadFromDropboxTask() has been retired. some comments from old code
                // TODO - needs retained fragment to run, so do not rotate phone while thumbs are loading
                // TODO - also found problems if Wifi is not available after new installation of app
                //        on 17.09.2017. And even then, this was bad with >40 images. CHECK!!! see notices
                // Note: we cannot run network operations directly in the checkMissingThumbs()
                // function. Because code in onPostExecute() does belong to the main thread
                // for the same reason: do not block during checkMissingThumbs!!!
                // https://stackoverflow.com/questions/10686107/what-does-runs-on-ui-thread-for-onpostexecute-really-mean
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
                }
                break;
            case "VIMEO_INFO":
                String thumbUrl = "n/a";
                String videoId = null;
                if (status == asyncLoad.FILENOTFOUND) {
                    break;
                }
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
                break;
            case "APOD_LOAD":
                // 26.10.2017 - apodTask - move into asyncLoad as well
                if (status == asyncLoad.FILENOTFOUND) {
                    Log.e("HFCM", "APOD Loader asyncLoad returned: File not found, " + output);
                    break;
                }
                // todo: this check needs to be done better via status - which exception is thrown?
                String h = (String)output;
                if (h.startsWith("Connection")) {
                    new dialogDisplay(MainActivity.this, h + "\n" + getString(R.string.enable_tls), "NASA Connect");
                    return;
                }
                createApodFromJson((String) output);
                break;
            case "IMG_LOWRES_LOAD":
                if (output instanceof Bitmap) {
                    //Log.i("HFCM", "Lowres bitmap returned by asyncLoad");
                    // moved to finalize
                    //apodItem.setLowSize(String.valueOf(((Bitmap) output).getWidth()) + "x" +
                    //        String.valueOf(((Bitmap) output).getHeight()));
                    finalizeApodWithLowresImage((Bitmap) output);
                } else {
                    Log.e("HFCM", "asyncLoad for lowres image did not return a bitmap");
                }
                break;
            /*case "MISSING_THUMBS_RELOAD":
                if (status == asyncLoad.OK) {
                    Log.i("HFCM", "Missing thumb reload returned from asyncLoad");
                }
                break;*/
            /*case "WALLPAPER":
                if (output instanceof Bitmap) {
                    Log.w("HFCM", "Disabled wallpaperload returning from asyncload - processfinish");
                } else {
                    Log.e("HFCM", "asyncLoad for WALLPAPER did not return a bitmap");
                }
                break;*/
            default:
                new dialogDisplay(MainActivity.this, "Unknown Tag '" + tag + "' from processFinish()", "Info for Herbert");
                break;
        }
        // recursive call is a really bad idea :)
        // new asyncLoad(this).execute("some url");
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
        // public static final int FLAG_LOCK = 1 << 1;
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
        SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = shPref.edit();
        editor.putString("CURRENT_WALLPAPER_FILE", wpToSet);
        editor.apply(); // apply is recommended by inspection instead of commit()
        // Refresh adapter (to update wp symbols on thumbnails)
        adp.notifyDataSetChanged();

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

}
