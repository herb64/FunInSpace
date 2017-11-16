package de.herb64.funinspace;

import android.content.Context;
import android.graphics.Color;
import android.support.annotation.LayoutRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import de.herb64.funinspace.models.spaceItem;

/**
 * Created by herbert on 10/22/17.
 * See also https://www.youtube.com/watch?v=YnNpwk_Q9d0
 * Strange effect: theme seems to be changed, text is just white, stars just black...
 * Root cause: inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
 *                                         ---
 * need to use the activity instead of context, see
 * https://stackoverflow.com/questions/28817716/recyclerview-textview-color-of-text-changed
 *
 * This adapter works in combination with filtering for Search as of 22.10.2017.
 * TODO - contextual action mode selections within filtered view not possible. Not sure, if useful
 *      - how about filtering against rating value?
 * See https://www.youtube.com/watch?v=9OWmnYPX1uc
 * instead of SearchView.setOnQueryTextListener along with change listen, we can use
 * Searchable Configuration, as shown in above video. That's in XML and very powerful.
 */

public class spaceAdapter extends ArrayAdapter implements Filterable {
    // interesting, that "implements Filterable" does not pop up inspection for implementation needs
    protected ArrayList<spaceItem> iList;
    private ArrayList<spaceItem> filterList;
    //private int resource;
    private LayoutInflater inflater;
    private Context ctx;
    private MainActivity act;
    private spaceItemFilter filter;
    private SparseIntArray idxMap;      // mapping of filtered/unfiltered index values
    private boolean bFullSearch;
    private int wpActiveIdx;            // idx into spaceitem list for current wallpaper (-1=none)

    // Constructor (add via alt+insert) and adjust to our list of type spaceItem
    spaceAdapter(@NonNull Context context,
                 @NonNull MainActivity activity,
                 @LayoutRes int resource,
                 @NonNull ArrayList<spaceItem> objects) {
        super(context, resource, objects);
        this.iList = objects;
        this.filterList = objects;
        //this.resource = resource;
        this.ctx = context;
        this.act = activity;
        // note: use act.getSystemService(..)! If using context instead of the
        //       activity, the Textviews all appear white and rating stars are black, regardless
        //       of their selection state...
        inflater = (LayoutInflater) act.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        idxMap = new SparseIntArray();
        idxMap.clear();
        //wpActiveIdx = -1;
    }

    // adding getView() by alt-insert - override methods - the "NonNull" stuff seems to be new
    // Document: strange, but getView() gets called many more times than rows exist if layout
    //           is bad. I had this effect and did only notice this by chance, while the app still
    //           looked fine. Check getView() calls from time to time to see, if it's ok.
    // After filtering: getView gets called with position value out of bound, so crash at end of
    // listview...
    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        //return super.getView(position, convertView, parent)
        // Finally gone for ViewHolder after having this on my list since beginning :)
        // Never create a new view on each call!! see "the world of listview - google io 2010"
        // https://www.youtube.com/watch?v=wDBM6wVEO70&feature=youtu.be&t=7m

        // get corresponding ID as index into full array from position depending on search filter
        int id;
        if (idxMap.size() > 0) {
            id = idxMap.get(position);
        } else {
            id = position;
        }

        ViewHolder holder;
        // We should checkout RecyclerView as a more sophisticated replacement for ListView
        // https://stackoverflow.com/questions/21501316/what-is-the-benefit-of-viewholder
        // https://developer.android.com/training/improving-layouts/smooth-scrolling.html
        // RecyclerView.ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.space_item, parent, false); // do not use inflate(id, null)
            holder = new ViewHolder();
            holder.ivThumb = convertView.findViewById(R.id.iv_thumb);
            holder.ivYoutube = convertView.findViewById(R.id.iv_youtube);   // TODO: rename
            holder.ivWallpaper = convertView.findViewById(R.id.iv_wallpaper);
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

        // handle highlighting by contextual action mode TODO: do we really need to do it our own?
        if (iList.get(position).isSelected()) {
            convertView.setBackgroundColor(Color.LTGRAY);
        } else {
            convertView.setBackgroundColor(Color.TRANSPARENT);
        }
        // getView() is called often during scroll, take care about any overhead
        // We use a listener created ONCE in the activity instead of creating a new one on
        // each call of getView().
        //ivThumb.setOnClickListener(new thumbClickListener());             // BAD
        holder.ivThumb.setOnClickListener(act.myThumbClickListener);        // BETTER
        holder.ivThumb.setTag(id);                                          // important!
        holder.ivThumb.setImageBitmap(iList.get(position).getBmpThumb());
        holder.ivThumb.setVisibility(View.VISIBLE);

        // Update the small wallpaper indicator within the thumbnail to current status
        switch (iList.get(position).getWpFlag()) {
            case MainActivity.WP_NONE:
                holder.ivWallpaper.setVisibility(View.INVISIBLE);
                break;
            case MainActivity.WP_EXISTS:
                holder.ivWallpaper.setColorFilter(ContextCompat.getColor(ctx, android.R.color.darker_gray));
                //holder.ivWallpaper.setColorFilter(ctx.getResources().getColor(android.R.color.holo_blue_light));
                holder.ivWallpaper.setVisibility(View.VISIBLE);
                break;
            case MainActivity.WP_ACTIVE:
                holder.ivWallpaper.setColorFilter(ContextCompat.getColor(ctx, R.color.color_hfcm_yellow_bright));
                //holder.ivWallpaper.setColorFilter(ctx.getResources().getColor(android.R.color.holo_red_light));
                holder.ivWallpaper.setVisibility(View.VISIBLE);
                break;
            default:
                break;
        }

        //iv.setColorFilter(getResources().getColor(android.R.color.black), PorterDuff.Mode.SRC_IN);

