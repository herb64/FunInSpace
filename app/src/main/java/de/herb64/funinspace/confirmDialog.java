package de.herb64.funinspace;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.widget.TextView;

/**
 * Created by herbert on 11/12/17.
 * This class is used to display confirmation dialogs to the user and react on the type of button
 * that has been clicked (POS, NEG, NEUTRAL)
 */

public class confirmDialog extends AppCompatDialogFragment {

    private String tag;
    private AlertDialog dialog;

    public interface ConfirmListener {
        void processConfirmation(int button, String tag, Object o);
    }

    /**
     * Override for show() funtion
     * @param manager fragment manager
     * @param tag TAG for identification
     */
    @Override
    public void show(FragmentManager manager, String tag) {
        super.show(manager, tag);
        this.tag = tag;
    }

    /**
     * Create dialog. This gets called after show() above
     * @param savedInstanceState saved instance state
     * @return dialog to be returned
     */
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //return super.onCreateDialog(savedInstanceState);
        final String title = getArguments().getString("TITLE");
        final String msg = getArguments().getString("MESSAGE");
        final String pos = getArguments().getString("POS");
        final String neg = getArguments().getString("NEG");
        final String neu = getArguments().getString("NEU");
        // TODO - this is just an int... what about other ... Parcelable
        final int idx = getArguments().getInt("IDX");
        final int iconid = getArguments().getInt("ICON_ID");
        final float textsize = getArguments().getFloat("MSGSIZE");

        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int clickedButton) {
                ConfirmListener act = (ConfirmListener) getActivity();
                act.processConfirmation(clickedButton, tag, idx);
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(msg);
        if (iconid != 0 && getResources().getResourceTypeName(iconid).equals("drawable")) {
            // this must be a R.drawable.id, otherwise crash!
            // android.content.res.Resources$NotFoundException:
            builder.setIcon(iconid);
        }
        if (pos != null) {
            builder.setPositiveButton(pos, listener);
        }
        if (neg != null) {
            builder.setNegativeButton(neg, listener);
        }
        if (neu != null) {
            builder.setNeutralButton(neu, listener);
        }

        dialog = builder.create();
        if (textsize != 0) {
            dialog.show();   // need to call this to be able to get the TextView as non-null pointer
            TextView tv = (TextView) dialog.findViewById(android.R.id.message);
            if (tv != null) {
                tv.setTextSize(textsize);
            }
        }
        return dialog;
    }
}
