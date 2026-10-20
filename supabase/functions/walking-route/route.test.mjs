import test from 'node:test';
import assert from 'node:assert/strict';
import { point, routeRequest, parseOrsRoute, RouteError } from './route.ts';

const origin = { latitude: 3.1478, longitude: 101.7100 };
const destination = { latitude: 3.1491, longitude: 101.7113 };

function orsFixture() {
  return {
    features: [{
      geometry: { type: 'LineString', coordinates: [[101.7100, 3.1478], [101.7108, 3.1482], [101.7113, 3.1491]] },
      properties: {
        summary: { distance: 230, duration: 165 },
        segments: [{ steps: [
          { distance: 100, instruction: 'Head northeast', way_points: [0, 1] },
          { distance: 130, instruction: 'Turn left', way_points: [1, 2] },
          { distance: 0, instruction: 'Arrive at your destination', way_points: [2, 2] },
        ] }],
      },
    }],
  };
}

test('validates finite coordinates and rejects strings, missing values, and out-of-bounds points', () => {
  assert.deepEqual(point(origin), origin);
  for (const invalid of [null, [], {}, { latitude: '3', longitude: 101 },
    { latitude: 91, longitude: 0 }, { latitude: 0, longitude: -181 },
    { latitude: NaN, longitude: 0 }, { latitude: 0, longitude: Infinity }]) {
    assert.throws(() => point(invalid), { status: 400 });
  }
});

test('accepts a nearby walking destination and rejects requests beyond 10 km', () => {
  assert.deepEqual(routeRequest({ from: origin, to: destination }), { from: origin, to: destination });
  assert.throws(() => routeRequest({ from: origin, to: { latitude: 4, longitude: 102 } }), { status: 400 });
  assert.throws(() => routeRequest({ from: origin }), { status: 400 });
});

test('converts longitude-first ORS geometry and step indices to the mobile API contract', () => {
  const route = parseOrsRoute(orsFixture());
  assert.deepEqual(route.points[0], origin);
  assert.deepEqual(route.points.at(-1), destination);
  assert.equal(route.distance_meters, 230);
  assert.equal(route.duration_seconds, 165);
  assert.deepEqual(route.instructions[1], {
    point: { latitude: 3.1482, longitude: 101.7108 }, distance_meters: 130, text: 'Turn left',
  });
});

test('does not replace malformed upstream geometry or instructions with invented directions', () => {
  const invalid = [null, {}, { features: [] }];
  const wrongType = orsFixture(); wrongType.features[0].geometry.type = 'Polygon'; invalid.push(wrongType);
  const badCoordinate = orsFixture(); badCoordinate.features[0].geometry.coordinates[1] = [200, 3]; invalid.push(badCoordinate);
  const badIndex = orsFixture(); badIndex.features[0].properties.segments[0].steps[0].way_points = [999, 1000]; invalid.push(badIndex);
  const noSteps = orsFixture(); noSteps.features[0].properties.segments[0].steps = []; invalid.push(noSteps);
  const negativeSummary = orsFixture(); negativeSummary.features[0].properties.summary.duration = -1; invalid.push(negativeSummary);
  for (const value of invalid) assert.throws(() => parseOrsRoute(value), { status: 502 });
});

test('rejects an actual route over 10 km even when the endpoints are nearby', () => {
  const fixture = orsFixture(); fixture.features[0].properties.summary.distance = 10001;
  assert.throws(() => parseOrsRoute(fixture), (error) => error instanceof RouteError && error.status === 422);
});
