package de.herb64.funinspace.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.annotation.IntDef;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeStandalonePlayer;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Locale;

import de.herb64.funinspace.MainActivity;
import de.herb64.funinspace.R;
import de.herb64.funinspace.helpers.dialogDisplay;
import de.herb64.funinspace.helpers.utils;
import de.herb64.funinspace.wallPaperActivator;

/**
 * Created by herbert on 11/26/17.
 */

public class apodJobService extends JobService {

    private ComponentName serviceComponent;

    /**
     *
     */
    @Override
    public void onCreate() {
        super.onCreate();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            serviceComponent = new ComponentName(this, apodJobService.class);
        }
        Log.i("HFCM", "Service has been created");
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("HFCM", "Service has been destroyed");
    }

    /**
     * @param intent intent
     * @param flags flags
     * @param startId start id
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("HFCM", "OnStartCommand: ID=" + startId);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * We read the next wp file from shared prefs instead of passed wallpaper filename instead of
     * passing a fixed filename into the job parameters!
     * @param jobParameters parameters
     * @return boolean
     */
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        // This is executed on main thread, so put logic in extra thread, then call jobfinshed
        // https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        // Service is destroyed on app close, but is recreated when job is scheduled
        String wpfile = utils.getRandomWpFileName(this);
        Log.i("HFCM", "Job has been started - wpfile = '" + wpfile + "'");

        Locale loc;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = getResources().getConfiguration().locale;
        }

        int count = jobParameters.getExtras().getInt("COUNT");
        Toast.makeText(getApplicationContext(), "Job (" + count + ") now started for WP = '" + wpfile + "'",
                Toast.LENGTH_LONG).show();

        if (!wpfile.isEmpty()) {
            wallPaperActivator wpact = new wallPaperActivator(getApplicationContext(), wpfile);
            Thread activator = new Thread(wpact);
            activator.start();  // of course, we do not join() :)

            // Store filename of current wallpaper into shared preferences - it looks like we only
            // can access the defaultSharedPrefs from our service - de.herb..funinspace_preferences
            // so we moved the current_wp shared pref to this from MainActivity.xml private file
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("CURRENT_WALLPAPER_FILE", wpfile);
            editor.apply();

            // logfile for debugging
            utils.logAppend(getApplicationContext(),
                    loc,
                    MainActivity.SHUFFLE_DEBUG_LOG,
                    "Job" + count + " - " + wpfile);

            // TODO: notify user if task is done - notification appears (white in upper left only)
            // seems we need custom big view to make it appear expanded...
            Notification notification  = new NotificationCompat.Builder(this)
                    //.setCategory(Notification.CATEGORY_MESSAGE)
                    .setContentTitle("Wallpaper shuffle")
                    .setContentText("Your wallpaper has been shuffled to '" + wpfile + "'")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .setSound(Uri.parse("android.resource://"
                            + this.getPackageName() + "/" + R.raw.nouveau_image))
                    //.setVibrate()
                    //.setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            notificationManager.notify(999, notification );

            /* for docu only... do we need an icon mandatory?
            11-26 14:32:09.180 1525-1525/system_process E/NotificationService: Not posting notification with icon==0: Notification(pri=0 contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=msg vis=PUBLIC)
            11-26 14:32:09.180 1525-1525/system_process E/NotificationService: WARNING: In a future release this will crash the app: de.herb64.funinspace
             */

            jobFinished(jobParameters, false);  // false: need no reschedule, work is done
            // Schedule next job in chain
            scheduleNext(loc, count);
        }
        return true;    // no more work to be done with this job
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.i("HFCM", "Job has been stopped");
        return false;   // false: drop the job
    }

    /**
     * Schedule again...
     */
    private void scheduleNext(Locale loc, int count) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            JobInfo.Builder builder = new JobInfo.Builder(MainActivity.JOB_ID_APOD, serviceComponent);
            // set jobinfo data here into builder
            builder.setMinimumLatency(1800000);
            builder.setOverrideDeadline(1920000);
            //builder.setPersisted(true);      // survive reboots

            // Extras to pass to the job - well, passing filename not good...
            PersistableBundle extras = new PersistableBundle();
            //String wpFile = "no-more-passed-filename";
            extras.putInt("COUNT", count+1);
            builder.setExtras(extras);

            JobInfo jobInfo = builder.build();
            Log.i("HFCM", jobInfo.toString());

            // Just in preparation of scheduling apod retrieve job
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            ArrayList<Long> data = utils.getNASAEpoch(loc,
                    sharedPref.getLong("LATEST_APOD_EPOCH", 0));
            String logentry = String.format(loc,
                    "schedNext: %d, Time to next APOD: %.1f hours",
                    count + 1, (float)data.get(2)/3600000f);
            utils.logAppend(getApplicationContext(),
                    loc,
                    MainActivity.SHUFFLE_DEBUG_LOG,
                    logentry);

            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            scheduler.schedule(builder.build());
        }
    }
}
