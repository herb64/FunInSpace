package de.herb64.funinspace;

import android.widget.Filter;
import java.util.ArrayList;
import de.herb64.funinspace.models.spaceItem;

/**
 * Created by herbert on 10/22/17.
 * This is used for filtering for our search implementation
 */

public class spaceItemFilter extends Filter {

    //Context ctx;
    private ArrayList<spaceItem> filterList;
    private spaceAdapter adapter;

    // Constructor
    public spaceItemFilter(ArrayList<spaceItem> filterList, spaceAdapter adapter) {
        this.filterList = filterList;
        this.adapter = adapter;
    }

    @Override
    protected FilterResults performFiltering(CharSequence filterTerm) {
        FilterResults results = new FilterResults();
        if (filterTerm != null && filterTerm.length() > 0) {
            filterTerm = filterTerm.toString().toUpperCase();
            ArrayList<spaceItem> filteredItems = new ArrayList<>();
            for (int i = 0; i< filterList.size(); i++) {
                if (filterList.get(i).getTitle().toUpperCase().contains(filterTerm)) {
                    filteredItems.add(filterList.get(i));
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
