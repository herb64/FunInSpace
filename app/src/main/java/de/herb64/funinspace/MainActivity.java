package de.herb64.funinspace;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
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

public class MainActivity extends AppCompatActivity implements ratingDialog.RatingListener {

    private spaceItem apodItem;                     // the latest item to be fetched
    private ArrayList<spaceItem> myList;            // to be replaced by LinkedHashMap
    private HashSet<String> itemTitles;             // just containing title strings of items - not used yet
    private LinkedHashMap<String, spaceItem> myMap; // replacement for myList - abandoned
    //private myAdapter adp;
    private spaceAdapter adp;
    private JSONArray parent;
    private String jsonData;
    private String localJson = "nasatest.json";
    protected thumbClickListener myThumbClickListener;
    private ratingChangeListener myRatingChangeListener;
    private ListView myItemsLV;
    private SearchView mySearch;
    private deviceInfo devInfo;
    private int maxTextureSize = 999;   // TODO clean this, just to check the 999 was ok - still seems to be found, see Nathan
    private String lastImage;           // for log dialog title
    private Locale loc;
    protected Drawable expl_points;
    private ActionMode mActionMode = null;
    private SharedPreferences sharedPref;
    private boolean thumbQualityChanged = false;    // indicate preference setting change

    // Using JNI for testing with NDK and C code in a shared lib .so file
    static {
        System.loadLibrary("hfcmlib");
    }
    public native String yT();
    public native String nS();
    public native String vA();

    // App settings variables from preferences dialog
    private boolean newestFirst = true;             // sort order for list of space items
    private boolean needWifi = false;               // hires loading - only with wifi?

    // We go for our CONSTANTS here, this is similar to #define in C for a constant
    //public static String TAG = MainActivity.class.getSimpleName();
    private static final int HIRES_LOAD_REQUEST = 1;
    private static final int GL_MAX_TEX_SIZE_QUERY = 2;
    private static final int SETTINGS_REQUEST = 3;
    // changed to other location on 13.10.2017 into testing folder
    private static final String DROPBOX_JSON = "https://dl.dropboxusercontent.com/s/3yqsmthlxth44w6/nasatest.json";
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
        newestFirst = true; // we go and remove that, newest always on top!!!

