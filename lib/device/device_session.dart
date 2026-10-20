import 'dart:async';

import 'package:camera/camera.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_compass/flutter_compass.dart';
import 'package:geolocator/geolocator.dart';

import '../core/dinefinder_core.dart';

/// Owns foreground sensors only. No synthetic readings enter a live session.
///
/// A session remains opted in while the application is backgrounded, but its
/// resources are released. It reconnects when the application resumes.
class DeviceSession extends ChangeNotifier with WidgetsBindingObserver {
  static const _noCompassMessage =
      'Live compass unavailable on this device. Use the written walking '
      'directions; the demo has a heading slider.';

  DeviceSession() {
    final state = WidgetsBinding.instance.lifecycleState;
    _foreground = state == null || state == AppLifecycleState.resumed;
    WidgetsBinding.instance.addObserver(this);
  }

  GeoPoint? position;
  double? heading;
  double? accuracyMeters;
  DateTime? lastFixAt;
  CameraController? cameraController;
  String? locationError;
  String? cameraError;
  String? headingError;

  bool _busy = false;
  bool _running = false;
  bool _foreground = true;
  bool _disposed = false;
  int _generation = 0;
  Future<void>? _activation;
  Future<void>? _cleanup;
  StreamSubscription<Position>? _locationSubscription;
  StreamSubscription<CompassEvent>? _headingSubscription;

  bool get busy => _busy;

  /// Whether the user has opted into live mode, including while suspended.
  bool get running => _running;

  bool get cameraReady {
    final controller = cameraController;
    return controller != null &&
        controller.value.isInitialized &&
        !controller.value.hasError;
  }

  bool _isCurrent(int generation) =>
      !_disposed && _running && _foreground && generation == _generation;

  void _notify() {
    if (!_disposed) notifyListeners();
  }

  /// Requests foreground location permission and starts the available sensors.
  /// A failed sensor does not prevent the other sensors from being used.
  Future<void> start() async {
    if (_disposed) return;
    _running = true;
    locationError = null;
    cameraError = null;
    headingError = null;
    if (!_foreground) {
      _notify();
      return;
    }
    await _restart();
  }

  Future<void> _restart() {
    final generation = ++_generation;
    final previous = _activation;
    final previousCleanup = _cleanup;
    final cleanup = _releaseResources();
    _cleanup = cleanup;
    _busy = true;
    _notify();

    final activation = () async {
      // An invalidated initialization may still be returning from a native
      // permission dialog. Let it dispose its local camera before opening one.
      if (previous != null) await previous;
      if (previousCleanup != null) await previousCleanup;
      await cleanup;
      if (!_isCurrent(generation)) return;
      try {
        _startHeading(generation);
        final canLocate = await _prepareLocation(generation);
        if (!_isCurrent(generation)) return;
        await Future.wait<void>([
          if (canLocate) _startLocation(generation),
          _startCamera(generation),
        ]);
      } finally {
        if (_isCurrent(generation)) {
          _busy = false;
          _notify();
        }
      }
    }();
    _activation = activation;
    return activation;
  }

