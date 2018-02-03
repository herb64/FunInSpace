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
    //private static final int MIN_DELAY = 900000;
    //private static final int MAX_DELAY = 1800000;
    public static final int JOB_ID_APOD = 3124;
    public static final long DEADLINE_DELAY = 300000;
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

        // TODO: new logic to verify, if correct apod is loaded + retries in case of failure
        // 1. check for existing valid scheduled apod file (s___<epoch>.json) for today. Valid
        //    means, that the apod contains a date field (yyyy-mm-dd) matching the correct epoch.
        // a) If it does not exist or is invalid: trigger a the apodJsonLoader and reschedule a new
        //    run in 2 minutes to verify existence after running apodJsonLoader.
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

        // First step in new logic: check for valid json - this is just the verification, after
        // which we reschedule for the next day.
        long epoch = utils.getNASAEpoch();

        // add check, if today's epoch already in json (is last entry in this case) - if so, just
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

        if (utils.haveValidScheduledJson(getApplicationContext(), epoch)) {
            // in this case, just schedule for the next day and keep the json file for processing
            // in getScheduledAPODs(), when user opens the app GUI
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    "onStartJob(" + count + ") - APOD - valid file " +
                            String.format(loc,
                                    APOD_SCHED_PREFIX + "%d.json", epoch));

            // TODO - seems we need custom big view to make it appear expanded...
            // TODO - notification ids... (998...)
            // TODO - launch app if clicking...

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
            builder.setContentTitle("New APOD")
                    .setContentText("New apod loaded")
                    .setSmallIcon(R.mipmap.ic_launcher)
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
            builder.setOngoing(true);
            builder.setSubText("TODO Herbert: put in more infos here into the notification");

            Notification notification = builder.build();
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(998, notification);
            } else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "onStartJob(" + count + ") - APOD - notificationManager is null");
            }

            // TODO - send broadcast to update UI, in case it is open at time of new apod
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

        long conntime = utils.testSocketConnect(SOCK_TIMEOUT);
        if (conntime == SOCK_TIMEOUT) {
            // reschedule by increasing timeout - TODO: handle too large increases
            long nxt = 30000 + count * 30000;
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "onStartJob(" + count + ") - testSocketConnect TIMEOUT (" + conntime + "ms), reschedule by " + nxt/1000 + " seconds...");
            // finish and schedule retry
            jobFinished(jobParameters, false);  // false: no reschedule, work is done
            scheduleNext(nxt, 10000, count, url);
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

        /*utils.logAppend(getApplicationContext(),
                MainActivity.DEBUG_LOG,
                "onStartJob(): Job" + count + " - APOD - " +
                        String.format(loc,
                                APOD_SCHED_PREFIX + "%d.json", epoch));*/

        /*Notification notification  = new NotificationCompat.Builder(this)
                //.setCategory(Notification.CATEGORY_MESSAGE)
                .setContentTitle("New APOD")
                .setContentText("New apod loaded")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .setSound(Uri.parse("android.resource://"
                        + this.getPackageName() + "/" + R.raw.newapod))
                //.setVibrate()
                //.setVisibility(Notification.VISIBILITY_PUBLIC)
                .build();
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(998, notification);
        } else {
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "onStartJob() - APOD - notificationManager is null");
        }*/

        // send a broadcast - not needed here - see shuffleJobService.java for code

        //Log.i("HFCM", "Job ID " + jobParameters.getJobId() + " now finished");
        jobFinished(jobParameters, false);  // false: need no reschedule, work is done
        long nxt = 30000 + count * 300000;
        scheduleNext(nxt, 10000, count, url);
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
        Log.i("HFCM", "onStopJob received: APOD");
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
            Log.i("HFCM", jobInfo.toString());

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
                        "scheduleNext() - APOD - null scheduler object - scheduler not built");
            }
        }
    }
}
