package com.mapbox.mapboxsdk.plugins.locationlayer;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.annotation.SuppressLint;
import android.location.Location;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.animation.LinearInterpolator;

import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapboxMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerConstants.ACCURACY_RADIUS_ANIMATION_DURATION;
import static com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerConstants.COMPASS_UPDATE_RATE_MS;
import static com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerConstants.MAX_ANIMATION_DURATION_MS;
import static com.mapbox.mapboxsdk.plugins.locationlayer.LocationLayerConstants.TRANSITION_ANIMATION_DURATION_MS;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_CAMERA_COMPASS_BEARING;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_CAMERA_GPS_BEARING;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_CAMERA_LATLNG;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_LAYER_ACCURACY;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_LAYER_COMPASS_BEARING;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_LAYER_GPS_BEARING;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_LAYER_LATLNG;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_TILT;
import static com.mapbox.mapboxsdk.plugins.locationlayer.PluginAnimator.ANIMATOR_ZOOM;

final class PluginAnimatorCoordinator {

  @SuppressLint("UseSparseArrays")
  private final Map<Integer, PluginAnimator> animatorMap = new HashMap<>();

  private final List<PluginAnimator.OnLayerAnimationsValuesChangeListener> layerListeners = new ArrayList<>();
  private final List<PluginAnimator.OnCameraAnimationsValuesChangeListener> cameraListeners = new ArrayList<>();

  private Location previousLocation;
  private float previousAccuracyRadius = -1;
  private float previousCompassBearing = -1;
  private long locationUpdateTimestamp = -1;

  void addLayerListener(PluginAnimator.OnLayerAnimationsValuesChangeListener listener) {
    layerListeners.add(listener);
  }

  void removeLayerListener(PluginAnimator.OnLayerAnimationsValuesChangeListener listener) {
    layerListeners.remove(listener);
  }

  void addCameraListener(PluginAnimator.OnCameraAnimationsValuesChangeListener listener) {
    cameraListeners.add(listener);
  }

  void removeCameraListener(PluginAnimator.OnCameraAnimationsValuesChangeListener listener) {
    cameraListeners.remove(listener);
  }

  void feedNewLocation(@NonNull Location newLocation, @NonNull CameraPosition currentCameraPosition,
                       boolean isGpsNorth) {
    if (previousLocation == null) {
      previousLocation = newLocation;
      locationUpdateTimestamp = SystemClock.elapsedRealtime();
    }

    LatLng previousLayerLatLng = getPreviousLayerLatLng();
    float previousLayerBearing = getPreviousLayerGpsBearing();
    LatLng previousCameraLatLng = currentCameraPosition.target;
    float previousCameraBearing = (float) currentCameraPosition.bearing;

    LatLng targetLatLng = new LatLng(newLocation);
    float targetLayerBearing = newLocation.getBearing();
    float targetCameraBearing = newLocation.getBearing();
    targetCameraBearing = checkGpsNorth(isGpsNorth, targetCameraBearing);

    updateLayerAnimators(previousLayerLatLng, targetLatLng, previousLayerBearing, targetLayerBearing);
    updateCameraAnimators(previousCameraLatLng, previousCameraBearing, targetLatLng, targetCameraBearing);

    playLocationAnimators(getAnimationDuration());

    previousLocation = newLocation;
  }

  void feedNewCompassBearing(float targetCompassBearing, @NonNull CameraPosition currentCameraPosition) {
    if (previousCompassBearing < 0) {
      previousCompassBearing = targetCompassBearing;
    }

    float previousLayerBearing = getPreviousLayerCompassBearing();
    float previousCameraBearing = (float) currentCameraPosition.bearing;

    updateCompassAnimators(targetCompassBearing, previousLayerBearing, previousCameraBearing);
    playCompassAnimators(COMPASS_UPDATE_RATE_MS);

    previousCompassBearing = targetCompassBearing;
  }

  void feedNewAccuracyRadius(float targetAccuracyRadius, boolean noAnimation) {
    if (previousAccuracyRadius < 0) {
      previousAccuracyRadius = targetAccuracyRadius;
    }

    float previousAccuracyRadius = getPreviousAccuracyRadius();
    updateAccuracyAnimators(targetAccuracyRadius, previousAccuracyRadius);
    playAccuracyAnimator(noAnimation ? 0 : ACCURACY_RADIUS_ANIMATION_DURATION);

    this.previousAccuracyRadius = targetAccuracyRadius;
  }

