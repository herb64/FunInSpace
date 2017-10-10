package de.herb64.funinspace;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
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
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.HttpsURLConnection;

import de.herb64.funinspace.helpers.deviceInfo;
import de.herb64.funinspace.helpers.dialogDisplay;
import de.herb64.funinspace.helpers.helpers;
import de.herb64.funinspace.models.spaceItem;


// TODO ITEMS
// Splash screen / Launch screen
// https://www.bignerdranch.com/blog/splash-screens-the-right-way/
// https://android.jlelse.eu/right-way-to-create-splash-screen-on-android-e7f1709ba154
// main issue: do not waste time of users

// TODO Log statements: Log.d etc.. should not be contained in final release, but how to do this?
// see https://stackoverflow.com/questions/2446248/remove-all-debug-logging-calls-before-publishing-are-there-tools-to-do-this
// checkout "ProGuard", which is mentioned here...


// TODO: handle possible errors returned: found on 02.08.2017 - leads to null bitmap returned
// so the http return code seems to be passed in json...
/*
{
        "code": 500,
        "msg": "Internal Service Error",
        "service_version": "v1"
        }*/

public class MainActivity extends AppCompatActivity {

    private spaceItem apodItem;                     // the latest item to be fetched
    //private List<spaceItem> myList;
    private ArrayList<spaceItem> myList;
    private myAdapter adp;
    private JSONArray parent;
    private String jsonData;
    private helpers h;
    private String localJson = "nasatest.json";
    //private String testJson = "herbtest.json";
    private thumbClickListener myThumbClickListener;
    private Intent hiresIntent;
    private ListView myItemsLV;
    private deviceInfo devInfo;
    private int maxTextureSize = 999; // TODO clean this, just to check the 999 was ok
    private String lastImage;           // for log dialog title
    private Locale loc;
    //private Button test1 = null;
    private Drawable d;
    private ActionMode mActionMode = null;
    private SharedPreferences sharedPref;

    // Using JNI for testing with NDK
    static {
        System.loadLibrary("hfcmlib");
    }
    public native String yT();
    public native String nS();

    // App settings variables from preferences dialog
    private boolean newestFirst = true;         // sort order for list of space items
    private boolean needWifi = false;           // hires loading - only with wifi?

    // We go for our CONSTANTS here, this is similar to #define in C for a constant
    //public static String TAG = MainActivity.class.getSimpleName();
    private static final int HIRES_LOAD_REQUEST = 1;
    private static final int GL_MAX_TEX_SIZE_QUERY = 2;
    private static final int SETTINGS_REQUEST = 3;
    private static final String DROPBOX_JSON = "https://dl.dropboxusercontent.com/s/j77ttfcjn4zonpi/nasatest.json";
    //private static final int KIB = 1024;
    //private static final int MIB = 1024 * KIB;
    //private static final int GIB = 1024 * MIB;
    // strings for media type classification
    private static final String M_IMAGE = "image";
    private static final String M_YOUTUBE = "youtube";
    private static final String M_VIMEO = "vimeo";
    private static final String M_VIDEO_UNKNOWN = "unknown-video";

    // dealing with the number of displayed lines in the Explanation text view
    private static final int MAX_ELLIPSED_LINES = 2;
    private static final int MAX_LINES = 1000;      // hmm, ridiculous, but safe // TODO think


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

        // READ SETTINGS FROM DEFAULT SHARED PREFERENCES
        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        //String order = sharedPref.getString("item_order", "newest_first");
        //newestFirst = order.equals("newest_first");
        newestFirst = sharedPref.getString("item_order", "newest_first").equals("newest_first");

        // Drawable for textview - use builtin in "android.R...." - now use SVG graphic via xml
        // https://stackoverflow.com/questions/29041027/android-getresources-getdrawable-deprecated-api-22
        //d = ContextCompat.getDrawable(this, android.R.drawable.arrow_down_float);
        d = ContextCompat.getDrawable(this, R.drawable.hfcm);

        // For later NDK Code -- hmm, need a static block ??
        //System.loadLibrary("native-lib");
        String test1 = yT();
        String test2 = nS();

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