  Future<bool> _prepareLocation(int generation) async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        if (_isCurrent(generation)) {
          locationError =
              'Location services are off. Enable them in location settings.';
          _notify();
        }
        return false;
      }
      if (!_isCurrent(generation)) return false;
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        if (!_isCurrent(generation)) return false;
        permission = await Geolocator.requestPermission();
      }
      if (!_isCurrent(generation)) return false;
      if (permission == LocationPermission.deniedForever) {
        locationError =
            'Location permission is blocked. Allow location in app settings, '
            'then retry.';
        _notify();
        return false;
      }
      if (permission != LocationPermission.whileInUse &&
          permission != LocationPermission.always) {
        locationError =
            'Location permission was not granted. Allow location to find '
            'nearby restaurants.';
        _notify();
        return false;
      }
      return true;
    } catch (_) {
      if (_isCurrent(generation)) {
        locationError = kIsWeb
            ? 'Location is unavailable. Use a secure HTTPS page and allow '
                  'location in your browser.'
            : 'Unable to access location services. Check device settings and '
                  'retry.';
        _notify();
      }
      return false;
    }
  }

  Future<void> _startLocation(int generation) async {
    const streamSettings = LocationSettings(
      accuracy: LocationAccuracy.high,
      distanceFilter: 3,
    );
    try {
      if (!_isCurrent(generation)) return;
      _locationSubscription =
          Geolocator.getPositionStream(locationSettings: streamSettings).listen(
            (fix) => _acceptPosition(fix, generation),
            onError: (Object error) {
              if (!_isCurrent(generation)) return;
              locationError =
                  'Location updates stopped. Check location services and retry.';
              _notify();
            },
          );
      final fix = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 20),
        ),
      );
      _acceptPosition(fix, generation);
    } on TimeoutException {
      if (_isCurrent(generation) && position == null) {
        locationError =
            'Still waiting for a GPS fix. Move to an open area or retry. '
            'Location updates will continue in the foreground.';
        _notify();
      }
    } catch (_) {
      if (_isCurrent(generation) && position == null) {
        locationError =
            'Unable to obtain your position. Check location access and retry.';
        _notify();
      }
    }
  }

  /// Refreshes a foreground fix, for example before calculating a route.
  ///
  /// Stationary devices need not emit distance-filtered stream events. This
  /// obtains a new reading without restarting the camera or requesting another
  /// permission. Callers can inspect [lastFixAt] after completion to ensure the
  /// provider actually returned a recent position.
  Future<void> refreshPosition() async {
    final generation = _generation;
    if (!_isCurrent(generation)) return;
    try {
      final fix = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 20),
        ),
      );
      _acceptPosition(fix, generation);
    } on TimeoutException {
      if (!_isCurrent(generation)) return;
      locationError =
          'A fresh GPS fix took too long. Move to an open area and retry '
          'navigation.';
      _notify();
    } catch (_) {
      if (!_isCurrent(generation)) return;
      locationError =
          'Unable to refresh your position. Check location services and retry '
          'navigation.';
      _notify();
    }
  }

  void _acceptPosition(Position fix, int generation) {
    if (!_isCurrent(generation)) return;
    if (!fix.latitude.isFinite ||
        !fix.longitude.isFinite ||
        fix.latitude < -90 ||
        fix.latitude > 90 ||
        fix.longitude < -180 ||
        fix.longitude > 180) {
      locationError = 'The device returned an invalid location. Retrying GPS.';
      _notify();
      return;
    }
    // A current-position request and the stream may resolve out of order.
    if (lastFixAt != null && fix.timestamp.isBefore(lastFixAt!)) return;
    position = GeoPoint(fix.latitude, fix.longitude);
    accuracyMeters = fix.accuracy.isFinite && fix.accuracy >= 0
        ? fix.accuracy
        : null;
    lastFixAt = fix.timestamp;
    locationError = null;
    _notify();
  }

  void _startHeading(int generation) {
    try {
      final events = FlutterCompass.events;
      if (events == null) {
        headingError = _noCompassMessage;
        _notify();
        return;
      }
      _headingSubscription = events.listen(
        (event) {
          if (!_isCurrent(generation)) return;
          final degrees = event.heading;
          if (degrees == null || !degrees.isFinite) {
            heading = null;
            headingError = _noCompassMessage;
          } else {
            heading = ((degrees % 360) + 360) % 360;
            headingError = null;
          }
          _notify();
        },
        onError: (Object error) {
          if (!_isCurrent(generation)) return;
          heading = null;
          headingError = _noCompassMessage;
          _notify();
        },
      );
    } catch (_) {
      if (!_isCurrent(generation)) return;
      headingError = _noCompassMessage;
      _notify();
    }
  }

  Future<void> _startCamera(int generation) async {
    CameraController? candidate;
    try {
      final cameras = await availableCameras();
      if (!_isCurrent(generation)) return;
      if (cameras.isEmpty) {
        cameraError = 'No camera is available on this device.';
        _notify();
        return;
      }
      final rearCameras = cameras.where(
        (description) => description.lensDirection == CameraLensDirection.back,
      );
      final selected = rearCameras.isEmpty ? cameras.first : rearCameras.first;
      final controller = CameraController(
        selected,
        ResolutionPreset.medium,
        enableAudio: false,
      );
      candidate = controller;
      await controller.initialize().timeout(const Duration(seconds: 20));
      if (!_isCurrent(generation)) {
        await _disposeCamera(controller);
        return;
      }
      cameraController = controller;
      cameraError = null;
      controller.addListener(() {
        if (!_isCurrent(generation) || cameraController != controller) return;
        if (controller.value.hasError) {
          cameraError = 'Camera access was interrupted. Retry live mode.';
          _notify();
        }
      });
      _notify();
    } on CameraException catch (error) {
      if (candidate != null) await _disposeCamera(candidate);
      if (!_isCurrent(generation)) return;
      cameraError = switch (error.code) {
        'CameraAccessDenied' =>
          'Camera permission was denied. Allow camera access and retry.',
        'CameraAccessDeniedWithoutPrompt' =>
          'Camera access is blocked. Enable it in app settings and retry.',
        'CameraAccessRestricted' =>
          'Camera access is restricted by this device.',
        _ => 'Unable to open the camera. Check camera access and retry.',
      };
      _notify();
    } on TimeoutException {
      if (candidate != null) await _disposeCamera(candidate);
      if (!_isCurrent(generation)) return;
      cameraError = 'The camera took too long to start. Retry camera access.';
      _notify();
    } catch (_) {
      if (candidate != null) await _disposeCamera(candidate);
      if (!_isCurrent(generation)) return;
      cameraError = kIsWeb
          ? 'Camera access is unavailable. Use HTTPS and allow camera access '
                'in your browser.'
          : 'Unable to open the camera. Check camera access and retry.';
      _notify();
    }
  }

  /// Stops foreground sensors and clears readings. Safe to call repeatedly.
  Future<void> stop() async {
    if (_disposed) return;
    _running = false;
    ++_generation;
    _busy = false;
    final active = _activation;
    final cleanup = _releaseResources();
    _cleanup = cleanup;
    _notify();
    await cleanup;
    if (active != null) await active;
  }

  Future<void> _releaseResources() async {
    final location = _locationSubscription;
    final compass = _headingSubscription;
    final camera = cameraController;
    _locationSubscription = null;
    _headingSubscription = null;
    cameraController = null;
    position = null;
    heading = null;
    accuracyMeters = null;
    lastFixAt = null;
    await Future.wait<void>([
      if (location != null) _cancelSubscription(location),
      if (compass != null) _cancelSubscription(compass),
      if (camera != null) _disposeCamera(camera),
    ]);
  }

  Future<void> _cancelSubscription(
    StreamSubscription<dynamic> subscription,
  ) async {
    try {
      await subscription.cancel();
    } catch (_) {
      // Teardown remains best effort if a platform sensor has disconnected.
    }
  }

  Future<void> _disposeCamera(CameraController controller) async {
    try {
      await controller.dispose().timeout(const Duration(seconds: 5));
    } catch (_) {
      // Disposal may race a native disconnect. No stale controller is exposed.
    }
  }

  Future<bool> openSettings() async {
    try {
      return await Geolocator.openAppSettings();
    } catch (_) {
      return false;
    }
  }

  Future<bool> openLocationSettings() async {
    try {
      return await Geolocator.openLocationSettings();
    } catch (_) {
      return false;
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (_disposed) return;
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground) {
      if (_running) unawaited(_resume());
      return;
    }
    ++_generation;
    _busy = false;
    _cleanup = _releaseResources();
    _notify();
  }

  Future<void> _resume() async {
    final cleanup = _cleanup;
    if (cleanup != null) await cleanup;
    if (_disposed || !_running || !_foreground) return;
    await _restart();
  }

  @override
  void dispose() {
    if (_disposed) return;
    _disposed = true;
    _running = false;
    ++_generation;
    WidgetsBinding.instance.removeObserver(this);
    unawaited(_releaseResources());
    super.dispose();
  }
}
