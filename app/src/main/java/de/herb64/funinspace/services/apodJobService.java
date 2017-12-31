package de.herb64.funinspace.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
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
    private static final int MIN_DELAY = 900000;
    private static final int MAX_DELAY = 1800000;

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

        Locale loc = Locale.getDefault();

        int count = jobParameters.getExtras().getInt("COUNT");
        String actNetType = utils.getActiveNetworkTypeName(getApplicationContext());
        utils.logAppend(getApplicationContext(),
                MainActivity.DEBUG_LOG,
                "onStartJob(): Job" + count + " - APOD - currently active network type: " + actNetType);

        String url = jobParameters.getExtras().getString("URL");
        //boolean tls = jobParameters.getExtras().getBoolean("PRE_LOLLOPOP_TLS");
        // java.lang.IllegalAccessError: Method 'void android.os.BaseBundle.putBoolean(java.lang.String, boolean)' is inaccessible to class 'de.herb64.funinspace.MainActivity'
        boolean tls = true;

        TimeZone tzNASA = TimeZone.getTimeZone("America/New_York");
        Calendar cNASA = Calendar.getInstance(tzNASA);
        String yyyymmddNASA = String.format(loc, "%04d-%02d-%02d",
                cNASA.get(Calendar.YEAR),
                cNASA.get(Calendar.MONTH) + 1,
                cNASA.get(Calendar.DAY_OF_MONTH));
        SimpleDateFormat dF = new SimpleDateFormat("yyyy-MM-dd", loc);
        long epoch = 0;
        try {
            dF.setCalendar((Calendar) cNASA.clone()); // clone to avoid timezone overwrite
            epoch = dF.parse(yyyymmddNASA).getTime();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        long conntime = utils.testSocketConnect(3000);
        if (conntime == 3000) {
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "onStartJob() - testSocketConnect TIMEOUT (" + conntime + "ms)");
        } else {
            utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                    "onStartJob() - testSocketConnect OK (" + conntime + "ms)");
        }

        apodJsonLoader loader = new apodJsonLoader(getApplicationContext(),
                url,
                String.format(loc,
                        MainActivity.APOD_SCHED_PREFIX + "%d.json", epoch),
                tls);
        Thread loaderthread = new Thread(loader);
        loaderthread.start();
        //new Thread(loader).start();

        // logfile for debugging
        utils.logAppend(getApplicationContext(),
                MainActivity.DEBUG_LOG,
                "onStartJob(): Job" + count + " - APOD - " +
                        String.format(loc,
                                MainActivity.APOD_SCHED_PREFIX + "%d.json", epoch));

        // TODO - seems we need custom big view to make it appear expanded...
        // TODO - notification ids... (998...)
        Notification notification  = new NotificationCompat.Builder(this)
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
        }

        // send a broadcast - not needed here ???
        /*Intent intent = new Intent();
        intent.putExtra("??", ??);
        intent.setAction(MainActivity.BCAST_APOD);
        sendBroadcast(intent);*/

        Log.i("HFCM", "Job ID " + jobParameters.getJobId() + " now finished");
        jobFinished(jobParameters, false);  // false: need no reschedule, work is done
        scheduleNext(count, url, epoch);
        return true;    // no more work to be done with this job
    }

    /**
     * This method is called if the system has determined that you must stop execution of your
     * job even before you've had a chance to call
     * @param jobParameters
     * @return
     */
    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.i("HFCM", "onStopJob received: APOD");
        return false;   // false: drop the job
    }

    /**
     * Reschedule again as onetime job
     * @param count count
     * @param url url
     * @param lastEpoch The last epoch of NASA, for which a new json has been loaded
     */
    private void scheduleNext(int count, String url, long lastEpoch) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, apodJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(MainActivity.JOB_ID_APOD, serviceComponent);
            int random = MIN_DELAY + new Random().nextInt((MAX_DELAY - MIN_DELAY));
            ArrayList<Long> data = utils.getNASAEpoch(lastEpoch);
            builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
            builder.setMinimumLatency(data.get(2) + random);
            builder.setOverrideDeadline(data.get(2) + random + 300000); // 5 min deadline
            builder.setPersisted(true);      // survive reboots

            // Extras to pass to the job - well, passing filename not good...
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", count+1);
            extras.putString("URL", url);
            builder.setExtras(extras);

            JobInfo jobInfo = builder.build();
            Log.i("HFCM", jobInfo.toString());

            String logentry = String.format(Locale.getDefault(),
                    "schedNext: %d, Time to next APOD: %.1f hours + random delay of " + random/1000 + " seconds",
                    count + 1, (float)data.get(2)/3600000f);
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
