package de.herb64.funinspace;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.widget.TextView;

/**
 * Created by herbert on 11/12/17.
 * This is currently used for wallpaper changes only - TODO rework for universal after crash
 * TODO - how about an icon, that can be passed?
 */

public class confirmDialog extends AppCompatDialogFragment {

    private String tag;
    private AlertDialog ddd;

    public interface ConfirmListener {
        void processConfirmation(int button, String tag, Object o);
        //void processNegConfirm(int idx);
    }

    /**
     * @param manager
     * @param tag
     */
    @Override
    public void show(FragmentManager manager, String tag) {
        super.show(manager, tag);
        this.tag = tag;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //return super.onCreateDialog(savedInstanceState);

        // Set values
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
            public void onClick(DialogInterface dialogInterface, int i) {
                ConfirmListener act = (ConfirmListener) getActivity();
                act.processConfirmation(i, tag, (Object)idx);
                /*if (i == DialogInterface.BUTTON_POSITIVE) {
                    ConfirmListener act = (ConfirmListener) getActivity();
                    act.processConfirmation(i, tag, (Object)idx);
                } else if (i == DialogInterface.BUTTON_NEGATIVE) {
                    ConfirmListener act = (ConfirmListener) getActivity();
                    act.processNegConfirm(idx);
                } else if (i == DialogInterface.BUTTON_NEUTRAL) {
                    ConfirmListener act = (ConfirmListener) getActivity();
                    act.processNegConfirm(idx);
                }*/
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(msg);
                //.setPositiveButton(pos, listener)
                //.setNegativeButton(neg, listener);
        if (iconid != 0 && getResources().getResourceTypeName(iconid).equals("drawable")) {
            // this must be a R.drawable.id otherwise crash!
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

        ddd = builder.create();
        if (textsize != 0) {
            ddd.show();         // need to call this to be able to get the TextView as non-null pointer
            TextView tv = (TextView) ddd.findViewById(android.R.id.message);
            if (tv != null) {
                tv.setTextSize(textsize);
            }
        }
        return ddd;

        //return builder.create();
    }
}
