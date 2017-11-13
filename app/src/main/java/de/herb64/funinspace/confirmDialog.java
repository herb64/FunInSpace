package de.herb64.funinspace;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;

/**
 * Created by herbert on 11/12/17.
 * This is currently used for wallpaper changes.
 */

public class confirmDialog extends AppCompatDialogFragment {

    public interface ConfirmListener {
        void processConfirmation(int idx);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //return super.onCreateDialog(savedInstanceState);

        // Set values
        final String title = getArguments().getString("TITLE");
        final String msg = getArguments().getString("MESSAGE");
        final String pos = getArguments().getString("POS");
        final String neg = getArguments().getString("NEG");
        final int idx = getArguments().getInt("IDX");

        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                if (i == DialogInterface.BUTTON_POSITIVE) {
                    ConfirmListener act = (ConfirmListener) getActivity();
                    act.processConfirmation(idx);
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(pos, listener)
                .setNegativeButton(neg, listener);
        //builder.setMessage(msg);
        //builder.setPositiveButton(pos, listener);
        //builder.setNegativeButton(neg, listener);
        //builder.setView(v);
        return builder.create();
    }
}
