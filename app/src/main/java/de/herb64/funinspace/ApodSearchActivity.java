package de.herb64.funinspace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.savvi.rangedatepicker.CalendarPickerView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.herb64.funinspace.helpers.dialogDisplay;

/**
 * Created by Herbert on 22.03.2018
 * TODO: https://apod.nasa.gov/apod/archivepix.html - all titles in one call - ?????? useful?
 * using date range picker: https://github.com/savvisingh/DateRangePicker
 * Search dialog used to search APODs
 * 1. Determine a date range to use as query parameter for API - check for best options
 * 2. Enter search term to be searched for within the returned items
 * 3. Select case sensitive search and if previously deleted items should be loaded again
 */
public class ApodSearchActivity extends AppCompatActivity {
    private EditText searchentry;
    private Intent returnIntent;
    private CalendarPickerView calendarpicker;
    private CheckBox cbCaseSensitive;
    private CheckBox cbFullSearch;
    private CheckBox cbReloadDeleted;
    private SimpleDateFormat dF;
    private Date minDate;

    // constants
    private static final String firstAPOD = "1995-06-20";   // first available APOD ever
    private static final int maxDays = 30;                  // maximum span in days allowed

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apod_search);
        dF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        TextView explainText = (TextView)findViewById(R.id.searchExplain);
        searchentry = (EditText)findViewById(R.id.searchstring);
        cbCaseSensitive = (CheckBox)findViewById(R.id.cb_archive_search_case_sensitive);
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        cbFullSearch = (CheckBox)findViewById(R.id.cb_archive_search_full);
        cbReloadDeleted = (CheckBox)findViewById(R.id.cb_archive_search_reload_deleted);
        Button doit = (Button)findViewById(R.id.do_search);
        Button cancel = (Button)findViewById(R.id.cancel_search);
        explainText.setText(String.format(Locale.getDefault(),
                getString(R.string.archive_search_explain), maxDays));
        // note: do not use inner class for "doit" button to avoid need for final declarations
        doit.setOnClickListener(searchButtonOnClickListener);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setResult(RESULT_CANCELED);
                finish();
            }
        });

        returnIntent = new Intent();
        setResult(RESULT_OK,returnIntent);

        // Maximum date: is today. for some reason, it does not work just using new Date(), although
        // value is ok. But during init, this fails:
        /* maxDate = new Date();
        Just using new Date() as maxDate does set correct date, but later in init it runs one minute back...
        Caused by: java.lang.IllegalArgumentException: SelectedDate must be between minDate and maxDate.
                                                                    minDate: Tue Jun 20 00:00:00 GMT+02:00 1995
                                                                    maxDate: Sat Mar 24 23:59:00 GMT+01:00 2018         <<<<<< BAD
                                                                    selectedDate: Sun Mar 25 19:18:44 GMT+02:00 2018
         */
        Calendar max = Calendar.getInstance();
        max.add(Calendar.DATE, 1);
        Date maxDate = max.getTime();

        try {
            minDate = dF.parse(firstAPOD);
        } catch (ParseException e) {
            e.printStackTrace();
            new dialogDisplay(ApodSearchActivity.this, "Could not create minDate", "Error");
            finish();
        }


        calendarpicker = (CalendarPickerView) findViewById(R.id.datepickerview);
        calendarpicker.setBackgroundColor(getResources().getColor(android.R.color.transparent));

        // prepare deactivated dates
        /*ArrayList<Integer> deactivated = new ArrayList<>();
        deactivated.add(1);
        calendarpicker.deactivateDates(deactivated);*/

        // prepare highlighted dates
        /*ArrayList<Date> highlighted = new ArrayList<>();
        try {
            Date newdate = dF.parse("2018-02-22");
            Date newdate2 = dF.parse("2018-02-26");
            highlighted.add(newdate);
            highlighted.add(newdate2);
        } catch (ParseException e) {
            e.printStackTrace();
        }*/

        //calendarpicker.setCellClickInterceptor(myInterceptor);

        // selected date taken from shared prefs NEXT_ARCHIVE_SEARCH_BEGIN_DATE, so that a new
        // search starts at end of last search.
        //SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String selected = sharedPref.getString("NEXT_ARCHIVE_SEARCH_BEGIN_DATE", "");
        Date selectedDate;
        if (selected.isEmpty()) {
            selectedDate = new Date();
        } else {
            try {
                selectedDate = dF.parse(selected);
            } catch (ParseException e) {
                selectedDate = new Date();
                e.printStackTrace();
            }
        }

        calendarpicker.init(minDate, maxDate, new SimpleDateFormat("MMMM, yyyy", Locale.getDefault()))
                .inMode(CalendarPickerView.SelectionMode.RANGE)
                .withSelectedDate(selectedDate);
                //.withSelectedDate(new Date());
                //.withDeactivateDates(deactivated)
                //.withHighlightedDates(highlighted);
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }

    /**
     * When search button is clicked, read the information about date, search string etc.. and pass
     * back via return intent.
     * We do not use an inner class to avoid the need to declare variables like beginDate as final.
     */
    View.OnClickListener searchButtonOnClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            List<Date> mylist = calendarpicker.getSelectedDates();
            Date first = mylist.get(0);
            Date last = first;
            if (mylist.size() > 1) {
                last = mylist.get(mylist.size()-1);
            }
            if (last.getTime() - first.getTime() <= 86400000L * maxDays) {
                returnIntent.putExtra("beginDate", dF.format(first));
                returnIntent.putExtra("endDate", dF.format(last));
                returnIntent.putExtra("isCaseSensitive", cbCaseSensitive.isChecked());
                returnIntent.putExtra("isFullSearch", cbFullSearch.isChecked());
                returnIntent.putExtra("reloadDeleted", cbReloadDeleted.isChecked());
                if (cbCaseSensitive.isChecked()) {
                    returnIntent.putExtra("search", searchentry.getText().toString());
                } else {
                    returnIntent.putExtra("search", searchentry.getText().toString().toLowerCase());
                }
                finish();
            } else {
                Log.w("HFCM", "overflow in difference");
                // android.view.WindowManager$BadTokenException: Unable to add window -- token null is not for an application
                new dialogDisplay(ApodSearchActivity.this,
                        String.format(getString(R.string.archive_search_exceeded), maxDays),
                        getString(R.string.archive_search_title));
                calendarpicker.selectDate(first);
            }
        }
    };

    /**
     * Just for testing the intercept function - we might exclude
     */
    /*CalendarPickerView.CellClickInterceptor myInterceptor = new CalendarPickerView.CellClickInterceptor() {
        @Override
        public boolean onCellClicked(Date date) {
            Log.i("HFCM", "clicked on " + dF.format(date));
            return false;
        }
    };*/

    /**
     * TODO: actually, this listener is not needed at all, if no special actions are done
     * Checkbox onClick listener. Definition is done via xml in this case: android:onClick
     * @param view the view of the checkbox to react on
     */
    /*public void onCbClicked(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        switch (view.getId()) {
            case R.id.cb_archive_search_case_sensitive:
                returnIntent.putExtra("isCaseSensitive", checked);
                break;
            case R.id.cb_archive_search_reload_deleted:
                returnIntent.putExtra("reloadDeleted", checked);
                break;
        }
    }*/

}