        // TODO: move this to "first time installation", and keep values in shared preferences
//        Intent texSizeIntent = new Intent(this, TexSizeActivity.class);
//        startActivityForResult(texSizeIntent, GL_MAX_TEX_SIZE_QUERY);

        // get some helper stuff from helpers package - just need to pass our context
        // TODO helpers might fail after rotating the phone - context lost??
        h = new helpers(getApplicationContext());

        // ------ REMINDER ONLY for docu
        // Just ugly code to get some test data to the real phone
        // 1. run python -m SimpleHTTPServer on host in test directory
        // 2. Temporarily sudo systemctl stop firewalld.service
        // -----------------------------------------------------------

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

        // Handling multiple choices - add another listener. Note, that this also repsonds to
        // long clicks without using the longclick listener explicitly...
        // https://www.youtube.com/watch?v=kyErynku-BM  (Prabeesh R K)
        myItemsLV.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        //myItemsLV.setSelector(android.R.color.darker_gray);
        myItemsLV.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

            // we keep an arraylist of selected position values
            private ArrayList<Integer> selected;

            @Override
            public void onItemCheckedStateChanged(android.view.ActionMode actionMode, int position, long id, boolean checked) {
                if (checked) {
                    selected.add(position);
                } else {
                    // hey, either object or postion as parameter? here, both are int...
                    // https://stackoverflow.com/questions/4534146/properly-removing-an-integer-from-a-listinteger
                    selected.remove(Integer.valueOf(position));
                }
                myList.get(position).setSelected(checked);
                adp.notifyDataSetChanged();
                //myItemsLV.setBackgroundColor(Color.BLUE);
                /*String title = myList.get(position).getTitle();
                String toaster = String.format("State Change on %d, id %d, state %b: %s, now have %d selected elements", position, id, checked, title, selected.size());
                Toast.makeText(MainActivity.this, toaster, Toast.LENGTH_SHORT).show();*/

            }

