package de.herb64.funinspace;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;

import de.herb64.funinspace.helpers.dialogDisplay;

/**
 * Created by herbert on 12.12.17.
 */

public class NetworkBcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case ConnectivityManager.CONNECTIVITY_ACTION:
                //new dialogDisplay(context,"TODO: handle the network broadcast which indicates some change in network connectivity...", "Network Broadcast received");
                break;
            case Intent.ACTION_AIRPLANE_MODE_CHANGED:
                new dialogDisplay(context, "Airplane mode change broadcast received");
                break;
        }
    }
}
