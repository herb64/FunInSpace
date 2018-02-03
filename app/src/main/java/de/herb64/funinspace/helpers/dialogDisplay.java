package de.herb64.funinspace.helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.TextView;

import de.herb64.funinspace.MainActivity;

/**
 * Created by herbert on 9/13/17.
 */
// https://stackoverflow.com/questions/36947709/how-do-i-fix-or-correct-the-default-file-template-warning-in-intellij-idea

// need to add extends Activity, so that getApplicationContext() is available
// https://stackoverflow.com/questions/5796611/dialog-throwing-unable-to-add-window-token-null-is-not-for-an-application-wi

//builder.setMessage("Sorry, but this links to a '" + media +
//        "' , which cannot be shown (yet).\n(" + hiresUrl + ")");
// https://stackoverflow.com/questions/12627457/format-statement-in-a-string-resource-file
// formatted=false not needed in XML. Use format string as %[POSITION]$[TYPE]


public class dialogDisplay {
    /**
     * Standard dialog with default title, only content can be defined
     * @param ctx context
     * @param content string with content to be shown
     */
    public dialogDisplay(Context ctx, String content) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle("Infos for Herbert");
        builder.setMessage(content);
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // hmm, what could we do here??
                        dialogInterface.dismiss();
                    }
                });
        builder.create();
        builder.show();
    }

    /**
     * Enhanced dialog with title and content to be passed
     * @param ctx context
     * @param content string with content to be shown
     * @param title string with title to be shown
     */
    public dialogDisplay(Context ctx, String content, String title) {
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(title);
        builder.setMessage(content);
        builder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // hmm, what could we do here??
                        dialogInterface.dismiss();
                    }
                });
        builder.create();
        builder.show();
    }

    /**
     * Enhance dialog with title, content and size of content text. Useful for larger content
     * @param ctx context
     * @param content string with content to be shown
     * @param title string with title to be shown
     * @param size float value for size of content text
     */
    public dialogDisplay(Context ctx, String content, String title, float size) {

        AlertDialog dlg = new AlertDialog.Builder(ctx).setMessage(content).
                setTitle(title).
                setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                // hmm, what could we do here??
                                dialogInterface.dismiss();
                            }
                        }).
                show();
        TextView tv = dlg.findViewById(android.R.id.message);
        tv.setTextSize(size);
    }
}
