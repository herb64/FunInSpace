package de.herb64.funinspace;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AbsListView;
import android.widget.ListView;
import android.widget.RatingBar;
import android.support.v7.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeStandalonePlayer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

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

/*
 * The MainActivity Class for FunInSpace
 */
public class MainActivity extends AppCompatActivity implements ratingDialog.RatingListener, asyncLoad.AsyncResponse {

    private spaceItem apodItem;                     // the latest item to be fetched
    private ArrayList<spaceItem> myList;            // to be replaced by LinkedHashMap
    //private LinkedHashMap<String, spaceItem> myMap; // replacement for myList - abandoned
    private spaceAdapter adp;
    private JSONArray parent;
    private String jsonData;
    private String localJson = "nasatest.json";
    protected thumbClickListener myThumbClickListener;
    private ratingChangeListener myRatingChangeListener;
    private ListView myItemsLV;
    private deviceInfo devInfo;
    private int maxTextureSize = 999;   // TODO clean this, just to check the 999 was ok - still seems to be found, see Nathan
    private String lastImage;           // for log dialog title
    private Locale loc;
    protected Drawable expl_points;
    //private ActionMode mActionMode = null;
    private SharedPreferences sharedPref;
    private boolean thumbQualityChanged = false;    // indicate preference setting change

    // Using JNI for testing with NDK and C code in a shared lib .so file
    static {
        System.loadLibrary("hfcmlib");
    }
    public native String yT();
    public native String nS();
    public native String vA();                      // vimeo
    public native String vE();
    public native String vKE();
    public native String vHH();

    // App settings variables from preferences dialog
    //private boolean needWifi = false;               // hires loading - only with wifi?

    // We go for our CONSTANTS here, this is similar to #define in C for a constant
    //public static String TAG = MainActivity.class.getSimpleName();
    private static final int HIRES_LOAD_REQUEST = 1;
    private static final int GL_MAX_TEX_SIZE_QUERY = 2;
    private static final int SETTINGS_REQUEST = 3;
    // changed to other location on 13.10.2017 into testing folder on my dropbox
    private static final String DROPBOX_JSON = "https://dl.dropboxusercontent.com/s/3yqsmthlxth44w6/nasatest.json";
    private static final String DROPBOX_MINI = "https://dl.dropboxusercontent.com/s/5itsg1bjbytrk6d/nasamini.json";
    private static final String APOD_SIMULATE = "https://dl.dropboxusercontent.com/s/agfxia2f6or5plk/apod-simulate.json?";
    // interesting: the name behind the link is not important, notfound.json worked as well, need to change the ID!!
    private static final String APOD_NOTFOUND = "https://dl.dropboxusercontent.com/s/mzidejp3qfnosff/notfound.json";
    //private static final int KIB = 1024;
    //private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;
    // strings for media type classification
    private static final String M_IMAGE = "image";
    private static final String M_YOUTUBE = "youtube";
    private static final String M_VIMEO = "vimeo";
    private static final String M_VIDEO_UNKNOWN = "unknown-video";

    // dealing with the number of displayed lines in the Explanation text view
    protected static final int MAX_ELLIPSED_LINES = 2;
    private static final int MAX_LINES = 1000;      // hmm, ridiculous, but safe // TODO think
    protected static final int MAX_ITEMS = 10000;     // limit of items - for id handling


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

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
        sharedPref.registerOnSharedPreferenceChangeListener(prefChangeListener);
        //newestFirst = sharedPref.getString("item_order", "newest_first").equals("newest_first");
        //newestFirst = true; // we go and remove that, newest always on top!!!

