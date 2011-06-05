package edu.fsu.cs.contextprovider;

import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.prefs.Preferences;

import net.smart_entity.DateField;
import net.smart_entity.DoubleField;
import net.smart_entity.EntityManager;
import net.smart_entity.IntegerField;
import net.smart_entity.StringField;
import net.smart_entity.TextField;

import edu.fsu.cs.contextprovider.data.ContextConstants;
import edu.fsu.cs.contextprovider.data.DerivedEntity;
import edu.fsu.cs.contextprovider.data.LocationEntity;
import edu.fsu.cs.contextprovider.data.MovementEntity;
import edu.fsu.cs.contextprovider.data.SocialEntity;
import edu.fsu.cs.contextprovider.data.SystemEntity;
import edu.fsu.cs.contextprovider.data.WeatherEntity;
import edu.fsu.cs.contextprovider.monitor.DerivedMonitor;
import edu.fsu.cs.contextprovider.monitor.LocationMonitor;
import edu.fsu.cs.contextprovider.monitor.MovementMonitor;
import edu.fsu.cs.contextprovider.monitor.SocialMonitor;
import edu.fsu.cs.contextprovider.monitor.SystemMonitor;
import edu.fsu.cs.contextprovider.monitor.WeatherMonitor;
import edu.fsu.cs.contextprovider.sensor.AccelerometerService;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.location.Geocoder;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class ContextService extends Service {
	private static final String TAG = "ContextService";

	private static Timer timer = new Timer();
	private Context ctx;
	EntityManager entityManager;
	SharedPreferences prefs;
	
	// 5 min = 300 sec
	// 15 min = 900 sec
	//	private long POPUP_FREQ = 45;
	
	// location prefs
	private boolean locationEnabled;
	private boolean locationProximityEnabled;
	private int locationPollFreq;
	private int locationStoreFreq;
	// movement prefs
	private boolean movementEnabled;
	private int movementPollFreq;
	private int movementStoreFreq;
	// weather prefs
	private boolean weatherEnabled;
	private int weatherPollFreq;
	private int weatherStoreFreq;
	// social prefs
	private boolean socialEnabled;
	// system prefs
	private boolean systemEnabled;
	// derived prefs
	private boolean derivedEnabled;
	private int derivedCalcFreq;
	private int derivedStoreFreq;
	// general prefs
	private boolean startupEnabled;
	private boolean accuracyPopupEnabled;
	private boolean accuracyAudioEnabled;
	private int accuracyPopupPeriod;
	private int accuracyDismissDelay;
	// debug
	private boolean ttsEnabled;
	private boolean shakeEnabled;
	
	
	
	public IBinder onBind(Intent arg0) {
		return null;
	}

	public void onCreate() {
		super.onCreate();
		ctx = this;
		startService();
	}

	private void startService() {
		
		getPrefs();
		
		IntentFilter storeFilter = new IntentFilter();
		storeFilter.addAction(ContextConstants.CONTEXT_STORE_INTENT);
		registerReceiver(contextIntentReceiver, storeFilter);

		IntentFilter restartFilter = new IntentFilter();
		restartFilter.addAction(ContextConstants.CONTEXT_RESTART_INTENT);
		registerReceiver(restartIntentReceiver, restartFilter);

		Intent intent = null;
		
		if (locationEnabled) {
			/* Start GPS Service */
			intent = new Intent(this.getApplicationContext(), edu.fsu.cs.contextprovider.sensor.GPSService.class);
			startService(intent);
			/* Start Network Service */
			intent = new Intent(this.getApplicationContext(), edu.fsu.cs.contextprovider.sensor.NetworkService.class);
			startService(intent);
			/* Start LocationMonitor */
			Geocoder geocoder = new Geocoder(this, Locale.getDefault());
			LocationMonitor.StartThread(locationPollFreq, geocoder);
//			refreshLocation();
		}
		if (movementEnabled) {
			/* Start Accelerometer Service */
			intent = new Intent(this.getApplicationContext(), edu.fsu.cs.contextprovider.sensor.AccelerometerService.class);
			startService(intent);
			/* Start movement context */
			MovementMonitor.StartThread(movementPollFreq);
//			refreshMovement();
		}
		if (weatherEnabled) {
			/* Start weather monitor */
			WeatherMonitor.StartThread(weatherPollFreq);
//			refreshWeather();
		}
		if (systemEnabled) {
			/* Start Phone/SMS State Monitor Services */
			intent = new Intent(this.getApplicationContext(), edu.fsu.cs.contextprovider.sensor.TelephonyService.class);
			startService(intent);
//			refreshSystem();
		}
		if (socialEnabled) {
			/* Start social monitor */
			SocialMonitor.StartThread(weatherPollFreq);
//			refreshSocial();
		}
		if (derivedEnabled) {
			/* Start derived monitor */
			DerivedMonitor.StartThread(derivedCalcFreq);
//			refreshDerived();
		}
		
		timer.schedule(new ContextPopupTask(), (accuracyPopupPeriod * 1000)); // seconds*1000
	}
	
	
	
	
	
	
	
	private void getPrefs() {

//		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs = getSharedPreferences(ContextConstants.CONTEXT_PREFS, MODE_WORLD_READABLE);
		
		startupEnabled = prefs.getBoolean(ContextConstants.PREFS_STARTUP_ENABLED, true);
		accuracyPopupEnabled = prefs.getBoolean(ContextConstants.PREFS_ACCURACY_POPUP_ENABLED, true);
		accuracyAudioEnabled = prefs.getBoolean(ContextConstants.PREFS_ACCURACY_AUDIO_ENABLED, true);
		accuracyPopupPeriod = prefs.getInt(ContextConstants.PREFS_ACCURACY_POPUP_PERIOD, 45);
		accuracyDismissDelay = prefs.getInt(ContextConstants.PREFS_ACCURACY_POPUP_DISMISS_DELAY, 0);	
		
		locationEnabled = prefs.getBoolean(ContextConstants.PREFS_LOCATION_ENABLED, true);
		locationProximityEnabled = prefs.getBoolean(ContextConstants.PREFS_LOCATION_PROXIMITY_ENABLED, true);
		locationPollFreq = prefs.getInt(ContextConstants.PREFS_LOCATION_POLL_FREQ, 0);
		locationStoreFreq = prefs.getInt(ContextConstants.PREFS_LOCATION_STORE_FREQ, 0);
		
		movementEnabled = prefs.getBoolean(ContextConstants.PREFS_MOVEMENT_ENABLED, true);
		movementPollFreq = prefs.getInt(ContextConstants.PREFS_MOVEMENT_POLL_FREQ, 0);
		movementStoreFreq = prefs.getInt(ContextConstants.PREFS_MOVEMENT_STORE_FREQ, 0);
		
		weatherEnabled = prefs.getBoolean(ContextConstants.PREFS_WEATHER_ENABLED, true);
		weatherPollFreq = prefs.getInt(ContextConstants.PREFS_WEATHER_POLL_FREQ, 0);
		weatherStoreFreq = prefs.getInt(ContextConstants.PREFS_WEATHER_STORE_FREQ, 0);

		socialEnabled = prefs.getBoolean(ContextConstants.PREFS_SOCIAL_ENABLED, true);
		systemEnabled = prefs.getBoolean(ContextConstants.PREFS_SYSTEM_ENABLED, true);
		
		derivedEnabled = prefs.getBoolean(ContextConstants.PREFS_DERIVED_ENABLED, true);
		derivedCalcFreq = prefs.getInt(ContextConstants.PREFS_DERIVED_CALC_FREQ, 0);
		derivedStoreFreq = prefs.getInt(ContextConstants.PREFS_DERIVED_STORE_FREQ, 0);
		
		ttsEnabled = prefs.getBoolean(ContextConstants.PREFS_TTS_ENABLED, false);
		shakeEnabled = prefs.getBoolean(ContextConstants.PREFS_SHAKE_ENABLED, false);
	}

	
	private class ContextPopupTask extends TimerTask {
		public void run() {
			// Random myRandom = new Random();
			// long delay = 5000; // + myRandom.nextInt();
			toastHandler.sendEmptyMessage(0);
			// toastHandler.sendMessage((Message) String.valueOf(delay));
			timer.schedule(new ContextPopupTask(), (accuracyPopupPeriod * 1000)); // seconds*1000
		}
	}

	public void onDestroy() {
		super.onDestroy();
		Toast.makeText(this, "Service Stopped ...", Toast.LENGTH_SHORT).show();
		unregisterReceiver(contextIntentReceiver);
		unregisterReceiver(restartIntentReceiver);
	}

	private final Handler toastHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Toast.makeText(getApplicationContext(), "Context Accuracy Popup", Toast.LENGTH_SHORT).show();
			Intent intent = new Intent(ctx, edu.fsu.cs.contextprovider.ContextAccuracyActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		}
	};

	BroadcastReceiver contextIntentReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Received Intent: " + intent.getAction());
			Boolean placeAccurate, movementAccurate, activityAccurate, shelterAccurate, onPersonAccurate;

			placeAccurate = intent.getBooleanExtra(ContextConstants.PLACE_ACCURATE, true);
			movementAccurate = intent.getBooleanExtra(ContextConstants.MOVEMENT_ACCURATE, true);
			activityAccurate = intent.getBooleanExtra(ContextConstants.ACTIVITY_ACCURATE, true);
			shelterAccurate = intent.getBooleanExtra(ContextConstants.SHELTER_ACCURATE, true);
			onPersonAccurate = intent.getBooleanExtra(ContextConstants.ONPERSON_ACCURATE, true);

			Toast.makeText(
					getApplicationContext(),
					"ContextService Accuracy: \n" + "Place: " + placeAccurate + "\n" + "Movement: " + movementAccurate + "\n" + "Activity: "
							+ activityAccurate + "\n" + "Shelter: " + shelterAccurate + "\n" + "OnPerson: " + onPersonAccurate,
					Toast.LENGTH_LONG).show();

			try {
				StoreLocation(String.valueOf(placeAccurate));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				StoreMovement(String.valueOf(movementAccurate));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				StoreWeather(String.valueOf(activityAccurate));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				StoreSocial(String.valueOf(shelterAccurate));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				StoreSystem(String.valueOf(onPersonAccurate));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}
	};

	BroadcastReceiver restartIntentReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			Log.d(TAG, "Received Intent: " + intent.getAction());
			Toast.makeText(getApplicationContext(), "ContextService Restart", Toast.LENGTH_LONG).show();
		}
	};

	private void StoreAll() throws Exception {
		String accuracy = "NA";
		StoreLocation(accuracy);
		StoreMovement(accuracy);
		StoreWeather(accuracy);
		StoreSocial(accuracy);
		StoreSystem(accuracy);
		StoreDerived(accuracy);
	}

	private void StoreLocation(String accuracy) throws Exception {
		try {
			entityManager = EntityManager.GetManager(this);
			LocationEntity location = new LocationEntity();
			location.Timestamp.setValue(new Date());
			location.Address.setValue(LocationMonitor.getAddress());
			location.Neighborhood.setValue(LocationMonitor.getNeighborhood());
			location.Zip.setValue(LocationMonitor.getZip());
			location.Latitude.setValue(LocationMonitor.getLatitude());
			location.Longitude.setValue(LocationMonitor.getLongitude());
			location.Altitude.setValue(LocationMonitor.getAltitude());
			location.Accuracy.setValue(accuracy);
			int uid = entityManager.store(location);
			// LocationEntity fetchedLocation = (LocationEntity)
			// entityManager.fetchById(uid);
			// String address = fetchedLocation.Address.getValue();
		} catch (Exception e) {
			throw e;
		}
	}

	private void StoreMovement(String accuracy) throws Exception {
		try {
			entityManager = EntityManager.GetManager(this);
			MovementEntity movement = new MovementEntity();
			movement.Timestamp.setValue(new Date());
			movement.State.setValue(MovementMonitor.getMovementState());
			movement.Speed.setValue((double) MovementMonitor.getSpeedMph());
			movement.Bearing.setValue((double) LocationMonitor.getBearing());
			movement.Steps.setValue((int) AccelerometerService.getStepCount());
			movement.LastStep.setValue(AccelerometerService.getLastStepTimestamp());
			movement.Accuracy.setValue(accuracy);
			int uid = entityManager.store(movement);
		} catch (Exception e) {
			throw e;
		}
	}

	private void StoreWeather(String accuracy) throws Exception {
		try {
			entityManager = EntityManager.GetManager(this);
			WeatherEntity weather = new WeatherEntity();
			weather.Timestamp.setValue(new Date());
			weather.Condition.setValue(WeatherMonitor.getWeatherCond());
			weather.Temperature.setValue(WeatherMonitor.getWeatherTemp());
			weather.Humidity.setValue(WeatherMonitor.getWeatherHumid());
			weather.Wind.setValue(WeatherMonitor.getWeatherWindCond());
			weather.HazardLevel.setValue(WeatherMonitor.getWeatherHazard());
			weather.Accuracy.setValue(accuracy);
			int uid = entityManager.store(weather);
		} catch (Exception e) {
			throw e;
		}
	}

	private void StoreSocial(String accuracy) throws Exception {
		try {
			entityManager = EntityManager.GetManager(this);
			SocialEntity social = new SocialEntity();
			social.Timestamp.setValue(new Date());
			social.Contact.setValue(SocialMonitor.getContact());
			social.Communication.setValue(SocialMonitor.getCommunication());
			social.Message.setValue(SocialMonitor.getMessage());
			social.LastIncoming.setValue(SocialMonitor.getLastInDate());
			social.LastOutgoing.setValue(SocialMonitor.getLastOutDate());
			social.Accuracy.setValue(accuracy);
			int uid = entityManager.store(social);
		} catch (Exception e) {
			throw e;
		}
	}

	private void StoreSystem(String accuracy) throws Exception {
		try {
			entityManager = EntityManager.GetManager(this);
			SystemEntity system = new SystemEntity();
			system.Timestamp.setValue(new Date());
			system.State.setValue(SystemMonitor.getState());
			system.BatteryLevel.setValue(SystemMonitor.getBatteryLevel());
			system.Plugged.setValue(SystemMonitor.isBatteryPluggedString());
			system.LastPlugged.setValue(SystemMonitor.getBatteryLastPluggedDate());
			system.LastPresent.setValue(SystemMonitor.getUserLastPresentDate());
			system.SSID.setValue(SystemMonitor.getSSID());
			system.Signal.setValue(SystemMonitor.getSignal());
			system.Accuracy.setValue(accuracy);
			int uid = entityManager.store(system);
		} catch (Exception e) {
			throw e;
		}
	}

	private void StoreDerived(String accuracy) throws Exception {
		try {
			entityManager = EntityManager.GetManager(this);
			DerivedEntity derived = new DerivedEntity();
			derived.Timestamp.setValue(new Date());
			derived.Place.setValue(DerivedMonitor.getPlace());
			derived.Activity.setValue(DerivedMonitor.getActivity());
			derived.Shelter.setValue(DerivedMonitor.getShelterString());
			derived.Pocket.setValue(DerivedMonitor.getPocketString());
			derived.Mood.setValue(DerivedMonitor.getMood());
			derived.Accuracy.setValue(accuracy);
			int uid = entityManager.store(derived);
		} catch (Exception e) {
			throw e;
		}
	}

}