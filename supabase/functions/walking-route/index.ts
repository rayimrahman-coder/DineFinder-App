import { createClient } from 'npm:@supabase/supabase-js@2.118.0';
import { parseOrsRoute, RouteError, routeRequest } from './route.ts';

const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...cors, 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
  });
}

async function smallJson(request: Request): Promise<unknown> {
  if (!request.body) throw new RouteError(400, 'A JSON request body is required.');
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let length = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    length += value.length;
    if (length > 4096) {
      await reader.cancel();
      throw new RouteError(413, 'The route request is too large.');
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(length);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.length;
  }
  try {
    return JSON.parse(new TextDecoder().decode(bytes));
  } catch {
    throw new RouteError(400, 'The request body must be valid JSON.');
  }
}

Deno.serve(async (request: Request) => {
  if (request.method === 'OPTIONS') return new Response(null, { status: 204, headers: cors });
  if (request.method !== 'POST') return json({ error: 'Use POST to request a walking route.' }, 405);
  try {
    if (!request.headers.get('content-type')?.toLowerCase().includes('application/json')) {
      throw new RouteError(415, 'Use the application/json content type.');
    }
    const authorization = request.headers.get('authorization') ?? '';
    const token = /^Bearer\s+(\S+)$/i.exec(authorization)?.[1];
    if (!token) throw new RouteError(401, 'Sign in before requesting a route.');

    const url = Deno.env.get('SUPABASE_URL');
    const anonKey = Deno.env.get('SUPABASE_ANON_KEY');
    const routeKey = Deno.env.get('ORS_API_KEY');
    if (!url || !anonKey || !routeKey) {
      throw new RouteError(503, 'Walking navigation has not been configured.');
    }
    const supabase = createClient(url, anonKey, {
      auth: { persistSession: false, autoRefreshToken: false },
      global: { fetch: (input, init) => fetch(input, { ...init, signal: AbortSignal.timeout(10000) }) },
    });
    // Explicitly validate a user JWT; possession of the public app key is not
    // sufficient. Anonymous Supabase Auth users have valid user sessions.
    const { data, error } = await supabase.auth.getUser(token);
    if (error || !data.user) throw new RouteError(401, 'Your session has expired. Please try again.');

    const { from, to } = routeRequest(await smallJson(request));
    const response = await fetch('https://api.openrouteservice.org/v2/directions/foot-walking/geojson', {
      method: 'POST',
      headers: { Authorization: routeKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        coordinates: [[from.longitude, from.latitude], [to.longitude, to.latitude]],
        instructions: true,
        language: 'en',
        units: 'm',
      }),
      signal: AbortSignal.timeout(12000),
    });
    if (response.status === 429) throw new RouteError(429, 'Walking directions are busy. Please try again later.');
    if (!response.ok) throw new RouteError(502, 'No walking route is available right now.');
    return json(parseOrsRoute(await response.json()));
  } catch (error) {
    if (error instanceof RouteError) return json({ error: error.message }, error.status);
    // Do not return upstream responses, authorization headers, or secret values.
    return json({ error: 'Walking directions could not be loaded. Please try again.' }, 502);
  }
});
