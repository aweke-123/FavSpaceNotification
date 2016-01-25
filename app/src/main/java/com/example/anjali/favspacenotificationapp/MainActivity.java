package com.example.anjali.favspacenotificationapp;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu
        ;
import android.view.MenuItem;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

public class MainActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor>{

    private GoogleMap googleMap;
    MarkerOptions markerOptions;
    LatLng latLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        int status = GooglePlayServicesUtil.isGooglePlayServicesAvailable(getBaseContext());
        if (status != ConnectionResult.SUCCESS) {

            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(status, this, requestCode);
            dialog.show();

        } else {
            SupportMapFragment fm = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            googleMap = fm.getMap();

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            googleMap.setMyLocationEnabled(true);
            getSupportLoaderManager().initLoader(0, null, this);
        }

        GeofenceController.getInstance().init(this);    //initializes GeofenceController
        googleMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {

            @Override
            public void onMapClick(LatLng point) {
               //check for network and location
                if(isNetworkConnected(getApplicationContext())==true){
                LocationGeofence geofence = new LocationGeofence();
                geofence.name = (point.latitude + point.longitude) + "";
                geofence.latitude = point.latitude;
                geofence.longitude = point.longitude;
                geofence.radius = 100;

                //create geofence
                GeofenceController.getInstance().addGeofence(geofence, geofenceControllerListener);

                //draw marker and save values in db
                drawMarker(point);
                ContentValues contentValues = new ContentValues();
                contentValues.put(LocationsDB.FIELD_LAT, point.latitude);
                contentValues.put(LocationsDB.FIELD_LNG, point.longitude);
                contentValues.put(LocationsDB.FIELD_ZOOM, googleMap.getCameraPosition().zoom);
                LocationInsertTask insertTask = new LocationInsertTask();
                insertTask.execute(contentValues);
                Toast.makeText(getBaseContext(), R.string.space_added, Toast.LENGTH_SHORT).show();
            } else
                //if no Internet display notification message
                    Toast.makeText(getApplicationContext(),R.string.no_connection, Toast.LENGTH_LONG).show();
            }
        });

    }


    //function to check internet connectivity
    public boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
        LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        NetworkInfo ni = cm.getActiveNetworkInfo();

        if(ni != null && ni.isConnected()) {
            //condition to check whether GPS is Enabled or not
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                return true;
            }else{
                showGPSDisabledAlertToUser();
            }
        }

        return false;
    }

    //function to display dialog to turn on GPS
    private void showGPSDisabledAlertToUser(){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setMessage(R.string.GPS_disabled_error)
                .setCancelable(false)
                .setPositiveButton(R.string.GPS_settings,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent callGPSSettingIntent = new Intent(
                                        android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                                startActivity(callGPSSettingIntent);
                            }
                        });
        alertDialogBuilder.setNegativeButton(R.string.GPS_cancel,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                        finish();
                    }
                });
        AlertDialog alert = alertDialogBuilder.create();
        alert.show();
    }

    //function to add marker on map
    private void drawMarker(LatLng point){
        latLng  = point;
        markerOptions = new MarkerOptions();
        markerOptions.position(latLng);
        googleMap.addMarker(markerOptions);
        new ReverseGeocodingTask(getBaseContext()).execute(point);
    }


    private class LocationInsertTask extends AsyncTask<ContentValues, Void, Void>{
        @Override
        protected Void doInBackground(ContentValues... contentValues) {
            //insert valuse to db
            getContentResolver().insert(LocationsContentProvider.CONTENT_URI, contentValues[0]);
            return null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        //check for google play services
        int googlePlayServicesCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        Log.i(MainActivity.class.getSimpleName(), "googlePlayServicesCode = " + googlePlayServicesCode);

        if (googlePlayServicesCode == 1 || googlePlayServicesCode == 2 || googlePlayServicesCode == 3) {
            GooglePlayServicesUtil.getErrorDialog(googlePlayServicesCode, this, 0).show();
        }
    }

    private class LocationDeleteTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... params) {
            //delete the values of location
            getContentResolver().delete(LocationsContentProvider.CONTENT_URI, null, null);
            return null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0,Bundle arg1) {
        Uri uri = LocationsContentProvider.CONTENT_URI;
        return new CursorLoader(this, uri, null, null, null, null);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_remove_spaces) {
            //dialog to delete the all saved spaces
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.AreYouSure)
                    .setPositiveButton(R.string.Yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            GeofenceController.getInstance().removeAllGeofences(geofenceControllerListener);
                            googleMap.clear();
                            LocationDeleteTask deleteTask = new LocationDeleteTask();
                            deleteTask.execute();
                            Toast.makeText(getBaseContext(), R.string.remove_all_spaces, Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton(R.string.No, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                        }
                    })
                    .create()
                    .show();



        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor arg1) {
       //load values from db
        int locationCount = 0;
        double lat=0;
        double lng=0;
        float zoom=0;
        locationCount = arg1.getCount();
        arg1.moveToFirst();
        for(int i=0;i<locationCount;i++){
            lat = arg1.getDouble(arg1.getColumnIndex(LocationsDB.FIELD_LAT));
            lng = arg1.getDouble(arg1.getColumnIndex(LocationsDB.FIELD_LNG));
            zoom = arg1.getFloat(arg1.getColumnIndex(LocationsDB.FIELD_ZOOM));
            LatLng location = new LatLng(lat, lng);
            drawMarker(location);
            arg1.moveToNext();
        }

        if(locationCount>0){
            googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(lat, lng)));
            googleMap.animateCamera(CameraUpdateFactory.zoomTo(zoom));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }

    private GeofenceController.GeofenceControllerListener geofenceControllerListener = new GeofenceController.GeofenceControllerListener() {
        @Override
        public void onError() {
           //display error if GPS is Off
            showErrorToast();
        }
    };

    private void showErrorToast() {
        Toast.makeText(this, this.getString(R.string.Toast_Error), Toast.LENGTH_SHORT).show();
    }

    private class ReverseGeocodingTask extends AsyncTask<LatLng, Void, String>{
        Context mContext;

        public ReverseGeocodingTask(Context context){
            super();
            mContext = context;
        }

        // Finding address using reverse geocoding
        @Override
        protected String doInBackground(LatLng... params) {
            Geocoder geocoder = new Geocoder(mContext);
            double latitude = params[0].latitude;
            double longitude = params[0].longitude;

            List<Address> addresses = null;
            String addressText="";

            try {
                addresses = geocoder.getFromLocation(latitude, longitude,1);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(addresses != null && addresses.size() > 0 ){
                Address address = addresses.get(0);

                addressText = String.format("%s, %s, %s",
                        address.getMaxAddressLineIndex() > 0 ? address.getAddressLine(0) : "",
                        address.getLocality(),
                        address.getCountryName());
            }

            return addressText;
        }

        @Override
        protected void onPostExecute(String addressText) {
            googleMap.addMarker(
                            new MarkerOptions()
                                    .position(latLng)
                                    .title(addressText))
                    .showInfoWindow();
        }
    }

}
