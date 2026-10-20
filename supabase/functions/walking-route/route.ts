export type Point = { latitude: number; longitude: number };
export type RouteInstruction = {
  point: Point;
  distance_meters: number;
  text: string;
};
export type WalkingRoute = {
  points: Point[];
  instructions: RouteInstruction[];
  distance_meters: number;
  duration_seconds: number;
};

export class RouteError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.name = 'RouteError';
    this.status = status;
  }
}

function record(value: unknown): Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new RouteError(400, 'Expected a JSON object.');
  }
  return value as Record<string, unknown>;
}

export function point(value: unknown): Point {
  const p = record(value);
  if (typeof p.latitude !== 'number' || !Number.isFinite(p.latitude) ||
    p.latitude < -90 || p.latitude > 90 || typeof p.longitude !== 'number' ||
    !Number.isFinite(p.longitude) || p.longitude < -180 || p.longitude > 180) {
    throw new RouteError(400, 'Coordinates must be finite latitude and longitude values.');
  }
  return { latitude: p.latitude, longitude: p.longitude };
}

export function distanceMeters(a: Point, b: Point): number {
  const radians = Math.PI / 180;
  const lat = (b.latitude - a.latitude) * radians;
  const lon = (b.longitude - a.longitude) * radians;
  const h = Math.sin(lat / 2) ** 2 + Math.cos(a.latitude * radians) *
    Math.cos(b.latitude * radians) * Math.sin(lon / 2) ** 2;
  return 6371000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(Math.max(0, 1 - h)));
}

export function routeRequest(value: unknown): { from: Point; to: Point } {
  const body = record(value);
  const from = point(body.from);
  const to = point(body.to);
  if (distanceMeters(from, to) > 10000) {
    throw new RouteError(400, 'Walking destinations must be within 10 km.');
  }
  return { from, to };
}

function nonNegative(value: unknown): number {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) {
    throw new RouteError(502, 'The routing service returned an invalid route.');
  }
  return value;
}

// Only accepts the documented ORS GeoJSON format. Never invent a direct-line
// walking route when an upstream route or its instructions are unavailable.
export function parseOrsRoute(value: unknown): WalkingRoute {
  try {
    const root = record(value);
    if (!Array.isArray(root.features) || root.features.length === 0) {
      throw new Error('No route');
    }
    const feature = record(root.features[0]);
    const geometry = record(feature.geometry);
    if (geometry.type !== 'LineString' || !Array.isArray(geometry.coordinates) ||
      geometry.coordinates.length < 2 || geometry.coordinates.length > 100000) {
      throw new Error('Invalid geometry');
    }
    const points = geometry.coordinates.map((coordinate) => {
      if (!Array.isArray(coordinate) || coordinate.length < 2) throw new Error('Invalid coordinate');
      return point({ latitude: coordinate[1], longitude: coordinate[0] });
    });
    const properties = record(feature.properties);
    const summary = record(properties.summary);
    const distance = nonNegative(summary.distance);
    const duration = nonNegative(summary.duration);
    if (distance > 10000) throw new RouteError(422, 'The available walking route is longer than 10 km.');
    if (!Array.isArray(properties.segments)) throw new Error('Missing route steps');
    const instructions: RouteInstruction[] = [];
    for (const segment of properties.segments) {
      const steps = record(segment).steps;
      if (!Array.isArray(steps)) throw new Error('Missing steps');
      for (const rawStep of steps) {
        const step = record(rawStep);
        if (!Array.isArray(step.way_points) || !Number.isInteger(step.way_points[0]) ||
          step.way_points[0] < 0 || step.way_points[0] >= points.length ||
          typeof step.instruction !== 'string' || !step.instruction.trim()) {
          throw new Error('Invalid instruction');
        }
        instructions.push({
          point: points[step.way_points[0]],
          distance_meters: nonNegative(step.distance),
          text: step.instruction.trim().slice(0, 300),
        });
      }
    }
    if (instructions.length === 0) throw new Error('No instructions');
    return { points, instructions, distance_meters: distance, duration_seconds: duration };
  } catch (error) {
    if (error instanceof RouteError && error.status === 422) throw error;
    throw new RouteError(502, 'The routing service did not return a usable walking route.');
  }
}
