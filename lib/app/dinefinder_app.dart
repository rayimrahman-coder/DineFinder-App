import 'dart:async';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import '../core/dinefinder_core.dart';
import '../device/device_session.dart';
import 'widgets/camera_stage.dart';
import 'widgets/discovery_widgets.dart';

const _ink = Color(0xFF123F3A);
const _muted = Color(0xFF6C7A74);
const _paper = Color(0xFFF7F8F3);
const _gold = Color(0xFFF2BD58);

class DineFinderApp extends StatelessWidget {
  const DineFinderApp({
    super.key,
    this.client,
    this.demoRepository = const DemoRestaurantRepository(),
    this.demoRouteService = const DemoRouteService(),
  });

  final SupabaseClient? client;
  final RestaurantRepository demoRepository;
  final RouteService demoRouteService;

  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'DineFinder',
    debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(
        seedColor: _ink,
        primary: _ink,
        secondary: _gold,
        surface: Colors.white,
      ),
      scaffoldBackgroundColor: _paper,
      textTheme: ThemeData.light().textTheme.apply(
        bodyColor: _ink,
        displayColor: _ink,
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          backgroundColor: _ink,
          foregroundColor: Colors.white,
          padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 18),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFE0E7DF)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: const BorderSide(color: Color(0xFFE0E7DF)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        backgroundColor: Colors.white,
        indicatorColor: const Color(0xFFE3EDE5),
        labelTextStyle: WidgetStateProperty.all(
          const TextStyle(
            color: _ink,
            fontWeight: FontWeight.w600,
            fontSize: 12,
          ),
        ),
      ),
    ),
    home: _Home(
      client: client,
      demoRepository: demoRepository,
      demoRouteService: demoRouteService,
    ),
  );
}

class _Home extends StatefulWidget {
  const _Home({
    required this.client,
    required this.demoRepository,
    required this.demoRouteService,
  });
  final SupabaseClient? client;
  final RestaurantRepository demoRepository;
  final RouteService demoRouteService;

  @override
  State<_Home> createState() => _HomeState();
}

class _HomeState extends State<_Home> {
  late final DeviceSession _device;
  final _search = TextEditingController();
  var _live = false;
  var _loading = true;
  var _routing = false;
  var _tab = 0;
  var _radius = 1000.0;
  var _cuisine = 'All';
  var _demoHeading = 35.0;
  var _demoProgress = 0.0;
  var _loadGeneration = 0;
  var _routeGeneration = 0;
  List<Restaurant> _restaurants = [];
  String? _error;
  Restaurant? _destination;
  RoutePlan? _route;
  GeoPoint? _lastQueryCenter;
  DateTime? _lastQueryTime;

  GeoPoint? get _position => _live
      ? _device.position
      : (_route == null ? demoOrigin : _pointOnRoute(_route!, _demoProgress));
  double? get _heading => _live ? _device.heading : _demoHeading;
  RouteProgress? get _progress => _route != null && _position != null
      ? nearestRouteProgress(_position!, _route!)
      : null;

  @override
  void initState() {
    super.initState();
    _device = DeviceSession()..addListener(_sensorChanged);
    unawaited(_reload());
  }

  @override
  void dispose() {
    _device.removeListener(_sensorChanged);
    _device.dispose();
    _search.dispose();
    super.dispose();
  }

  void _sensorChanged() {
    if (!mounted || !_live) return;
    setState(() {});
    final point = _device.position;
    if (point == null || _loading) return;
    final changed =
        _lastQueryCenter == null ||
        distanceMeters(_lastQueryCenter!, point) >= 60;
    final elapsed =
        _lastQueryTime == null ||
        DateTime.now().difference(_lastQueryTime!).inSeconds >= 15;
    if (changed && elapsed) unawaited(_reload());
  }

