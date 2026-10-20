import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/geo.dart';
import '../../core/models.dart';

const _background = Color(0xFFF7F8F2);
const _teal = Color(0xFF245B50);
const _text = Color(0xFF1E2925);
const _muted = Color(0xFF737C76);
const _mint = Color(0xFFDEECE3);
const _green = Color(0xFF13C881);

/// The scan entry screen. Restaurant discovery starts only after its CTA.
class ScanHome extends StatelessWidget {
  const ScanHome({
    super.key,
    required this.displayName,
    required this.onScan,
    this.busy = false,
    this.isDemo = true,
    this.position,
    this.restaurants = const [],
  });

  final String displayName;
  final VoidCallback onScan;
  final bool busy;
  final bool isDemo;
  final GeoPoint? position;
  final List<Restaurant> restaurants;

  @override
  Widget build(BuildContext context) => ColoredBox(
    color: _background,
    child: LayoutBuilder(
      builder: (context, constraints) {
        final availableHeight = constraints.hasBoundedHeight
            ? constraints.maxHeight
            : 700.0;
        final contentHeight = math.max(560.0, availableHeight);
        final contents = SizedBox(
          height: contentHeight,
          child: Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 600),
              child: Padding(
                padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      'Hello, ${displayName.trim().isEmpty ? 'Guest' : displayName.trim()}',
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(
                        color: _text,
                        fontSize: 30,
                        height: 1.16,
                        fontWeight: FontWeight.w800,
                        letterSpacing: -.8,
                      ),
                    ),
                    const SizedBox(height: 10),
                    const Text(
                      'Let’s find nearby restaurants using AR navigation.',
                      style: TextStyle(
                        color: _muted,
                        fontSize: 16,
                        height: 1.5,
                        letterSpacing: .1,
                      ),
                    ),
                    Expanded(
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: 20),
                        child: _DiscoveryIllustration(
                          position: position,
                          restaurants: restaurants,
                          isDemo: isDemo,
                        ),
                      ),
                    ),
                    if (isDemo) ...[
                      const Text(
                        'Demo · sample places',
                        textAlign: TextAlign.center,
                        style: TextStyle(color: _muted, fontSize: 11),
                      ),
                      const SizedBox(height: 12),
                    ],
                    SizedBox(
                      width: double.infinity,
                      height: 84,
                      child: FilledButton(
                        key: const ValueKey('scan-restaurants'),
                        onPressed: busy ? null : onScan,
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF080B09),
                          disabledBackgroundColor: const Color(0xFF25322B),
                          foregroundColor: Colors.white,
                          disabledForegroundColor: Colors.white70,
                          padding: const EdgeInsets.symmetric(
                            horizontal: 18,
                            vertical: 13,
                          ),
                          elevation: 4,
                          shadowColor: Colors.black26,
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(24),
                          ),
                        ),
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            if (busy)
                              const SizedBox.square(
                                dimension: 24,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                  color: _green,
                                ),
                              )
                            else
                              const Icon(
                                Icons.camera_alt_rounded,
                                color: _green,
                                size: 27,
                              ),
                            const SizedBox(height: 7),
                            Text(
                              busy
                                  ? 'Locating restaurants…'
                                  : 'Scan for Restaurant',
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 16,
                                fontWeight: FontWeight.w700,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        );
        if (availableHeight < 560 || !constraints.hasBoundedHeight) {
          return SingleChildScrollView(child: contents);
        }
        return contents;
      },
    ),
  );
}

class _DiscoveryIllustration extends StatelessWidget {
  const _DiscoveryIllustration({
    required this.position,
    required this.restaurants,
    required this.isDemo,
  });

  final GeoPoint? position;
  final List<Restaurant> restaurants;
  final bool isDemo;

  @override
  Widget build(BuildContext context) {
    final hasPositions = position?.isValid == true && restaurants.isNotEmpty;
    return Semantics(
      excludeSemantics: true,
      label: hasPositions
          ? '${isDemo ? 'Sample' : 'Nearby'} restaurant positions within one kilometre.'
          : 'Restaurant discovery illustration. Scan to find restaurants within one kilometre.',
      child: LayoutBuilder(
        builder: (context, constraints) {
          final dimension = math.min(
            340.0,
            math.min(constraints.maxWidth * .98, constraints.maxHeight),
          );
          final centerDiameter = math.min(98.0, dimension * .31);
          return Center(
            child: SizedBox.square(
              dimension: dimension,
              child: Stack(
                alignment: Alignment.center,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _DiscoveryPainter(
                        position: hasPositions ? position : null,
                        restaurants: hasPositions ? restaurants : const [],
                      ),
                    ),
                  ),
                  Container(
                    width: centerDiameter,
                    height: centerDiameter,
                    padding: EdgeInsets.all(centerDiameter * .14),
                    decoration: BoxDecoration(
                      color: Colors.white,
                      shape: BoxShape.circle,
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withAlpha(25),
                          blurRadius: 18,
                          offset: const Offset(0, 5),
                        ),
                      ],
                    ),
                    child: DecoratedBox(
                      decoration: const BoxDecoration(
                        color: _mint,
                        shape: BoxShape.circle,
                      ),
                      child: Icon(
                        Icons.location_on_rounded,
                        color: _teal,
                        size: centerDiameter * .36,
                      ),
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

class _DiscoveryPainter extends CustomPainter {
  const _DiscoveryPainter({required this.position, required this.restaurants});

  final GeoPoint? position;
  final List<Restaurant> restaurants;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final radius = math.min(size.width, size.height) / 2 - 5;
    final ring = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1.1;
    for (var i = 1; i <= 4; i++) {
      ring.color = _mint.withAlpha(65 + i * 28);
      canvas.drawCircle(center, radius * i / 4, ring);
    }
    final origin = position;
    if (origin != null) {
      var index = 0;
      for (final restaurant in restaurants) {
        final distance = distanceMeters(origin, restaurant.location);
        if (distance > 1000) continue;
        final angle =
            bearingDegrees(origin, restaurant.location) * math.pi / 180;
        final point = Offset(
          center.dx + math.sin(angle) * radius * distance / 1000,
          center.dy - math.cos(angle) * radius * distance / 1000,
        );
        canvas.drawCircle(
          point,
          4.3,
          Paint()..color = index++ % 2 == 0 ? _teal : _green,
        );
      }
      return;
    }
    // Decoration only; its semantics explicitly identify this as an illustration.
    canvas.drawCircle(
      center + Offset(radius * .55, -radius * .57),
      4.3,
      Paint()..color = _text,
    );
    canvas.drawCircle(
      center + Offset(-radius * .56, radius * .35),
      5.5,
      Paint()..color = _green,
    );
    canvas.drawCircle(
      center + Offset(radius * .75, radius * .03),
      3.3,
      Paint()..color = const Color(0xFFC7D1C9),
    );
  }

  @override
  bool shouldRepaint(covariant _DiscoveryPainter oldDelegate) =>
      oldDelegate.position != position ||
      oldDelegate.restaurants != restaurants;
}
