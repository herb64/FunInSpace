package de.herb64.funinspace.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

import de.herb64.funinspace.MainActivity;
import de.herb64.funinspace.R;
import de.herb64.funinspace.apodJsonLoader;
import de.herb64.funinspace.helpers.utils;

/**
 * Created by herbert on 11/26/17.
 * If schedule is reached, this loads the new json metadata information from NASA into a file, that
 * can later be read on startup of the app. This makes sure, that we do not miss any APODs if the
 * app has not been started for some days.
 */

public class apodJobService extends JobService {

    // Randomize NASA Server access with scheduled operations to avoid "DDOS behaviour"
    public static final int APOD_NOTIFY_ID = 998;
    public static final int JOB_ID_APOD = 3124;
    private static final long DEADLINE = 10000;
    public static final long DEADLINE_DELAY = 300000;
    private static final long RESCHEDULE_BASE = 30000;
    private static final long SOCK_TIMEOUT_SCHEDULE_MS = 30000;
    private static final long SOCK_TIMEOUT_MAX_SCHED = 1200000;
    private static final long RECHECK_SCHEDULE_MS = 300000;
    private static final long RECHECK_MAX_SCHED = 3600000;
    public static final String APOD_SCHED_PREFIX = "s___";
    private static final int SOCK_TIMEOUT = 3000;

