package com.example.anjali.favspacenotificationapp;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.Geofence;
import com.google.android.gms.location.GeofencingRequest;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

//use to interact with GoogleApi client
public class GeofenceController {
  private final String TAG = GeofenceController.class.getName();

  private Context context;           //use to connect with Google Api client
  private GoogleApiClient googleApiClient;  //use to connect with Google Api client
  private Gson gson;                //use to serialize geofences to disk
  private SharedPreferences prefs;  //use to serialize geofences to disk
  private GeofenceControllerListener listener;

  private List<LocationGeofence> locationGeofences;
  public List<LocationGeofence> getLocationGeofences() {
    return locationGeofences;
  }

  private List<LocationGeofence> locationGeofencesToRemove;

  private Geofence geofenceToAdd;
  private LocationGeofence locationGeofenceToAdd;
  private static GeofenceController INSTANCE;

  //create and access instance
  public static GeofenceController getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new GeofenceController();
    }
    return INSTANCE;
  }

  public void init(Context context) {
    this.context = context.getApplicationContext();

    gson = new Gson();
    locationGeofences = new ArrayList<>();
    locationGeofencesToRemove = new ArrayList<>();
    prefs = this.context.getSharedPreferences(Constants.SharedPrefs.Geofences, Context.MODE_PRIVATE);

    loadGeofences();
  }

  public void addGeofence(LocationGeofence locationGeofence, GeofenceControllerListener listener) {
    this.locationGeofenceToAdd = locationGeofence;
    this.geofenceToAdd = locationGeofence.geofence();
    this.listener = listener;

    connectWithCallbacks(connectionAddListener);
  }

  public void removeGeofences(List<LocationGeofence> locationGeofencesToRemove, GeofenceControllerListener listener) {
    this.locationGeofencesToRemove = locationGeofencesToRemove;
    this.listener = listener;

    connectWithCallbacks(connectionRemoveListener);
  }

  public void removeAllGeofences(GeofenceControllerListener listener) {
    locationGeofencesToRemove = new ArrayList<>();
    for (LocationGeofence locationGeofence : locationGeofences) {
      locationGeofencesToRemove.add(locationGeofence);
    }
    this.listener = listener;

    connectWithCallbacks(connectionRemoveListener);
  }

  private void loadGeofences() {
    Map<String, ?> keys = prefs.getAll();
    for (Map.Entry<String, ?> entry : keys.entrySet()) {
      String jsonString = prefs.getString(entry.getKey(), null);
      LocationGeofence locationGeofence = gson.fromJson(jsonString, LocationGeofence.class);
      locationGeofences.add(locationGeofence);
    }

    Collections.sort(locationGeofences);
  }


  private void connectWithCallbacks(GoogleApiClient.ConnectionCallbacks callbacks) {
    googleApiClient = new GoogleApiClient.Builder(context)
            .addApi(LocationServices.API)
            .addConnectionCallbacks(callbacks)
            .addOnConnectionFailedListener(connectionFailedListener)
            .build();
    googleApiClient.connect();
  }

  private GeofencingRequest getAddGeofencingRequest() {
    List<Geofence> geofencesToAdd = new ArrayList<>();
    geofencesToAdd.add(geofenceToAdd);
    GeofencingRequest.Builder builder = new GeofencingRequest.Builder();
    builder.setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER);
    builder.addGeofences(geofencesToAdd);
    return builder.build();
  }

  private void saveGeofence() {
    locationGeofences.add(locationGeofenceToAdd);

    String json = gson.toJson(locationGeofenceToAdd);
    SharedPreferences.Editor editor = prefs.edit();
    editor.putString(locationGeofenceToAdd.id, json);
    editor.apply();
  }

  private void removeSavedGeofences() {
    SharedPreferences.Editor editor = prefs.edit();

    for (LocationGeofence locationGeofence : locationGeofencesToRemove) {
      int index = locationGeofences.indexOf(locationGeofence);
      editor.remove(locationGeofence.id);
      locationGeofences.remove(index);
      editor.apply();
    }

  }

  private void sendError() {
    if (listener != null) {
      listener.onError();
    }
  }

  private GoogleApiClient.ConnectionCallbacks connectionAddListener = new GoogleApiClient.ConnectionCallbacks() {
    @Override
    public void onConnected(Bundle bundle) {
      Intent intent = new Intent(context, LocationIntentService.class);
      PendingIntent pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
      PendingResult<Status> result = LocationServices.GeofencingApi.addGeofences(googleApiClient, getAddGeofencingRequest(), pendingIntent);
      result.setResultCallback(new ResultCallback<Status>() {
        @Override
        public void onResult(Status status) {
          if (status.isSuccess()) {
            saveGeofence();
          } else {
            Log.e(TAG, "Registering geofence failed: " + status.getStatusMessage() + " : " + status.getStatusCode());
            sendError();
          }
        }
      });
    }

    @Override
    public void onConnectionSuspended(int i) {
      Log.e(TAG, "Connecting to GoogleApiClient suspended.");
      sendError();
    }
  };

  private GoogleApiClient.ConnectionCallbacks connectionRemoveListener = new GoogleApiClient.ConnectionCallbacks() {
    @Override
    public void onConnected(Bundle bundle) {
      List<String> removeIds = new ArrayList<>();
      for (LocationGeofence locationGeofence : locationGeofencesToRemove) {
        removeIds.add(locationGeofence.id);
      }

      if (removeIds.size() > 0) {
        PendingResult<Status> result = LocationServices.GeofencingApi.removeGeofences(googleApiClient, removeIds);
        result.setResultCallback(new ResultCallback<Status>() {
          @Override
          public void onResult(Status status) {
            if (status.isSuccess()) {
              removeSavedGeofences();
            } else {
              Log.e(TAG, "Removing geofence failed: " + status.getStatusMessage());
              sendError();
            }
          }
        });
      }
    }

    @Override
    public void onConnectionSuspended(int i) {
      Log.e(TAG, "Connecting to GoogleApiClient suspended.");
      sendError();
    }
  };

  private GoogleApiClient.OnConnectionFailedListener connectionFailedListener = new GoogleApiClient.OnConnectionFailedListener() {
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
      Log.e(TAG, "Connecting to GoogleApiClient failed.");
      sendError();
    }
  };


  public interface GeofenceControllerListener {
    void onError();
  }

}