  void feedNewZoomLevel(double targetZoomLevel, @NonNull CameraPosition currentCameraPosition, long animationDuration,
                        @Nullable MapboxMap.CancelableCallback callback) {
    updateZoomAnimator((float) targetZoomLevel, (float) currentCameraPosition.zoom, callback);
    playZoomAnimator(animationDuration);
  }

  void feedNewTilt(double targetTilt, @NonNull CameraPosition currentCameraPosition, long animationDuration,
                   @Nullable MapboxMap.CancelableCallback callback) {
    updateTiltAnimator((float) targetTilt, (float) currentCameraPosition.tilt, callback);
    playTiltAnimator(animationDuration);
  }

  private LatLng getPreviousLayerLatLng() {
    LatLng previousLatLng;
    PluginAnimator latLngAnimator = animatorMap.get(ANIMATOR_LAYER_LATLNG);
    if (latLngAnimator != null) {
      previousLatLng = (LatLng) latLngAnimator.getAnimatedValue();
    } else {
      previousLatLng = new LatLng(previousLocation);
    }
    return previousLatLng;
  }

  private float getPreviousLayerGpsBearing() {
    LayerGpsBearingAnimator animator = (LayerGpsBearingAnimator) animatorMap.get(ANIMATOR_LAYER_GPS_BEARING);
    float previousBearing;
    if (animator != null) {
      previousBearing = (float) animator.getAnimatedValue();
    } else {
      previousBearing = previousLocation.getBearing();
    }
    return previousBearing;
  }

  private float getPreviousLayerCompassBearing() {
    LayerCompassBearingAnimator animator =
      (LayerCompassBearingAnimator) animatorMap.get(ANIMATOR_LAYER_COMPASS_BEARING);

    float previousBearing;
    if (animator != null) {
      previousBearing = (float) animator.getAnimatedValue();
    } else {
      previousBearing = previousCompassBearing;
    }
    return previousBearing;
  }

  private float getPreviousAccuracyRadius() {
    LayerAccuracyAnimator animator = (LayerAccuracyAnimator) animatorMap.get(ANIMATOR_LAYER_ACCURACY);
    float previousRadius;
    if (animator != null) {
      previousRadius = (float) animator.getAnimatedValue();
    } else {
      previousRadius = previousAccuracyRadius;
    }
    return previousRadius;
  }

  private void updateLayerAnimators(LatLng previousLatLng, LatLng targetLatLng,
                                    float previousBearing, float targetBearing) {
    createNewAnimator(ANIMATOR_LAYER_LATLNG, new LayerLatLngAnimator(previousLatLng, targetLatLng, layerListeners));

    float normalizedLayerBearing = Utils.shortestRotation(targetBearing, previousBearing);
    createNewAnimator(ANIMATOR_LAYER_GPS_BEARING,
      new LayerGpsBearingAnimator(previousBearing, normalizedLayerBearing, layerListeners));
  }

  private void updateCameraAnimators(LatLng previousCameraLatLng, float previousCameraBearing,
                                     LatLng targetLatLng, float targetBearing) {
    createNewAnimator(ANIMATOR_CAMERA_LATLNG,
      new CameraLatLngAnimator(previousCameraLatLng, targetLatLng, cameraListeners));

    float normalizedCameraBearing = Utils.shortestRotation(targetBearing, previousCameraBearing);
    createNewAnimator(ANIMATOR_CAMERA_GPS_BEARING,
      new CameraGpsBearingAnimator(previousCameraBearing, normalizedCameraBearing, cameraListeners));
  }

