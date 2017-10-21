/**
 * Created by herbert on 10/18/17.
 * We use the ArrayAdapter source code as reference to learn on implementation. The original code
 * has been using an ArrayAdapter but the data structure needs to be traversed, so the Hashmap
 * was a better way to get access to elements by KEY = "title string". Unfortunately, there's no
 * readily available adapter for hashmap (especially LinkedHashMap to preserve order of elements)
 * The original Android source Code used as base:
 *
 * https://github.com/aosp-mirror/platform_frameworks_base/blob/master/core/java/android/widget/ArrayAdapter.java
 *
 * This is a very very sparse implementation based on above code for learning purpose. Only those
 * functions necessary for operation with funinspace have been implemented. It is not meant as a
 * general class. E.g. filtering code has been removed completely
 *
 * Well, to think about: finally, we end up in iterations as well, because also CAB handling
 * requires handling of index values, so we need to iterate again, now even for each selection
 * during contextual action mode... maybe better go for the additional title->index map creator
 * after loading new apod
 *
 * THIS CODE IS NOT CURRENTLY USED!!!
 */

package de.herb64.funinspace;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

// we make this "package private", it's only intended for use with FunInSpace
class HfcmMapAdapter<K, V> extends BaseAdapter {
    private LinkedHashMap<K, V> mHashMapData;
    private final Object mLock = new Object();
    private int mResource;
    //private int mDropDownResource;
    private int mFieldId = 0;
    //private boolean mNotifyOnChange = true;
    private Context mContext;
    private LayoutInflater mInflater;

    // The constructor
    HfcmMapAdapter(Context context, int resource, LinkedHashMap<K, V> mapData) {
        mContext = context;
        mResource = resource;
        mHashMapData = mapData;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public Context getContext() {
        return mContext;
    }

    @Override
    public int getCount() {
        return this.mHashMapData.entrySet().size();
    }

    // hmm using for loop with entry set is one option, but what about iterator?
    // http://tutorialswithexamples.com/how-to-iterate-through-map-or-hashmap-in-java/
    //
    // Using entrySet() and for loop
    // Using keyset() and for loop
    // Using Iterator interface
    //
    // but still: we have more iterations than with our list. So maybe we should keep the
    // ArrayList structure and keep a separate "title -> index" HashMap in our main activity, which
    // gets filled after loading the APOD.

    @Override
    public Entry<K, V> getItem(int position) {
        int i = 0;
        for (Entry<K, V> entry : mHashMapData.entrySet()) {
            if (i++ == position) return entry;
        }
        return null;
    }

    public long getItemId(int position) {
        Entry<K, V> item = getItem(position);
        return item == null ? 0 : item.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mResource);
    }

    private View createViewFromResource(int position, View convertView, ViewGroup parent,
                                        int resource) {
        View view;
        TextView text;

        if (convertView == null) {
            view = mInflater.inflate(resource, parent, false);
        } else {
            view = convertView;
        }

        try {
            if (mFieldId == 0) {
                //  If no custom field is assigned, assume the whole resource is a TextView
                text = (TextView) view;
            } else {
                //  Otherwise, find the TextView field within the layout
                text = (TextView) view.findViewById(mFieldId);
            }
        } catch (ClassCastException e) {
            Log.e("ArrayAdapter", "You must supply a resource ID for a TextView");
            throw new IllegalStateException(
                    "ArrayAdapter requires the resource ID to be a TextView", e);
        }

        Entry<K, V> item = getItem(position);
        if (item instanceof CharSequence) {
            text.setText((CharSequence) item.getValue());
        } else {
            text.setText(item.getValue().toString());
        }

        return view;
    }

    // TODO
    // I did not yet understand that stuff with mOriginalValues in googles original code. So
    // changing this to work with my data structure
    public V remove(K key) {
        V result;
        synchronized (mLock) {
            result = mHashMapData.remove(key);
        }
        //if (mNotifyOnChange) notifyDataSetChanged();
        return result;
    }
    /*public void setDropDownViewResource(int resource) {
        this.mDropDownResource = resource;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return createViewFromResource(position, convertView, parent, mDropDownResource);
    }*/
}
