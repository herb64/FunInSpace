package de.herb64.funinspace;

import android.preference.PreferenceManager;
import android.util.SparseIntArray;
import android.widget.Filter;
import java.util.ArrayList;

import de.herb64.funinspace.models.spaceItem;

/**
 * Created by herbert on 10/22/17.
 * This is used for filtering for our search implementation
 */

public class spaceItemFilter extends Filter {

    private ArrayList<spaceItem> filterList;
    private spaceAdapter adapter;
    protected SparseIntArray idxMap;     // TIP: SparseIntArray preferred for efficiency over HashMap

    // Constructor
    public spaceItemFilter(ArrayList<spaceItem> filterList, spaceAdapter adapter) {
        this.filterList = filterList;
        this.adapter = adapter;
        this.idxMap = new SparseIntArray();
        for (int i = 0; i< filterList.size(); i++) {
            idxMap.append(i, i);
        }
    }

    @Override
    protected FilterResults performFiltering(CharSequence filterTerm) {
        FilterResults results = new FilterResults();
        if (filterTerm != null && filterTerm.length() > 0) {
            filterTerm = filterTerm.toString().toUpperCase();
            ArrayList<spaceItem> filteredItems = new ArrayList<>();
            idxMap.clear();
            int key = 0;
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
            }
            results.count = filteredItems.size();
            results.values = filteredItems;
        } else {
            results.count = filterList.size();
            results.values = filterList;
        }
        return results;
    }

    @Override
    protected void publishResults(CharSequence charSequence, FilterResults filterResults) {
        adapter.iList = (ArrayList<spaceItem>) filterResults.values;
        adapter.notifyDataSetChanged();
    }
}
