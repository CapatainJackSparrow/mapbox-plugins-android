package com.mapbox.mapboxsdk.plugins.offline;

public class OfflineConstants {
  public static final String ACTION_START_DOWNLOAD = "com.mapbox.mapboxsdk.plugins.offline.download.start";
  public static final String ACTION_CANCEL_DOWNLOAD = "com.mapbox.mapboxsdk.plugins.offline.download.cancel";
  public static final String ACTION_OFFLINE = "com.mapbox.mapboxsdk.plugins.offline";
  public static final String KEY_STATE = "com.mapbox.mapboxsdk.plugins.offline.state";
  public static final String STATE_STARTED = "com.mapbox.mapboxsdk.plugins.offline.state.started";
  public static final String STATE_FINISHED = "com.mapbox.mapboxsdk.plugins.offline.state.complete";
  public static final String STATE_ERROR = "com.mapbox.mapboxsdk.plugins.offline.state.error";
  public static final String STATE_CANCEL = "com.mapbox.mapboxsdk.plugins.offline.state.cancel";
  public static final String STATE_PROGRESS = "com.mapbox.mapboxsdk.plugins.offline.state.progress";
  public static final String KEY_BUNDLE_OFFLINE_REGION = "com.mapbox.mapboxsdk.plugins.offline.region";
  public static final String KEY_BUNDLE_ERROR = "com.mapbox.mapboxsdk.plugins.offline.error";
  public static final String KEY_BUNDLE_MESSAGE = "com.mapbox.mapboxsdk.plugins.offline.error";
  public static final String KEY_PROGRESS = "com.mapbox.mapboxsdk.plugins.offline.progress";
  public static final String NOTIFICATION_CHANNEL = "offline";
  public static final int REQ_CANCEL_DOWNLOAD = 98;

  public static final String RETURNING_DEFINITION = "com.mapbox.mapboxsdk.plugins.offline.returning.definition";
  public static final String RETURNING_REGION_NAME = "com.mapbox.mapboxsdk.plugins.offline.returing.region.name";

  public static final String KEY_BUNDLE = "com.mapbox.mapboxsdk.plugins.offline.download.object";
}