        // TODO solve issue with vector on 4.1 (4.x?) - for now, just dirty workaround...
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

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                return false;
            }

            // Now we did click on an action item in our CAB and need to process actions on all
            // selected SpaceItems in our listview
            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem menuItem) {
                switch (menuItem.getItemId()) {
                    case R.id.cab_delete:
                        //new dialogDisplay(MainActivity.this, "This currently only deletes items for testing from the shown list. " +
                        //        "This is not yet persistent, and restart of the App loads all deleted items again.", "Don't panic...");
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
                            // Remove thumbfile, BEFORE indexed item gets deleted from list
                            Log.i("HFCM", "Delete: " + myList.get(idx).getThumb());
                            File thdel = new File(getApplicationContext().getFilesDir(), myList.get(idx).getThumb());
                            if (!thdel.delete()) {
                                Log.i("HFCM", "File delete did not return true");
                            }
                            //new File(getApplicationContext().getFilesDir(),
                            //        myList.get(idx).getThumb()).delete();
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
        myRatingChangeListener = new ratingChangeListener();

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
                new asyncLoad(MainActivity.this, "DROPBOX_INIT").execute(DROPBOX_JSON);
            }

            // Get latest APOD item to append from NASA
            // TODO: reduce these calls to required minimum
            if (sharedPref.getBoolean("get_apod", true)) {
                //new apodTask().execute(nS());
                //new apodTask().execute(APOD_SIMULATE); // just for testing - files on dropbox
                // new version using asyncload - apodTask() is now retired
                new asyncLoad(MainActivity.this,
                        "APOD_LOAD",
                        sharedPref.getBoolean("enable_tls_pre_lollipop", true)).execute(nS());
                /*new asyncLoad(MainActivity.this,
                        "APOD_LOAD",
                        sharedPref.getBoolean("enable_tls_pre_lollipop", true)).execute(APOD_SIMULATE);*/
            } else {
                new dialogDisplay(this, getString(R.string.warn_apod_disable), getString(R.string.reminder));
            }
        }
    }

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

    @Override
    protected void onResume() {
        super.onResume();
    }

    // Interface implementation for Rating Dialog result: return from DialogFragment to Activity
    // This is called, if Rating Dialog OK button has been pressed
    @Override
    public void updateRating(int rating, ArrayList<Integer> selected) {
        HashSet<String> titles = new HashSet<>();
        for (int idx : selected) {
            myList.get(idx).setRating(rating);
            titles.add(myList.get(idx).getTitle());
        }
        adp.notifyDataSetChanged();
        // TODO make persistent in JSON file as well
        JSONObject obj = null;
        JSONObject content = null;
        for (int i = 0; i < parent.length(); i++) {
            try {
                obj = parent.getJSONObject(i);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // get the "Content" object, not yet checking type, currently only "APOD" is expected
            try {
                content = obj.getJSONObject("Content");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // get the fields - we know, that the keys exist, because we have written this ourselves
            // ha, not always :( bad with 11.09.2017 - no lowres key present, because "url" missing
            // in json from NASA TODO 11.09.2017
            try {
                String strTitle = content.getString("Title");
                if (titles.contains(strTitle)) {
                    content.put("Rating", rating);
                }
            } catch (JSONException e) {
                e.printStackTrace();
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
    /*
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
            sCopyright = "Copyright: " + parent.getString("copyright").
                    replaceAll(System.getProperty("line.separator"), " ");
            sTitle = parent.getString("title");
            imgUrl = parent.getString("url"); // TODO: 11.09.2017 - missing leads to strange effects of missing keys
            resource_uri = Uri.parse(imgUrl);
            sHiresUrl = parent.getString("hdurl");
            sExplanation = parent.getString("explanation");
        } catch (JSONException e) {
            e.printStackTrace();
            Log.e("HFCM", e.getMessage());
            new dialogDisplay(this, e.getMessage(), "APOD Processing error");
        }

        if (resource_uri != null) {
            if (sMediaType.equals("video")) {
                // note, that this rewrites sMediaType variable!
                String host = resource_uri.getHost();
                List<String> path = resource_uri.getPathSegments();
                apodItem.setHires(resource_uri.toString());
                if (host.equals("www.youtube.com")) {
                    // TODO: now we just assume 'embed' link - might not be true always ??
                    // url> https://www.youtube.com/embed/" + path.get(1) + "?rel=0
                    // img> https://img.youtube.com/vi/9Vp2jUQ4rNM/0.jpg
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
                    // thumbnail image name: th_<video-id>.jpg
                    // Note: this needs a REST API call to gather the required infos..
                    apodItem.setLowres("");     // will hold thumbnail url
                } else {
                    sMediaType = M_VIDEO_UNKNOWN;
                    apodItem.setThumb("th_UNKNOWN.jpg");
                }
            } else {
                // thumbnail gets name of lowres image with prefix 'th_'
                apodItem.setThumb("th_" + resource_uri.getLastPathSegment());
                apodItem.setHires(sHiresUrl);
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
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", loc);
        // normally we would use code as below for correct locale, but we parse a specific
        // format here :_ "date": "2017-09-17", as returned by NASA in their json
        // DateFormat dF = SimpleDateFormat.getDateInstance();
        long epoch = 0;
        try {
            epoch = dF.parse(sDate).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }
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
    // asyncLoad.java is one of the results of this - now still make sure about memory leaks!!!!
    //
    // private class ImgLowresTask extends AsyncTask<String, String, Bitmap> {
    // .. now also retired and replaced by asyncLoad

    // new code on 26.10. to replace imglowrestask with asyncload as well
    void lowresBitmapOnPostExecuteReplacement(Bitmap bitmap) {
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


    /* N O T E:   T H I S   I S   N O T   A C T I V E
     *Listener for rating bar changes - useless for me, because I use "small" bars
     *style="@style/Widget.AppCompat.RatingBar"       > works
     *style="@style/Widget.AppCompat.RatingBar.Small" > fails
     * >> "small" versions are designed to ignore any interaction, see Android docs
     */
    private class ratingChangeListener implements RatingBar.OnRatingBarChangeListener {
        @Override
        public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
            Toast.makeText(MainActivity.this, "Have rating" + v, Toast.LENGTH_SHORT).show();
        }
    }

    // Listener for clicks on items in the list. This is registered in spaceAdapter.
    // The adapter sets a Tag, which can be read here and used for further actions.
    private class thumbClickListener implements View.OnClickListener {
        // onClick() gets passed the view in which we store the URL to be opened
        // in a TAG - this happens in getView() in our adapter...
        // passing multiple tags? we could just combine this in a single string of special format
        // See https://developer.android.com/training/basics/firstapp/starting-activity.html
        @Override
        public void onClick(View view) {
            int idx = (int) view.getTag();  // for "filtered" view - index into full list!
            /* Can't use this, because "small" ratingbars do not support interaction. So going
               for a separate dialog to set rating for items selected via contextual action mode
            if (idx >= 2*MAX_ITEMS) {
                RatingBar rb = (RatingBar) view;
            }*/
            // bad hack: index i+MAX_ITEMS is corresponding textview for thumbnail on index i
            if (idx >= MAX_ITEMS) {
                // we just reset the maxlines to a larger limit and remove the ellipse stuff. This
                // is only temporarily and automatically disappears when scrolling or when clicking
                // again.
                // TODO - during background load of thumbs, the full text always
                //        disappears on refreshs.
                TextView v = (TextView) view;
                if (v.getMaxLines() == MAX_ELLIPSED_LINES) {
                    v.setMaxLines(MAX_LINES);
                    v.setEllipsize(null);
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,null);
                } else {
                    v.setEllipsize(TextUtils.TruncateAt.END);
                    v.setMaxLines(MAX_ELLIPSED_LINES);
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,expl_points);
                }
                myItemsLV.setSelection(idx-MAX_ITEMS);  // selected item to top of view
                return;
            }

            // here, we decide, if we want to proceed with wifi only
            if (sharedPref.getBoolean("wifi_switch", false) && !devInfo.isWifiActive()) {
                new dialogDisplay(MainActivity.this, "WiFi connection not active, will not load video or hires now, please check settings", "No Wifi");
                return;
            }
            String hiresUrl = myList.get(idx).getHires();
            String media = myList.get(idx).getMedia();
            // get maximum allocatable heap mem at time of pressing the button
            int maxAlloc = devInfo.getMaxAllocatable();
            Log.i("HFCM", "maximum alloc:" + String.valueOf(maxAlloc));
            if (media.equals(M_IMAGE)) {
                    Intent hiresIntent = new Intent(getApplication(), ImageActivity.class);
                    hiresIntent.putExtra("hiresurl", hiresUrl);
                    hiresIntent.putExtra("listIdx", idx);
                    hiresIntent.putExtra("maxAlloc", maxAlloc);
                    hiresIntent.putExtra("maxtexturesize", maxTextureSize);
                    lastImage = myList.get(idx).getTitle();
                    hiresIntent.putExtra("imagename", lastImage);
                    // forResult now ALWAYS to get logstring returned for debugging
                    // if hires size is already
                    // TODO: how about running one thread to just query the hires image size
                    startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
            } else if (media.equals(M_YOUTUBE)) {
                String thumb = myList.get(idx).getThumb();
                // We get the ID from thumb name - hmmm, somewhat dirty ?
                playYouTube(thumb.replace("th_", "").replace(".jpg", ""));
            } else {
                playVimeo(hiresUrl);
            }
        }
    }

    /**
     * Play a youtube video by id  - first quick shot for basic test, which uses the autoplay in
     * lightbox mode.
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
            new dialogDisplay(this, getString(R.string.no_youtube), getString(R.string.sorry));
        }
    }

    // Play a vimeo video for given URL in a WebView
    private void playVimeo(String url) {
        // Play a vimeo video identified by the url string
        // hmmm, check this, which works: http://vimeo.com/api/v2/video/11386048.json
        // - this allows for thumbnails...
        // hmm, this looks not so good: no native play without "pro"
        // https://vimeo.com/forums/help/topic:280621
        // Available infos for testing:
        // String uri = "/videos/11386048";   // 11.09.2017
        // String uri = "/videos/53641212";   // 20.08.2017
        /* possible valid vimeo links, from which we might need to extract the id
        http://vimeo.com/<id>
        http://player.vimeo.com/video/<id>
        http://player.vimeo.com/video/<id>?title=0&byline=0&portrait=0
        http://player.vimeo.com/video/<id>#t=0m58s?color=8BA0FF&portrait=0
        http://vimeo.com/channels/staffpicks/67019026 */
        // Check this: https://stackoverflow.com/questions/10488943/easy-way-to-get-vimeo-id-from-a-vimeo-url
        // need to answer to this post, because newer version does not work as good, while oEmbed  (https://oembed.com) works
        // we can ask vimeo for the id!!
        // -> https://developer.vimeo.com/api/endpoints/videos#GET/videos
        // playground shows, that this cannot resolve "player.vimeo.com" based urls
        // using the oembed endpoint: even works with full string, e.g.
        // https://vimeo.com/api/oembed.json?url=https://player.vimeo.com/video/11386048#t=0m58s?color=8BA0FF&portrait=0
        /*new dialogDisplay(MainActivity.this,
                getString(R.string.no_video_yet, "vimeo", url),
                getString(R.string.no_support_yet));*/

        Intent vimeoIntent = new Intent(this, VideoActivity.class);
        vimeoIntent.putExtra("vimeourl", url);
        startActivity(vimeoIntent);

        /*
         * My code for testing with Vimeo API video objects. Actually, this has been abandoned,
         * because embedding iframes in WebView caused quite some trouble.. to be checked again,
         * but for now we are just using the NASA link in a webview.
         */
        /*
        Configuration.Builder b = new Configuration.Builder(vA());
        b.setApiVersionString("3.2");       // need 3.3 for the Play object to avoid deprecated video.embed
        // but 3.3 fails - see below in failure() function. Looks like for public, 3.2 is the
        // currently supported version, see https://vimeo.com/forums/api/topic:289338, June 2017
        VimeoClient.initialize(b.build());
        Configuration cfg = VimeoClient.getInstance().getConfiguration();
        VimeoClient.getInstance().fetchNetworkContent(uri, new ModelCallback<Video>(Video.class) {
            @Override
            public void success(Video video) {
                int dur = video.duration;
                String des = video.description;
                String trail = video.getTrailerUri();

                PictureCollection pc = video.pictures;
                String pcuri = pc.uri;

                Play pl1 = video.getPlay();  // returns null
                Play pl = video.play;        // also null, although should be preferred...
                Embed emb = pl.mEmbed;
                String sss = emb.toString();

                // hmm, only the deprecated way is working with 3.2 and 3.3 cannot be used.... :(
                String html = video.embed != null ? video.embed.html : null;
                if(html != null) {
                    new dialogDisplay(MainActivity.this, html, "vimeo-embed");
                    // html is in the form "<iframe .... ></iframe>"
                    // display the html however you wish
                }
            }
            // with 3.3 set, we get error:
            // Unsupported response format provided via the accept header. We expected [application/vnd.vimeo.video;version=3.2].
            @Override
            public void failure(VimeoError error) {
                String tt = error.toString();
                new dialogDisplay(MainActivity.this, tt, "vimeo problem");
            }
        });*/
    }

    // Get results from Activities started with startActivityForResult()
    // 1. image Activity for hires size
    // 2. GL max texture size query at very first run
    // 3. Settings dialog
    // Returned resultCode = 0 (RESULT_CANCELED) after having rotated the phone while
    // displaying the image in hires ImageActivity
    // https://stackoverflow.com/questions/32803497/incorrect-activity-result-code-after-rotating-activity
    // same problem in above post - this was because activity was gone underneath
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == HIRES_LOAD_REQUEST) {
            if (resultCode == RESULT_OK) {
                String testsize = data.getStringExtra("sizeHires");
                //String hUrl = data.getStringExtra("hiresurl");
                int listidx = data.getIntExtra("lstIdx",0);
                String logString = data.getStringExtra("logString");
                new dialogDisplay(this, logString, lastImage);
                if (myList.get(listidx).getHiSize().equals(testsize)) {
                    return;     // no action needed if hires size already in data
                }
                // maybe change json to put Title out of content one hierarchy higher ??
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
            }
        }
    }

    // Listener for changed preferences. This is called after each single change IMMEDIATELY!
    // This means, if changing a preference that might trigger a more expensive action, it could
    // get bad if user toggles that switch and in the end closes the dialog without any effective
    // change.
    // The onActivityResult() method reacting on SETTINGS_REQUEST is called AFTER the settings
    // dialog activity is closed and might be a better place to handle changes. The following
    // code solves this by using "change flags", checked in onActivityResult() later on.
    SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener = new
            SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String changed) {
                    if (changed.equals("rgb565_thumbs")) {
                        // On each switch toggle, we invert the value without immediately starting
                        // any action. The final value is checked in onActivityResult()
                        // to determine, if an action is needed based on the value of this setting
                        // after the user has closed the dialog.
                        thumbQualityChanged ^= true;
                    }
                    if (changed.equals("full_search")) {
                        // this one: just set on each single toggle
                        adp.setFullSearch(sharedPref.getBoolean("full_search", false));
                    }
                }
            };

    /*
     * The options menu is the primary Application menu. Do not confuse with "settings" dialog.
     * It is called during startup of the activity once.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        MenuItem searchItem = menu.findItem(R.id.action_search); // 22.10.2017 - add
        //SearchView sv = (SearchView) MenuItemCompat.getActionView(searchItem); // deprecated
        SearchView sv = (SearchView) searchItem.getActionView();

        // on close listener does not work at all - see also discussions on web. I now use the
        // current search string length = 0 to detect, that the search is closed and that the list
        // is not filtered again.
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
        return true;
    }

    /*
     * This code handles selections from the menu bar. Contents are defined in menu_main.xml.
     * Note, that action_search is defined in menu_main.xml as well, with following parameters:
     * app:showAsAction="ifRoom|collapseActionView"
     * app:actionViewClass="android.support.v7.widget.SearchView"
     * This is handled by actionViewClass
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        int o = 0;
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
            return true;
        }
        if (id == R.id.action_mail) {
            //Intent i = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto",vE(), null));
            Intent i = new Intent(Intent.ACTION_SENDTO);  // ACTION_SEND - also shows whatsapp etc..
            i.setData(Uri.parse("mailto:" + vE()));
            //i.setType("message/rfc822");    // had to be removed
            //String to[] = {"user@domain.com","user2@domain.com"};
            String cc[] = {vKE() + "," + vHH()};
            //i.putExtra(Intent.EXTRA_EMAIL, new String[]{"user@domain.com"});
            i.putExtra(Intent.EXTRA_EMAIL, cc);
            i.putExtra(Intent.EXTRA_SUBJECT, "Greetings from FunInSpace");
            i.putExtra(Intent.EXTRA_TEXT, "Just a test email sent by the famous FunInSpace App. I hope you enjoy the attachment :)");

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
                    // see more infos in lalatex - TODO: how to restrict grant to selected app?
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
                builder = new AlertDialog.Builder(MainActivity.this, android.R.style.Theme_Material_Dialog_Alert);
            } else {
                builder = new AlertDialog.Builder(MainActivity.this);
            }

            // we could add an extended dialog with selection of different contents to load, e.g.
            // having data filtered by criteria on refresh
            // - only videos, no videos, only youtube or vimeo
            // - only images newer than, older than, timerange
            // - only images larger than given size
            builder.setTitle("Refresh of image list")
                    .setMessage("Do you want to refresh your list with Herbert's Dropbox informations?")
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

        // TIP: calling 'return super.onOptionsItemSelected(item);' made menu icons disappear after
        //      using overflow menu while having the SearchView open - this was really nasty
        // GOOD return values here:
        // true  --> Event Consumed here, so should not be forwarded for other event
        // false --> Forward for other event to get consumed, we use this if we did not handle it
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
                //Log.i("HFCM", "Thumbnail size: " + strThumb + " = " + utils.getBMPBytes(thumb));
            } else {
                newitem.setBmpThumb(null);
            }
            myList.add(newitem);
        }
    }

    // Check for any missing thumb images. We iterate all items and for those with no bitmap
    // contained, we kick of a loader thread, which gets the image file from the given lowres
    // URL and saves a thumbnail file.
    public void checkMissingThumbs() {
        ArrayList<Integer> missing = new ArrayList<Integer>();
        for(int i=0; i<myList.size(); i++) {
            if (myList.get(i).getBmpThumb() == null && !myList.get(i).getLowres().equals("")) {
                // TODO unknown not yet checked...
                missing.add(i);
                myList.get(i).setThumbLoadingState(View.VISIBLE);
            }
        }
        if (missing.size() > 0) {
            Toast.makeText(MainActivity.this, getString(R.string.load_miss_thumbs, missing.size()),
                    Toast.LENGTH_LONG).show();
            // kick off a new thread for loading from network, because this function is called
            // from within onPostExecute()
            new thumbLoaderTask().execute(missing);
        }
    }

    // Load all missing image thumbnails URL contained in "lowres". A list of indices into the
    // list is passed and images are loaded one after the other. TODO - maybe parallel load?
    // https://stackoverflow.com/questions/6053602/what-arguments-are-passed-into-asynctaskarg1-arg2-arg3
    // new: also called for all images, if thumb quality is changed between rgb565 and argb8888
    private class thumbLoaderTask extends AsyncTask<ArrayList<Integer>, Integer, Void> {
        private ArrayList<Integer> missing;

        @Override
        protected Void doInBackground(ArrayList<Integer>... params) {
            Bitmap bitmap = null;
            Bitmap thumbnail = null;
            //File thumbFile = null;
            spaceItem wkItem;
            missing = params[0];
            for (int i=0; i < missing.size(); i++) {
                wkItem = myList.get(missing.get(i));
                String lowresurl = wkItem.getLowres();
                try {
                    URL imgurl = new URL(lowresurl);
                    bitmap = BitmapFactory.decodeStream((InputStream)imgurl.getContent());
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (bitmap != null) {
                    wkItem.setLowSize(String.valueOf(bitmap.getWidth()) + "x" +
                            String.valueOf(bitmap.getHeight()));
                    // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
                    //thumbFile = new File(getApplicationContext().getFilesDir(), wkItem.getThumb());
                    thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 120, 120);
                    wkItem.setBmpThumb(thumbnail);
                }
                // TODO DOCU: adp.notifyDataSetChanged() fails with following exception:
                // Caused by: android.view.ViewRootImpl$CalledFromWrongThreadException:
                // >> Only the original thread that created a view hierarchy can touch its views. <<
                // Therefore use the progress update mechanism for this.
                // https://stackoverflow.com/questions/6450275/android-how-to-work-with-asynctasks-progressdialog
                publishProgress(missing.get(i));

                // Write the thumbnail as small jpeg file to internal storage
                utils.writeJPG(getApplicationContext(), wkItem.getThumb(), thumbnail);
            }
            return null;
        }

        // For each loaded thumbnail, we receive a progress update and notify the adapter to update
        // the view, if the image has been loaded. We might run this in parallel for multiple images
        // triggered by publishProgress() within doInBackground()
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            myList.get(values[0]).setThumbLoadingState(View.INVISIBLE);
            adp.notifyDataSetChanged();
        }
    }

    /*
     * Refresh the local json array with contents from the dropbox. The current array is replaced
     * by the new array, which might contain more ore less images as the current array.
     * - Existing rating values in the local array are preserved
     * - Thumbnail files of images are deleted, if they are not referenced any more in the new array
     */
    private void refreshFromDropbox(JSONArray dropbox) {
        // TODO: filters for things to load and: KEEP NEWER IMAGES already present on device?? (now, apod is removed if not in dropbox and then reloaded)
        // for now, just overwrite existing data by brute force, which means
        // 1. metadata on the device which is not on dropbox is lost - could be restored from local nasa json info, if already stored (timestamp.json)
        // 2. deleted items are loaded again - no problem, because deletes are not yet persistent yet, but also later: how to determine, why not to restore an item?
        // 3. personal ratings stored on device are overwritten with dropbox content - easy to solve
        // 4. what about thumbnail images in internal storage?
        JSONObject obj = null;
        JSONObject content = null;
        HashMap<String, Integer> ratings;
        HashMap<String, String> thumbsToDelete;

        // Iterate the currently active json array to fill maps for rating / thumbfilenames
        ratings = new HashMap<>();
        thumbsToDelete = new HashMap<>();
        for (int i = 0; i < parent.length(); i++) {
            try {
                /*obj = parent.getJSONObject(i);
                if (obj != null) {
                    content = obj.getJSONObject("Content");
                }*/
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
        // Remove remaining thumbnails which have no reference any more in the new json array
        for (String key : thumbsToDelete.keySet()) {
            Log.i("HFCM", "Delete thumb: " + thumbsToDelete.get(key));
            new File(getApplicationContext().getFilesDir(), thumbsToDelete.get(key)).delete();
        }

        // Activate the contents of the new json array
        parent = dropbox;
        utils.writeJson(getApplicationContext(), localJson, parent);
        myList.clear();
        addItems();
        checkMissingThumbs();
        adp.notifyDataSetChanged();
        if (sharedPref.getBoolean("get_apod", true)) {
            //new apodTask().execute(nS());
            new asyncLoad(MainActivity.this,
                    "APOD_LOAD",
                    sharedPref.getBoolean("enable_tls_pre_lollipop", true)).execute(nS());
        }
    }

    /*
     * Testing asyncLoad class - this is implementation of interface for this class. This is called
     * when the asyncLoad class has finished retrieving the json data.
     * Note: for parallel asynctask execution (executeonexecutor), see also my lalatex document...
     */
    @Override
    public void processFinish(int status, String tag, Object output) {
        // switch-case for String type available since Java version 7
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
                // 26.10.2017 - vimeoInfoTask replaced by asyncLoad as well
                Log.i("HFCM", "Vimeo info task returned via asyncLoad....");
                JSONObject parent = null;
                String thumbUrl = "n/a";
                String videoId = null;
                if (status == asyncLoad.FILENOTFOUND) {
                    break;
                }
                try {
                    parent = new JSONObject((String) output);
                    videoId = parent.getString("video_id");
                    thumbUrl = parent.getString("thumbnail_url");
                } catch (JSONException e) {
                    e.printStackTrace();
                    Log.e("HFCM", e.getMessage());
                    new dialogDisplay(this, "Could not load thumbnail for this video\n'" +
                            e.getMessage() + "'",
                            "VIMEO INFO JSON ERROR");
                }
                if (videoId != null) {
                    apodItem.setThumb("th_" + videoId + ".jpg");   // thumb filename
                }
                apodItem.setLowres(thumbUrl);
                //new ImgLowresTask().execute(thumbUrl);
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
                    Log.i("HFCM", "Lowres bitmap returned by asyncLoad");
                    apodItem.setLowSize(String.valueOf(((Bitmap) output).getWidth()) + "x" +
                            String.valueOf(((Bitmap) output).getHeight()));
                    lowresBitmapOnPostExecuteReplacement((Bitmap) output);
                } else {
                    Log.e("HFCM", "asyncLoad for lowres image did not return a bitmap");
                }
                break;
            default:
                new dialogDisplay(MainActivity.this, "Unknown Tag '" + tag + "' from processFinish()", "Info for Herbert");
                break;
        }
        // recursive call is a really bad idea :)
        // new asyncLoad(this).execute("some url");
    }



    ////////////////////////////////////////////////////////////////////////////////////////////////
    //  O L D    C O D E   J U S T   K E P T   H E R E
    ////////////////////////////////////////////////////////////////////////////////////////////////

    // This is only used in combination with long click listener. It is not needed with multiple
    // choice variant, which does implement these functions in "MultiChoiceModeListener"
    // see option 1
    /*private ActionMode.Callback mActionModeCallback = new ActionMode.Callback() {

        // startActionMode() has been called.
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
            //mode.getMenuInflater().inflate(R.menu.menu_cab_main, menu);
            //return true;
        }

        // Called each time the action mode is shown
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        // User has selected a menu item
        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            switch (item.getItemId()) {
                case R.id.cab_delete:
                    new dialogDisplay(MainActivity.this, "Delete not yet possible", "Info");
                    mode.finish();
                    return true;
                case R.id.cab_share:
                    new dialogDisplay(MainActivity.this, "Sharing not yet possible", "Info");
                    mode.finish();
                    return true;
                default:
                    return false;
            }
        }

        // Exiting the menu
        @Override
        public void onDestroyActionMode(ActionMode mode) {
            mActionMode = null;
        }
    };*/

    //////////////////// OLD CODE //////////////////////////////////////////////////


    // This is the adapter working on ArrayList type object myList...
    /* This one has been also abandoned and replaced by spaceAdapter, which also contains filtering
    private class myAdapter extends ArrayAdapter {
        private List<spaceItem> iList;
        int resource;
        private LayoutInflater inflater;

        // Constructor (add via alt+insert) and adjust to our list of type spaceItem
        private myAdapter(@NonNull Context context,
                          @LayoutRes int resource,
                          @NonNull List<spaceItem> objects) {
            super(context, resource, objects);
            iList = objects;
            this.resource = resource;
            inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        // adding getView() by alt-insert - override methods - the "NonNull" stuff seems to be new
        // Document: strange, but getView() gets called many more times than rows exist if layout
        //           is bad. I had this effect and did only notice this by chance, while the app
        //           looked fine. Check getView() calls from time to time to see, if it's fine.
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            //return super.getView(position, convertView, parent)
            // Finally going for ViewHolder after having this on my list since beginning :)
            // Never create a new view on each call!! see "the world of listview - google io 2010"
            // https://www.youtube.com/watch?v=wDBM6wVEO70&feature=youtu.be&t=7m
            ViewHolder holder;
            // We should checkout RecyclerView as a more sophisticated replacement for ListView
            // https://stackoverflow.com/questions/21501316/what-is-the-benefit-of-viewholder
            // https://developer.android.com/training/improving-layouts/smooth-scrolling.html
            // RecyclerView.ViewHolder holder;

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.space_item, null);
                holder = new ViewHolder();
                holder.ivThumb = convertView.findViewById(R.id.iv_thumb);
                holder.ivYoutube = convertView.findViewById(R.id.iv_youtube);
                holder.rbRating = convertView.findViewById(R.id.id_rating);
                holder.tvTitle = convertView.findViewById(R.id.tv_title);
                holder.tvExplanation = convertView.findViewById(R.id.tv_explanation);
                holder.tvDate = convertView.findViewById(R.id.tv_date);
                holder.tvCopyright = convertView.findViewById(R.id.tv_copyright);
                holder.tvLowSize = convertView.findViewById(R.id.tv_lowsize);
                holder.tvHiSize = convertView.findViewById(R.id.tv_hisize);
                holder.lbThumb = convertView.findViewById(R.id.pb_thumb_loading);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            // handle highlighting by contextual action mode
            if (iList.get(position).isSelected()) {
                convertView.setBackgroundColor(Color.LTGRAY);
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            // getView() is called often during scroll, take care of the overhead
            // We use a listener created ONCE in the activity instead of creating a new one on
            // each call of getView().
            //ivThumb.setOnClickListener(new thumbClickListener());         // BAD
            holder.ivThumb.setOnClickListener(myThumbClickListener);        // BETTER
            holder.ivThumb.setTag(position);
            holder.ivThumb.setImageBitmap(iList.get(position).getBmpThumb());
            holder.ivThumb.setVisibility(View.VISIBLE);

            // Rating bar - unfortunately, the "small" versions do not support interaction
            // https://developer.android.com/reference/android/widget/RatingBar.html
            // My initial idea was to make the stars below the thumbnail clickable directly within
            // the list, but the ratingbar evend does not respond do a simple onClick()
            holder.rbRating.setRating(iList.get(position).getRating());
            //rbRating.setTag(2*MAX_ITEMS + position);
            //rbRating.setOnClickListener(myThumbClickListener);
            //rbRating.setOnRatingBarChangeListener(myRatingChangeListener);

            if (iList.get(position).getMedia().equals("youtube")) {
                // https://www.youtube.com/yt/about/brand-resources/#logos-icons-colors
                holder.ivYoutube.setImageResource(R.drawable.youtube_social_icon_red);
                holder.ivYoutube.setVisibility(View.VISIBLE);
            } else if(iList.get(position).getMedia().equals("vimeo")) {
                holder.ivYoutube.setImageResource(R.drawable.vimeo_icon);
                holder.ivYoutube.setVisibility(View.VISIBLE);
            } else {
                holder.ivYoutube.setVisibility(View.INVISIBLE);
            }
            //ProgressBar lbThumb = convertView.findViewById(R.id.pb_thumb_loading);
            //noinspection ResourceType
            holder.lbThumb.setVisibility(iList.get(position).getThumbLoadingState());
            holder.tvTitle.setText(iList.get(position).getTitle());
            Date iDate = new Date(iList.get(position).getDateTime());
            String formattedDate = new SimpleDateFormat("dd. MMM yyyy").format(iDate);
            holder.tvDate.setText(formattedDate);
            holder.tvCopyright.setText(iList.get(position).getCopyright());

            holder.tvExplanation.setText(iList.get(position).getExplanation());
            iList.get(position).setMaxLines(holder.tvExplanation.getLineCount());
            holder.tvExplanation.setTag(MAX_ITEMS + position);
            holder.tvExplanation.setEllipsize(TextUtils.TruncateAt.END);
            holder.tvExplanation.setMaxLines(MAX_ELLIPSED_LINES);
            // and here, we have a friendly listener, which temporarily overwrites that stuff, when
            // we click on the text view content - We reuse the existing listener for the thumbs
            // and distinguish views by ID ranges
            holder.tvExplanation.setOnClickListener(myThumbClickListener);
            holder.tvExplanation.setCompoundDrawablesWithIntrinsicBounds(null, null, null, expl_points);

            // Note: setText and concat is bad, use resources and format string instead!
            // BAD: tvLowSize.setText("Lowres: " + iList.get(position).getLowSize());
            holder.tvLowSize.setText(getString(R.string.lowres, iList.get(position).getLowSize()));
            holder.tvHiSize.setText(getString(R.string.hires, iList.get(position).getHiSize()));
            return convertView;
        }
    }*/

    /*
    private class myHashAdapter extends HfcmMapAdapter {
        private LayoutInflater inflater;

        public myHashAdapter(@NonNull Context context, @LayoutRes int resource, @NonNull LinkedHashMap obj) {
            super(context, resource, obj);
            inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.space_item, null);
            }
            spaceItem item = (spaceItem) this.getItem(position).getValue();
            if (item.isSelected()) {
                convertView.setBackgroundColor(Color.RED);
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            ImageView ivThumb = convertView.findViewById(R.id.iv_thumb);
            ImageView ivYoutube = convertView.findViewById(R.id.iv_youtube);
            ivThumb.setOnClickListener(myThumbClickListener);         // BETTER
            ivThumb.setTag(position);
            TextView tvTitle = convertView.findViewById(R.id.tv_title);
            final TextView tvExplanation = convertView.findViewById(R.id.tv_explanation);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            final TextView tvCopyright = convertView.findViewById(R.id.tv_copyright);
            TextView tvLowSize = convertView.findViewById(R.id.tv_lowsize);
            TextView tvHiSize = convertView.findViewById(R.id.tv_hisize);
            ivThumb.setImageBitmap(item.getBmpThumb());
            ivThumb.setVisibility(View.VISIBLE);
            if (item.getMedia().equals("youtube")) {
                // https://www.youtube.com/yt/about/brand-resources/#logos-icons-colors
                ivYoutube.setImageResource(R.drawable.youtube_social_icon_red);
                ivYoutube.setVisibility(View.VISIBLE);
            } else if(item.getMedia().equals("vimeo")) {
                ivYoutube.setImageResource(R.drawable.vimeo_icon);
                ivYoutube.setVisibility(View.VISIBLE);
            } else {
                ivYoutube.setVisibility(View.INVISIBLE);
            }
            ProgressBar lbThumb = convertView.findViewById(R.id.pb_thumb_loading);
            //noinspection ResourceType
            lbThumb.setVisibility(item.getThumbLoadingState());
            tvTitle.setText(item.getTitle());
            Date iDate = new Date(item.getDateTime());
            String formattedDate = new SimpleDateFormat("dd. MMM yyyy").format(iDate);
            tvDate.setText(formattedDate);
            tvCopyright.setText(item.getCopyright());
            tvExplanation.setText(item.getExplanation());
            item.setMaxLines(tvExplanation.getLineCount());
            tvExplanation.setTag(MAX_ITEMS + position);
            tvExplanation.setEllipsize(TextUtils.TruncateAt.END);
            tvExplanation.setMaxLines(MAX_ELLIPSED_LINES);
            tvExplanation.setOnClickListener(myThumbClickListener);
            tvExplanation.setCompoundDrawablesWithIntrinsicBounds(null, null, null, expl_points);
            tvLowSize.setText(getString(R.string.lowres, item.getLowSize()));
            tvHiSize.setText(getString(R.string.hires, item.getHiSize()));
            return convertView;
        }

        //@NonNull
        //@Override
        //public View getView(String title, @Nullable View convertView, @NonNull ViewGroup parent) {
        //
        //    if (convertView == null) {
        //        convertView = inflater.inflate(R.layout.space_item, null);
        //    }
        //    return convertView;
        //}
    }*/

    // OLD CODE FOR DROPBOX LOAD - retired on 25.10.2017
    /*private class loadFromDropboxTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {
            HttpsURLConnection dropbox_conn = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                dropbox_conn = (HttpsURLConnection) url.openConnection();
                try {
                    dropbox_conn.connect();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // Read data from this connection into an input stream
                InputStream jsonstream = dropbox_conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(jsonstream));
                StringBuffer jsonbuffer = new StringBuffer();
                String jsonstring;

                while((jsonstring = reader.readLine()) != null) {
                    jsonbuffer.append(jsonstring);
                }
                return jsonbuffer.toString();   // returned to onPostExecute

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                // Running into this because of DNS problems in AVD device. Seems to be because
                // of LAN card and Wifi present in host - only on Win10 Android Studio installation
                e.printStackTrace();
                return e.getMessage();
            } finally {
                if(dropbox_conn != null) {
                    try {
                        dropbox_conn.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if(reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);
            if (s != null) {
                try {
                    parent = new JSONArray(s);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // write json file to internal storage, spacing of 2
                utils.writeJson(getApplicationContext(), localJson, parent);
                addItems();
                checkMissingThumbs();
                adp.notifyDataSetChanged();
            }
        }
    }*/

    // Get vimeo infos about thumbnail picture URL first, before calling imgLowResTask to load
    // this thumbnail image.
    /* 26.10.2017 - replaced by calling asyncLoad with interface AsyncResponse.processFinish()
    private class vimeoInfoTask extends AsyncTask<String, String, String> {
        @Override
        protected String doInBackground(String... params) {
            HttpsURLConnection vimeo_conn = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                vimeo_conn = (HttpsURLConnection) url.openConnection();
                vimeo_conn.connect();
                // We now read data from this connection into an input stream
                InputStream jsonstream = vimeo_conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(jsonstream));
                StringBuffer jsonbuffer = new StringBuffer();
                String jsonstring;

                while((jsonstring = reader.readLine()) != null) {
                    jsonbuffer.append(jsonstring);
                }
                return jsonbuffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return "FILENOTFOUND";
            } catch (IOException e) {
                e.printStackTrace();
                return e.getMessage();
            } finally {
                if(vimeo_conn != null) {
                    try {
                        vimeo_conn.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if(reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(String s) {
            JSONObject parent = null;
            String thumbUrl = "n/a";
            String videoIid = null;
            if(s != null) {
                if (s.equals("FILENOTFOUND")) {
                    return;
                }
            }
            try {
                parent = new JSONObject(s);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(parent == null) {
                return;
            }
            // We are interested in video ID and thumbnail_url
            if(parent.has("video_id")) {
                try {
                    videoIid = parent.getString("video_id");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if(parent.has("thumbnail_url")) {
                try {
                    thumbUrl = parent.getString("thumbnail_url");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (videoIid != null) {
                apodItem.setThumb("th_" + videoIid + ".jpg");   // thumb filename
            }
            apodItem.setLowres(thumbUrl);
            super.onPostExecute(s);
            new ImgLowresTask().execute(thumbUrl);
        }
    }*/

    /* OLD APOD TASK CODE, 26.10.2017 - replaced by asyncLoad
    private class apodTask extends AsyncTask<String, String, String> {
        private String imgUrl;
        @Override
        protected String doInBackground(String... params) {
            apodItem = new spaceItem();

            // Enable TLSv1 below 5.0 (Lollipop) TODO: TLS > 1.0 / move to oncreate / android 7
            // https://blog.dev-area.net/2015/08/13/android-4-1-enable-tls-1-1-and-tls-1-2/
            // https://developer.android.com/about/versions/nougat/android-7.0-changes.html#tls-ssl
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP &&
                    sharedPref.getBoolean("enable_tls_pre_lollipop", true)) {
                SSLContext sslcontext = null;
                try {
                    sslcontext = SSLContext.getInstance("TLSv1");
                    sslcontext.init(null, null, null);
                    SSLSocketFactory noSSLv3Factory = new TLSSocketFactory();
                    HttpsURLConnection.setDefaultSSLSocketFactory(noSSLv3Factory);
                } catch (NoSuchAlgorithmException | KeyManagementException e) {
                    e.printStackTrace();
                }
            }

            HttpsURLConnection nasa_conn = null;
            BufferedReader reader = null;
            try {
                URL url = new URL(params[0]);
                nasa_conn = (HttpsURLConnection) url.openConnection();
                nasa_conn.connect();
                InputStream jsonstream = nasa_conn.getInputStream();
                reader = new BufferedReader(new InputStreamReader(jsonstream));
                StringBuffer jsonbuffer = new StringBuffer();
                String jsonstring;

                while((jsonstring = reader.readLine()) != null) {
                    jsonbuffer.append(jsonstring);
                }
                // this is returned to onPostExecute
                return jsonbuffer.toString();

            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                // TODO : handle over rate limit here
                // OVER_RATE_LIMIT
                // You have exceeded your rate limit. Try again later or contact us at https://api.nasa.gov/contact/ for assistance
                return "FILENOTFOUND";
            } catch (IOException e) {
                // running into this because of DNS problems in AVD device. Seems to be because
                // of LAN card and Wifi present in host - Problem on my Windows Studio installation
                // And on 14.10.2017 on AVD Android 4.1...
                e.printStackTrace();
                return e.getMessage();
            } finally {
                if(nasa_conn != null) {
                    try {
                        nasa_conn.disconnect();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if(reader != null) {
                        reader.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
        // INFO: this one get's added by starting to type onPostEx.. in the editor
        @Override
        protected void onPostExecute(String s) {
            String sTitle = "n/a";
            String sCopyright = "";
            String sExplanation = "";
            String sMediaType = "";
            String sHiresUrl = "";
            String sDate = "";
            JSONObject parent = null;
            Uri resource_uri = null;
            if(s != null) {
                if (s.equals("FILENOTFOUND")) {
                    // TODO: this is not necessarily the case...
                    Log.e("HFCM", "FILENOTFOUND - MAYBE API RATE LIMIT EXCEEDED ??");
                    new dialogDisplay(MainActivity.this, getString(R.string.rate_limit_exceeded),
                            getString(R.string.no_apod));
                    return;
                } else if (s.startsWith("Connection")) {
                    new dialogDisplay(MainActivity.this, s + "\n" + getString(R.string.enable_tls), "NASA Connect");
                    return;
                }
                //JSONObject parent = null;
                try {
                    parent = new JSONObject(s);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(parent == null) {
                    return;
                }
                if(parent.has("media_type")) {
                    try {
                        sMediaType = parent.getString("media_type");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if(parent.has("date")) {
                    try {
                        sDate = parent.getString("date");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if(parent.has("copyright")) {
                    try {
                        sCopyright = "Copyright: " + parent.getString("copyright").
                                replaceAll(System.getProperty("line.separator"), " ");
                        // removing any new lines - as found in IC 1396: Emission Nebula in Cepheus
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if(parent.has("title")) {
                    try {
                        sTitle = parent.getString("title");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if(parent.has("url")) {
                    try {
                        imgUrl = parent.getString("url");
                        resource_uri = Uri.parse(imgUrl);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        // TODO: missing on 11.09.2017 - leads to strange effects of missing keys
                    }
                }
                if(parent.has("hdurl")) {
                    try {
                        sHiresUrl = parent.getString("hdurl");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
                if(parent.has("explanation")) {
                    try {
                        sExplanation = parent.getString("explanation");
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                if (resource_uri != null) {
                    if (sMediaType.equals("video")) {
                        // note, that this rewrites sMediaType variable!
                        String host = resource_uri.getHost();
                        List<String> path = resource_uri.getPathSegments();
                        apodItem.setHires(resource_uri.toString());
                        if (host.equals("www.youtube.com")) {
                            // TODO: now we just assume 'embed' link - might not be true always ??
                            // url> https://www.youtube.com/embed/" + path.get(1) + "?rel=0
                            // img> https://img.youtube.com/vi/9Vp2jUQ4rNM/0.jpg
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
                            // thumbnail image name: th_<video-id>.jpg
                            // Note: this needs a REST API call to gather the required infos..
                            apodItem.setLowres("");     // will hold thumbnail url
                        } else {
                            sMediaType = M_VIDEO_UNKNOWN;
                            apodItem.setThumb("th_UNKNOWN.jpg");
                        }
                    } else {
                        // thumbnail gets name of lowres image with prefix 'th_'
                        apodItem.setThumb("th_" + resource_uri.getLastPathSegment());
                        apodItem.setHires(sHiresUrl);
                        apodItem.setLowres(imgUrl);
                    }
                }
            } else {
                Log.e("HFCM", "no APOD String returned from doInBackground");
            }
            // TODO shouldn't super be executed first ??? might have done this bad
            super.onPostExecute(s);

            apodItem.setTitle(sTitle);
            apodItem.setCopyright(sCopyright);
            apodItem.setExplanation(sExplanation);
            apodItem.setHires(sHiresUrl);
            // now using long int epoch value for date in spaceItem
            SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", loc);
            // normally we would use code as below for correct locale, but we parse a specific
            // format here :_ "date": "2017-09-17", as returned by NASA in their json
            // DateFormat dF = SimpleDateFormat.getDateInstance();
            long epoch = 0;
            try {
                epoch = dF.parse(sDate).getTime();
            } catch (ParseException e) {
                e.printStackTrace();
            }
            apodItem.setDateTime(epoch);
            apodItem.setMedia(sMediaType);
            apodItem.setRating(0);
            apodItem.setLowSize("");
            apodItem.setHiSize("");

            // Keep a local copy of the original apod nasa json file returned by api
            // TODO: remove these as well? they cannot be recreated later on!
            if (parent != null) {
                utils.writeJson(getApplicationContext(), String.valueOf(epoch) + ".json", parent);
            }

            // For VIMEO, "insert" an extra thread using oembed API to determine infos about the
            // thumbnail image URL before calling the imgLowResTask thread to load thumbnail bitmap
            if (sMediaType.equals(M_VIMEO)) {   // TODO: test this
                //new vimeoInfoTask().execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
                Log.i("HFCM", "Launching asyncLoad for VIMEO info: " + sHiresUrl);
                new asyncLoad(MainActivity.this, "VIMEO_INFO").
                        execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
            } else {
                new ImgLowresTask().execute(imgUrl);
            }
        }
    }*/

    /* Old code for imglowres Load to create thumbnails - now in asyncload
    private class ImgLowresTask extends AsyncTask<String, String, Bitmap> {
        @Override
        protected Bitmap doInBackground(String... params) {
            // TODO 20.08.2017 - bitmap could not be loaded from youtube by the below method
            //                   using link https://img.youtube.com/vi/53641212/0.jpg
            //                   bitmap null as result passed to onPostExecute() - crashed
            Bitmap bitmap = null;
            try {
                URL lrURL = new URL(params[0]);     // URL for image in params[0]
                try {
                    bitmap = BitmapFactory.decodeStream((InputStream)lrURL.getContent());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(bitmap != null) {
                apodItem.setLowSize(String.valueOf(bitmap.getWidth()) + "x" +
                        String.valueOf(bitmap.getHeight()));
            }
            return bitmap;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
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
                File thumbFile = new File(getApplicationContext().getFilesDir(), apodItem.getThumb());
                Bitmap thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 120, 120);
                FileOutputStream outstream = null;
                try {
                    outstream = new FileOutputStream(thumbFile);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, outstream);
                try {
                    if (outstream != null) {
                        outstream.flush();
                        outstream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
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
    }*/
}
