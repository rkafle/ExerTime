package com.example.exertime;

/**
 * Created by snowk on 5/4/2018.
 * Code taken and modified from Google Developers
 */

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.ExponentialBackOff;

import com.google.api.services.calendar.CalendarScopes;
import com.google.api.client.util.DateTime;

import com.google.api.services.calendar.model.*;
import com.google.api.services.calendar.model.Calendar;

import android.Manifest;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends AppCompatActivity
        implements EasyPermissions.PermissionCallbacks {
    GoogleAccountCredential mCredential;
    private TextView mOutputText; //Text box for information from Google calendar
    private TextView mYourSchedule; //Text box that will say "Your Schedule"
    private TextView mExerciseText; //Thext box that will show your exercise schedule
    private Button mCallApiButton; //Generate schedule button
    ProgressDialog mProgress; //Process box pop up that comes while the app generates the schedule

    //constants for Google services
    static final int REQUEST_ACCOUNT_PICKER = 1000;
    static final int REQUEST_AUTHORIZATION = 1001;
    static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;

    private static final String BUTTON_TEXT = "Generate Schedule";
    private static final String PREF_ACCOUNT_NAME = "accountName";
    private static final String[] SCOPES = { CalendarScopes.CALENDAR_READONLY };

    ArrayList<OurEvent> busyEvents = new ArrayList<OurEvent>(); //Array list of when the person is busy
    private ArrayList<String> stringData = new ArrayList<String>(); //your exercise schedule as one string

    private static final String FILE_NAME_Sc = "schedule.txt"; //name of file that the schedule will be saved to
    String mainTheScText = " "; //Initializing the schedule text

    /**
     * Create the main activity.
     * @param savedInstanceState previously saved instance data.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Creating the layout constraints
        LinearLayout activityLayout = new LinearLayout(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);
        activityLayout.setLayoutParams(lp);
        activityLayout.setOrientation(LinearLayout.VERTICAL);
        activityLayout.setPadding(16, 16, 16, 16);

        ViewGroup.LayoutParams tlp = new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);

        //creating and formatting the Generate Schedule button
        mCallApiButton = new Button(this);
        mCallApiButton.setText(BUTTON_TEXT);
        mCallApiButton.setTextSize(17);
        mCallApiButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mCallApiButton.setEnabled(false);
                mOutputText.setText("");
                getResultsFromApi();
                mCallApiButton.setEnabled(true);
            }
        });
        activityLayout.addView(mCallApiButton);

        //creating and formatting the text box for information from Google calendar
        mOutputText = new TextView(this);
        mOutputText.setLayoutParams(tlp);
        mOutputText.setPadding(16, 16, 16, 25);
        mOutputText.setVerticalScrollBarEnabled(true);
        mOutputText.setMovementMethod(new ScrollingMovementMethod());
        mOutputText.setText(
                "Click the \'" + BUTTON_TEXT +"\' button to generate your schedule for today.");
        mOutputText.setTextSize(17);
        mOutputText.setLineSpacing(1, 2);
        activityLayout.addView(mOutputText);

        //creating and formatting the text box for "Your Schedule" text
        mYourSchedule = new TextView(this);
        mYourSchedule.setLayoutParams(tlp);
        mYourSchedule.setPadding(16, 30, 16, 16);
        mYourSchedule.setVerticalScrollBarEnabled(true);
        mYourSchedule.setMovementMethod(new ScrollingMovementMethod());
        mYourSchedule.setText(
                "Your exercise schedule: ");
        mYourSchedule.setTextSize(17);
        activityLayout.addView(mYourSchedule);

        //creating and formatting the text box for your schedule of exercises
        mExerciseText = new TextView(this);
        mExerciseText.setLayoutParams(tlp);
        mExerciseText.setPadding(16, 16, 16, 16);
        mExerciseText.setVerticalScrollBarEnabled(true);
        mExerciseText.setMovementMethod(new ScrollingMovementMethod());
        mExerciseText.setText(
                "");
        mExerciseText.setTextSize(17);
        mExerciseText.setLineSpacing(1,2);
        activityLayout.addView(mExerciseText);

        //creating
        mProgress = new ProgressDialog(this);
        mProgress.setMessage("Calling Google Calendar API ...");

        setContentView(activityLayout);

        // Initialize credentials and service object.
        mCredential = GoogleAccountCredential.usingOAuth2(
                getApplicationContext(), Arrays.asList(SCOPES))
                .setBackOff(new ExponentialBackOff());

        //returns the exercise schedule from file
        getListFromFile();
    }



    /**
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (! isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices();
        } else if (mCredential.getSelectedAccountName() == null) {
            chooseAccount();
        } else if (! isDeviceOnline()) {
            mOutputText.setText("No network connection available.");
        } else {
            new MakeRequestTask(mCredential).execute();
        }
    }

    /**
     * Attempts to set the account used with the API credentials. If an account
     * name was previously saved it will use that one; otherwise an account
     * picker dialog will be shown to the user. Note that the setting the
     * account to use with the credentials object requires the app to have the
     * GET_ACCOUNTS permission, which is requested here if it is not already
     * present. The AfterPermissionGranted annotation indicates that this
     * function will be rerun automatically whenever the GET_ACCOUNTS permission
     * is granted.
     */
    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private void chooseAccount() {
        if (EasyPermissions.hasPermissions(
                this, Manifest.permission.GET_ACCOUNTS)) {
            String accountName = getPreferences(Context.MODE_PRIVATE)
                    .getString(PREF_ACCOUNT_NAME, null);
            if (accountName != null) {
                mCredential.setSelectedAccountName(accountName);
                getResultsFromApi();
            } else {
                // Start a dialog from which the user can choose an account
                startActivityForResult(
                        mCredential.newChooseAccountIntent(),
                        REQUEST_ACCOUNT_PICKER);
            }
        } else {
            // Request the GET_ACCOUNTS permission via a user dialog
            EasyPermissions.requestPermissions(
                    this,
                    "This app needs to access your Google account (via Contacts).",
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS);
        }
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode code indicating the result of the incoming
     *     activity result.
     * @param data Intent (containing result data) returned by incoming
     *     activity result.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch(requestCode) {
            case REQUEST_GOOGLE_PLAY_SERVICES:
                if (resultCode != RESULT_OK) {
                    mOutputText.setText(
                            "This app requires Google Play Services. Please install " +
                                    "Google Play Services on your device and relaunch this app.");
                } else {
                    getResultsFromApi();
                }
                break;
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null &&
                        data.getExtras() != null) {
                    String accountName =
                            data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    if (accountName != null) {
                        SharedPreferences settings =
                                getPreferences(Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putString(PREF_ACCOUNT_NAME, accountName);
                        editor.apply();
                        mCredential.setSelectedAccountName(accountName);
                        getResultsFromApi();
                    }
                }
                break;
            case REQUEST_AUTHORIZATION:
                if (resultCode == RESULT_OK) {
                    getResultsFromApi();
                }
                break;
        }
    }

    /**
     * Respond to requests for permissions at runtime for API 23 and above.
     * @param requestCode The request code passed in
     *     requestPermissions(android.app.Activity, String, int, String[])
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *     which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(
                requestCode, permissions, grantResults, this);
    }

    /**
     * Callback for when a permission is granted using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsGranted(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Callback for when a permission is denied using the EasyPermissions
     * library.
     * @param requestCode The request code associated with the requested
     *         permission
     * @param list The requested permission list. Never null.
     */
    @Override
    public void onPermissionsDenied(int requestCode, List<String> list) {
        // Do nothing.
    }

    /**
     * Checks whether the device currently has a network connection.
     * @return true if the device has a network connection, false otherwise.
     */
    private boolean isDeviceOnline() {
        ConnectivityManager connMgr =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        return (networkInfo != null && networkInfo.isConnected());
    }

    /**
     * Check that Google Play services APK is installed and up to date.
     * @return true if Google Play Services is available and up to
     *     date on this device; false otherwise.
     */
    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        return connectionStatusCode == ConnectionResult.SUCCESS;
    }

    /**
     * Attempt to resolve a missing, out-of-date, invalid or disabled Google
     * Play Services installation via a user dialog, if possible.
     */
    private void acquireGooglePlayServices() {
        GoogleApiAvailability apiAvailability =
                GoogleApiAvailability.getInstance();
        final int connectionStatusCode =
                apiAvailability.isGooglePlayServicesAvailable(this);
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode);
        }
    }


    /**
     * Display an error dialog showing that Google Play Services is missing
     * or out of date.
     * @param connectionStatusCode code describing the presence (or lack of)
     *     Google Play Services on this device.
     */
    void showGooglePlayServicesAvailabilityErrorDialog(
            final int connectionStatusCode) {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        Dialog dialog = apiAvailability.getErrorDialog(
                MainActivity.this,
                connectionStatusCode,
                REQUEST_GOOGLE_PLAY_SERVICES);
        dialog.show();
    }

    /**
     * An asynchronous task that handles the Google Calendar API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    //--Everything for functionality has to happen in this method
    private class MakeRequestTask extends AsyncTask<Void, Void, List<String>> {
        private com.google.api.services.calendar.Calendar mService = null;
        private Exception mLastError = null;

        MakeRequestTask(GoogleAccountCredential credential) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.calendar.Calendar.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName("ExerT!me")
                    .build();
        }

        /**
         * Background task to call Google Calendar API.
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<String> doInBackground(Void... params) {
            try {
                return getDataFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of events between now (when the button is pressed)
         * and midnight that day from the primary calendar.
         * @return List of Strings describing returned events (in 12 hour format)
         * @throws IOException
         */
        private List<String> getDataFromApi() throws IOException {
            // List all event from now to midnight the next day from primary calendar.
            DateTime now = new DateTime(System.currentTimeMillis());
            DateTime endofhrs = new DateTime((System.currentTimeMillis()/(1000*60*60*24) + 1)*(1000*60*60*24)+14400000);
            List<String> eventStrings = new ArrayList<String>();
            List<Integer> busyTimes = new ArrayList<Integer>();
            Events events = mService.events().list("primary")
                    .setTimeMax(endofhrs)
                    .setTimeMin(now)
                    .setOrderBy("startTime")
                    .setSingleEvents(true)
                    .execute();
            List<Event> items = events.getItems();

            for (Event event : items) {
                String start = event.getStart().getDateTime().toStringRfc3339();
                String end = event.getEnd().getDateTime().toStringRfc3339();

                String minstart = start.substring(14,16);
                String minend = end.substring(14,16);

                int hrStartInt = Integer.parseInt(start.substring(11,13));
                String hrstart = Integer.toString(hrStartInt%12);
                if(hrstart.equals("0")){
                    hrstart = "12";
                }

                int hrEndInt = Integer.parseInt(end.substring(11,13));
                String hrend = Integer.toString(hrEndInt%12);
                if(hrend.equals("0")){
                    hrend = "12";
                }

                String ampmStart = "";
                if (hrStartInt>=0 && hrStartInt<12)
                    ampmStart = "AM";
                else
                    ampmStart = "PM";

                String ampmEnd = "";
                if (hrEndInt>=0 && hrEndInt<12)
                    ampmEnd = "AM";
                else
                    ampmEnd = "PM";

                String startingtime = start.substring(11,16).replace(":","");
                String endingtime = end.substring(11,16).replace(":","");

                int startInt = Integer.parseInt(startingtime);
                int stopInt = Integer.parseInt(endingtime);

                if (start == null) {
                    // All-day events don't have start times, so just use
                    // the start date.
                    start = event.getStart().getDate().toStringRfc3339();
                }
                eventStrings.add(
                        String.format("%s: %s:%s %s - %s:%s %s", event.getSummary(), hrstart, minstart, ampmStart, hrend, minend, ampmEnd));
                busyTimes.add(startInt);
                busyTimes.add(stopInt);
            }
            makeBusyEvents(busyTimes);
            return eventStrings;
        }


        //Text shown while the app goes into the calender and generates the schedule
        @Override
        protected void onPreExecute() {
            mOutputText.setText("");
            mProgress.show();

            mYourSchedule.setText("");
        }

        //Text shown after generation
        @Override
        protected void onPostExecute(List<String> output) {
            mProgress.hide();
            if (output == null || output.size() == 0) {
                mOutputText.setText("You have no more events today.");
            } else {
                output.add(0, "The current events from your Google Calendar:");
                mOutputText.setText(TextUtils.join("\n", output));
            }

            mYourSchedule.setText("Your Exercise Schedule:");

            //makes and returns the exercises events
            makeListofEvents();

        }

        //Catching errors
        @Override
        protected void onCancelled() {
            mProgress.hide();
            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    showGooglePlayServicesAvailabilityErrorDialog(
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            MainActivity.REQUEST_AUTHORIZATION);
                } else {
                    mOutputText.setText("The following error occurred:\n"
                            + mLastError.getMessage());
                }
            } else {
                mOutputText.setText("Request cancelled.");
            }
        }
    }

    //makes the list of OurEvent objects of the user's scheduled events from the calendar.
    public void makeBusyEvents(List<Integer> eventInts){
        int start = 0000;
        int stop = 0000;
        ArrayList<OurEvent> tempList = new ArrayList<OurEvent>();

        for(int i=0; i<eventInts.size(); i=i+2){
            start = eventInts.get(i);
            stop = eventInts.get(i+1);
            OurEvent oe = new OurEvent(start, stop);
            tempList.add(oe);
        }

        for(int i=0; i<tempList.size(); i++){
            busyEvents.add(i, tempList.get(i));
        }

        Log.d("MainActiviy", "made busy events");

        //This is just to make sure it works - a tester
        for(int i=0; i<busyEvents.size(); i++){
            Log.i("BusyEvent", Integer.toString(busyEvents.get(i).getstarttime()));

        }
    }

    //returns list of OurEvent objects of the user's scheduled events from the calendar
    public List<OurEvent> getBusyEvents(){
        return busyEvents;
    }

    /*makes the exercise schedule from the user's google events and creates a string for the
    schedule with each time in 12-hour format
    */
    public void makeListofEvents(){

        Date g = java.util.Calendar.getInstance().getTime();
        //System.out.println("Current time => " + g);

        Day day = new Day();

        Date date = new Date();   // given date
        java.util.Calendar calendar = GregorianCalendar.getInstance(); // creates a new calendar instance
        calendar.setTime(date);   // assigns calendar to given date

        int x = calendar.get(java.util.Calendar.HOUR_OF_DAY); // gets hour in 24h format
        calendar.get(java.util.Calendar.HOUR);        // gets hour in 12h format
        String p = calendar.get(java.util.Calendar.DAY_OF_MONTH)+""+calendar.get(java.util.Calendar.MONTH)+""+calendar.get(java.util.Calendar.YEAR);
        int y = calendar.get(java.util.Calendar.MINUTE);
        int numberday = Integer.parseInt(p);

        InputStream is = (InputStream) getResources().openRawResource(R.raw.exerciselist);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        String line = "";
        ExerciseMasterList masterlists = new ExerciseMasterList();

        try {

            while ((line = reader.readLine()) != null) {
                //Split line by ","

                String[] fields = line.split(",");
                Exercise exercise = new Exercise(fields[0], fields[1], Integer.parseInt(fields[2]), Integer.parseInt(fields[3]), Integer.parseInt(fields[4]));
                masterlists.addexercise(exercise);
            }
        } catch (IOException e) {
            Log.e("MainActivity", "Error reading data from file on line " + line);
        }

        day = new Day(numberday, busyEvents, masterlists);
        day.makeExerciseList();
        String title;
        String time;
        String theSchedule = new String("");
        for (int z=0; z<day.getExerciseList().size(); z++){
            title = day.getExerciseList().get(z).getTitleofExercise();
            time = day.getExerciseList().get(z).getStartTimeString();
            stringData.add(time);
            stringData.add(title);
            theSchedule = theSchedule+time+" - "+title+" \n";
        }

        mExerciseText.setText(theSchedule);
        mainTheScText = theSchedule;

        save();


    }

    //saves the exercise schedule (as one string) to a file
    public void save(){
        FileOutputStream fileout = null;

        try {
            fileout = openFileOutput(FILE_NAME_Sc, MODE_PRIVATE);
            fileout.write(mainTheScText.getBytes());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fileout != null) {
                try {
                    fileout.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //returns the exercise schedule (as one string) from a file
    public void getListFromFile(){
        FileInputStream filein_1 = null;

        try {
            filein_1 = openFileInput(FILE_NAME_Sc);
            InputStreamReader reader = new InputStreamReader(filein_1);
            BufferedReader buffer = new BufferedReader(reader);
            StringBuilder sbuilder = new StringBuilder();
            String text;

            while ((text = buffer.readLine()) != null) {
                sbuilder.append(text).append("\n");
            }

            mExerciseText.setText(sbuilder.toString());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}