import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../../core/dinefinder_core.dart';

const _ink = Color(0xFF123F3A);
const _muted = Color(0xFF6C7D77);
const _gold = Color(0xFFF2BD58);

String _distanceLabel(double meters) => meters >= 1000
    ? '${(meters / 1000).toStringAsFixed(1)} km'
    : '${meters.round()} m';

/// A bundled, offline illustration that does not depend on remote photo URLs.
class RestaurantArt extends StatelessWidget {
  const RestaurantArt(this.restaurant, {super.key, this.size = 64});

  final Restaurant restaurant;
  final double size;

  @override
  Widget build(BuildContext context) {
    final accent = Color(restaurant.accentHex);
    return ExcludeSemantics(
      child: ClipRRect(
        borderRadius: BorderRadius.circular(size * .26),
        child: SizedBox.square(
          dimension: size,
          child: Stack(
            alignment: Alignment.center,
            children: [
              ColoredBox(
                color: accent.withAlpha(28),
                child: const SizedBox.expand(),
              ),
              Positioned(
                top: -size * .21,
                right: -size * .18,
                child: _ArtCircle(
                  size: size * .86,
                  color: accent.withAlpha(24),
                ),
              ),
              Positioned(
                bottom: -size * .30,
                left: -size * .16,
                child: _ArtCircle(
                  size: size * .81,
                  color: accent.withAlpha(28),
                ),
              ),
              Transform.rotate(
                angle: -.08,
                child: Text(
                  restaurant.emoji,
                  style: TextStyle(fontSize: size * .54, height: 1.2),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ArtCircle extends StatelessWidget {
  const _ArtCircle({required this.size, required this.color});

  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    width: size,
    height: size,
    decoration: BoxDecoration(color: color, shape: BoxShape.circle),
  );
}

class RestaurantCard extends StatelessWidget {
  const RestaurantCard({
    super.key,
    required this.restaurant,
    required this.distance,
    required this.onTap,
    this.selected = false,
  });

  final Restaurant restaurant;
  final double distance;
  final VoidCallback onTap;
  final bool selected;

  @override
  Widget build(BuildContext context) {
    final price = List.filled(
      restaurant.priceLevel.clamp(1, 4).toInt(),
      r'$',
    ).join();
    return Semantics(
      button: true,
      label:
          '${restaurant.name}, ${restaurant.cuisine}, ${_distanceLabel(distance)} away',
      child: Material(
        color: selected ? const Color(0xFFF0F6EF) : Colors.white,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(22),
          side: BorderSide(color: selected ? _ink : const Color(0xFFE8ECE6)),
        ),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                RestaurantArt(restaurant),
                const SizedBox(width: 13),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        restaurant.name,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(
                          color: _ink,
                          fontWeight: FontWeight.w800,
                          fontSize: 16,
                          letterSpacing: -.3,
                        ),
                      ),
                      const SizedBox(height: 5),
                      Text(
                        '${restaurant.cuisine} · $price',
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: _muted, fontSize: 12),
                      ),
                      const SizedBox(height: 7),
                      Row(
                        children: [
                          const Icon(
                            Icons.star_rounded,
                            color: Color(0xFFDDA52E),
                            size: 15,
                          ),
                          const SizedBox(width: 3),
                          Text(
                            restaurant.rating > 0
                                ? restaurant.rating.toStringAsFixed(1)
                                : 'New',
                            style: const TextStyle(
                              color: _ink,
                              fontSize: 12,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                          const SizedBox(width: 9),
                          Expanded(
                            child: Text(
                              restaurant.address,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: const TextStyle(
                                fontSize: 11,
                                color: _muted,
                              ),
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Column(
                  mainAxisSize: MainAxisSize.min,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 7,
                        vertical: 5,
                      ),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF2F5EF),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        _distanceLabel(distance),
                        style: const TextStyle(
                          color: _ink,
                          fontWeight: FontWeight.w700,
                          fontSize: 11,
                        ),
                      ),
                    ),
                    const SizedBox(height: 9),
                    const Icon(
                      Icons.arrow_forward_rounded,
                      size: 17,
                      color: _ink,
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// A north-up geographic radar. It deliberately contains no invented road map.
class NearbyRadar extends StatelessWidget {
  const NearbyRadar({
    super.key,
    required this.center,
    required this.restaurants,
    required this.radiusMeters,
    required this.onSelect,
    this.destination,
    this.isDemo = true,
  });

  final GeoPoint center;
  final List<Restaurant> restaurants;
  final double radiusMeters;
  final ValueChanged<Restaurant> onSelect;
  final Restaurant? destination;
  final bool isDemo;

  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(
      color: const Color(0xFFF0F4EA),
      borderRadius: BorderRadius.circular(26),
      border: Border.all(color: const Color(0xFFE3E9DC)),
    ),
    child: ClipRRect(
      borderRadius: BorderRadius.circular(26),
      child: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 0),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'A little closer to delicious.',
                        style: TextStyle(
                          color: _ink,
                          fontWeight: FontWeight.w800,
                          fontSize: 15,
                          letterSpacing: -.3,
                        ),
                      ),
                      SizedBox(height: 4),
                      Text(
                        'NEARBY RADAR',
                        style: TextStyle(
                          color: _muted,
                          fontSize: 9,
                          fontWeight: FontWeight.w700,
                          letterSpacing: 1.5,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 6,
                  ),
                  decoration: BoxDecoration(
                    color: Colors.white.withAlpha(210),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text(
                    isDemo ? 'DEMO AREA' : 'GPS AREA',
                    style: const TextStyle(
                      color: _ink,
                      fontSize: 8,
                      fontWeight: FontWeight.w800,
                      letterSpacing: .8,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: LayoutBuilder(
              builder: (context, constraints) {
                final width = constraints.maxWidth;
                final height = constraints.maxHeight;
                final radius = math.max(
                  24.0,
                  math.min(width - 58, height - 20) / 2,
                );
                final origin = Offset(width / 2, height / 2);
                return Stack(
                  clipBehavior: Clip.none,
                  children: [
                    Positioned.fill(
                      child: CustomPaint(
                        painter: _RadarPainter(radius: radius),
                      ),
                    ),
                    for (final restaurant in restaurants)
                      if (distanceMeters(center, restaurant.location) <=
                          radiusMeters)
                        _marker(restaurant, origin, radius),
                    Positioned(
                      left: origin.dx - 22,
                      top: origin.dy - 22,
                      child: Semantics(
                        label: isDemo
                            ? 'Simulated location, radar center'
                            : 'Your location, radar center',
                        child: Container(
                          width: 44,
                          height: 44,
                          decoration: BoxDecoration(
                            color: _ink,
                            shape: BoxShape.circle,
                            border: Border.all(color: Colors.white, width: 4),
                            boxShadow: [
                              BoxShadow(
                                color: _ink.withAlpha(40),
                                blurRadius: 12,
                                spreadRadius: 3,
                              ),
                            ],
                          ),
                          child: const Icon(
                            Icons.near_me_rounded,
                            color: _gold,
                            size: 18,
                          ),
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 17),
            child: Wrap(
              alignment: WrapAlignment.spaceBetween,
              crossAxisAlignment: WrapCrossAlignment.center,
              spacing: 12,
              runSpacing: 6,
              children: [
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.radar_rounded, color: _muted, size: 14),
                    const SizedBox(width: 5),
                    Text(
                      '${_distanceLabel(radiusMeters)} radius',
                      style: const TextStyle(color: _muted, fontSize: 11),
                    ),
                  ],
                ),
                const Text(
                  'Tap a place to explore',
                  style: TextStyle(
                    color: _ink,
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    ),
  );

  Widget _marker(Restaurant restaurant, Offset origin, double radarRadius) {
    final distance = distanceMeters(center, restaurant.location);
    final angle = bearingDegrees(center, restaurant.location) * math.pi / 180;
    final ratio = radiusMeters <= 0
        ? 0.0
        : (distance / radiusMeters).clamp(0.0, 1.0);
    final offset = Offset(
      origin.dx + math.sin(angle) * radarRadius * ratio,
      origin.dy - math.cos(angle) * radarRadius * ratio,
    );
    final selected = destination?.id == restaurant.id;
    return Positioned(
      left: offset.dx - 22,
      top: offset.dy - 22,
      child: Tooltip(
        message: '${restaurant.name} · ${_distanceLabel(distance)}',
        child: Semantics(
          button: true,
          label: 'Explore ${restaurant.name}, ${_distanceLabel(distance)} away',
          child: Material(
            color: selected ? _gold : Colors.white,
            shape: CircleBorder(
              side: BorderSide(
                color: selected ? _ink : const Color(0xFFDCE5D6),
                width: selected ? 2 : 1,
              ),
            ),
            elevation: selected ? 3 : 1,
            shadowColor: _ink.withAlpha(40),
            child: InkWell(
              onTap: () => onSelect(restaurant),
              customBorder: const CircleBorder(),
              child: SizedBox.square(
                dimension: 44,
                child: Center(
                  child: Text(
                    restaurant.emoji,
                    style: const TextStyle(fontSize: 22),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _RadarPainter extends CustomPainter {
  const _RadarPainter({required this.radius});

  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    final center = Offset(size.width / 2, size.height / 2);
    final line = Paint()
      ..color = const Color(0xFFCCD9C3)
      ..style = PaintingStyle.stroke
      ..strokeWidth = .9;
    final soft = Paint()..color = const Color(0xFFE6EDD9);
    canvas.drawCircle(center, radius, soft);
    canvas.drawCircle(
      center,
      radius * .66,
      Paint()..color = const Color(0xFFE0E9D3),
    );
    canvas.drawCircle(
      center,
      radius * .33,
      Paint()..color = const Color(0xFFD9E4CD),
    );
    for (final ratio in [.33, .66, 1.0]) {
      canvas.drawCircle(center, radius * ratio, line);
    }
    final cross = Paint()
      ..color = const Color(0xFFC0CEB5)
      ..strokeWidth = 1;
    const dash = 4.0;
    for (double d = -radius; d < radius; d += dash * 2) {
      final end = math.min(d + dash, radius);
      canvas.drawLine(center + Offset(d, 0), center + Offset(end, 0), cross);
      canvas.drawLine(center + Offset(0, d), center + Offset(0, end), cross);
    }
    _label(canvas, 'N', center + Offset(0, -radius - 8));
    _label(canvas, 'S', center + Offset(0, radius + 8));
    _label(canvas, 'W', center + Offset(-radius - 13, 0));
    _label(canvas, 'E', center + Offset(radius + 13, 0));
  }

  void _label(Canvas canvas, String text, Offset position) {
    final painter = TextPainter(
      text: TextSpan(
        text: text,
        style: const TextStyle(
          color: _muted,
          fontSize: 8,
          fontWeight: FontWeight.w800,
          letterSpacing: .4,
        ),
      ),
      textDirection: TextDirection.ltr,
    )..layout();
    painter.paint(
      canvas,
      position - Offset(painter.width / 2, painter.height / 2),
    );
  }

  @override
  bool shouldRepaint(covariant _RadarPainter oldDelegate) =>
      oldDelegate.radius != radius;
}
