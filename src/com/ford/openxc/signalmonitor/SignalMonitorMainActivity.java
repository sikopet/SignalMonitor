package com.ford.openxc.signalmonitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;

import com.openxc.VehicleManager;
import com.openxc.measurements.EngineSpeed;
import com.openxc.measurements.Measurement;
import com.openxc.measurements.UnrecognizedMeasurementTypeException;
import com.openxc.measurements.VehicleSpeed;
import com.openxc.remote.VehicleServiceException;

/**
 *
 * @author mjohn706
 *
 */
public class SignalMonitorMainActivity extends Activity {

    private static final String TAG = "SignalMonitor";
    private static final String HTTP_POST_ENDPOINT = "http://your.hostname.here.com:5000/";
    
    // per the tutorial, at object creation time:
    private VehicleManager mVehicleManager;
    private VehicleSnapshotSink mSnapshotSink = new VehicleSnapshotSink();
    private HashMap<String, Trigger> mNamesToTriggers = new HashMap<String, Trigger>();

    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, VehicleManager.class);
        if(mVehicleManager == null) {
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //A block o' local variables
        // It is a small file, it should not take long, so we read it in here:
        // Read in list of signals to monitor and threshold conditions/values
        File sdcard = Environment.getExternalStorageDirectory();
        File watchersFile = new File(sdcard, "Watchers.txt");
        StringBuffer watchersStringBuff = new StringBuffer();
        JSONObject watchersObject = null;
        String signal_name;
        String threshold_type = "";
        String threshold_value;
        String team_id;

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signal_monitor_main);
        int lineNo = 0;
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(watchersFile));
            String line=null;
            Log.i(TAG, "br = " + br.toString());
            // originally buffered up line by line. But what I want is do do all
            // the processing based on line read in.
            while ((line = br.readLine()) != null) {
                if (line.length() == 0) { // works, but should not be needed
                    continue;
                }
                Log.i(TAG, "line length: " + line.length() + " line: " + line);
                watchersStringBuff.append(line); // may keep for debugging, but no longer used.
                try {
                    watchersObject = new JSONObject(line);
                    Log.i(TAG, "created JSONObject " + watchersObject);
                    // read the easy ones: threshold_type, team_id
                    threshold_type = watchersObject.get("threshold_type").toString();
                    Log.i(TAG,     "threshold_type: "    + threshold_type);
                    team_id =  watchersObject.get("team_id").toString();
                    Log.i(TAG, "team_id: " + team_id);

                } catch (JSONException e) {
                    Log.e(TAG, "Exception reading JSONobject itself from one line of Watchers.txt: " + e.getMessage());
                } finally {
                    Log.e(TAG, "Read JSON on one line");
                }
                try {
                    @SuppressWarnings("unchecked")
                    Iterator<String> ourNamesIt = watchersObject.keys();
                    String candidateKey;
                    while (ourNamesIt.hasNext()) {
                        candidateKey = ourNamesIt.next();
                        if (candidateKey.equals("team_id")    || candidateKey.equals("threshold_type")) {
                            ourNamesIt.remove();
                        }
                    }
                    // at this point, watchersObject has only one name
                    // left, the signal name.
                    String won = watchersObject.names().toString();
                    Log.i(TAG, "watchersObject signal name: " + won); // alas, Android's JSON incomplete, so:
                    String wsb = new String(won.substring(2, won.length() - 2));
                    Log.i(TAG, "watchersObject signal name as string: "    + wsb); // alas, Android's JSON incomplete, so:
                    signal_name = wsb;
                    // at this point we have read in the whole line and set
                    // only wsb=signal_name and threshold_type. But we can
                    // use the signal_name to get the value, so:
                    // and now the all important value:
                    threshold_value = watchersObject.getString(signal_name);

                    Trigger trigger = new Trigger(signal_name, threshold_value, threshold_type);
                    mNamesToTriggers.put(signal_name, trigger);
                } catch (IllegalStateException e1) {
                    Log.e(TAG, "IllegalStateException reading from newly created JSONObject" + e1.getMessage());
                } catch (UnsupportedOperationException boohoo) {
                    Log.e(TAG, "UnsupportedOperationException reading from newly created JSONObject" + boohoo.getMessage()); // we should never get here
                } catch (JSONException e) {
                    Log.e(TAG, e.getMessage());
                } finally {
                    Log.e(TAG, "Parsed JSON object for team_id, threshold_type and signal name");
                    //setListeners (threshold_type, signal_name);
                }
                Log.i(TAG, "lineNo = " + lineNo);
                lineNo++;
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception reading Watchers file" + e.getMessage());
        } finally {
            if(br != null) {
                try {
                    br.close();
                } catch(IOException ee) {
                    Log.e(TAG, "Exception closing buffered reader");
                } //TODO: obviate ts
            }
            Log.i(TAG, "Got it all");
        }
    }

    public void onPause() {
        super.onPause();
        Log.i(TAG, "Unbinding from vehicle service");
        if(mVehicleManager != null) {
            unbindService(mConnection);
        }
    }

    public void onResume() {
        super.onResume();
        if(mVehicleManager == null) {
            Intent intent = new Intent(this, VehicleManager.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.signal_monitor_main, menu);
        return true;
    }

    private void uploadSnapshot() {
        Intent intent = new Intent(this, PostalService.class);
        intent.putExtra(PostalService.INTENT_EXTRA_DATA_FLAG,
                mSnapshotSink.generateSnapshot());
        intent.putExtra(PostalService.INTENT_HTTP_ENDPOINT, HTTP_POST_ENDPOINT);
        Log.i(TAG, "triggering PostalService with: " + intent);
        startService(intent);
    }

    private VehicleSpeed.Listener mSpeedListener = new VehicleSpeed.Listener() {
        public void receive(Measurement measurement) {
            final VehicleSpeed speed = (VehicleSpeed) measurement;

            // "do stuff with the measurement"
            // what I do is test against a criterion, using my new Trigger class
            Trigger ourTrigger = mNamesToTriggers.get("vehicle_speed");
            if(ourTrigger != null) {
                Log.i(TAG, "Testing for speed " + ourTrigger.mTestCriterion + " speed");
                if (ourTrigger.test(speed.getValue().doubleValue())) {
                    Log.i(TAG, "vehicle speed test passed");
                    uploadSnapshot();
                }
            }
        }
    };

    private EngineSpeed.Listener mEngineSpeedListener = new EngineSpeed.Listener() {
        public void receive(Measurement measurement) {
            final EngineSpeed speed = (EngineSpeed) measurement;

            // "do stuff with the measurement"
            // what I do is test against a criterion, using my new Trigger class
            Trigger ourTrigger = mNamesToTriggers.get("engine_speed");
            if(ourTrigger != null) {
                Log.i(TAG, "Testing for engine speed " + ourTrigger.mTestCriterion + " speed");
                if (ourTrigger.test(speed.getValue().doubleValue())){
                    Log.i(TAG, "engine speed test passed");
                    uploadSnapshot();
                }
            }
        }
    };

    private ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.i(TAG, "Bound to VehicleManager");

            mVehicleManager = ((VehicleManager.VehicleBinder) service).getService();
            mVehicleManager.addSink(mSnapshotSink);

            // He forgot to say this 'try' would be necessary
            try {
                mVehicleManager.addListener(VehicleSpeed.class, mSpeedListener);
                mVehicleManager.addListener(EngineSpeed.class, mEngineSpeedListener);
            } catch (VehicleServiceException e) {
                Log.e(TAG, "Vehicle Service Exception " + e.toString());
            } catch (UnrecognizedMeasurementTypeException e) {
                Log.e(TAG, "Unrecognized Measurment type: " + e.toString());
            }
            // and now try using it, test Snapshot:
            //uploadSnapshot(); // for testing uploadSnapshot only. Now that triggering works, this is commented out.

        }

        // Called when the connection with the service disconnects unexpectedly
        public void onServiceDisconnected(ComponentName className) {
            Log.w(TAG, "VehicleService disconnected unexpectedly");
            mVehicleManager = null;
        }
    };
}