    /**
     *
     */
    @Override
    public void onCreate() {
        super.onCreate();
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            serviceComponent = new ComponentName(this, apodJobService.class);
        }*/
        Log.i("HFCM", "APOD service has been created");
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("HFCM", "APOD service has been destroyed");
    }

    /**
     * This seems only to be called if starting the service via intent from activity
     * TODO why should we do that? Doing a start of 2 services might have crashed  my KlausS4 AVD - permanently running into trouble - see doc...
     *      It might have been related to defect in ssd already, which died some days later completely...
     * @param intent intent
     * @param flags flags
     * @param startId start id
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("HFCM", "OnStartCommand: ID=" + startId);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Load a new APOD JSON information and store into a special file. No more activity at this
     * point. This allows to pickup the app on next start all files written since last start and
     * add these into the list of space items.
     * @param jobParameters parameters
     * @return boolean
     */
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        // This is executed on main thread, so put logic in extra thread, then call jobfinshed
        // https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        // Service is destroyed on app close, but is recreated when job is scheduled

        // TODO: docu logic to verify, if correct apod is loaded + retries in case of failure
        // 1. Check, if new apod already in local json structure - if so, no action required
        // 2. check for existing valid scheduled apod file (s___<epoch>.json) for today. Valid
        //    means, that the apod contains a date field (yyyy-mm-dd) matching the correct epoch.
        // a) If it does not exist or is invalid: trigger a the apodJsonLoader and reschedule a new
        //    run to verify existence after running apodJsonLoader.
        // b) If a valid one with current epoch in content exists: done. Schedule for next day
        //    This can be repeated with increasing intervals.
        // By this logic, the app does not give up after one single try. E.g. APOD might be delayed
        // or server errors might happen.

        // TODO: check, if the current days apod is already in nasatest.json - if so, we just
        //       reschedule without doing any load of apod image

        Locale loc = Locale.getDefault();
        int count = jobParameters.getExtras().getInt("COUNT");  // now used to count retries
        String url = jobParameters.getExtras().getString("URL");
        //boolean tls = jobParameters.getExtras().getBoolean("PRE_LOLLIPOP_TLS");
        // java.lang.IllegalAccessError: Method 'void android.os.BaseBundle.putBoolean(java.lang.String, boolean)' is inaccessible to class 'de.herb64.funinspace.MainActivity'
        boolean tls = true;

        long epoch = utils.getNASAEpoch();

        // Check, if today's epoch already in json (is last entry in this case) - if so, just
        // reschedule for next day
        if (utils.todaysImageAlreadyInList(getApplicationContext(), epoch)) {
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    String.format(loc,
                            "onStartJob(%d) - APOD - already in local json for epoch %d",
                            count, epoch));
            // here, we can close this now
            jobFinished(jobParameters, false);  // false: no reschedule, work is done
            scheduleNext(utils.getMsToNextApod(getApplicationContext()), DEADLINE_DELAY, 0, url);
            return true;
        }

        // This one might not be needed, because loading via range should be done on ui load - this
        // also makes android 4 load missed images without scheduler. Scheduler now only useful
        // to send a notification about new image availability
        //SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        //long lastUILaunchEpoch = sharedPref.getLong("LAST_UI_LAUNCH_EPOCH", 0);

        // Check for valid json - this is the verification, after which we reschedule for the next day.
        String sTitle = utils.haveValidScheduledJson(getApplicationContext(), epoch);

        if (sTitle != null) {
            // in this case, just schedule for the next day and keep the json file for processing
            // in getScheduledAPODs(), when user opens the app GUI
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    String.format(loc,"onStartJob(%d) - APOD - valid file %s%d.json - '%s'",
                            count, APOD_SCHED_PREFIX, epoch, sTitle));

            // TODO - seems we need custom big view to make it appear expanded...
            // TODO - notification ids...

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setContentTitle(getString(R.string.notify_apod_title))
                    .setContentText(getString(R.string.notify_apod_content))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.notify_apod_content)))
                    .setAutoCancel(true);
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            if (sharedPref.getBoolean("apod_bg_load_sound", false)) {
                builder.setSound(Uri.parse("android.resource://"
                        + this.getPackageName() + "/" + R.raw.newapod));
            }

            // New: clicking on notification now starts the App UI
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    notificationIntent, 0);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            builder.setContentIntent(pendingIntent);
            builder.setOngoing(false);  // if true, user cannot swipe out the notification (Heinz)
            builder.setSubText(String.format(loc, getString(R.string.notify_apod_subtext),sTitle));

            Notification notification = builder.build();
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(APOD_NOTIFY_ID, notification);
            } else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "onStartJob(" + count + ") - APOD - notificationManager is null");
            }

            // Send broadcast to update UI, in case it is open at time of new apod
            Intent intent = new Intent();
            //intent.putExtra("NEWWP", wpfile);
            intent.setAction(MainActivity.BCAST_APOD);
            sendBroadcast(intent);

            // we can close this now
            jobFinished(jobParameters, false);  // false: no reschedule, work is done
            scheduleNext(utils.getMsToNextApod(getApplicationContext()), DEADLINE_DELAY, 0, url);
            return true;
        }

        String actNetType = utils.getActiveNetworkTypeName(getApplicationContext());
        utils.logAppend(getApplicationContext(),
                MainActivity.DEBUG_LOG,
                "onStartJob(" + count + ") - APOD - currently active network type: " + actNetType);

        // if socket connection test fails, reschedule by increasing interval
        long conntime = utils.testSocketConnect(SOCK_TIMEOUT);
        if (conntime == SOCK_TIMEOUT) {
            // reschedule by increasing timeout - limit to 20 minutes
            long nxt = RESCHEDULE_BASE + count * SOCK_TIMEOUT_SCHEDULE_MS;
            if (nxt > SOCK_TIMEOUT_MAX_SCHED) {
                nxt = SOCK_TIMEOUT_MAX_SCHED;
            }
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "onStartJob(" + count + ") - testSocketConnect TIMEOUT (" + conntime + "ms), reschedule by " + nxt/1000 + " seconds...");
            // finish and schedule retry
            jobFinished(jobParameters, false);  // false: no reschedule, work is done
            scheduleNext(nxt, DEADLINE, count, url);
            return true;
        } else {
            count = 0;
        }
        utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                "onStartJob(" + count + ") - testSocketConnect OK (" + conntime + "ms), kickoff loader thread...");

        // Kick off the loader thread
        apodJsonLoader loader = new apodJsonLoader(getApplicationContext(),
                url,
                String.format(loc,
                        APOD_SCHED_PREFIX + "%d.json", epoch),
                tls);
        new Thread(loader).start();

        // reschedule for check in increasing intervals
        jobFinished(jobParameters, false);  // false: need no reschedule, work is done
        long nxt = RESCHEDULE_BASE + count * RECHECK_SCHEDULE_MS;
        if (nxt > RECHECK_MAX_SCHED) {
            nxt = RECHECK_MAX_SCHED;
        }
        scheduleNext(nxt, DEADLINE, count, url);
        return true;    // no more work to be done with this job
    }

    /**
     * This method is called if the system has determined that you must stop execution of your
     * job even before you've had a chance to call
     * @param jobParameters job parameters
     * @return false to drop the job
     */
    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        //Log.i("HFCM", "onStopJob received: APOD");
        return false;   // false: drop the job
    }

    /**
     * Reschedule again as onetime job. Important: in case of a bad url for example, the same bad
     * URL will be rescheduled over and over again, if it is taken from current job parameters.
     * TODO: document this, and in case of bad url, job needs to be recreated from scratch!!!
     * @param interval interval (including any random offset) in milliseconds
     * @param deadline deadline (in milliseconds)
     * @param count count
     * @param url url
     */
    private void scheduleNext(long interval, long deadline, int count, String url) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, apodJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_APOD, serviceComponent);
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            builder.setMinimumLatency(interval);
            builder.setOverrideDeadline(interval + deadline);
            builder.setPersisted(true);                             // survive reboots

            // Extras to pass to the job - well, passing filename not good...
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", count+1);
            extras.putString("URL", url);
            builder.setExtras(extras);

            JobInfo jobInfo = builder.build();
            //Log.i("HFCM", jobInfo.toString());

            String logentry = String.format(Locale.getDefault(),
                    "schedNext (%d), Time to next: %d(+%d) seconds (incl. random delay)",
                    count + 1, interval/1000, deadline/1000);
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    logentry);

            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.schedule(builder.build());
            } else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "scheduleNext() - APOD - null scheduler object");
            }
        }
    }
}
