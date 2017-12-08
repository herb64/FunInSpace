package de.herb64.funinspace.services;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;

import de.herb64.funinspace.MainActivity;
import de.herb64.funinspace.helpers.utils;

/**
 * Created by herbert on 12/2/17.
 * Loading thumbnails in jobservice - app can be closed and thumbs will finish in background
 * !!! https://catinean.com/2014/10/19/smart-background-tasks-with-jobscheduler/
 */

public class thumbLoaderJobService extends JobService {

    @Override
    public boolean onStartJob(JobParameters jobParameters) {
        new thumbLoaderTask(this).execute(jobParameters);

        // NOTIFICATION ??

        //jobFinished(jobParameters, false);  // false: need no reschedule, work is done // true will kick off backoff logic...
        return true;    // no more work to be done with this job
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        Log.i("HFCM", "thumbLoaderJobService - onStopJob()");
        return false;
    }

    private class thumbLoaderTask extends AsyncTask<JobParameters, String, JobParameters> {
        // params, progress, result
        private final JobService jobService;

        public thumbLoaderTask(JobService jobService) {
            this.jobService = jobService;
        }

        @Override
        protected JobParameters doInBackground(JobParameters... params) {
            Bitmap bitmap = null;
            Bitmap thumbnail;
            Uri resource_uri = null;

            // Job parameters can only contain string array String[]...
            String[] urls = params[0].getExtras().getStringArray("URLS");
            /*ArrayList<String> urlarray= new ArrayList<>();
            if (urls != null) {
                utils.logAppend(getApplicationContext(),
                        MainActivity.DEBUG_LOG,
                        "Job - THUMB - Starting reload of " + urls.length + " Thumbnails");
                for (String url : urls) {   // TODO Collections addAll... DOCU
                    urlarray.add(url);
                }
            }*/

            for (String lowresurl : urls) {

                publishProgress(lowresurl);
                SystemClock.sleep(3000);

                /*resource_uri = Uri.parse(lowresurl);
                // TODO : BAD for example with Youtube...!!!
                String thumbname = ("th_" + resource_uri.getLastPathSegment());
                try {
                    URL imgurl = new URL(lowresurl);
                    bitmap = BitmapFactory.decodeStream((InputStream)imgurl.getContent());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (bitmap != null) {
                    // Create a thumbnail from the obtained bitmap and store as th_<file>.jpg
                    //thumbFile = new File(getApplicationContext().getFilesDir(), thumbname);
                    thumbnail = ThumbnailUtils.extractThumbnail(bitmap, 120, 120);
                    publishProgress(thumbname);
                    Log.i("HFCM", "Write thumbfile to " + thumbname);
                    utils.writeJPG(getApplicationContext(), thumbname, thumbnail);
                }*/
            }
            return params[0];
        }

        @Override
        protected void onProgressUpdate(String... values) {
            super.onProgressUpdate(values);
            utils.logAppend(getApplicationContext(),
                    MainActivity.DEBUG_LOG,
                    "Job - THUMB - loaded thumbnail: " + values[0]);
            Intent intent = new Intent();
            intent.putExtra("THUMBNAIL", values[0]);
            intent.setAction(MainActivity.BCAST_THUMB);
            sendBroadcast(intent);
        }

        @Override
        protected void onPostExecute(JobParameters jobParameters) {
            super.onPostExecute(jobParameters);
            jobFinished(jobParameters, true);  // ??? false
        }
    }
}
