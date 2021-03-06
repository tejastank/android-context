/*******************************************************************************
 * Copyright 2009, 2010 Omnidroid - http://code.google.com/p/omnidroid
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package edu.fsu.cs.contextprovider.monitor;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import net.smart_entity.EntityManager;

import edu.fsu.cs.contextprovider.data.ContextConstants;
import edu.fsu.cs.contextprovider.data.LocationEntity;
import edu.fsu.cs.contextprovider.sensor.GPSService;
import edu.fsu.cs.contextprovider.sensor.NetworkService;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;


/**
 * The class is responsible for communication with the Location Service. It provides access to
 * Location Services External Attribute, as well as Initiates Location Change Intents.
 */
public class LocationMonitor extends TimerTask {
	private static final String TAG = "LocationMonitor";
	private static final long radiusOfEarthMeters = 6378100;

	private static final boolean DEBUG_TTS = false;
	private static boolean running = false;
	private static Timer timer = new Timer();
	private static Address currentAddress = null;
	private static LocationMonitor locationObj = new LocationMonitor();
	private static Location currentLocation = new Location(new String());
	private static Geocoder geocoder = null;
	
	EntityManager entityManager;
	
//	private static HashMap<String, Address> addressBook = new HashMap<String, Address>();
//	private static HashMap<String, String> nicknameToAddress = new HashMap<String, String>();
	private static HashMap<String, Address> proximityAddressCache = new HashMap<String, Address>();

	public static void StartThread(int interval, Geocoder geo) {
		if (running == true) {
			return;
		}
		Log.i(TAG, "Start()");
		timer.schedule(locationObj, 100, interval*1000);
		running = true;
		geocoder = geo;
	}

	/**
	 * Stop the thread/timer that keeps the movement state up to date
	 */
	public static void StopThread() {
		Log.i(TAG, "Stop()");
		timer.purge();
		locationObj = new LocationMonitor();
		running = false;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		updateGeoLocation();
		Log.i(TAG, "Address: [" + currentAddress + "]");
	}

	public static double proximityTo(String loc) {
		Address tmpAddress = null;
		Address address = null;
		
		/* Try grabbing the address from our cache first */
		if ((tmpAddress = proximityAddressCache.get(loc)) != null) {
			address = tmpAddress;
		} else {
		/* not in the cache, translate it */
			address = getGeoFromAddress(loc);
			if (address == null) {
				Log.i(TAG, "proximityTo() could not convert [" + loc + "]");
				return -1.0;
			} else {
				/* Successful translation, store it */
				proximityAddressCache.put(loc, address);
			}
		}

		return distanceMeters(address.getLatitude(), address.getLongitude(), getLatitude(), getLongitude());
	}

	public static double proximityTo(double longitude, double latitude) {
		return distanceMeters(latitude, longitude, getLatitude(), getLongitude());
	}

	public static boolean isInside() {
		if (GPSService.isReliable() == false) {
			return true;
		}
		return false;
	}
	
	public static String getZip() {
		if (currentAddress == null)
			return null;
		return currentAddress.getPostalCode();
	}

	public static String getAddress() {
		if (currentAddress == null)
			return null;
		return currentAddress.getAddressLine(0);
	}

	public static Address getAddressObj() {
		return currentAddress;
	}

	public static String getNeighborhood() {
		if (currentAddress == null)
			return null;
		return currentAddress.getSubLocality();
	}

	public static double getLongitude() {
		if (GPSService.isReliable() == true) {
			return GPSService.getLongitude();
		} else if (NetworkService.isReliable() == true) {
			return NetworkService.getLongitude();
		}
		return 0;
	}

	public static double getLatitude() {
		if (GPSService.isReliable() == true) {
			return GPSService.getLatitude();
		} else if (NetworkService.isReliable() == true) {
			return NetworkService.getLatitude();
		}
		return 0;
	}

	public static double getAltitude() {
		if (GPSService.isReliable() == true) {
			return GPSService.getAltitude();
		} else if (NetworkService.isReliable() == true) {
			return NetworkService.getAltitude();
		}
		return 0;
	}

	public static float getBearing() {
		if (GPSService.isReliable() == true) {
			return GPSService.getBearing();
		} 
		return 0;
	}
	
	public static float getSpeed() {
		if (GPSService.isReliable() == true) {
			return GPSService.getSpeed();
		} 
		return 0;
	}

	public static Location getLocation() {
		if (GPSService.isReliable() == true) {
			return GPSService.getLocation();
		} else if (NetworkService.isReliable() == true) {
			return NetworkService.getLocation();
		}
		return null;
	}
	
	private static Address getGeoFromAddress(String str) {
		List<Address> addresses = null;
		Address address = null;
		try {
			addresses = geocoder.getFromLocationName(str, 1);
			if (addresses == null) {
				Log.i(TAG, "getGeoFromAddress(): getFromLocationName() returned null");
				return null;
			}
			if (addresses.size() > 0) {
				address =  addresses.get(0);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return address;
	}

	private static Address getAddressFromGeo(double latitude, double longitude) {
		List<Address> addresses = null;
		Address address = null;
		try {
			addresses = geocoder.getFromLocation(latitude, longitude,1);
			if (addresses == null) {
				Log.i(TAG, "getAddressFromGeo(): getFromLocation() returned null");
				return null;
			}
			if (addresses.size() > 0) {
				address = addresses.get(0);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return address;
	}

	private void updateGeoLocation() {
		Address address = getAddressFromGeo(getLatitude(), getLongitude());
		if (address != null) {
			currentAddress = address;
		}
	}
	
	public static double milesPerHourToMetersPerSecond(double milesPerHour) {
		return 0.44704 * milesPerHour;
	}

	public static double distanceMeters(double aLat, double aLon, double bLat, double bLon) {
		// Haversine formula, from http://mathforum.org/library/drmath/view/51879.html
		double dlon = bLon - aLon;
		double dlat = bLat - aLat;
		double a = Math.pow(Math.sin(Math.toRadians(dlat/2)), 2) +
		Math.cos(Math.toRadians(aLat)) *
		Math.cos(Math.toRadians(bLat)) *
		Math.pow(Math.sin(Math.toRadians(dlon / 2)), 2);
		double greatCircleDistance = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		return radiusOfEarthMeters * greatCircleDistance;
	}
	
}
