package de.herb64.funinspace;


import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatDialogFragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.File;
import java.io.FileFilter;


/**
 * Created by herbert on 9/18/17.
 */

public class fileTransferDialog extends AppCompatDialogFragment {

    private Dialog dlg;

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        //return super.onCreateDialog(savedInstanceState);

        // Create the view by inflating the layout
        View v = LayoutInflater.from(getActivity()).inflate(R.layout.test_dialog, null);
        TextView tv_details = v.findViewById(R.id.tv_details);
        tv_details.setText(R.string.filetransfer1);
        final EditText et_ip = v.findViewById(R.id.et_serverip);
        final EditText et_port = v.findViewById(R.id.et_port);
        TextView tv_ip = v.findViewById(R.id.tv_serverip);
        TextView tv_port = v.findViewById(R.id.tv_port);
        final TextView tv_log = v.findViewById(R.id.tv_log);
        tv_ip.setText("Server IP:");
        tv_port.setText("Port:");
        //tv_log.setText("log infos should go here... :)");
        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
        et_ip.setText(sharedPref.getString("serverip", "10.0.2.2"));
        //et_port.setText("9000");
        et_port.setText(String.valueOf(sharedPref.getInt("serverport", 9000)));
        // using buttons was not a good idea, go for the default with text and 3 types
        //Button b_transfer = v.findViewById(R.id.b_transfer);
        //Button b_cancel = v.findViewById(R.id.b_cancel);

        // needs final, because accessed from inner class
        //final String localJson = "nasatest.json";
        // some testing here for directory iteration
        String xferFiles = "";
        File appdir = getActivity().getFilesDir();
        // change to omit wp_xxx.jpg - the wallpaper files
        FileFilter myfilter = new FileFilter() {
            @Override
            public boolean accept(File file) {
                if (file.toString().endsWith(".json") ||
                        (file.toString().endsWith(".jpg") && !file.toString().contains("files/wp_"))
                        ) {
                    return true;
                }
                return false;
            }
        };

        final File[] files;
        files = appdir.listFiles(myfilter); // TODO: shouldn't we catch security exceptions here?
        /*try {
            files = appdir.listFiles(myfilter);
            // use foreach instead of for loop!!
            for (File f : files) {
                xferFiles += f.toString() + "\n";
            }
        } catch (SecurityException e) {
            e.printStackTrace();
        }*/

        for (File f : files) {
            xferFiles += f.toString() + "\n";
        }


        // Create listener for button clicks
        // https://developer.android.com/reference/android/content/DialogInterface.OnClickListener.html
        final DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                Log.i("HFCM", "Old code in onclick..." + i);
                /*if (i == DialogInterface.BUTTON_POSITIVE) {
                    // here we can start a thread, then wait until it finishes
                    fileSender xfer = new fileSender(getActivity(), "192.168.1.34", 9000, localJson);
                    Thread sender = new Thread(xfer);
                    //tv_log.setText("Starting to send: " + localJson);
                    //Thread sender = new Thread(new fileSender(getActivity(), "192.168.1.34", 9000, localJson));
                    // TODO: we should also make sure, that we have a timeout
                    sender.start();
                    try {
                        sender.join();
                        String result = xfer.getLogString();
                        Log.i("HFCM", "Got back: " + result);
                        //tv_log.setText(result);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        //tv_log.setText(e.toString());
                    }
                    //SystemClock.sleep(3000);
                }*/
            }

        };

        View.OnClickListener lst = new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                //int tag = (int) view.getTag();
                //Log.i("HFCM", "Clicked on " + tag);
                //if (tag == R.id.b_transfer) {
                //    Thread sender = new Thread(new fileSender(getActivity(), "192.168.1.34", 9000, localJson));
                //    sender.start();
                //}
            }
        };



        /*
        b_transfer.setOnClickListener((View.OnClickListener) listener);
        b_transfer.setTag(R.id.b_transfer,"xfer");
        b_transfer.setOnClickListener(lst);

        //b_cancel.setOnClickListener((View.OnClickListener) listener);
        b_cancel.setTag(R.id.b_cancel,"cancel");
        b_cancel.setOnClickListener(lst);
        */

        // Build the alert dialog: use android.support.v7.app!!, otherwise we cannot call setView()
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity())
                .setTitle("JSON Metadata File Transfer");
        builder.setPositiveButton("START TRANSFER", listener);
        builder.setNegativeButton("CLOSE", listener);
        /*builder.setMessage("Haha")
                .setPositiveButton("FIRE", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // FIRE ZE MISSILES!
                    }
                })
                .setNegativeButton("CANCEL", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // User cancelled the dialog
                    }
                });*/
        //if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        builder.setView(v);
        //}
        // Create the AlertDialog object and return it
        dlg = builder.create();

        // This allows to KEEP THE DIALOG OPEN when clicking on TRANSFER (POSITIVE BUTTON)
        // so we can update infos now. This is done by not calling dismiss()...
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button button = ((AlertDialog) dlg).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        // TODO Do something
                        Log.i("HFCM", "we are in onShowListener now !!!!!!!!!!!");
                        String srvip = et_ip.getText().toString();
                        int srvport = Integer.parseInt(et_port.getText().toString());
                        SharedPreferences sharedPref = getActivity().getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = sharedPref.edit();
                        editor.putString("serverip", srvip);
                        editor.putInt("serverport", srvport);
                        //editor.commit();  // apply is recommended by inspection
                        editor.apply();

                        // https://developer.android.com/guide/topics/ui/dialogs.html
                        // https://material.io/guidelines/components/dialogs.html
                        // Do not instanciate a Dialog directly, use AlertDialog, Datepickerdialog,
                        // timepickerdialog instead. Use a dialogfragment as recommended by google
                        // sending part is moved into the dialog itself
                        // Start a new thread for each file to transfer. We also could pass a list
                        // of files and run one single thread (with progress notification via
                        // asynctask...
                        for (File f : files) {
                            String path = f.toString();
                            //String name_via_string = path.substring(path.lastIndexOf("/")+1);
                            //String filename=f.toString().substring(f.toString().lastIndexOf("/")+1);
                            String name = f.getName();
                            // note, although we have the name available we transfer the path here,
                            // to show the effect with openFileInput() and paths... see fileSender code
                            fileSender xfer = new fileSender(getActivity(), srvip, srvport, path);
                            Thread sender = new Thread(xfer);
                            tv_log.setText(String.format("%s\n%s",
                                    tv_log.getText(),
                                    "Transferring: " + name));
                            //tv_log.setText("Transferring: " + name);
                            //Thread sender = new Thread(new fileSender(getActivity(), "192.168.1.34", 9000, localJson));
                            // TODO: we should also make sure, that we have a timeout
                            sender.start();
                            try {
                                sender.join();
                                String result = xfer.getLogString();
                                Log.i("HFCM", "Got back: " + result);
                                tv_log.setText(String.format("%s... %s", tv_log.getText(), result));
                                //((AlertDialog) dlg).setButton(AlertDialog.BUTTON_NEGATIVE, "DONE", listener);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                                tv_log.setText(String.format("%s...Failed\n%s", tv_log.getText(), e.toString()));
                            }
                        }
                        // We DO NOT DISMISS, so the dialog remains open
                        //dlg.dismiss();
                    }
                });
            }
        });
        return dlg;
    }


}