        // setting onclicklistener prevents cab selection with multichoicemodelistener. No need
        // to set an onclicklistener for the views. This only is done for the thumbnail.
        // https://stackoverflow.com/questions/39057161/onclicklistener-in-getview-method-messes-up-multichoicemodelistener

        // Rating bar - unfortunately, the "small" versions do not support any interaction
        // https://developer.android.com/reference/android/widget/RatingBar.html
        // My initial idea was to make the stars below the thumbnail clickable directly within
        // the list, but these ratingbars even do not respond do a simple onClick()
        holder.rbRating.setRating(iList.get(position).getRating());
        //rbRating.setTag(2*MAX_ITEMS + position);
        //rbRating.setOnClickListener(myThumbClickListener);
        //rbRating.setOnRatingBarChangeListener(myRatingChangeListener);

        if (iList.get(position).getMedia().equals(MainActivity.M_YOUTUBE)) {
            // https://www.youtube.com/yt/about/brand-resources/#logos-icons-colors
            holder.ivYoutube.setImageResource(R.drawable.youtube_social_icon_red);
            holder.ivYoutube.setVisibility(View.VISIBLE);
        } else if(iList.get(position).getMedia().equals(MainActivity.M_VIMEO)) {
            holder.ivYoutube.setImageResource(R.drawable.vimeo_icon);
            holder.ivYoutube.setVisibility(View.VISIBLE);
        } else if(iList.get(position).getMedia().endsWith(MainActivity.M_MP4)) {
            // TODO : check license with flaticon com - how to add into credits
            holder.ivYoutube.setImageResource(R.drawable.movie_other_64);
            holder.ivYoutube.setVisibility(View.VISIBLE);
        } else {
            holder.ivYoutube.setVisibility(View.INVISIBLE);
        }
        //ProgressBar lbThumb = convertView.findViewById(R.id.pb_thumb_loading);
        //noinspection ResourceType
        holder.lbThumb.setVisibility(iList.get(position).getThumbLoadingState());
        holder.tvTitle.setText(iList.get(position).getTitle());
        /*Date iDate = new Date(iList.get(position).getDateTime());
        String formattedDate = new SimpleDateFormat("dd. MMM yyyy").format(iDate);*/

        // New: use formatter defined in activity which is based on NASA server time
        // Also we do not need to create a date object - pass the epoch value
        String formattedDate = act.formatter.format(iList.get(position).getDateTime());

        holder.tvDate.setText(formattedDate);
        holder.tvCopyright.setText(iList.get(position).getCopyright());

        holder.tvExplanation.setText(iList.get(position).getExplanation());
        iList.get(position).setMaxLines(holder.tvExplanation.getLineCount());
        if (idxMap.size() > 0) {
            //static member being accessed by instance reference
            //holder.tvExplanation.setTag(act.MAX_ITEMS + position);
            holder.tvExplanation.setTag(MainActivity.MAX_ITEMS + position);
        } else {
            holder.tvExplanation.setTag(MainActivity.MAX_ITEMS + id);
        }
        holder.tvExplanation.setEllipsize(TextUtils.TruncateAt.END);
        holder.tvExplanation.setMaxLines(MainActivity.MAX_ELLIPSED_LINES);
        // and here, we have a friendly listener, which temporarily overwrites that stuff, when
        // we click on the text view content - We reuse the existing listener for the thumbs
        // and distinguish views by ID ranges
        //holder.tvExplanation.setOnClickListener(act.myThumbClickListener);
        holder.tvExplanation.setCompoundDrawablesWithIntrinsicBounds(null, null, null, act.expl_points);

        // Note: setText and concat is bad, use resources and format string instead!
        // BAD: tvLowSize.setText("Lowres: " + iList.get(position).getLowSize());
        holder.tvLowSize.setText(ctx.getString(R.string.lowres, iList.get(position).getLowSize()));
        holder.tvHiSize.setText(ctx.getString(R.string.hires, iList.get(position).getHiSize()));
        return convertView;
    }

    @NonNull
    @Override
    public Filter getFilter() {
        // for some reason, inspection did not warn of this missing interface implementation
        if (filter == null) {
            filter = new spaceItemFilter(filterList, this);
            idxMap = filter.idxMap;
        }
        return filter;
    }

    // important: filtered list will run out of index - so overwrite getCount() as well...
    @Override
    public int getCount() {
        return iList.size();
        //return super.getCount();
    }

    // this might be helpful to get the "original" index from filtered view. That's our way
    // to work with the idxmap. But this is only useful for listeners, which get passed the ID,
    // which is not the case for our thumbclicklistener
    @Override
    public long getItemId(int position) {
        if (idxMap.size() > 0) {
            return idxMap.get(position);
        } else {
            return position;
        }
        //return super.getItemId(position);
    }

    // override remove(): we need to remove the object from the current filtered view as well, if
    // we are in a filtered view
    @Override
    public void remove(@Nullable Object object) {
        if (idxMap.size() > 0) {
            iList.remove(object);
        }
        super.remove(object);
    }

    /*@Nullable
    @Override
    public Object getItem(int position) {
        return super.getItem(position);
    }*/

    // some ugly hack to reset the index map, if no filtering is present. This is called
    // onQueryTextSubmit / onQueryTextChange, if search string is empty. The onClose() listener
    // for the searc view does not work.
    public void cleanMap() {
        idxMap.clear();
    }

    public void setFullSearch(boolean fullSearch) {
        bFullSearch = fullSearch;
    }

    public boolean getFullSearch() {
        return bFullSearch;
    }

    /*public int getWpActiveIdx() {
        return wpActiveIdx;
    }

    public void setWpActiveIdx(int wpActive) {
        this.wpActiveIdx = wpActive;
    }*/
}
