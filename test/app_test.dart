import 'package:dinefinder/app/dinefinder_app.dart';
import 'package:dinefinder/app/widgets/discovery_widgets.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('demo search filters restaurants and reset restores results', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 900));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const DineFinderApp());
    await tester.pumpAndSettle();
    expect(find.byType(RestaurantCard), findsNWidgets(6));
    await tester.enterText(find.byType(TextField), 'not-a-real-place');
    await tester.pumpAndSettle();
    expect(find.byType(RestaurantCard), findsNothing);
    await tester.tap(find.text('Reset filters'));
    await tester.pumpAndSettle();
    expect(find.byType(RestaurantCard), findsNWidgets(6));
    expect(tester.takeException(), isNull);
  });

  testWidgets('restaurant profile starts a demo journey and supports arrival', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1280, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const DineFinderApp());
    await tester.pumpAndSettle();
    await tester.tap(find.byType(RestaurantCard).first);
    await tester.pumpAndSettle();
    expect(
      find.text('Sample restaurant · fictional listing for the demo'),
      findsOneWidget,
    );
    await tester.tap(find.text('Navigate'));
    await tester.pumpAndSettle();
    expect(find.text('YOUR WALKING DIRECTIONS'), findsOneWidget);
    expect(find.text('SIMULATED VIEW'), findsOneWidget);
    final walk = tester.widget<Slider>(find.byKey(const ValueKey('demo-walk')));
    walk.onChanged!(1);
    await tester.pumpAndSettle();
    expect(find.text('YOU’VE ARRIVED'), findsOneWidget);
    expect(find.text('Finish journey'), findsOneWidget);
    await tester.ensureVisible(find.text('Finish journey'));
    await tester.tap(find.text('Finish journey'));
    await tester.pumpAndSettle();
    expect(find.byType(RestaurantCard), findsNWidgets(6));
    expect(tester.takeException(), isNull);
  });

  testWidgets('phone layout and unavailable live mode remain usable', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(390, 844));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const DineFinderApp());
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Camera').last);
    await tester.pumpAndSettle();
    expect(find.text('SIMULATED VIEW'), findsOneWidget);
    expect(tester.takeException(), isNull);
    await tester.tap(find.text('Live'));
    await tester.pumpAndSettle();
    expect(find.text('Live discovery is not connected'), findsOneWidget);
    await tester.tap(find.text('Keep exploring'));
    await tester.pumpAndSettle();
    expect(find.text('SIMULATED VIEW'), findsOneWidget);
  });
}