            @Override
            public boolean onCreateActionMode(android.view.ActionMode actionMode, Menu menu) {
                actionMode.getMenuInflater().inflate(R.menu.menu_cab_main, menu);
                selected = new ArrayList<>();
                return true;
                //return false;
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
                        // Remove from back to front !!!
                        Collections.sort(selected, Collections.<Integer>reverseOrder());
                        for (int idx : selected) {
                            Log.i("HFCM", "Deleting now: %d" + idx);
                            adp.remove(myList.get(idx));
                            adp.notifyDataSetChanged();
                        }
                        actionMode.finish();
                        return true;
                    case R.id.cab_share:
                        new dialogDisplay(MainActivity.this, "(Multi) Sharing not yet possible", "Info");
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


        // Create a listener for handling clicks on the thumbnail image
        myThumbClickListener = new thumbClickListener();

        // Prepare an intent for starting the hires image load in ImageActivity
        hiresIntent = new Intent(this, ImageActivity.class);

        // Test button: not of any function except doing tests - remove in later code
        /*test1 = (Button) findViewById(R.id.bTest1);
        test1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //test1.setText(new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", loc).format(new Date()));
            }
        });*/


        // Check, if phone has been rotated and restore saved instance state
        if (savedInstanceState != null) {
            // the spaceItem arraylist and the json data string are restored
            // on Android 8.0 this fails with Transaction Too Large error, so we do not put this
            // list onto the saved instance state and instead recreate using addITems()
            //myList = savedInstanceState.getParcelableArrayList("myList");
            myList = new ArrayList<>();
            adp = new myAdapter(getApplicationContext(), R.layout.space_item, myList);
            myItemsLV.setAdapter(adp);
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
            // create the list object to store our space items
            myList = new ArrayList<>();     // <> is sufficient, no <spaceItem> needed
            // create the adapter and associate with the listview of space items
            adp = new myAdapter(getApplicationContext(), R.layout.space_item, myList);
            myItemsLV.setAdapter(adp);

            // If local history json file does not exist, this is a newly installed app and we
            // might get some basic json data from dropbox for testing.
            // TODO: or restore from backup
            jsonData = null;
            File jsonFile = new File(getApplicationContext().getFilesDir(), localJson);
            if (jsonFile.exists()) {
                jsonData = h.readf(localJson);
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
                // prefilled json file from a hardcoded dropbox link.

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
            new apodTask().execute(nS());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        // added 18.09.2017 TODO; important for docu, but code had to be removed
        // https://stackoverflow.com/questions/12503836/how-to-save-custom-arraylist-on-android-screen-rotate
//        outState.putParcelableArrayList("myList", myList);
        // 03.10.2017 - remove that, because it causes Transaction too large error. Why this only
        // happens on Android 8.0 (Markus Hilger) is not clear, because there seems to be a limit
        // on 1MB in general.
        // see also
        // https://stackoverflow.com/questions/33182309/passing-bitmap-to-another-activity-ends-in-runtimeexception

        // unfortunately, JSONarray does not implement Parcelable, so we cannot put it. But we
        // add the jsonData String, from which we then need to regenerate the JSONArray parent
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

    // Add the adapter by typing in the new class
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
        // TODO strange, but getView() gets called many more times than rows exist
        // document this behaviour because of bad layout..
        // TODO change getView() for ViewHolder!! - getView called often, findViewById is expensive
        //      and is called quite often in every getView() call - this is still BAAAAAAAD.... (:
        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            //return super.getView(position, convertView, parent)
            if (convertView == null) {
                convertView = inflater.inflate(R.layout.space_item, null);
            }
            //Log.i("HFCM", "Adapter called");

            if (iList.get(position).isSelected()) {
                convertView.setBackgroundColor(Color.LTGRAY);
            } else {
                convertView.setBackgroundColor(Color.TRANSPARENT);
            }
            // important: call findViewByID for convertView!!! - otherwise null pointer!
            ImageView ivThumb = convertView.findViewById(R.id.iv_thumb);
            ImageView ivYoutube = convertView.findViewById(R.id.iv_youtube);

            // getView() is called often during scroll, take care of the overhead
            // We use a listener created ONCE in the activity instead of creating a new one on
            // each call of getView(). The Tag is by the listener.
            //ivThumb.setOnClickListener(new thumbClickListener());   // BAD
            ivThumb.setOnClickListener(myThumbClickListener);         // BETTER
            ivThumb.setTag(position);

            TextView tvTitle = convertView.findViewById(R.id.tv_title);
            final TextView tvExplanation = convertView.findViewById(R.id.tv_explanation);
            TextView tvDate = convertView.findViewById(R.id.tv_date);
            final TextView tvCopyright = convertView.findViewById(R.id.tv_copyright);
            TextView tvLowSize = convertView.findViewById(R.id.tv_lowsize);
            TextView tvHiSize = convertView.findViewById(R.id.tv_hisize);
            ivThumb.setImageBitmap(iList.get(position).getBmpThumb());
            ivThumb.setVisibility(View.VISIBLE);
            // TODO ivYoutube - bad, better ivVideoTag, so that the marker is set dynamically
            if (iList.get(position).getMedia().equals("youtube")) {
                // https://www.youtube.com/yt/about/brand-resources/#logos-icons-colors
                //ivYoutube.setImageResource(R.drawable.youtube_social_icon_red); - done in xml
                ivYoutube.setVisibility(View.VISIBLE);
            } else {
                ivYoutube.setVisibility(View.INVISIBLE);
            }
            ProgressBar lbThumb = convertView.findViewById(R.id.pb_thumb_loading);
            //noinspection ResourceType
            lbThumb.setVisibility(iList.get(position).getThumbLoadingState());
            tvTitle.setText(iList.get(position).getTitle());
            Date iDate = new Date(iList.get(position).getDateTime());
            // TODO - make display format of date configurable in settings
            String formattedDate = new SimpleDateFormat("dd. MMM yyyy").format(iDate);
            tvDate.setText(formattedDate);
            tvCopyright.setText(iList.get(position).getCopyright());

            // just keep that for reference in documentation - about textwatchers...
            /*final int pos = position;
            tvExplanation.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {}
                @Override
                public void afterTextChanged(Editable editable) {}
            });*/

            tvExplanation.setText(iList.get(position).getExplanation());
            iList.get(position).setMaxLines(tvExplanation.getLineCount());
            tvExplanation.setTag(10000 + position);
            // order of maxlines / ellipse statements might be answer to
            // https://stackoverflow.com/questions/8087555/programmatically-create-textview-with-ellipsis
            // NO, it is not! OR: there has not yet been set any text before
            //tvExplanation.setMaxLines(MAX_ELLIPSED_LINES);
            tvExplanation.setEllipsize(TextUtils.TruncateAt.END);
            tvExplanation.setMaxLines(MAX_ELLIPSED_LINES);
            // and here, we have a friendly listener, which temporarily overwrites that stuff, when
            // we click on the text view content - We reuse the existing listener for the thumbs
            // and distinguish views by ID ranges
            tvExplanation.setOnClickListener(myThumbClickListener);
            tvExplanation.setCompoundDrawablesWithIntrinsicBounds(null, null, null, d);

            // TODO DOCU: setText and concat is bad! use resources and format string!!!
            // BAD: tvLowSize.setText("Lowres: " + iList.get(position).getLowSize());
            tvLowSize.setText(getString(R.string.lowres, iList.get(position).getLowSize()));
            tvHiSize.setText(getString(R.string.hires, iList.get(position).getHiSize()));
            //Log.i("HFCM","Title: " + iList.get(position).getTitle() + ">> " + position + " - " + iList.get(position).getBmpThumb());
            //Log.i("HFCM","Title: " + iList.get(position).getTitle() + ">> " + position + " - " + maxTextureSize + " lines");
            return convertView;
        }
    }

    // GET APOD JSON INFOS FROM NASA. THIS STARTS ANOTHER THREAD TO LOAD THE LOWRES IMAGE
    private class apodTask extends AsyncTask<String, String, String> {
        // this one get's added by alt + insert keys
        private String imgUrl;
        @Override
        protected String doInBackground(String... params) {
            Log.i("HFCM", "APOD Loader task started");
            apodItem = new spaceItem();
            HttpsURLConnection nasa_conn = null;
            BufferedReader reader = null;

            try {
                URL url = new URL(params[0]);
                nasa_conn = (HttpsURLConnection) url.openConnection();
                nasa_conn.connect();
                // We now read data from this connection into an input stream
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
                    Log.e("HFCM", "FILENOTFOUND - API RATE LIMIT EXCEEDED ??");
                    new dialogDisplay(MainActivity.this, getString(R.string.rate_limit_exceeded),
                            getString(R.string.no_apod));
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
                // For media_type 'video', a youtube embed link was returned (17.07.2017)
                // https://www.youtube.com/embed/9Vp2jUQ4rNM?rel=0
                // TIP: a thumbnail image for a given youtube ID can be retrieved by
                // https://img.youtube.com/vi/9Vp2jUQ4rNM/0.jpg
                // TODO - handle potential case, where no youtube link like that is returned
                if (resource_uri != null) {
                    if (sMediaType.equals("video")) {
                        String host = resource_uri.getHost();
                        List<String> path = resource_uri.getPathSegments();
                        apodItem.setHires(resource_uri.toString());
                        if (host.equals("www.youtube.com")) {
                            // TODO: now we just assume embed link - might not be true always ??
                            // hires: video embed URL, lowres: thumbnail from youtube (0.jpg)
                            // https://www.youtube.com/embed/" + path.get(1) + "?rel=0
                            sMediaType = M_YOUTUBE;
                            // thumbnail name: youtube video ID with prefix 'th_'
                            apodItem.setThumb("th_" + path.get(1) + ".jpg");
                            sHiresUrl = imgUrl;
                            imgUrl = "https://img.youtube.com/vi/" + path.get(1) + "/0.jpg";
                            apodItem.setLowres(imgUrl);
                        } else if (host.endsWith("vimeo.com")) { // vimeo.com vs. player.vimeo.com
                            sMediaType = M_VIMEO;
                            // analyze path and prepare logic for thumbnail/lowres
                            apodItem.setThumb("th_VIMEO_TODO.jpg");
                            sHiresUrl = imgUrl;     // see 11.09.2017 json example - has no hires
                            apodItem.setLowres("");
                        } else {
                            sMediaType = M_VIDEO_UNKNOWN;
                            // hmmm, many more possibilities might exist...
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
                h.writef(String.valueOf(epoch) + ".json", parent.toString(2));
            } catch (JSONException e) {
                e.printStackTrace();
            }

            new ImgLowresTask().execute(imgUrl);
        }
    }

    // TODO - about returning values from asynctask !!!
    // TODO - seems to work with execute().get()  but it most likely blocks!!! - search topic: wait for asynctask to complete
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

            // just in case - check for double title
            // TODO - this is just dirty code for comparison - improve for large lists
            // For example: go from last to first item or even only check last item!!!!!!!!!!!!!!!!!
            for(int i=0; i<myList.size();i++) {
                if(apodItem.getTitle().equals(myList.get(i).getTitle())) {
                    // TODO here's the point to check, if the thumbnail file exists, and if not
                    // it was lost. so load the image and create thumb again...
                    Toast.makeText(MainActivity.this, R.string.already_loaded,
                            Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
            // TODO 20.08.2017 - null bitmap was passed by doInBackground() - crashed here
            // have a prepared "not found image thumbnail" for these cases...
            // TODO - VIMEO Video!!!
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
                apodItem.setBmpThumb(null);
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
            h.writef(localJson, outString);

            // add our new entry to the spaceItem list and notify the Adapter
            // myList.add(apodItem);
            // REVERSE
            // myList.add(0, apodItem);
            if (newestFirst) {
                myList.add(0, apodItem);
            } else {
                myList.add(apodItem);
            }
            adp.notifyDataSetChanged();
            // Write a toast
            Toast.makeText(MainActivity.this, R.string.apod_load,
                    Toast.LENGTH_LONG).show();

            // scroll to the new space Item in the list, that has been loaded
            //myItemsLV.setSelection(adp.getCount() -1);
            // REVERSE
            //myItemsLV.setSelection(0);
            if (newestFirst) {
                myItemsLV.setSelection(0);
            } else {
                myItemsLV.setSelection(adp.getCount() -1);
            }
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

            // bad hack: index i+10000 is corresponding textview for thumbnail on index i
            if (idx >= 10000) {
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
                    v.setCompoundDrawablesWithIntrinsicBounds(null,null,null,d);
                }
                myItemsLV.setSelection(idx-10000);
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
                // We get the ID from thumb name
                playYouTube(thumb.replace("th_", "").replace(".jpg", ""));
            } else {
                // TODO this is video - media-string changed from "video" to "YouTube-embed"
                // or vimeo etc... and therefore this needs to be handled here...
                // https://developers.google.com/youtube/android/player/
                // https://stackoverflow.com/questions/21278633/play-youtube-videos-in-video-view-in-android
                new dialogDisplay(MainActivity.this,
                        getString(R.string.no_video_yet, media, hiresUrl),
                        getString(R.string.no_support_yet));
            }
        }
    }

    // Play a youtube video by id  - first quick shot for basic test
    private void playYouTube(String id) {
        // TODO - check other options - only first test with StandalonePlayer in lightbox mode
        // fullscreen: only in landscape, no rotate, mainactivity is recreated ... bad
        // lightbox_: better, but also recreates mainactivity
        // but that's my fault, mainactivity does not yet handle this... :(
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

    // add via alt-insert - For getting result from image Activity for hires size and for GL
    // maximum texture size query at first application start after installation
    // used with startActivityForResult()
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
                //String toaster = "<" + String.valueOf(listidx) + "> " + testsize;
                //Toast.makeText(MainActivity.this, toaster, Toast.LENGTH_LONG).show();
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
                        h.writef(localJson, outString);
                        break;
                    }
                }
                adp.notifyDataSetChanged();
            }
        } else if (requestCode == GL_MAX_TEX_SIZE_QUERY) {
            if (resultCode == RESULT_OK) {
                maxTextureSize = data.getIntExtra("maxTextureSize",0);
                SharedPreferences shPref = this.getPreferences(Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = shPref.edit();
                editor.putInt("maxtexsize", maxTextureSize);
                //editor.commit();  // apply is recommended by inspection instead
                editor.apply();
                //Log.i("HFCM", "New Install - MAX_TEXTURE_SIZE written to shared prefs: " + maxTextureSize);
            }
        } else if (requestCode == SETTINGS_REQUEST) {
            if (resultCode == RESULT_OK) {
                // dirty test - we should check if order has been changed before recreating the list
                SharedPreferences shPref = PreferenceManager.getDefaultSharedPreferences(this);
                String order = shPref.getString("item_order", "newest_first");
                String returnedorder = data.getStringExtra("order");    // TODO
                newestFirst = order.equals("newest_first");
                Log.i("HFCM", "Refreshing list");
                myList.clear();
                addItems();
                adp.notifyDataSetChanged();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

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
            // File Transfer: do not open socket in main thread!!
            FragmentManager fm = getSupportFragmentManager();
            fileTransferDialog dlg = new fileTransferDialog();
            dlg.show(fm, "XFERTAG");
            return true;
        }
        if (id == R.id.action_help) {
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
                    h.writef(localJson, parent.toString(2));
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                addItems();
                // note: we cannot run network operations directly in the checkMissingThumbs()
                // function, because code in onPostExecute() does belong to the main thread
                // for the same reason: do not block during checkMissingThumbs!!!
                // https://stackoverflow.com/questions/10686107/what-does-runs-on-ui-thread-for-onpostexecute-really-mean
                checkMissingThumbs();
                adp.notifyDataSetChanged();
            }
        }
    }

    // this function is used on multiple occasions... TODO check
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
        int i;
        for (int k = 0; k < parent.length(); k++) {
        // REVERSE
        //for (int i = parent.length()-1; i >=0; i--) {
            if (newestFirst) {
                i = parent.length() - 1 -k;
            } else {
                i = k;
            }
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
            Log.i("HFCM", "Add Items " + strTitle + " > " + strThumb);
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
                Bitmap thumb = BitmapFactory.decodeFile(thumbFile.getAbsolutePath());
                newitem.setBmpThumb(thumb);
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
        //spaceItem wkItem = null;
        ArrayList<Integer> missing = new ArrayList<Integer>();
        for(int i=0; i<myList.size(); i++) {
            //if (myList.get(i).getBmpThumb() == null) {
            // TODO recheck no lowres key handling - here checked to avoid crash 11.09.2017
            if (myList.get(i).getBmpThumb() == null && !myList.get(i).getLowres().equals("")) {
                // TODO: this is temporary only: vimeo videos do not have a thumb yet, so skip
                // unknown not yet checked...
                if(!myList.get(i).getMedia().equals(M_VIMEO)) {
                    missing.add(i);
                }
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

    // Load all missing image thumbnails from lowres URL information. A list of indices into the
    // list is passed and images are loaded one after the other.
    // https://stackoverflow.com/questions/6053602/what-arguments-are-passed-into-asynctaskarg1-arg2-arg3
    private class thumbLoaderTask extends AsyncTask<ArrayList<Integer>, Integer, Void> {
        private ArrayList<Integer> missing;
        @Override
        protected Void doInBackground(ArrayList<Integer>... params) {
            // url for image is found in params[0]...
            Bitmap bitmap = null;
            Bitmap thumbnail = null;
            File thumbFile = null;
            spaceItem wkItem;
            missing = params[0];

            for (int i=0; i < missing.size(); i++) {
                wkItem = myList.get(missing.get(i));
                String lowresurl = wkItem.getLowres();
                try {
                    URL imgurl = new URL(lowresurl);
                    try {
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
        // the view
         @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            myList.get(values[0]).setThumbLoadingState(View.INVISIBLE);
            adp.notifyDataSetChanged();
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
            //mode.getMenuInflater().inflate(R.menu.menu_cab_main, menu); // TODO own menu
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
}