  Future<void> _reload() async {
    final center = _position;
    if (center == null) return;
    final generation = ++_loadGeneration;
    setState(() {
      _loading = true;
      _error = null;
    });
    _lastQueryCenter = center;
    _lastQueryTime = DateTime.now();
    try {
      final repository = _live
          ? SupabaseRestaurantRepository(widget.client!)
          : widget.demoRepository;
      final places = await repository
          .nearby(center, _radius)
          .timeout(const Duration(seconds: 20));
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _restaurants = places;
        _loading = false;
      });
    } on Object {
      if (!mounted || generation != _loadGeneration) return;
      setState(() {
        _loading = false;
        _error =
            'We couldn’t load nearby restaurants. Check your connection and try again.';
      });
    }
  }

  Future<void> _changeMode(bool live) async {
    if (live == _live) return;
    if (live && widget.client == null) {
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Live discovery is not connected'),
          content: const Text(
            'This version is running with sample places. '
            'Live discovery needs a restaurant database connection. '
            'You can explore every screen in the demo.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context),
              child: const Text('Keep exploring'),
            ),
          ],
        ),
      );
      return;
    }
    if (live) {
      final proceed = await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Find good food around you'),
          content: const Text(
            'DineFinder uses your foreground location to find restaurants '
            'and the camera and compass to show their directions. '
            'Camera images stay on your device. You can return to the demo at any time.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Later'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Continue'),
            ),
          ],
        ),
      );
      if (proceed != true || !mounted) return;
    }
    if (!mounted) return;
    _loadGeneration++;
    _routeGeneration++;
    setState(() {
      _live = live;
      _restaurants = [];
      _loading = false;
      _routing = false;
      _route = null;
      _destination = null;
      _demoProgress = 0;
      _cuisine = 'All';
      _error = null;
      _lastQueryCenter = null;
      _lastQueryTime = null;
    });
    if (live) {
      await _device.start();
      if (mounted && _live && !_loading) await _reload();
    } else {
      await _device.stop();
      if (mounted && !_live) await _reload();
    }
  }

  Future<void> _navigate(Restaurant restaurant) async {
    if (_position == null) return;
    final generation = ++_routeGeneration;
    final live = _live;
    setState(() {
      _routing = true;
      _error = null;
    });
    try {
      if (live &&
          (_device.lastFixAt == null ||
              DateTime.now().difference(_device.lastFixAt!).inSeconds > 45)) {
        await _device.refreshPosition();
      }
      if (!mounted || generation != _routeGeneration || live != _live) return;
      final start = _position;
      if (start == null ||
          (live &&
              (_device.lastFixAt == null ||
                  DateTime.now().difference(_device.lastFixAt!).inSeconds >
                      45))) {
        setState(() {
          _routing = false;
          _error =
              'Your location is out of date. Wait for a new GPS fix before navigating.';
        });
        return;
      }
      final service = live
          ? SupabaseRouteService(widget.client!)
          : widget.demoRouteService;
      final route = await service
          .route(start, restaurant.location)
          .timeout(const Duration(seconds: 30));
      if (!mounted || generation != _routeGeneration) return;
      setState(() {
        _route = route;
        _destination = restaurant;
        _demoProgress = 0;
        _routing = false;
        _tab = 2;
        if (!_live) {
          _demoHeading = bearingDegrees(route.points.first, route.points[1]);
        }
      });
    } on Object {
      if (!mounted || generation != _routeGeneration) return;
      setState(() {
        _routing = false;
        _error =
            'Walking directions are unavailable. Please try again. '
            'Live routes need an enabled routing connection.';
      });
    }
  }

  void _finishRoute() {
    _routeGeneration++;
    setState(() {
      _route = null;
      _destination = null;
      _demoProgress = 0;
      _tab = 0;
      _routing = false;
    });
    unawaited(_reload());
  }

  List<Restaurant> get _filtered {
    final query = _search.text.trim().toLowerCase();
    final position = _position;
    if (position == null) return [];
    return _restaurants
        .where(
          (restaurant) =>
              (_cuisine == 'All' || restaurant.cuisine == _cuisine) &&
              (query.isEmpty ||
                  '${restaurant.name} ${restaurant.cuisine} ${restaurant.tags.join(' ')}'
                      .toLowerCase()
                      .contains(query)) &&
              distanceMeters(position, restaurant.location) <= _radius,
        )
        .toList()
      ..sort(
        (a, b) => distanceMeters(
          position,
          a.location,
        ).compareTo(distanceMeters(position, b.location)),
      );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    body: SafeArea(
      bottom: false,
      child: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 1440),
          child: Column(
            children: [
              _header(),
              if (_routing)
                const LinearProgressIndicator(
                  semanticsLabel: 'Finding walking directions',
                ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 0, 20, 10),
                  child: _Notice(
                    text: _error!,
                    icon: Icons.wifi_off_rounded,
                    action: 'Dismiss',
                    onAction: () => setState(() => _error = null),
                  ),
                ),
              Expanded(
                child: switch (_tab) {
                  0 => _explore(),
                  1 => _cameraPage(),
                  _ => _journey(),
                },
              ),
            ],
          ),
        ),
      ),
    ),
    bottomNavigationBar: NavigationBar(
      selectedIndex: _tab,
      onDestinationSelected: (index) => setState(() => _tab = index),
      destinations: const [
        NavigationDestination(
          icon: Icon(Icons.explore_outlined),
          selectedIcon: Icon(Icons.explore),
          label: 'Discover',
        ),
        NavigationDestination(
          icon: Icon(Icons.view_in_ar_outlined),
          selectedIcon: Icon(Icons.view_in_ar),
          label: 'Camera',
        ),
        NavigationDestination(
          icon: Icon(Icons.route_outlined),
          selectedIcon: Icon(Icons.route),
          label: 'Journey',
        ),
      ],
    ),
  );

  Widget _header() => Padding(
    padding: const EdgeInsets.fromLTRB(22, 18, 22, 16),
    child: Row(
      children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(
            color: _ink,
            borderRadius: BorderRadius.circular(14),
          ),
          child: const Icon(Icons.ramen_dining_rounded, color: _gold, size: 25),
        ),
        const SizedBox(width: 10),
        const Expanded(
          child: Text(
            'DineFinder',
            style: TextStyle(
              fontSize: 21,
              fontWeight: FontWeight.w800,
              letterSpacing: -.8,
            ),
          ),
        ),
        SegmentedButton<bool>(
          showSelectedIcon: false,
          style: SegmentedButton.styleFrom(
            visualDensity: VisualDensity.compact,
            textStyle: const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w600,
            ),
          ),
          segments: const [
            ButtonSegment(value: false, label: Text('Demo')),
            ButtonSegment(value: true, label: Text('Live')),
          ],
          selected: {_live},
          onSelectionChanged: (value) => unawaited(_changeMode(value.first)),
        ),
      ],
    ),
  );

  Widget _explore() {
    final position = _position;
    final places = _filtered;
    final cuisineNames = _restaurants.map((r) => r.cuisine).toSet().toList()
      ..sort();
    final cuisines = ['All', ...cuisineNames];
    if (!cuisines.contains(_cuisine)) cuisines.add(_cuisine);
    return LayoutBuilder(
      builder: (context, bounds) {
        final wide = bounds.maxWidth >= 900;
        final list = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'A little curiosity.\nA great next bite.',
              style: TextStyle(
                fontSize: 34,
                height: 1.10,
                letterSpacing: -1.5,
                fontWeight: FontWeight.w800,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              _live
                  ? 'Discover the places around you.'
                  : 'Explore sample places around Kuala Lumpur.',
              style: const TextStyle(color: _muted, height: 1.5),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: _search,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: 'Find your next craving',
                prefixIcon: const Icon(Icons.search_rounded),
                suffixIcon: _search.text.isEmpty
                    ? null
                    : IconButton(
                        tooltip: 'Clear search',
                        icon: const Icon(Icons.close),
                        onPressed: () => setState(_search.clear),
                      ),
              ),
            ),
            const SizedBox(height: 14),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Row(
                children: cuisines
                    .map(
                      (cuisine) => Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: ChoiceChip(
                          label: Text(cuisine),
                          selected: _cuisine == cuisine,
                          showCheckmark: false,
                          selectedColor: _ink,
                          labelStyle: TextStyle(
                            color: _cuisine == cuisine ? Colors.white : _ink,
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                          ),
                          side: BorderSide(
                            color: _cuisine == cuisine
                                ? _ink
                                : const Color(0xFFE0E7DF),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(22),
                          ),
                          onSelected: (_) => setState(() => _cuisine = cuisine),
                        ),
                      ),
                    )
                    .toList(),
              ),
            ),
            const SizedBox(height: 20),
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${places.length} places to discover',
                    style: const TextStyle(
                      fontWeight: FontWeight.w700,
                      fontSize: 16,
                    ),
                  ),
                ),
                DropdownButton<double>(
                  value: _radius,
                  underline: const SizedBox(),
                  borderRadius: BorderRadius.circular(14),
                  items: const [
                    DropdownMenuItem(value: 500, child: Text('500 m')),
                    DropdownMenuItem(value: 1000, child: Text('1 km')),
                    DropdownMenuItem(value: 2000, child: Text('2 km')),
                  ],
                  onChanged: (radius) {
                    if (radius == null) return;
                    setState(() => _radius = radius);
                    unawaited(_reload());
                  },
                ),
                IconButton(
                  tooltip: 'Refresh restaurants',
                  onPressed: _loading ? null : _reload,
                  icon: const Icon(Icons.refresh_rounded, size: 21),
                ),
              ],
            ),
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 22),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (position == null) _locationWaiting(),
            if (!_loading && places.isEmpty && position != null)
              _emptyRestaurants(),
            for (final restaurant in places)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: RestaurantCard(
                  restaurant: restaurant,
                  distance: distanceMeters(position!, restaurant.location),
                  selected: _destination?.id == restaurant.id,
                  onTap: () => _showRestaurant(restaurant),
                ),
              ),
            if (!_live)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  'DEMO COLLECTION · Sample names, ratings, and places.',
                  style: TextStyle(
                    color: _muted,
                    fontSize: 10,
                    letterSpacing: .6,
                  ),
                ),
              ),
          ],
        );
        final radar = position == null
            ? Center(child: _locationWaiting())
            : NearbyRadar(
                center: position,
                restaurants: places,
                radiusMeters: _radius,
                destination: _destination,
                isDemo: !_live,
                onSelect: _showRestaurant,
              );
        if (wide) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(26, 10, 26, 24),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(flex: 6, child: radar),
                const SizedBox(width: 32),
                Expanded(
                  flex: 5,
                  child: SingleChildScrollView(
                    padding: const EdgeInsets.only(right: 8, bottom: 20),
                    child: list,
                  ),
                ),
              ],
            ),
          );
        }
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          child: Column(
            children: [
              SizedBox(height: 260, child: radar),
              const SizedBox(height: 24),
              list,
            ],
          ),
        );
      },
    );
  }

  Widget _emptyRestaurants() => Padding(
    padding: const EdgeInsets.symmetric(vertical: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'A new discovery is a little further away.',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 8),
        const Text(
          'Try a larger radius or a different search.',
          style: TextStyle(color: _muted),
        ),
        TextButton(
          onPressed: () {
            setState(() {
              _search.clear();
              _cuisine = 'All';
              _radius = 2000;
            });
            unawaited(_reload());
          },
          child: const Text('Reset filters'),
        ),
      ],
    ),
  );

  Widget _locationWaiting() => Padding(
    padding: const EdgeInsets.all(20),
    child: Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(
          _device.busy ? Icons.gps_not_fixed : Icons.location_off_outlined,
          size: 42,
          color: _muted,
        ),
        const SizedBox(height: 14),
        Text(
          _device.locationError ?? 'Finding your location…',
          textAlign: TextAlign.center,
          style: const TextStyle(color: _muted, height: 1.5),
        ),
        const SizedBox(height: 14),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          alignment: WrapAlignment.center,
          children: [
            OutlinedButton(
              onPressed: _device.busy ? null : () => unawaited(_device.start()),
              child: const Text('Retry sensors'),
            ),
            TextButton(
              onPressed: () => unawaited(_device.openSettings()),
              child: const Text('App settings'),
            ),
            TextButton(
              onPressed: () => unawaited(_device.openLocationSettings()),
              child: const Text('Location settings'),
            ),
          ],
        ),
      ],
    ),
  );

  Widget _cameraPage() => SingleChildScrollView(
    padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Follow your appetite.',
          style: TextStyle(
            fontSize: 30,
            fontWeight: FontWeight.w800,
            letterSpacing: -1,
          ),
        ),
        const SizedBox(height: 8),
        const Text(
          'Turn your phone to discover what’s in front of you.',
          style: TextStyle(color: _muted, height: 1.4),
        ),
        const SizedBox(height: 18),
        SizedBox(height: 480, child: _camera()),
        if (!_live) _demoControls(),
        if (_live) _sensorNotice(),
        const SizedBox(height: 8),
        const Text(
          'Stay aware of traffic and your surroundings. '
          'The overlay estimates a bearing and does not identify a safe walking path.',
          style: TextStyle(color: _muted, fontSize: 12, height: 1.5),
        ),
      ],
    ),
  );

  Widget _camera() {
    final position = _position;
    if (position == null) return _locationWaiting();
    final progress = _progress;
    return CameraStage(
      position: position,
      heading: _heading,
      restaurants: _filtered,
      isDemo: !_live,
      onSelect: _showRestaurant,
      controller: _device.cameraReady ? _device.cameraController : null,
      cameraError: _device.cameraError,
      target: progress?.targetPoint,
      navigationLabel: progress?.instruction?.text,
      arrived: progress?.hasArrived ?? false,
      offRoute: progress?.isOffRoute ?? false,
    );
  }

  Widget _demoControls() => Container(
    margin: const EdgeInsets.only(top: 14),
    padding: const EdgeInsets.fromLTRB(16, 16, 16, 6),
    decoration: BoxDecoration(
      color: const Color(0xFFFFF0CF),
      borderRadius: BorderRadius.circular(18),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Row(
          children: [
            Icon(Icons.tune, size: 17),
            SizedBox(width: 8),
            Text('Try the view', style: TextStyle(fontWeight: FontWeight.w700)),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            const Text('Heading', style: TextStyle(fontSize: 12)),
            Expanded(
              child: Slider(
                value: _demoHeading,
                max: 359,
                label: '${_demoHeading.round()}°',
                onChanged: (value) => setState(() => _demoHeading = value),
              ),
            ),
            SizedBox(
              width: 38,
              child: Text(
                '${_demoHeading.round()}°',
                style: const TextStyle(fontSize: 12),
              ),
            ),
          ],
        ),
        if (_route != null)
          Row(
            children: [
              const Text('Walk', style: TextStyle(fontSize: 12)),
              Expanded(
                child: Slider(
                  key: const ValueKey('demo-walk'),
                  value: _demoProgress,
                  label: '${(_demoProgress * 100).round()}%',
                  onChanged: (value) => setState(() => _demoProgress = value),
                ),
              ),
              Text(
                '${(_demoProgress * 100).round()}%',
                style: const TextStyle(fontSize: 12),
              ),
            ],
          ),
        const Padding(
          padding: EdgeInsets.only(bottom: 10),
          child: Text(
            'Simulation only. Demo paths are not real walking directions.',
            style: TextStyle(fontSize: 11, color: _muted),
          ),
        ),
      ],
    ),
  );

  Widget _sensorNotice() {
    final issues = <String>[
      if (_device.accuracyMeters != null && _device.accuracyMeters! > 35)
        'GPS accuracy is about ${_device.accuracyMeters!.round()} m. Directions may shift.',
      if (_device.lastFixAt != null &&
          DateTime.now().difference(_device.lastFixAt!).inSeconds > 45)
        'Waiting for an updated GPS fix.',
      if (_device.headingError != null) _device.headingError!,
      if (_device.cameraError != null) _device.cameraError!,
      if (_device.locationError != null) _device.locationError!,
    ];
    if (issues.isEmpty) return const SizedBox(height: 12);
    return Padding(
      padding: const EdgeInsets.only(top: 14),
      child: _Notice(
        text: issues.join(' '),
        icon: Icons.sensors_off,
        action: _device.busy ? 'Connecting…' : 'Retry',
        onAction: _device.busy ? null : () => unawaited(_device.start()),
      ),
    );
  }

  Widget _journey() {
    final route = _route;
    final destination = _destination;
    final progress = _progress;
    if (route != null && destination != null && progress == null) {
      return Center(child: _locationWaiting());
    }
    if (route == null || destination == null || progress == null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(30),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.route_rounded, size: 62, color: _ink),
              const SizedBox(height: 22),
              const Text(
                'Every great meal starts somewhere.',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 25, fontWeight: FontWeight.w700),
              ),
              const SizedBox(height: 12),
              const Text(
                'Choose a restaurant and tap Navigate to start your journey.',
                textAlign: TextAlign.center,
                style: TextStyle(color: _muted, height: 1.5),
              ),
              const SizedBox(height: 22),
              FilledButton(
                onPressed: () => setState(() => _tab = 0),
                child: const Text('Discover restaurants'),
              ),
            ],
          ),
        ),
      );
    }
    final eta = route.distanceMeters == 0
        ? 0
        : (route.durationSeconds *
                  progress.remainingMeters /
                  route.distanceMeters /
                  60)
              .ceil();
    final details = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            RestaurantArt(destination, size: 54),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    progress.hasArrived
                        ? 'Your next bite is here.'
                        : 'On your way to',
                    style: const TextStyle(color: _muted, fontSize: 12),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    destination.name,
                    style: const TextStyle(
                      fontSize: 23,
                      fontWeight: FontWeight.w800,
                      letterSpacing: -.6,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        const SizedBox(height: 20),
        Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: _ink,
            borderRadius: BorderRadius.circular(20),
          ),
          child: Row(
            children: [
              const Icon(Icons.directions_walk, color: _gold, size: 29),
              const SizedBox(width: 14),
              Expanded(
                child: Text(
                  progress.hasArrived
                      ? 'Route complete'
                      : '${math.max(1, eta)} min walk',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 23,
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              Text(
                _distance(progress.remainingMeters),
                style: const TextStyle(color: Colors.white70),
              ),
            ],
          ),
        ),
        const SizedBox(height: 16),
        if (route.isDemo)
          const _Notice(
            text:
                'Simulated journey. This path does not represent streets or walkable access.',
            icon: Icons.science_outlined,
          ),
        if (progress.isOffRoute && !progress.hasArrived)
          Padding(
            padding: const EdgeInsets.only(top: 10),
            child: _Notice(
              text:
                  'You’re away from the route. Recalculate before following the next direction.',
              icon: Icons.alt_route,
              action: _routing ? 'Calculating…' : 'Recalculate',
              onAction: _routing
                  ? null
                  : () => unawaited(_navigate(destination)),
            ),
          ),
        if (_live) _sensorNotice(),
        const SizedBox(height: 22),
        const Text(
          'YOUR WALKING DIRECTIONS',
          style: TextStyle(
            fontSize: 10,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.4,
            color: _muted,
          ),
        ),
        const SizedBox(height: 12),
        for (var i = 0; i < route.instructions.length; i++)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                CircleAvatar(
                  radius: 14,
                  backgroundColor: const Color(0xFFE2EBE2),
                  child: Text(
                    '${i + 1}',
                    style: const TextStyle(fontSize: 11, color: _ink),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    route.instructions[i].text,
                    style: const TextStyle(height: 1.4, fontSize: 13),
                  ),
                ),
                const SizedBox(width: 10),
                Text(
                  _distance(route.instructions[i].distanceMeters),
                  style: const TextStyle(color: _muted, fontSize: 12),
                ),
              ],
            ),
          ),
        const SizedBox(height: 20),
        SizedBox(
          width: double.infinity,
          child: FilledButton.icon(
            onPressed: _finishRoute,
            icon: Icon(progress.hasArrived ? Icons.check : Icons.close),
            label: Text(
              progress.hasArrived ? 'Finish journey' : 'End navigation',
            ),
          ),
        ),
        if (!_live) _demoControls(),
      ],
    );
    return LayoutBuilder(
      builder: (context, bounds) {
        if (bounds.maxWidth >= 900) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Expanded(flex: 6, child: _camera()),
                const SizedBox(width: 28),
                Expanded(flex: 5, child: SingleChildScrollView(child: details)),
              ],
            ),
          );
        }
        return SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
          child: Column(
            children: [
              SizedBox(height: 420, child: _camera()),
              const SizedBox(height: 22),
              details,
            ],
          ),
        );
      },
    );
  }

  Future<void> _showRestaurant(Restaurant place) async {
    if (_routing) return;
    final point = _position;
    if (point == null) return;
    final navigate = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      constraints: const BoxConstraints(maxWidth: 650),
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
      builder: (context) => SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(24, 0, 24, 24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  RestaurantArt(place, size: 90),
                  const SizedBox(width: 20),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          place.cuisine.toUpperCase(),
                          style: const TextStyle(
                            color: _muted,
                            letterSpacing: 1.2,
                            fontSize: 10,
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                        const SizedBox(height: 7),
                        Text(
                          place.name,
                          style: const TextStyle(
                            fontSize: 26,
                            fontWeight: FontWeight.w800,
                            letterSpacing: -.7,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '★ ${place.rating.toStringAsFixed(1)}   ·   ${place.priceLabel}   ·   '
                          '${_distance(distanceMeters(point, place.location))}',
                          style: const TextStyle(fontSize: 13),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 24),
              Text(
                place.description,
                style: const TextStyle(
                  color: _muted,
                  height: 1.65,
                  fontSize: 14,
                ),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: place.tags
                    .map(
                      (tag) => Chip(
                        label: Text(tag, style: const TextStyle(fontSize: 11)),
                        side: BorderSide.none,
                        backgroundColor: const Color(0xFFEDF2E9),
                      ),
                    )
                    .toList(),
              ),
              const SizedBox(height: 14),
              _ProfileLine(
                icon: Icons.location_on_outlined,
                text: place.address,
              ),
              const SizedBox(height: 12),
              _ProfileLine(icon: Icons.schedule, text: place.openingHours),
              if (!_live)
                const Padding(
                  padding: EdgeInsets.only(top: 18),
                  child: Text(
                    'Sample restaurant · fictional listing for the demo',
                    style: TextStyle(color: _muted, fontSize: 11),
                  ),
                ),
              const SizedBox(height: 24),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  onPressed: _routing
                      ? null
                      : () => Navigator.pop(context, true),
                  icon: const Icon(Icons.near_me_rounded, size: 19),
                  label: Text(_routing ? 'Calculating route…' : 'Navigate'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
    if (navigate == true && mounted) await _navigate(place);
  }
}

class _Notice extends StatelessWidget {
  const _Notice({
    required this.text,
    required this.icon,
    this.action,
    this.onAction,
  });
  final String text;
  final IconData icon;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: const Color(0xFFFFF0D5),
      borderRadius: BorderRadius.circular(14),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, color: const Color(0xFF866629), size: 19),
        const SizedBox(width: 10),
        Expanded(
          child: Text(
            text,
            style: const TextStyle(
              color: Color(0xFF715B32),
              fontSize: 12,
              height: 1.5,
            ),
          ),
        ),
        if (action != null)
          TextButton(onPressed: onAction, child: Text(action!)),
      ],
    ),
  );
}

class _ProfileLine extends StatelessWidget {
  const _ProfileLine({required this.icon, required this.text});
  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Icon(icon, size: 19, color: _muted),
      const SizedBox(width: 12),
      Expanded(
        child: Text(text, style: const TextStyle(fontSize: 13, height: 1.5)),
      ),
    ],
  );
}

String _distance(double metres) => metres >= 1000
    ? '${(metres / 1000).toStringAsFixed(1)} km'
    : '${metres.round()} m';

GeoPoint _pointOnRoute(RoutePlan route, double fraction) {
  final lengths = [
    for (var i = 0; i < route.points.length - 1; i++)
      distanceMeters(route.points[i], route.points[i + 1]),
  ];
  var remaining = lengths.fold(0.0, (sum, value) => sum + value) * fraction;
  for (var i = 0; i < lengths.length; i++) {
    if (remaining <= lengths[i] && lengths[i] > 0) {
      final t = remaining / lengths[i];
      final a = route.points[i];
      final b = route.points[i + 1];
      return GeoPoint(
        a.latitude + (b.latitude - a.latitude) * t,
        a.longitude + (b.longitude - a.longitude) * t,
      );
    }
    remaining -= lengths[i];
  }
  return route.points.last;
}
