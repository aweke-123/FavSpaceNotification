package com.example.anjali.favspacenotificationapp;

import android.support.annotation.NonNull;

import com.google.android.gms.location.Geofence;

import java.util.UUID;
//a model class that will be used to serialize the geofences
public class LocationGeofence implements Comparable {

  public String id;
  public String name;
  public double latitude;
  public double longitude;
  public float radius;

    //use to create object of geofence
  public Geofence geofence() {
    id = UUID.randomUUID().toString();
    return new Geofence.Builder()
            .setRequestId(id)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build();
  }

  @Override
  public int compareTo(@NonNull Object another) {
    LocationGeofence other = (LocationGeofence) another;
    return name.compareTo(other.name);
  }

}