  private void updateCompassAnimators(float targetCompassBearing, float previousLayerBearing,
                                      float previousCameraBearing) {
    float normalizedLayerBearing = Utils.shortestRotation(targetCompassBearing, previousLayerBearing);
    createNewAnimator(ANIMATOR_LAYER_COMPASS_BEARING,
      new LayerCompassBearingAnimator(previousLayerBearing, normalizedLayerBearing, layerListeners));

    float normalizedCameraBearing = Utils.shortestRotation(targetCompassBearing, previousCameraBearing);
    createNewAnimator(ANIMATOR_CAMERA_COMPASS_BEARING,
      new CameraCompassBearingAnimator(previousCameraBearing, normalizedCameraBearing, cameraListeners));
  }

  private void updateAccuracyAnimators(float targetAccuracyRadius, float previousAccuracyRadius) {
    createNewAnimator(ANIMATOR_LAYER_ACCURACY,
      new LayerAccuracyAnimator(previousAccuracyRadius, targetAccuracyRadius, layerListeners));
  }

  private void updateZoomAnimator(float targetZoomLevel, float previousZoomLevel,
                                  @Nullable MapboxMap.CancelableCallback cancelableCallback) {
    createNewAnimator(ANIMATOR_ZOOM,
      new ZoomAnimator(previousZoomLevel, targetZoomLevel, cameraListeners, cancelableCallback));
  }

  private void updateTiltAnimator(float targetTilt, float previousTiltLevel,
                                  @Nullable MapboxMap.CancelableCallback cancelableCallback) {
    createNewAnimator(ANIMATOR_TILT,
      new TiltAnimator(previousTiltLevel, targetTilt, cameraListeners, cancelableCallback));
  }

  private long getAnimationDuration() {
    long previousUpdateTimeStamp = locationUpdateTimestamp;
    locationUpdateTimestamp = SystemClock.elapsedRealtime();

    long animationDuration;
    if (previousUpdateTimeStamp == 0) {
      animationDuration = 0;
    } else {
      animationDuration = (long) ((locationUpdateTimestamp - previousUpdateTimeStamp) * 1.1f)
      /*make animation slightly longer*/;
    }

    animationDuration = Math.min(animationDuration, MAX_ANIMATION_DURATION_MS);

    return animationDuration;
  }

  private float checkGpsNorth(boolean isGpsNorth, float targetCameraBearing) {
    if (isGpsNorth) {
      targetCameraBearing = 0;
    }
    return targetCameraBearing;
  }

  private void playLocationAnimators(long duration) {
    List<Animator> locationAnimators = new ArrayList<>();
    locationAnimators.add(animatorMap.get(ANIMATOR_LAYER_LATLNG));
    locationAnimators.add(animatorMap.get(ANIMATOR_LAYER_GPS_BEARING));
    locationAnimators.add(animatorMap.get(ANIMATOR_CAMERA_LATLNG));
    locationAnimators.add(animatorMap.get(ANIMATOR_CAMERA_GPS_BEARING));
    AnimatorSet locationAnimatorSet = new AnimatorSet();
    locationAnimatorSet.playTogether(locationAnimators);
    locationAnimatorSet.setInterpolator(new LinearInterpolator());
    locationAnimatorSet.setDuration(duration);
    locationAnimatorSet.start();
  }

  private void playCompassAnimators(long duration) {
    List<Animator> compassAnimators = new ArrayList<>();
    compassAnimators.add(animatorMap.get(ANIMATOR_LAYER_COMPASS_BEARING));
    compassAnimators.add(animatorMap.get(ANIMATOR_CAMERA_COMPASS_BEARING));
    AnimatorSet compassAnimatorSet = new AnimatorSet();
    compassAnimatorSet.playTogether(compassAnimators);
    compassAnimatorSet.setDuration(duration);
    compassAnimatorSet.start();
  }

  private void playAccuracyAnimator(long duration) {
    PluginAnimator animator = animatorMap.get(ANIMATOR_LAYER_ACCURACY);
    animator.setDuration(duration);
    animator.start();
  }

  private void playZoomAnimator(long duration) {
    PluginAnimator animator = animatorMap.get(ANIMATOR_ZOOM);
    animator.setDuration(duration);
    animator.start();
  }

  private void playTiltAnimator(long duration) {
    PluginAnimator animator = animatorMap.get(ANIMATOR_TILT);
    animator.setDuration(duration);
    animator.start();
  }