        // TODO solve issue with vector on 4.1 (4.x?) - for now, just dirty workaround...
        // arrow_down_float image only - don't like that, but do it now
        // Drawable for textview - use builtin in "android.R...." - now use SVG graphic via xml
        // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            expl_points = ContextCompat.getDrawable(this, R.drawable.hfcm);
        } else {
            expl_points = ContextCompat.getDrawable(this, android.R.drawable.arrow_down_float);
        }
        // https://medium.com/@chrisbanes/appcompat-v23-2-age-of-the-vectors-91cbafa87c88
        // - vectorDrawables.useSupportLibrary = true >> already in build.gradle
        // - setting "app:srcCompat="@drawable/hfcm" in spaceitem textview xml also does not help
        // android.content.res.Resources$NotFoundException: File res/drawable/hfcm.xml from drawable resource ID #0x7f02005b
        // on 4.1 AVD....
        // https://stackoverflow.com/questions/39091521/vector-drawables-flag-doesnt-work-on-support-library-24
        // https://developer.android.com/topic/libraries/support-library/features.html

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
        // onCreate() and even the Toast wihing onActivityResult shows the correct value.
        devInfo = new deviceInfo(getApplicationContext());

        /* hmmm, strange behaviour
        boolean flashInstalled = false;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo("com.adobe.flashplayer", 0);
            if (ai != null)
                flashInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            flashInstalled = false;
        }*/

        // Prepare the main ListView containing all our Space Items
        myItemsLV = (ListView) findViewById(R.id.lv_content);

        // --------  Option 1 for  contextual action mode (non multi select) ---------

        // LONG CLICK MENU - Option 1 for "non multi select"...
        // needs ActionMode.Callback as well, see code at end of this listing...
        //myItemsLV.setLongClickable(true);
        // hmmm, it did not work, then setting in layout via xml - worked. removed again, still
        // works. and above setLongClickable also does not have influence... not sure, why it
        // now works
        // We also need an action bar
        // https://developer.android.com/design/patterns/actionbar.html
        // https://material.io/guidelines/patterns/selection.html
        // https://www.youtube.com/watch?v=blJMA9CHkyc  09:45 about xml creation of menu itself
        /*myItemsLV.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

            public boolean onItemLongClick(AdapterView<?> parent, View v,
                                           int position, long id) {

                // Just for testing and debugging: write a toast
                //parent.getItemAtPosition(position)
                String title = myList.get(position).getTitle();
                String toaster = String.format("Long click on %d, id %d: %s", position, id, title);
                Toast.makeText(MainActivity.this, toaster, Toast.LENGTH_LONG).show();

                // We like to use "contextual action mode" to be able to select multiple items
                // https://developer.android.com/guide/topics/ui/menus.html#CAB
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

        // Handling multiple choices - add another listener. Note, that this also responds to
        // long clicks without using the longclick listener explicitly...
        // https://www.youtube.com/watch?v=kyErynku-BM  (Prabeesh R K)
        myItemsLV.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        myItemsLV.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {
            // keep track of selected position values
            private ArrayList<Integer> selected;

            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode actionMode,
                                                  int position,
                                                  long id,
                                                  boolean checked) {
                if (checked) {
                    selected.add(position);
                } else {
                    // Either object or postion as parameter? here, both are of type int :)
                    // https://stackoverflow.com/questions/4534146/properly-removing-an-integer-from-a-listinteger
                    selected.remove(Integer.valueOf(position));
                }
                myList.get(position).setSelected(checked);
                adp.notifyDataSetChanged();
            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.menu_cab_main, menu);
                selected = new ArrayList<>();
                return true;
            }

            @Override
            public boolean onPrepareActionMode(android.view.ActionMode actionMode, Menu menu) {
                return false;
            }

            // Now we did click on an action item in our CAB and need to process actions on all
            // selected SpaceItems in our listview
            @Override
            public boolean onActionItemClicked(android.view.ActionMode actionMode, MenuItem menuItem) {
                //return false;
                switch (menuItem.getItemId()) {
                    case R.id.cab_delete:
                        // TODO need to remove from json as well to make it permanent... undo???
                        new dialogDisplay(MainActivity.this, "This currently only deletes items for testing from the shown list. " +
                                "This is not yet persistent, and restart of the App loads all deleted items again.", "Don't panic...");

                        // Within ArrayList - removal from back to front is essential!!
                        Collections.sort(selected, Collections.<Integer>reverseOrder());
                        for (int idx : selected) {
                            adp.remove(myList.get(idx));
                            adp.notifyDataSetChanged();     // TODO set notifychanged automatically
                            // TODO: remove from json and rewrite
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
                        // activity implements
                        FragmentManager fm = getSupportFragmentManager();
                        ratingDialog dlg = new ratingDialog();
                        // Just pass the indices to the rating fragment, so that they get returned
                        // by our interface implementation. This avoids a global def. of "selected"
                        // and potential problems with phone rotation
                        Bundle fragArguments = new Bundle();    // AGAIN: do NOT use non default constructor with fragments!
                        fragArguments.putIntegerArrayList("indices", selected);
                        int current = 0;
                        for (int i=0; i<selected.size();i++) {
                            if (myList.get(selected.get(i)).getRating() > current) {
                                current = myList.get(selected.get(i)).getRating();
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
                // make sure to reset all selected items
                for (int idx = 0; idx < myList.size(); idx++) {
                    myList.get(idx).setSelected(false);
                }
                mActionMode = null;
            }
        });

        // Create a listeners for handling clicks on the thumbnail image and for rating changes
        myThumbClickListener = new thumbClickListener();
        myRatingChangeListener = new ratingChangeListener();

        myList = new ArrayList<> ();
        itemTitles = new HashSet<>();
        //adp = new myAdapter(getApplicationContext(), R.layout.space_item, myList);
        adp = new spaceAdapter(getApplicationContext(), MainActivity.this, R.layout.space_item, myList);
        myItemsLV.setAdapter(adp);              // the "good old" arrayadapter
        // old stuff from trying Adapter to work with LinkedHashMap
        //myMap = new LinkedHashMap<>();
        //hashadp = new myHashAdapter(getApplicationContext(), R.layout.space_item, myMap);
        //myItemsLV.setAdapter(hashadp);        // the linkedhashmap version - currenty abandoned

        // TODO  - cleanup all the search/filtering stuff....
        // SearchView - this is defined in XML, but set to "GONE" by default. When search is
        // requested, this is shown above the listview.
        // https://www.youtube.com/watch?v=c9yC8XGaSv4
        // custom filters etc.. this one seems to be for us
        // https://www.youtube.com/watch?v=YnNpwk_Q9d0
        // See also
        // https://www.youtube.com/watch?v=9OWmnYPX1uc
        // Search View as a menu item.. See code in onCreateOptionsMenu() below...
        //mySearch = (SearchView) findViewById(R.id.sv_search);   // remove from the layout!!!
        //mySearch.setVisibility(View.GONE);
        /*mySearch.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                adp.getFilter().filter(s);
                mySearch.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adp.getFilter().filter(s);
                return false;
            }
        });*/

        if (savedInstanceState != null) {
            // The spaceItem internal structure and the json data string are restored
            // on Android 8.0 this fails with Transaction Too Large error, so we do not put this
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
            // TODO: backup and restore...
            jsonData = null;
            File jsonFile = new File(getApplicationContext().getFilesDir(), localJson);
            if (jsonFile.exists()) {
                jsonData = utils.readf(getApplicationContext(), localJson);
            }

            if (jsonData != null) {
                SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
                maxTextureSize = shPref.getInt("maxtexsize", 0);
                devInfo.setGlMaxTextureSize(maxTextureSize);
                Log.i("HFCM", "MAX_TEXTURE_SIZE read from shared prefs: " + maxTextureSize);
                // TODO: in case, the shared preferences are lost for some reason, we should
                // run the gl texture size detection again!

                if (jsonData.equals("")) {
                    jsonData = "[]";
                }
                parent = null;

                try {
                    parent = new JSONArray(jsonData);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // Parse the local json file contents and add these to myList, used by listview and
                // the parent json array. This is not yet a good solution.
                addItems();
                // TODO: recheck, just new test on 11.09.2017 - check for missing thumbs
                checkMissingThumbs();
            } else {
                // jsonData is empty. It looks like we did not have a local json file yet, or it
                // is not a valid one. So we create a new empty parent array and attempt to load a
                // prefilled json file from our (hardcoded) testing dropbox link.

                // GL TEXTURE check is only run at first time after installation and result is
                // written to shared preferences
                Intent texSizeIntent = new Intent(this, TexSizeActivity.class);
                startActivityForResult(texSizeIntent, GL_MAX_TEX_SIZE_QUERY);
                devInfo.setGlMaxTextureSize(maxTextureSize);

                parent = new JSONArray();
                Toast.makeText(MainActivity.this, "Initial installation - getting JSON base data " +
                                "from Herbert's DropBox and loading required images in background for " +
                                "thumbnail pictures generation to have some test data available...",
                        Toast.LENGTH_LONG).show();
                new loadFromDropboxTask().execute(DROPBOX_JSON); // calls addItems() + adapter notify
            }

            // now go for the latest APOD item to append - call NASA and get infos
            // TODO this should only be done once a day, afterwards info is available in local json
            // TODO make config option, if connection should be done if not connected in Wifi
            // we can disable this for debugging purposes to avoid unnecessary calls to NASA
            if (sharedPref.getBoolean("get_apod", true)) {
                new apodTask().execute(nS());
                // or some local URL using python simplehttpserver (change to httpurlconnection)
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
        //ArrayList<String> titles = new ArrayList<>();
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
        // Rewrite local json
        String outString = null;
        try {
            outString = parent.toString(2);     // spacing of 2
        } catch (JSONException e) {
            e.printStackTrace();
        }
        utils.writef(getApplicationContext(), localJson, outString);
    }

    // old code of myHashAdapter derived from HfcmMapAdapter is moved to bottom as comment
    // old code of myAdapter using ArrayList also moved to bottom as comment

    // GET APOD JSON INFOS FROM NASA. THIS STARTS ANOTHER THREAD TO LOAD THE LOWRES IMAGE
    /*
     * TODO: recheck again, VERY IMPORTANT!!!, also in terms of splitting large code with inner classes into smaller segments, as with my spaceItemFilter (22.10.2017)
     * https://medium.com/freenet-engineering/memory-leaks-in-android-identify-treat-and-avoid-d0b1233acc8
     * making apodTask static to eliminate implicit reference does not allow access to apodItem
     * any more. So we need a constructor
     */
    private class apodTask extends AsyncTask<String, String, String> {
        private String imgUrl;
        @Override
        protected String doInBackground(String... params) {
            apodItem = new spaceItem();

            // Enable TLSv1 on Android below 5.0 (Lollipop) TODO TLS higher versions?
            // https://blog.dev-area.net/2015/08/13/android-4-1-enable-tls-1-1-and-tls-1-2/
            // could we move that code to "onCreate?"
            // see also android 7 changes, might be of interest at a later point
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
                //String ttt = e.getMessage();
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
                        // note, that this rewrites mediatype!!
                        String host = resource_uri.getHost();
                        List<String> path = resource_uri.getPathSegments();
                        apodItem.setHires(resource_uri.toString());
                        if (host.equals("www.youtube.com")) {
                            // TODO: now we just assume 'embed' link - might not be true always ??
                            // https://www.youtube.com/embed/" + path.get(1) + "?rel=0
                            // corresponding image link can be found on
                            // https://img.youtube.com/vi/9Vp2jUQ4rNM/0.jpg
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
                            // hmmm, many more possibilities might exist, depending on NASA content
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
            // TODO shouldn't super be executed first ?=?? might have done this bad
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

            // 20.09.2017 - keep a local copy of the nasa json file returned by api for reference
            try {
                if (parent != null) {
                    utils.writef(getApplicationContext(),
                            String.valueOf(epoch) + ".json", parent.toString(2));
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // For Vimeo, we need to "insert" an extra thread using oembed API to determine
            // infos about the thumbnail image URL before calling the imgLowResTask thread to
            // load thumbnail bitmap data.
            if (sMediaType == M_VIMEO) {
                new vimeoInfoTask().execute("https://vimeo.com/api/oembed.json?url=" + sHiresUrl);
            } else {
                new ImgLowresTask().execute(imgUrl);
            }
        }
    }

    // Get vimeo infos about thumbnail picture URL first, before calling imgLowResTask to load
    // this thumbnail image.
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
    }

    // TODO - about returning values from asynctask !!!
    // TODO - seems to work with execute().get()  but it blocks!!! - search topic: wait for asynctask to complete
    // ++++   >>>  https://stackoverflow.com/questions/12575068/how-to-get-the-result-of-onpostexecute-to-main-activity-because-asynctask-is-a
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
            // Check, if title string is already present - we rely on unique title strings provided
            // by NASA for each new image!

            // Code using key finding in HashMap...
            /*if (myMap.containsKey(apodItem.getTitle())) {
                Toast.makeText(MainActivity.this, R.string.already_loaded,
                        Toast.LENGTH_LONG).show();
                return;
            }*/

            // "Good old ugly?" version just iterating all items in ArrayList. This is ok, because
            // first item should match, because latest image is on top of the list.
            // already been loaded before. In our case, it just depends on the order of search :)
            // TODO: why not skip that and allow for duplicate title strings?
            for(int i=0; i<myList.size();i++) {
                if(apodItem.getTitle().equals(myList.get(i).getTitle())) {
                    // TODO here's the point to check, if the thumbnail file exists, and if not
                    // it was lost. so load the image and create thumb again... but this is unlikely
                    // to happen, unless the app was uninstalled (already handled) or some hardware
                    // defects are present...
                    Toast.makeText(MainActivity.this, R.string.already_loaded,
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
            // TODO 20.08.2017 - null bitmap was passed by doInBackground() - crashed here
            // have a prepared "not found image thumbnail" for these cases...
            if (bitmap != null) {
                File thumbFile = new File(getApplicationContext().getFilesDir(), apodItem.getThumb());
                // TODO: make it RGB565 to save memory and work on not loading all images permanently into memory
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
                apodItem.setBmpThumb(null);     // just have a black image here
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
            // this one needs to be appended to the local json array
            parent.put(apodObj);
            String outString = null;
            try {
                outString = parent.toString(2);     // spacing of 2
            } catch (JSONException e) {
                e.printStackTrace();
            }
            // next, this needs to be rewritten to internal storage
            utils.writef(getApplicationContext(), localJson, outString);

            // add our new entry to the spaceItem list and notify the Adapter
            //myMap.put(apodItem.getTitle(), apodItem);
            //hashadp.notifyDataSetChanged();

            if (newestFirst) {
                myList.add(0, apodItem);
            } else {
                myList.add(apodItem);
            }
            adp.notifyDataSetChanged();

            Toast.makeText(MainActivity.this, R.string.apod_load,
                    Toast.LENGTH_LONG).show();
            // scroll to the new space Item in the list, that has been loaded
            if (newestFirst) {
                myItemsLV.setSelection(0);
            } else {
                myItemsLV.setSelection(adp.getCount() -1);
            }
        }
    }

    // Listen to any rating bar changes
    // Strange: does not get called at all, but works, if changing in xml
    // style="@style/Widget.AppCompat.RatingBar" - works
    // from
    // style="@style/Widget.AppCompat.RatingBar.Small" - fails
    // same with device default bars: small versions do not call the listener!!
    // YEAH, the "small" versions are designed to ignore any interaction - hmmmm, very bad for me
    private class ratingChangeListener implements RatingBar.OnRatingBarChangeListener {
        @Override
        public void onRatingChanged(RatingBar ratingBar, float v, boolean b) {
            Toast.makeText(MainActivity.this, "Have rating" + v, Toast.LENGTH_SHORT).show();
        }
    }

    // just as a first test: we create a listener class for onclick events on our
    // thumbnail images. The adapter sets a Tag, which can be read here and used
    // for further actions. NEW: this listener now also runs for the explanation textviews!!!
    // TODO add listeners for clicking on later to be added rating bar
    // maybe this is not a good way to do it, and just using base class might be better
    // but we are going to learn and experiment... :)
    private class thumbClickListener implements View.OnClickListener {
        // onClick() gets passed the view in which we store the URL to be opened
        // in a TAG - this happens in getView() in our adapter...
        // passing multiple tags? we could just combine this in a single string of special format
        // See https://developer.android.com/training/basics/firstapp/starting-activity.html
        @Override
        public void onClick(View view) {
            int idx = (int) view.getTag();

            // 20000+ - rating bars, but this actually does not work, because the small versions
            // do not support interaction. See links at other places in this code
            /*if (idx >= 2*MAX_ITEMS) {
                RatingBar rb = (RatingBar) view;
                Toast.makeText(MainActivity.this, "Rating value is " + rb.getRating(),
                        Toast.LENGTH_LONG).show();
            }*/

            // bad hack: index i+MAX_ITEMS is corresponding textview for thumbnail on index i
            if (idx >= MAX_ITEMS) {
                // we just reset the maxlines to a larger limit and remove the ellipse stuff. This
                // is only temporarily and automatically disappears when scrolling or when clicking
                // again.
                // TODO - during background load of thumbs at initial install, the full text always
                //        disappears on refreshs - hmm, should we really bother?
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
                myItemsLV.setSelection(idx-MAX_ITEMS);
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
                    // Use ..forResult now ALWAYS to get logstring returned for debugging, also
                    // if hires size is already
                    // TODO: how about running one thread to just query the hires image size
                    //       and then adjust possible exchanged resolution links (as i found)
                    //       and after this, getting the hires image..
                    startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                    /*if(myList.get(idx).getHiSize().equals("")) {
                        startActivityForResult(hiresIntent, HIRES_LOAD_REQUEST);
                    } else{
                        startActivity(hiresIntent);
                    }*/
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
                String hUrl = data.getStringExtra("hiresurl");
                int listidx = data.getIntExtra("lstIdx",0);
                String logString = data.getStringExtra("logString");
                new dialogDisplay(this, logString, lastImage);
                // we now have the index into our list, so set the field
                myList.get(listidx).setHiSize(testsize);

                // update local json array "parent" - bad here: doing a loop to search
                // the correct url... - should be done better
                // TODO: how to get the correct object immediately? Better change JSON format
                // to put Title out of content one hierarchy higher
                JSONObject obj = null;
                JSONObject content = null;
                String strHres = "";
                for (int i = 0; i < parent.length(); i++) {
                    try {
                        obj = parent.getJSONObject(i);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (obj != null) {
                            content = obj.getJSONObject("Content");
                        } else {
                            continue;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    try {
                        if (content != null) {
                            strHres = content.getString("Hires");
                        } else {
                            continue;
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    if(strHres.equals(hUrl)) {
                        try {
                            content.put("HiSize", testsize);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        String outString = null;
                        try {
                            outString = parent.toString(2);     // spacing of 2
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        utils.writef(getApplicationContext(), localJson, outString);
                        break;
                    }
                }
                adp.notifyDataSetChanged();
                //hashadp.notifyDataSetChanged();
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
                    //shPref.getBoolean("rgb565_thumbs", false);
                    Log.i("HFCM", "Low quality thumbs enable, now: " + shPref.getBoolean("rgb565_thumbs", false));
                    thumbQualityChanged = false;
                    myList.clear();
                    addItems();
                    adp.notifyDataSetChanged();
                }
                //SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(this);
                //String order = shPref.getString("item_order", "newest_first");
                //String returnedorder = data.getStringExtra("order");    // TODO
                //newestFirst = order.equals("newest_first");
                newestFirst = true;
            }
        }
    }

    // Try to listen for changed preferences here. Important: this one reacts IMMEDIATELY!!!
    // This means, if changing a preference and this might trigger a longer action, it could
    // get bad if user toggles that switch. The onActivityResult() method reacting on SETTINGS_REQUEST
    // is called AFTER the settings dialog activity is closed.
    SharedPreferences.OnSharedPreferenceChangeListener prefChangeListener = new
            SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
                                                      String changed) {
                    if (changed.equals("rgb565_thumbs")) {
                        // on each switch toggle, we invert the value. Final value is checked in
                        // onActivityResult() to determine, if an action is needed
                        thumbQualityChanged ^= true;
                    }
                }
            };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // 22.10.2017 - search
        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView sv = (SearchView) MenuItemCompat.getActionView(searchItem);
        sv.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String s) {
                adp.getFilter().filter(s);
                return false;
            }

            @Override
            public boolean onQueryTextChange(String s) {
                adp.getFilter().filter(s);
                return false;
            }
        });
        return true;
    }

    /*
     * This code handles selections from the menu bar
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
            return true;
        }
        // Sync metatata with dropbox. For now: just overwrite everything, This allows to refresh
        // without a new installation.
        // might be a merge, keeping local infos about ratings ... if not dropping this at all
        if (id == R.id.dropbox_sync) {
            new dialogDisplay(MainActivity.this, "Sync from dropbox without reinstallation of the App", "To be implemented");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // see also
    // https://developer.android.com/topic/performance/memory.html
    /*@Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
    }*/

    // Get JSON file from DropBox link after initial installation. This also triggers the load
    // of all thumbnails in an extra thread
    // TODO - needs retained fragment to run, so do not rotate phone while thumbs are loading
    // TODO - also found problems if Wifi is not available after new installation of app
    //        on 17.09.2017. And even then, this was bad with >40 images. CHECK!!! see notices
    private class loadFromDropboxTask extends AsyncTask<String, String, String> {
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
                // 19.09.2017 - new code to write json file.. not yet understood, how it could
                // work before at all, cause there was no write...
                try {
                    utils.writef(getApplicationContext(), localJson, parent.toString(2));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                addItems();
                // Note: we cannot run network operations directly in the checkMissingThumbs()
                // function. Because code in onPostExecute() does belong to the main thread
                // for the same reason: do not block during checkMissingThumbs!!!
                // https://stackoverflow.com/questions/10686107/what-does-runs-on-ui-thread-for-onpostexecute-really-mean
                checkMissingThumbs();
                adp.notifyDataSetChanged();
                //hashadp.notifyDataSetChanged();
            }
        }
    }

    /**
     * Add all items currently defined within the local JSON to the ArrayList of space items.
     */
    public void addItems() {
        JSONObject obj = null;
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
        // The newest first option has been disabled, we now always display the newest item first!
        //int i;
        //for (int k = 0; k < parent.length(); k++) {
        for (int i = parent.length()-1; i >=0 ; i--)
        {
            /*if (newestFirst) {
                i = parent.length() - 1 -k;
            } else {
                i = k;
            }*/
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
                strTitle = content.getString("Title");
                dateTime = content.getLong("DateTime");
                // TODO - temporary code for removing newlines from copyright for existing
                // TODO - long copyright notices, as in "Cassini approaches Saturn - 2017-09-11
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
            //Log.i("HFCM", "Add Items " + strTitle + " > " + strThumb);
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
                //Bitmap thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                newitem.setBmpThumb(thumb);
                int allocByteCount;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    allocByteCount = thumb.getAllocationByteCount();
                } else {
                    allocByteCount = thumb.getByteCount();
                }
                Log.i("HFCM", "Thumbnail size: " + strThumb + " = " + allocByteCount);
            } else {
                newitem.setBmpThumb(null);
            }
            myList.add(newitem);
            //myMap.put(strTitle, newitem);
        }
    }

    // Check for any missing thumb images. We iterate all items and for those with no bitmap
    // contained, we kick of a loader thread, which gets the image file from the given lowres
    // URL and saves a thumbnail file.
    public void checkMissingThumbs() {
        ArrayList<Integer> missing = new ArrayList<Integer>();
        //ArrayList<String> missing2 = new ArrayList<String>();
        for(int i=0; i<myList.size(); i++) {
            //if (myList.get(i).getBmpThumb() == null) {
            //String tt = myList.get(i).getMedia();
            // TODO recheck no lowres key handling - here checked to avoid crash 11.09.2017
            if (myList.get(i).getBmpThumb() == null && !myList.get(i).getLowres().equals("")) {
                // TODO: this is temporary only: vimeo videos do not have a thumb yet, so skip
                // unknown not yet checked...
                missing.add(i);
                myList.get(i).setThumbLoadingState(View.VISIBLE);
            }
        }
        // map version: arraylist of title strings
        /*for (String key : myMap.keySet()) {
            if (myMap.get(key).getBmpThumb() == null &&
                    !myMap.get(key).getLowres().equals("")) {
                missing2.add(key);
                myMap.get(key).setThumbLoadingState(View.VISIBLE);
            }
        }*/

        // we might change this to missing2 if using map based version - needs also quite some
        // changes in thumbLoaderTask() code
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
    //private class thumbLoaderTask extends AsyncTask<ArrayList<String>, String, Void> {
        private ArrayList<Integer> missing;
        //private ArrayList<String> missing2;
        //@Override
        //protected Void doInBackground(ArrayList<String>... params) {
        @Override
        protected Void doInBackground(ArrayList<Integer>... params) {
            // url for image is found in params[0]...
            Bitmap bitmap = null;
            Bitmap thumbnail = null;
            File thumbFile = null;
            spaceItem wkItem;
            //missing2 = params[0];
            //for (String str : missing2) {
            //    wkItem = myMap.get(str);
            missing = params[0];
            for (int i=0; i < missing.size(); i++) {
                wkItem = myList.get(missing.get(i));
                String lowresurl = wkItem.getLowres();
                try {
                    URL imgurl = new URL(lowresurl);
                    try {
                        // we use this option to save some memory - experimental
                        // but not here, because it could cause the saved file to be of low quality
                        // do it in addItems().
                        /*if (sharedPref.getBoolean("rgb565_thumbs", false)) {
                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inPreferredConfig = Bitmap.Config.RGB_565;
                            bitmap = BitmapFactory.decodeStream((InputStream) imgurl.getContent(), null, options);
                        } else {
                            bitmap = BitmapFactory.decodeStream((InputStream)imgurl.getContent());
                        }*/
                        bitmap = BitmapFactory.decodeStream((InputStream)imgurl.getContent());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (bitmap != null) {
                    wkItem.setLowSize(String.valueOf(bitmap.getWidth()) + "x" +
                            String.valueOf(bitmap.getHeight()));
                    // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
                    thumbFile = new File(getApplicationContext().getFilesDir(), wkItem.getThumb());
                    thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 120, 120);
                    wkItem.setBmpThumb(thumbnail);
                }
                // TODO DOCU: adapter notify does not work from within this thread
                // adp.notifyDataSetChanged();
                // Caused by: android.view.ViewRootImpl$CalledFromWrongThreadException:
                // Only the original thread that created a view hierarchy can touch its views.
                // possible option:  runOnUiThread ???
                // NO, just use the progress update mechanism for this
                // https://stackoverflow.com/questions/6450275/android-how-to-work-with-asynctasks-progressdialog
                publishProgress(missing.get(i));
                //publishProgress(str);

                // And write the thumbnail to internal storage
                FileOutputStream outstream = null;
                try {
                    if (thumbFile != null) {
                        outstream = new FileOutputStream(thumbFile);
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                try {
                    if (outstream != null) {
                        thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, outstream);
                        outstream.flush();
                        outstream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        // For each loaded thumbnail, we receive a progress update and notify the adapter to update
        // the view, if the image has been loaded. We might run this in parallel for multiple images
        //@Override
        //protected void onProgressUpdate(String... values) {
        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            myList.get(values[0]).setThumbLoadingState(View.INVISIBLE);
            adp.notifyDataSetChanged();
            /*myMap.get(values[0]).setThumbLoadingState(View.INVISIBLE);
            hashadp.notifyDataSetChanged();*/
        }
    }

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
}
