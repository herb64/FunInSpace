package de.herb64.funinspace;

import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseIntArray;
import android.widget.Filter;

import java.lang.reflect.Array;
import java.util.ArrayList;

import de.herb64.funinspace.helpers.utils;
import de.herb64.funinspace.models.spaceItem;

/**
 * Created by herbert on 10/22/17.
 * The filter called by either text based search using "SearchView" within title or
 * title+explanation. This filter now also supports "special" filter string, which allows to filter
 * for other characteristics like media type...
 */

public class spaceItemFilter extends Filter {

    private ArrayList<spaceItem> filterList;
    private spaceAdapter adapter;
    protected SparseIntArray idxMap;  // TIP: SparseIntArray preferred for efficiency over HashMap
    protected static final String FILTER_PREFIX = "___FILTER___";

    /**
     * Constructor
     * @param filterList filter list
     * @param adapter the adapter, from which the filter is created
     */
    public spaceItemFilter(ArrayList<spaceItem> filterList, spaceAdapter adapter) {
        //Log.i("HFCM", "spaceItemFilter constructor");
        this.filterList = filterList;
        this.adapter = adapter;
        this.idxMap = new SparseIntArray();
        for (int i = 0; i< filterList.size(); i++) {
            idxMap.append(i, i);
        }
    }

    /**
     * Perform the filtering - always requires a string as input.
     * For default "SearchView", this string is just submitted as search term.
     * The new "FilterView" class submits a string with a defined filter prefix. This string
     * contains all filter terms to be applied in a well defined format.
     * @param filterTerm the filter term passed by calling adapter.getFilter().filter()
     * @return the results structure
     */
    @Override
    protected FilterResults performFiltering(CharSequence filterTerm) {
        //Log.i("HFCM", "peformFiltering called with charsequence filterterm '" + filterTerm + "'");
        FilterResults results = new FilterResults();
        ArrayList<spaceItem> filteredItems;
        if (filterTerm != null &&
                filterTerm.toString().replace(FILTER_PREFIX, "").length() > 0 &&
                !filterTerm.toString().equals(FILTER_PREFIX + "0:0:0:0:0")) {
            String filter = filterTerm.toString();
            if (filter.startsWith(FILTER_PREFIX)) {
                filteredItems = filterByConstraints(filter.replace(FILTER_PREFIX, ""));
            } else {
                filteredItems = filterBySearchTerm(filter);
            }
            results.count = filteredItems.size();
            results.values = filteredItems;
        } else {
            results.count = filterList.size();
            results.values = filterList;
        }
        return results;
    }

    /**
     * Set values in adapter and notify of changes
     * @param charSequence sequence
     * @param filterResults results
     */
    @Override
    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
        adapter.iList = (ArrayList<spaceItem>) filterResults.values;
        adapter.notifyDataSetChanged();
    }

    /**
     * Search for text - using the "Search" button and just passing some text to search for. The
     * search string is searched in title only, or in title and explanation, depending on the
     * user settings, which are available via the adapter (getFullSearch)
     * @param filterTerm String containing the search text
     * @return The matching space items in an array list
     */
    private ArrayList<spaceItem> filterBySearchTerm(String filterTerm) {
        filterTerm = filterTerm.toUpperCase();
        ArrayList<spaceItem> filteredItems = new ArrayList<>();
        idxMap.clear();
        int key = 0;
        boolean isCaseSensitive = adapter.isCaseSensitive();
        boolean isFullSearch = adapter.getFullSearch();
        for (int i = 0; i< filterList.size(); i++) {
            if (adapter.getFullSearch()) {
                if (filterList.get(i).getTitle().toUpperCase().contains(filterTerm) ||
                        filterList.get(i).getExplanation().toUpperCase().contains(filterTerm)) {
                    filteredItems.add(filterList.get(i));
                    idxMap.append(key++, i);
                }
            } else {
                if (filterList.get(i).getTitle().toUpperCase().contains(filterTerm)) {
                    filteredItems.add(filterList.get(i));
                    idxMap.append(key++, i);
                }
            }
            /*if (isCaseSensitive) {
                if (isFullSearch) {
                    if (filterList.get(i).getTitle().contains(filterTerm) ||
                            filterList.get(i).getExplanation().contains(filterTerm)) {
                        filteredItems.add(filterList.get(i));
                        idxMap.append(key++, i);
                    }
                } else {
                    if (filterList.get(i).getTitle().contains(filterTerm)) {
                        filteredItems.add(filterList.get(i));
                        idxMap.append(key++, i);
                    }
                }

            } else {
                if (isFullSearch) {
                    if (utils.stringContainsCaseInsensitive(filterList.get(i).getTitle(), filterTerm) ||
                            utils.stringContainsCaseInsensitive(filterList.get(i).getTitle(), filterTerm)) {
                        filteredItems.add(filterList.get(i));
                        idxMap.append(key++, i);
                    }
                } else {
                    if (utils.stringContainsCaseInsensitive(filterList.get(i).getTitle(), filterTerm)) {
                        filteredItems.add(filterList.get(i));
                        idxMap.append(key++, i);
                    }
                }
            }*/
        }
        return filteredItems;
    }

    /**
     * FILTER function for NON TEXT constraints (e.g. by media type, rating, wallpaper status ...)
     * Search for space items, that match constraints, which are passed in a special format string.
     * This one exists as alternative to the text search.
     * @param s the constraints string (rating:video:wp:width:height)
     * @return The matching space items in an array list
     */
    private ArrayList<spaceItem> filterByConstraints(String s) {
        ArrayList<spaceItem> filteredItems = new ArrayList<>();
        idxMap.clear();
        int key = 0;
        String[] constraints = s.split(":");
        int rating = Integer.parseInt(constraints[0]);
        int video = Integer.parseInt(constraints[1]);
        int wpflag = Integer.parseInt(constraints[2]);
        //Log.i("HFCM", "Filter by constraints, string is '" + s + "'");
        for (int i = 0; i< filterList.size(); i++) {
            if (filterList.get(i).getRating() >= rating &&
                    (((video & FilterView.FILTER_YOUTUBE) == FilterView.FILTER_YOUTUBE &&
                            filterList.get(i).getMedia().equals(MainActivity.M_YOUTUBE)) ||
                    ((video & FilterView.FILTER_VIMEO) == FilterView.FILTER_VIMEO &&
                            filterList.get(i).getMedia().equals(MainActivity.M_VIMEO)) ||
                    ((video & FilterView.FILTER_MP4) == FilterView.FILTER_MP4 &&
                            filterList.get(i).getMedia().equals(MainActivity.M_MP4)) ||
                    video == FilterView.FILTER_VIDEO_NONE) &&
                    filterList.get(i).getWpFlag() >= wpflag) {
                filteredItems.add(filterList.get(i));
                idxMap.append(key++, i);
            }
        }
        return filteredItems;
    }
}
