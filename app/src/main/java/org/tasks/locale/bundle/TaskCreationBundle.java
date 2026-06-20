package org.tasks.locale.bundle;

import android.os.Bundle;
import org.tasks.BuildConfig;

public class TaskCreationBundle {

  public static final String EXTRA_BUNDLE = "org.tasks.locale.create";
  public static final String EXTRA_TITLE = "org.tasks.locale.create.STRING_TITLE";
  public static final String EXTRA_DUE_DATE = "org.tasks.locale.create.STRING_DUE_DATE";
  public static final String EXTRA_DUE_TIME = "org.tasks.locale.create.STRING_DUE_TIME";
  public static final String EXTRA_PRIORITY = "org.tasks.locale.create.STRING_PRIORITY";
  public static final String EXTRA_DESCRIPTION = "org.tasks.locale.create.STRING_DESCRIPTION";
  public static final String EXTRA_PLACE_NAME = "org.tasks.locale.create.STRING_PLACE_NAME";
  public static final String EXTRA_LATITUDE = "org.tasks.locale.create.STRING_LATITUDE";
  public static final String EXTRA_LONGITUDE = "org.tasks.locale.create.STRING_LONGITUDE";
  public static final String EXTRA_RADIUS = "org.tasks.locale.create.STRING_RADIUS";
  public static final String EXTRA_ARRIVAL = "org.tasks.locale.create.STRING_ARRIVAL";
  public static final String EXTRA_DEPARTURE = "org.tasks.locale.create.STRING_DEPARTURE";
  private static final String EXTRA_VERSION_CODE = "org.tasks.locale.create.INT_VERSION_CODE";

  private final Bundle bundle;

  public TaskCreationBundle() {
    this(null);
  }

  public TaskCreationBundle(Bundle bundle) {
    if (bundle == null) {
      this.bundle = new Bundle();
      this.bundle.putInt(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
    } else {
      this.bundle = bundle;
    }
  }

  public static boolean isBundleValid(Bundle bundle) {
    return -1 != bundle.getInt(EXTRA_VERSION_CODE, -1);
  }

  public String getTitle() {
    return bundle.getString(EXTRA_TITLE);
  }

  public void setTitle(String title) {
    bundle.putString(EXTRA_TITLE, title);
  }

  public String getDueDate() {
    return bundle.getString(EXTRA_DUE_DATE);
  }

  public void setDueDate(String dueDate) {
    bundle.putString(EXTRA_DUE_DATE, dueDate);
  }

  public String getDueTime() {
    return bundle.getString(EXTRA_DUE_TIME);
  }

  public void setDueTime(String dueTime) {
    bundle.putString(EXTRA_DUE_TIME, dueTime);
  }

  public String getPriority() {
    return bundle.getString(EXTRA_PRIORITY);
  }

  public void setPriority(String priority) {
    bundle.putString(EXTRA_PRIORITY, priority);
  }

  public String getDescription() {
    return bundle.getString(EXTRA_DESCRIPTION);
  }

  public void setDescription(String description) {
    bundle.putString(EXTRA_DESCRIPTION, description);
  }

  public String getPlaceName() {
    return bundle.getString(EXTRA_PLACE_NAME);
  }

  public void setPlaceName(String placeName) {
    bundle.putString(EXTRA_PLACE_NAME, placeName);
  }

  public String getLatitude() {
    return bundle.getString(EXTRA_LATITUDE);
  }

  public void setLatitude(String latitude) {
    bundle.putString(EXTRA_LATITUDE, latitude);
  }

  public String getLongitude() {
    return bundle.getString(EXTRA_LONGITUDE);
  }

  public void setLongitude(String longitude) {
    bundle.putString(EXTRA_LONGITUDE, longitude);
  }

  public String getRadius() {
    return bundle.getString(EXTRA_RADIUS);
  }

  public void setRadius(String radius) {
    bundle.putString(EXTRA_RADIUS, radius);
  }

  public String getArrival() {
    return bundle.getString(EXTRA_ARRIVAL);
  }

  public void setArrival(String arrival) {
    bundle.putString(EXTRA_ARRIVAL, arrival);
  }

  public String getDeparture() {
    return bundle.getString(EXTRA_DEPARTURE);
  }

  public void setDeparture(String departure) {
    bundle.putString(EXTRA_DEPARTURE, departure);
  }

  public Bundle build() {
    bundle.putInt(EXTRA_VERSION_CODE, BuildConfig.VERSION_CODE);
    return bundle;
  }

  @Override
  public String toString() {
    return "TaskCreationBundle{" + "bundle=" + bundle + '}';
  }
}