  private void playCameraLocationAnimators(long duration) {
    List<Animator> locationAnimators = new ArrayList<>();
    locationAnimators.add(animatorMap.get(ANIMATOR_CAMERA_LATLNG));
    locationAnimators.add(animatorMap.get(ANIMATOR_CAMERA_GPS_BEARING));
    AnimatorSet locationAnimatorSet = new AnimatorSet();
    locationAnimatorSet.playTogether(locationAnimators);
    locationAnimatorSet.setInterpolator(new LinearInterpolator());
    locationAnimatorSet.setDuration(duration);
    locationAnimatorSet.start();
  }

  void resetAllCameraAnimations(CameraPosition currentCameraPosition, boolean isGpsNorth) {
    resetCameraCompassAnimation(currentCameraPosition);
    resetCameraLocationAnimations(currentCameraPosition, isGpsNorth);
    playCameraLocationAnimators(TRANSITION_ANIMATION_DURATION_MS);
  }

  private void resetCameraLocationAnimations(CameraPosition currentCameraPosition, boolean isGpsNorth) {
    resetCameraLatLngAnimation(currentCameraPosition);
    resetCameraGpsBearingAnimation(currentCameraPosition, isGpsNorth);
  }

  private void resetCameraLatLngAnimation(CameraPosition currentCameraPosition) {
    CameraLatLngAnimator animator = (CameraLatLngAnimator) animatorMap.get(ANIMATOR_CAMERA_LATLNG);
    if (animator == null) {
      return;
    }

    LatLng currentTarget = animator.getTarget();
    LatLng previousCameraTarget = currentCameraPosition.target;
    createNewAnimator(ANIMATOR_CAMERA_LATLNG,
      new CameraLatLngAnimator(previousCameraTarget, currentTarget, cameraListeners));
  }

  private void resetCameraGpsBearingAnimation(CameraPosition currentCameraPosition, boolean isGpsNorth) {
    CameraGpsBearingAnimator animator = (CameraGpsBearingAnimator) animatorMap.get(ANIMATOR_CAMERA_GPS_BEARING);
    if (animator == null) {
      return;
    }

    float currentTargetBearing = animator.getTarget();
    currentTargetBearing = checkGpsNorth(isGpsNorth, currentTargetBearing);
    float previousCameraBearing = (float) currentCameraPosition.bearing;
    float normalizedCameraBearing = Utils.shortestRotation(currentTargetBearing, previousCameraBearing);
    createNewAnimator(ANIMATOR_CAMERA_GPS_BEARING,
      new CameraGpsBearingAnimator(previousCameraBearing, normalizedCameraBearing, cameraListeners));
  }

  private void resetCameraCompassAnimation(CameraPosition currentCameraPosition) {
    CameraCompassBearingAnimator animator =
      (CameraCompassBearingAnimator) animatorMap.get(ANIMATOR_CAMERA_COMPASS_BEARING);
    if (animator == null) {
      return;
    }

    float currentTargetBearing = animator.getTarget();
    float previousCameraBearing = (float) currentCameraPosition.bearing;
    float normalizedCameraBearing = Utils.shortestRotation(currentTargetBearing, previousCameraBearing);
    createNewAnimator(ANIMATOR_CAMERA_COMPASS_BEARING,
      new CameraCompassBearingAnimator(previousCameraBearing, normalizedCameraBearing, cameraListeners));
  }

  private void createNewAnimator(@PluginAnimator.Type int animatorType, PluginAnimator animator) {
    cancelAnimator(animatorType);
    animatorMap.put(animatorType, animator);
  }

  void cancelZoomAnimation() {
    cancelAnimator(ANIMATOR_ZOOM);
  }

  void cancelTiltAnimation() {
    cancelAnimator(ANIMATOR_TILT);
  }

  void cancelAllAnimations() {
    for (@PluginAnimator.Type int animatorType : animatorMap.keySet()) {
      cancelAnimator(animatorType);
    }
  }

  private void cancelAnimator(@PluginAnimator.Type int animatorType) {
    PluginAnimator animator = animatorMap.get(animatorType);
    if (animator != null) {
      animator.cancel();
      animator.removeAllUpdateListeners();
      animator.removeAllListeners();
      animatorMap.put(animatorType, null);
    }
  }
}
