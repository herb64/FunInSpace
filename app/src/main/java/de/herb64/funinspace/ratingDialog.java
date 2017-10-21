package de.herb64.funinspace;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RatingBar;

import java.util.ArrayList;

/**
 * Created by herbert on 10/21/17.
 */

public class ratingDialog extends AppCompatDialogFragment {

    public interface RatingListener {
        void updateRating(int rating, ArrayList<Integer> indices);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //return super.onCreateDialog(savedInstanceState);
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.rating_dialog, null);
        final RatingBar rb = v.findViewById(R.id.rb_rating);
        final ArrayList<Integer> mIndices = getArguments().getIntegerArrayList("indices");

        // Create listener for button clicks
        // https://developer.android.com/reference/android/content/DialogInterface.OnClickListener.html
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (i == DialogInterface.BUTTON_POSITIVE) {
                    RatingListener act = (RatingListener) getActivity();
                    act.updateRating(Math.round(rb.getRating()), mIndices);
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle("Rating selection");
        builder.setPositiveButton("OK", listener);
        builder.setNegativeButton("CANCEL", listener);
        builder.setView(v);
        return builder.create();
    }
}
