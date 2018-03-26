package de.herb64.funinspace;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
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
import com.savvi.rangedatepicker.CalendarRowView;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import de.herb64.funinspace.helpers.dialogDisplay;
import de.herb64.funinspace.helpers.utils;

/**
 * Created by Herbert on 22.03.2018
 * Search dialog used to search APODs
 * 1. Determine a date range to use as query parameter for API - check for best options
 * TODO: what about using date range picker: https://github.com/savvisingh/DateRangePicker
 * 2. Enter search term to be searched for within the returned items
 * TODO: keep end date in shared prefs to use as new initial start date on next run
 */
public class ApodSearchActivity extends AppCompatActivity {
    private EditText searchentry;
    private Intent returnIntent;
    private CalendarPickerView calendarpicker;
    private CheckBox cbCaseSensitive;
    private CheckBox cbReloadDeleted;
    private SimpleDateFormat dF;
    private Date minDate;
    private Date maxDate;

    // constants
    private static final int maxDays = 30;          // maximum time span allowed for search in days

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apod_search);

        dF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

        TextView explainText = (TextView)findViewById(R.id.searchExplain);
        searchentry = (EditText)findViewById(R.id.searchstring);
        cbCaseSensitive = (CheckBox)findViewById(R.id.cb_archive_search_case_sensitive);
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

        // prepare return intent for first call....
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
        Calendar nextYear = Calendar.getInstance();
        nextYear.add(Calendar.DATE, 1);
        maxDate = nextYear.getTime();

        // minimum date: 20.06.1995 was first APOD release (NOT 16.06. as mentioned in some doc)
        try {
            minDate = dF.parse("1995-06-20");
        } catch (ParseException e) {
            e.printStackTrace();
            new dialogDisplay(ApodSearchActivity.this, "Could not create minDate", "Error");
            finish();
        }
        final Calendar lastYear = Calendar.getInstance();
        lastYear.add(Calendar.YEAR, -1);


        calendarpicker = (CalendarPickerView) findViewById(R.id.datepickerview);
        calendarpicker.setBackgroundColor(getResources().getColor(android.R.color.transparent));

        // prepare deactivated dates
        ArrayList<Integer> deactivated = new ArrayList<>();
        deactivated.add(1);
        //calendarpicker.deactivateDates(deactivated);

        // prepare highlighted dates
        ArrayList<Date> highlighted = new ArrayList<>();
        try {
            Date newdate = dF.parse("2018-02-22");
            Date newdate2 = dF.parse("2018-02-26");
            highlighted.add(newdate);
            highlighted.add(newdate2);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        calendarpicker.setCellClickInterceptor(myInterceptor);

        calendarpicker.init(minDate, maxDate, new SimpleDateFormat("MMMM, yyyy", Locale.getDefault()))
                .inMode(CalendarPickerView.SelectionMode.RANGE)
                .withSelectedDate(new Date());
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
                        "maximum of 90 days exceeded", "Warning for test");
                calendarpicker.selectDate(first);
            }
        }
    };

    /**
     * Just for testing the intercept function - we might exclude
     */
    CalendarPickerView.CellClickInterceptor myInterceptor = new CalendarPickerView.CellClickInterceptor() {
        @Override
        public boolean onCellClicked(Date date) {
            Log.i("HFCM", "clicked on " + dF.format(date));
            return false;
        }
    };

    /**
     * TODO: actually, this listener is not needed at all, if no special actions are done
     * Checkbox onClick listener. Definition is done via xml in this case: android:onClick
     * @param view the view of the checkbox to react on
     */
    public void onCbClicked(View view) {
        boolean checked = ((CheckBox) view).isChecked();
        switch (view.getId()) {
            case R.id.cb_archive_search_case_sensitive:
                returnIntent.putExtra("isCaseSensitive", checked);
                break;
            case R.id.cb_archive_search_reload_deleted:
                returnIntent.putExtra("reloadDeleted", checked);
                break;
        }
    }

}
