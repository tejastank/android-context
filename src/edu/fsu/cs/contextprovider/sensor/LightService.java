package edu.fsu.cs.contextprovider.sensor;

import java.util.List;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class LightService extends Service implements SensorEventListener {

	private static final String TAG = "LightSensor Service";
	private static final boolean DEBUG = true;
	protected int contextId;
	protected boolean serviceEnabled = false;
	private SensorManager sm;
	private Sensor lightSensor;
	private static float lux=0;

	public void init() {
		if (!serviceEnabled) {

			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			// pluginId = prefs.getInt(LightEditActivity.KEY_PLUGIN_ID, -1);

			// make sure not to call it twice
			sm = (SensorManager) getSystemService(SENSOR_SERVICE);
			List<Sensor> sensors = sm.getSensorList(Sensor.TYPE_LIGHT);
			if (sensors != null && sensors.size() > 0) {
				lightSensor = sensors.get(0);
				sm.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_UI);
				serviceEnabled = true;
			} else {
				Toast.makeText(this, "Light sensor is not available on this device!", Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	public void onDestroy() {
		if (serviceEnabled) {
			sm.unregisterListener(this);
		}
		super.onDestroy();
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// we don't need this
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_LIGHT) {

			lux = event.values[0];

			if (DEBUG)
				Log.d(TAG, "send: " + lux);
			// Amarino.sendDataFromPlugin(this, pluginId, lux);

		}
	}
	
	public static boolean isInPocket() {
		if (lux < .5) {
			return true;
		}
		return false;
	}

	
	public String getTAG() {
		return TAG;
	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}

}
