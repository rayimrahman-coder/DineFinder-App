import 'dart:math' as math;

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';

import '../../core/dinefinder_core.dart';

class CameraStage extends StatelessWidget {
  const CameraStage({
    super.key,
    required this.position,
    required this.heading,
    required this.restaurants,
    required this.isDemo,
    required this.onSelect,
    this.controller,
    this.cameraError,
    this.target,
    this.navigationLabel,
    this.arrived = false,
    this.offRoute = false,
  });

  final GeoPoint position;
  final double? heading;
  final List<Restaurant> restaurants;
  final bool isDemo;
  final ValueChanged<Restaurant> onSelect;
  final CameraController? controller;
  final String? cameraError;
  final GeoPoint? target;
  final String? navigationLabel;
  final bool arrived;
  final bool offRoute;

  @override
  Widget build(BuildContext context) {
    final activeCamera = controller;
    final hasCamera =
        !isDemo && activeCamera != null && activeCamera.value.isInitialized;
    return ClipRRect(
      borderRadius: BorderRadius.circular(28),
      child: LayoutBuilder(
        builder: (context, bounds) {
          final visible = heading == null
              ? <Restaurant>[]
              : restaurants
                    .where(
                      (place) => projectMarker(
                        bearingDegrees(position, place.location),
                        heading!,
                      ).isVisible,
                    )
                    .take(6)
                    .toList();
          return Stack(
            fit: StackFit.expand,
            children: [
              if (hasCamera)
                _CameraBackground(controller: activeCamera)
              else if (isDemo)
                const CustomPaint(painter: _DemoStreetPainter())
              else
                const ColoredBox(color: Color(0xFF152D32)),
              const DecoratedBox(
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Color(0x9909181C),
                      Colors.transparent,
                      Color(0xBB09181C),
                    ],
                    stops: [0, .45, 1],
                  ),
                ),
              ),
              Positioned(
                top: 18,
                left: 16,
                right: 16,
                child: Row(
                  children: [
                    Flexible(
                      flex: 3,
                      child: _GlassPill(
                        icon: isDemo
                            ? Icons.science_outlined
                            : Icons.videocam_outlined,
                        label: isDemo ? 'SIMULATED VIEW' : 'LIVE CAMERA',
                        color: isDemo ? const Color(0xFFF2BD58) : Colors.white,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Flexible(
                      flex: 2,
                      child: _GlassPill(
                        icon: Icons.explore_outlined,
                        label: heading == null
                            ? 'NO HEADING'
                            : '${heading!.round()}°',
                      ),
                    ),
                  ],
                ),
              ),
              if (!isDemo && !hasCamera)
                Positioned(
                  top: bounds.maxHeight * .22,
                  left: 28,
                  right: 28,
                  child: Column(
                    children: [
                      const Icon(
                        Icons.no_photography_outlined,
                        color: Colors.white70,
                        size: 38,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        cameraError ?? 'Starting the camera…',
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 14,
                          height: 1.5,
                        ),
                      ),
                    ],
                  ),
                ),
              if (heading == null)
                Positioned(
                  left: 24,
                  right: 24,
                  bottom: 120,
                  child: const Text(
                    'Waiting for a compass heading.\nRestaurant directions appear when a heading is available.',
                    textAlign: TextAlign.center,
                    style: TextStyle(color: Colors.white, height: 1.5),
                  ),
                ),
              for (var i = 0; i < visible.length; i++)
                _marker(visible[i], i, bounds),
              if (target != null && heading != null && !arrived && !offRoute)
                Positioned(
                  bottom: 105,
                  left: 20,
                  right: 20,
                  child: Column(
                    children: [
                      Transform.rotate(
                        angle:
                            headingDelta(
                              bearingDegrees(position, target!),
                              heading!,
                            ) *
                            math.pi /
                            180,
                        child: const Icon(
                          Icons.navigation_rounded,
                          color: Color(0xFFF6C668),
                          size: 88,
                          shadows: [
                            Shadow(color: Colors.black38, blurRadius: 20),
                          ],
                        ),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        navigationLabel ?? 'Follow the walking route',
                        textAlign: TextAlign.center,
                        maxLines: 2,
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 17,
                          fontWeight: FontWeight.w700,
                          shadows: [
                            Shadow(color: Colors.black54, blurRadius: 8),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              if (arrived)
                const Center(
                  child: _GlassPill(
                    icon: Icons.check_circle,
                    label: 'YOU’VE ARRIVED',
                    color: Color(0xFFF2BD58),
                  ),
                ),
              if (offRoute && !arrived)
                const Positioned(
                  left: 24,
                  right: 24,
                  bottom: 130,
                  child: _GlassPill(
                    icon: Icons.alt_route,
                    label: 'OFF ROUTE · RECALCULATE',
                    color: Color(0xFFF2BD58),
                  ),
                ),
              if (target == null && heading != null && visible.isEmpty)
                Center(
                  child: _GlassPill(
                    icon: Icons.screen_rotation_alt,
                    label: restaurants.isEmpty
                        ? 'NO RESTAURANTS IN THIS SCAN'
                        : 'TURN TO FIND NEARBY PLACES',
                  ),
                ),
              Positioned(
                bottom: 24,
                left: 22,
                right: 22,
                child: Column(
                  children: [
                    CustomPaint(
                      size: Size(math.min(bounds.maxWidth - 44, 300), 22),
                      painter: _CompassTicksPainter(heading ?? 0),
                    ),
                    const SizedBox(height: 12),
                    const Text(
                      'GPS + compass overlay · approximate alignment',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.white70, fontSize: 11),
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Widget _marker(Restaurant place, int index, BoxConstraints bounds) {
    final projection = projectMarker(
      bearingDegrees(position, place.location),
      heading!,
    );
    final markerWidth = math.min(154.0, bounds.maxWidth - 24);
    final left =
        ((projection.horizontal + 1) /
                    2 *
                    (bounds.maxWidth - markerWidth - 24) +
                12)
            .clamp(12.0, math.max(12.0, bounds.maxWidth - markerWidth - 12))
            .toDouble();
    final distance = distanceMeters(position, place.location);
    return Positioned(
      top: 95 + (index % 3) * 74.0,
      left: left,
      width: markerWidth,
      child: Semantics(
        label: '${place.name}, ${distance.round()} metres away',
        button: true,
        child: Material(
          elevation: 6,
          shadowColor: Colors.black26,
          color: const Color(0xF5FFFFFF),
          borderRadius: BorderRadius.circular(16),
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () => onSelect(place),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Row(
                children: [
                  Text(place.emoji, style: const TextStyle(fontSize: 24)),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          place.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(
                            fontWeight: FontWeight.w700,
                            fontSize: 12,
                            color: Color(0xFF123F3A),
                          ),
                        ),
                        Text(
                          '${distance.round()} m',
                          style: const TextStyle(
                            color: Color(0xFF687771),
                            fontSize: 11,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _CameraBackground extends StatelessWidget {
  const _CameraBackground({required this.controller});
  final CameraController controller;

  @override
  Widget build(BuildContext context) {
    final size = controller.value.previewSize;
    if (size == null) return CameraPreview(controller);
    return ClipRect(
      child: FittedBox(
        fit: BoxFit.cover,
        child: SizedBox(
          width: size.height,
          height: size.width,
          child: CameraPreview(controller),
        ),
      ),
    );
  }
}

class _GlassPill extends StatelessWidget {
  const _GlassPill({
    required this.icon,
    required this.label,
    this.color = Colors.white,
  });
  final IconData icon;
  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
    decoration: BoxDecoration(
      color: const Color(0xA61A302F),
      borderRadius: BorderRadius.circular(24),
      border: Border.all(color: Colors.white24),
    ),
    child: Row(
      mainAxisSize: MainAxisSize.min,
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(icon, size: 15, color: color),
        const SizedBox(width: 6),
        Flexible(
          child: Text(
            label,
            maxLines: 2,
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w700,
              letterSpacing: .6,
              color: color,
            ),
          ),
        ),
      ],
    ),
  );
}

class _CompassTicksPainter extends CustomPainter {
  const _CompassTicksPainter(this.heading);
  final double heading;

  @override
  void paint(Canvas canvas, Size size) {
    final paint = Paint()
      ..color = Colors.white38
      ..strokeWidth = 1;
    for (var i = 0; i <= 20; i++) {
      final x = size.width * i / 20;
      canvas.drawLine(Offset(x, 0), Offset(x, i % 5 == 0 ? 13 : 6), paint);
    }
    canvas.drawPath(
      Path()
        ..moveTo(size.width / 2 - 4, 18)
        ..lineTo(size.width / 2, 12)
        ..lineTo(size.width / 2 + 4, 18)
        ..close(),
      Paint()..color = const Color(0xFFF2BD58),
    );
  }

  @override
  bool shouldRepaint(covariant _CompassTicksPainter oldDelegate) =>
      oldDelegate.heading != heading;
}

class _DemoStreetPainter extends CustomPainter {
  const _DemoStreetPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    canvas.drawRect(
      Offset.zero & size,
      Paint()
        ..shader = const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFF5B8D91), Color(0xFFB6C8B9), Color(0xFFDFCBA4)],
        ).createShader(Offset.zero & size),
    );
    canvas.drawCircle(
      Offset(w * .74, h * .20),
      30,
      Paint()..color = const Color(0x66FFE2A6),
    );
    for (var i = 0; i < 9; i++) {
      final x = w * i / 8 - w * .05;
      final height = h * (.17 + (i % 3) * .055);
      canvas.drawRRect(
        RRect.fromRectAndRadius(
          Rect.fromLTWH(x, h * .46 - height, w * .16, height),
          const Radius.circular(3),
        ),
        Paint()
          ..color = i.isEven
              ? const Color(0xFF6C8A83)
              : const Color(0xFF7D9A91),
      );
    }
    canvas.drawPath(
      Path()
        ..moveTo(w * .46, h * .46)
        ..lineTo(w * .55, h * .46)
        ..lineTo(w * 1.12, h)
        ..lineTo(-w * .1, h)
        ..close(),
      Paint()..color = const Color(0xFF526966),
    );
    for (var i = 0; i < 5; i++) {
      final depth = i / 5;
      final y = h * (.5 + depth * .5);
      canvas.drawLine(
        Offset(w * .5, y),
        Offset(w * .5, y + 8 + depth * 22),
        Paint()
          ..color = const Color(0x77E8DFC4)
          ..strokeWidth = 1 + depth * 3,
      );
    }
    _shop(
      canvas,
      Rect.fromLTRB(0, h * .36, w * .31, h * .75),
      const Color(0xFFDFC297),
    );
    _shop(
      canvas,
      Rect.fromLTRB(w * .74, h * .34, w, h * .76),
      const Color(0xFFAEC0AD),
    );
    for (final side in [0.13, 0.87]) {
      canvas.drawLine(
        Offset(w * side, h * .72),
        Offset(w * side, h * .37),
        Paint()
          ..color = const Color(0xFF35554F)
          ..strokeWidth = 4,
      );
      canvas.drawCircle(
        Offset(w * side, h * .35),
        7,
        Paint()..color = const Color(0xFFFFD58B),
      );
    }
  }

  void _shop(Canvas canvas, Rect r, Color color) {
    canvas.drawRect(r, Paint()..color = color);
    canvas.drawRect(
      Rect.fromLTWH(
        r.left + r.width * .14,
        r.top + r.height * .22,
        r.width * .72,
        r.height * .65,
      ),
      Paint()..color = const Color(0xFF365B55),
    );
    for (var i = 0; i < 6; i++) {
      canvas.drawRect(
        Rect.fromLTWH(
          r.left + i * r.width / 6,
          r.top + r.height * .18,
          r.width / 6,
          r.height * .13,
        ),
        Paint()
          ..color = i.isEven
              ? const Color(0xFF1B6255)
              : const Color(0xFFF2DBB1),
      );
    }
  }

  @override
  bool shouldRepaint(covariant CustomPainter oldDelegate) => false;
}
