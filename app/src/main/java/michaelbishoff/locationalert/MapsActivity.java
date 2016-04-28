package michaelbishoff.locationalert;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.ui.PlaceAutocompleteFragment;
import com.google.android.gms.location.places.ui.PlaceSelectionListener;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

/**
 * Sends a Toast notification when the user is within 200 meters from a location that they specify.
 * @author David Wiedel & Michael Bishoff
 */
public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, LocationListener {

    // Map Objects
    private GoogleMap mMap;
    private Marker targetMarker;
    private Circle radius;

    // Location Objects
    private LocationManager locationManager;
    private Location userLoc;
    private Location targetLoc;

    // Used to determine if the user is already within the radius so
    // that they don't get spammed with Toasts
    private boolean isAlreadyWithinRadius;

    // The minimum time and distance required to receive a location update
    private static final int MIN_TIME = 0;
    private static final int MIN_DISTANCE = 0;

    private static final int ALERT_RADIUS = 200; // meters
    public static final double EARTH_CIRCUMFERENCE = 6378137; // meters
    private static final int TWO_MINUTES = 1000 * 60 * 2; // milliseconds
    private static final int ZOOM_LEVEL = 15; // 2 - 21

    // Similar to a filename for SharedPreferences
    private static final String PREFERENCE = "PREFS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Gets the location manager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // Gets the autocomplete fragment
        PlaceAutocompleteFragment autocompleteFragment = (PlaceAutocompleteFragment)
                getFragmentManager().findFragmentById(R.id.place_autocomplete_fragment);

        // Adds a listener so we know when the user selected a new location
        autocompleteFragment.setOnPlaceSelectedListener(new PlaceSelectionListener() {
            @Override
            public void onPlaceSelected(Place place) {
                LatLng placeLatLng = place.getLatLng();

                // Creates the target if it's null
                if (targetLoc == null) {
                    targetLoc = new Location("MapsActivity");
                }

                // Updates the new target's locatoin
                targetLoc.setLatitude(placeLatLng.latitude);
                targetLoc.setLongitude(placeLatLng.longitude);

                // Adds the target and its radius to the map or moves the target
                // and radius to the new location
                if (targetMarker == null) {
                    targetMarker = mMap.addMarker(new MarkerOptions()
                            .position(placeLatLng)
                            .title(place.getName().toString()));
                } else {
                    targetMarker.setPosition(placeLatLng);
                    targetMarker.setTitle(place.getName().toString());
                }
                if (radius == null) {
                    radius = mMap.addCircle(new CircleOptions()
                            .radius(ALERT_RADIUS)
                            .center(placeLatLng)
                            .strokeColor(Color.RED));
                } else {
                    radius.setCenter(placeLatLng);
                }

                // Saves the target location to the SharedPreferences
                saveTarget(new Double(placeLatLng.latitude).floatValue(),
                           new Double(placeLatLng.longitude).floatValue(),
                           place.getName().toString());
            }

            @Override
            public void onError(Status status) {
                Log.d("MapsActivity", "An error occurred: " + status);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Checks if we have permission to access the user's location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Requests location updates
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_TIME, MIN_DISTANCE, this);
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, MIN_TIME, MIN_DISTANCE, this);

        Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location networkLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

        // Both null, so don't set the location
        if (gpsLoc == null && networkLoc == null) {
            Log.d("MapsActivity", "GPS & Network locations are NULL. User has LocationServices disabled");
            return;

            // One is null
        } else if (gpsLoc == null) {
            userLoc = networkLoc;

        } else if (networkLoc == null) {
            userLoc = gpsLoc;

            // Both are Not null
        } else {

            // Sets the initial previous location to the freshest location
            if (gpsLoc.getTime() > networkLoc.getTime()) {
                userLoc = gpsLoc;
            } else {
                userLoc = networkLoc;
            }
        }

        // userMarker is Not null at this point, but map might be
        if (mMap != null) {
            // Move the camera to the user's position
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(userLoc.getLatitude(), userLoc.getLongitude()), ZOOM_LEVEL));
        }

        // If a target location is already set, check if the user is within the radius
        if (targetLoc != null) {
            if (isWithinTarget(userLoc, targetLoc)) {
                Toast.makeText(MapsActivity.this, "Entered Target Radius", Toast.LENGTH_SHORT).show();
                isAlreadyWithinRadius = true;
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Checks if we have permission to access the user's location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationManager.removeUpdates(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // mMap is Not null at this point, but user's location might be
        if (userLoc != null) {
            // Move the camera to the user's position
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(userLoc.getLatitude(), userLoc.getLongitude()), ZOOM_LEVEL));
        }

        // Checks if we have permission to access the user's location
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Adds the user's dot to the map
        mMap.setMyLocationEnabled(true);

        setSavedTarget();
    }

    /**
     * Checks if the user is now within the radius of their specified target location
     */
    @Override
    public void onLocationChanged(Location location) {
        // If the new location is better, use it
        if (isBetterLocation(location, userLoc)) {
            userLoc = location;

            // if the target location is set
            if (targetLoc != null) {

                // Checks if the user is within the target radius
                boolean isNowWithinRadius = isWithinTarget(userLoc, targetLoc);

                // If the user is not already within the target radius and they are now within the
                // target radius, alert them with a toast
                if (!isAlreadyWithinRadius && isNowWithinRadius) {
                    Toast.makeText(MapsActivity.this, "Entered Target Radius", Toast.LENGTH_SHORT).show();
                    isAlreadyWithinRadius = true;
                }
                // The user was within the radius and they just left the radius
                else if (isAlreadyWithinRadius && !isNowWithinRadius) {
                    Toast.makeText(MapsActivity.this, "Left Target Radius", Toast.LENGTH_SHORT).show();
                    isAlreadyWithinRadius = false;
                }
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) { }

    @Override
    public void onProviderEnabled(String provider) { }

    @Override
    public void onProviderDisabled(String provider) { }

    /**
     * Returns whether the user is within the alert radius of the target
     */
    private boolean isWithinTarget(Location user, Location target) {
        if (getDistance(user, target) < ALERT_RADIUS) {
            return true;
        }
        return false;
    }

    /**
     * Finds the distance between two points in meters by using Great Circle Distance
     */
    private double getDistance(Location loc1, Location loc2) {
        double lat1 = loc1.getLatitude();
        double lng1 = loc1.getLongitude();

        double lat2 = loc2.getLatitude();
        double lng2 = loc2.getLongitude();

        // Converts the difference in lat/lng to radians
        double radLat = Math.toRadians(lat2 - lat1);
        double radLng = Math.toRadians(lng2 - lng1);

        // Calculates the variable 'a' in the Great Circle Distance equation
        double a = Math.pow(Math.sin(radLat / 2), 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(radLng / 2), 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        double distance = EARTH_CIRCUMFERENCE * c;

        return distance;
    }

    /**
     * Initializes the target location to the user's previous target location
     */
    private void setSavedTarget() {
        SharedPreferences preferences = getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);

        // Get the value String with the key "NAME". If the key "NAME" does not exist, return "Tom" instead
        float lat = preferences.getFloat("LAT", 0);
        float lng = preferences.getFloat("LNG", 0);
        String title = preferences.getString("TITLE", null);

        // If there is a saved target location
        if (lat != 0 && lng != 0 && title != null) {
            targetLoc = new Location("MapsActivity");
            targetLoc.setLatitude(lat);
            targetLoc.setLongitude(lng);

            // This is called from onMapReady() so this should never be false
            if (mMap != null) {
                LatLng latLng = new LatLng(targetLoc.getLatitude(), targetLoc.getLongitude());

                targetMarker = mMap.addMarker(new MarkerOptions()
                        .position(latLng)
                        .title(title));

                radius = mMap.addCircle(new CircleOptions()
                        .radius(ALERT_RADIUS)
                        .center(latLng)
                        .strokeColor(Color.RED));
            }
        }
    }

    /**
     * Saves the user's target location to the SharedPreferences
     */
    private void saveTarget(float lat, float lng, String title) {
        // MODE_PRIVATE is default
        SharedPreferences preferences = getSharedPreferences(PREFERENCE, Context.MODE_PRIVATE);

        // The editor allows us to add key value pairs
        SharedPreferences.Editor edit = preferences.edit();
        edit.putFloat("LAT", lat);
        edit.putFloat("LNG", lng);
        edit.putString("TITLE", title);

        // Writes the key value pair to the Shared Preference;
        edit.commit();
    }

    /*
     * Copied from Google's Location Strategies:
     * http://developer.android.com/guide/topics/location/strategies.html
     */

    /** Determines whether one Location reading is better than the current Location fix
     * @param location  The new Location that you want to evaluate
     * @param currentBestLocation  The current Location fix, to which you want to compare the new one
     */
    protected boolean isBetterLocation(Location location, Location currentBestLocation) {
        if (currentBestLocation == null) {
            // A new location is always better than no location
            return true;
        }

        // Check whether the new location fix is newer or older
        long timeDelta = location.getTime() - currentBestLocation.getTime();
        boolean isSignificantlyNewer = timeDelta > TWO_MINUTES;
        boolean isSignificantlyOlder = timeDelta < -TWO_MINUTES;
        boolean isNewer = timeDelta > 0;

        // If it's been more than two minutes since the current location, use the new location
        // because the user has likely moved
        if (isSignificantlyNewer) {
            return true;
            // If the new location is more than two minutes older, it must be worse
        } else if (isSignificantlyOlder) {
            return false;
        }

        // Check whether the new location fix is more or less accurate
        int accuracyDelta = (int) (location.getAccuracy() - currentBestLocation.getAccuracy());
        boolean isLessAccurate = accuracyDelta > 0;
        boolean isMoreAccurate = accuracyDelta < 0;
        boolean isSignificantlyLessAccurate = accuracyDelta > 200;

        // Check if the old and new location are from the same provider
        boolean isFromSameProvider = isSameProvider(location.getProvider(),
                currentBestLocation.getProvider());

        // Determine location quality using a combination of timeliness and accuracy
        if (isMoreAccurate) {
            return true;
        } else if (isNewer && !isLessAccurate) {
            return true;
        } else if (isNewer && !isSignificantlyLessAccurate && isFromSameProvider) {
            return true;
        }
        return false;
    }

    /** Checks whether two providers are the same */
    private boolean isSameProvider(String provider1, String provider2) {
        if (provider1 == null) {
            return provider2 == null;
        }
        return provider1.equals(provider2);
    }
}
