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
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import de.herb64.funinspace.MainActivity;
import de.herb64.funinspace.R;
import de.herb64.funinspace.helpers.utils;
import de.herb64.funinspace.wallPaperActivator;

/**
 * Created by herbert on 11/26/17.
 * MP3 resources:
 * new_wallpaper_1.mp3 https://notificationsounds.com/standard-ringtones/oringz-w427-371
 * new_wallpaper_2.mp3 - gentle-alarm
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

public class shuffleJobService extends JobService {

    //private ComponentName serviceComponent; -- moved to schedulenext...

    /**
     *
     */
    @Override
    public void onCreate() {
        super.onCreate();
        /*if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            serviceComponent = new ComponentName(this, shuffleJobService.class);
        }*/
        Log.i("HFCM", "Shuffle service has been created");
    }

    /**
     *
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i("HFCM", "Shuffle service has been destroyed");
    }

    /**
     * This seems only to be called if starting the service via intent from activity
     * TODO why should we do that? Doing a start of 2 services might have crashed  my KlausS4 AVD - permanently running into trouble - see doc...
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
     * This job sets a random wallpaper from the available wallpaper images. The random filename is
     * determined from the shared preferences WP_SELECT_LIST string each time the job starts. This
     * string might be changed between runs by the user (adding wps, changing ratings ...)
     * @param jobParameters parameters
     * @return boolean
     */
    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        // This is executed on main thread, so put logic in extra thread, then call jobfinshed
        // https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        Locale loc;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = getResources().getConfiguration().locale;
        }
        int count = jobParameters.getExtras().getInt("COUNT");

        String wpfile = utils.getRandomWpFileName(this);
        Log.i("HFCM", "Job WPSHUFFLE started, new random file = '" + wpfile + "'");
        if (!wpfile.isEmpty()) {
            wallPaperActivator wpact = new wallPaperActivator(getApplicationContext(), wpfile);
            Thread activator = new Thread(wpact);
            activator.start();  // of course, we do not join() :)

            // Store filename of current wallpaper into shared preferences - it looks like we only
            // can access the defaultSharedPrefs from our service (de.herb...funinspace_preferences)
            // so we moved the current_wp shared pref to this from MainActivity.xml private file
            // TODO - if UI is open, how to trigger an immediate update of wp icon on thumbnails
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("CURRENT_WALLPAPER_FILE", wpfile);
            editor.apply();

            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    "Job" + count + " - SHUFFLE - " + wpfile);

            // TODO seems we need custom big view to make it appear expanded - for later...
            // TODO notification on Android O - some problem
            Notification notification  = new NotificationCompat.Builder(this)
                    //.setCategory(Notification.CATEGORY_MESSAGE)
                    .setContentTitle("Wallpaper shuffle")
                    .setContentText("New: '" + wpfile + "'")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setAutoCancel(true)
                    .setSound(Uri.parse("android.resource://"
                            + this.getPackageName() + "/" + R.raw.new_wallpaper_2))
                    //.setVibrate()
                    //.setVisibility(Notification.VISIBILITY_PUBLIC)
                    .build();
            NotificationManager notificationManager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(999, notification);
            }  else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "onStartJob() - SHUFFLE - notificationManager is null");
            }

            /* for docu only... do we need an icon mandatory?
            E/NotificationService: Not posting notification with icon==0: Notification(pri=0 contentView=null vibrate=null sound=null defaults=0x0 flags=0x10 color=0x00000000 category=msg vis=PUBLIC)
            E/NotificationService: WARNING: In a future release this will crash the app: de.herb64.funinspace
             */

            // send a broadcast to trigger UI update for thumbnail wallpaper icon
            Intent intent = new Intent();
            intent.putExtra("NEWWP", wpfile);
            intent.setAction(MainActivity.BCAST_SHUFFLE);
            sendBroadcast(intent);

        }
        Log.i("HFCM", "Job ID " + jobParameters.getJobId() + " now finished");
        jobFinished(jobParameters, false);  // false: need no reschedule, work is done
        scheduleNext(loc, count);
        return true;    // no more work to be done with this job
    }

    /**
     * This method is called if the system has determined that you must stop execution of your
     * job even before you've had a chance to call - for cleanups to be done here...
     * @param jobParameters job parameters
     * @return return: false = drop the job
     */
    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.i("HFCM", "onStopJob received: WPSHUFFLE");
        /*int count = jobParameters.getExtras().getInt("COUNT");
        Locale loc;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N){
            loc = getResources().getConfiguration().getLocales().get(0);
        } else{
            //noinspection deprecation
            loc = getResources().getConfiguration().locale;
        }
        scheduleNext(loc, count);*/
        return false;
    }


    /**
     * Reschedule the job again to run at full hour. We calculate a new delay from current time, so
     * that delays do not add up over time.
     * @param loc locale
     * @param count count
     */
    private void scheduleNext(Locale loc, int count) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            ComponentName serviceComponent = new ComponentName(this, shuffleJobService.class);
            JobInfo.Builder builder = new JobInfo.Builder(MainActivity.JOB_ID_SHUFFLE, serviceComponent);

            /*long minDelay = utils.getMsToNextFullHour(loc);
            // at 23:00, keep night calm and schedule for next day 06:00
            TimeZone tzSYS = TimeZone.getDefault();
            Calendar cSYS = Calendar.getInstance(tzSYS);
            String hh = String.format(Locale.getDefault(), "%02d",
                    cSYS.get(Calendar.HOUR_OF_DAY));
            Log.i("HFCM", "Having hour string of '" + hh + "'");
            if (hh.equals("23")) {
                minDelay += 3600000 * 6;
            }*/

            // Fixed and improved to use shared prefs "wp_shuffle_times"
            long minDelay = utils.getMsToNextShuffle(getApplicationContext());

            builder.setMinimumLatency(minDelay);
            builder.setOverrideDeadline(minDelay + 15000);  // TODO: why is deadline needed?
            builder.setPersisted(true);                     // See also manifest!!

            // Extras to pass to the job - well, passing filename not good...
            PersistableBundle extras = new PersistableBundle();
            extras.putInt("COUNT", count+1);
            builder.setExtras(extras);

            JobInfo jobInfo = builder.build();
            Log.i("HFCM", jobInfo.toString());

            // Just in preparation of scheduling apod retrieve job
            SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
            ArrayList<Long> data = utils.getNASAEpoch(sharedPref.getLong("LATEST_APOD_EPOCH", 0));
            String logentry = String.format(loc,
                    "schedNext: %d, Time to next Shuffle: %d seconds",
                    count + 1, minDelay/1000);
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    logentry);

            JobScheduler scheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler != null) {
                scheduler.schedule(builder.build());
            } else {
                utils.logAppend(getApplicationContext(), MainActivity.DEBUG_LOG,
                        "scheduleNext() - SHUFFLE - null scheduler object - scheduler not built");
            }
        }
    }
}
