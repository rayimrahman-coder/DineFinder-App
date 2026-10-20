# DineFinder backend

The Flutter demo works without these services. Live mode needs a Supabase project,
restaurant records, and an OpenRouteService account for walking routes. This code
has not been deployed to your project automatically.

## Restaurant discovery

Link your Supabase project with the Supabase CLI, then run `supabase db push`
from the repository root to apply `migrations/202610200001_restaurants.sql`.
Alternatively, run that migration in the project's SQL editor. It installs
PostGIS in the `extensions` schema, enables read-only public restaurant access
through RLS, and creates an indexed radius search RPC. If an existing project's
PostGIS is installed in a different schema, resolve that migration difference
before applying it; do not drop an existing extension.

`nearby_restaurants` takes `latitude`, `longitude`, and `radius_meters` (100–5000,
default 1000). It returns up to 200 records sorted by geographic distance.
The app's radius is an as-the-crow-flies discovery boundary, not a walking-route
distance. The client publishable/anon key can read profiles but cannot write them.
Manage trusted restaurant records through an authorized backend or dashboard.

`seed.sql` is optional development data. Every listing, rating, address, and
schedule in it is fictitious. Run it only in a development project or clearly
identify its records as samples. It uses the same sample restaurant IDs as the
Flutter demo.

## Walking routes

1. Enable **Anonymous Sign-Ins** in your Supabase project's Auth settings. The
   app obtains an anonymous user session on its first live navigation request;
   no login form is needed. Consider enabling Supabase's abuse protections for a
   public release.
2. In the Supabase dashboard's Edge Function secrets, create `ORS_API_KEY` with
   your OpenRouteService API key. Do not put this key in Flutter's build defines,
   tracked files, logs, or chat. `SUPABASE_URL` and `SUPABASE_ANON_KEY` are supplied
   by the Edge Functions runtime.
3. From this repository, deploy with `supabase functions deploy walking-route`.
   `config.toml` disables the gateway's legacy JWT check because the function
   explicitly verifies each bearer token using `auth.getUser()`.
4. Configure the Flutter client with your project URL and public client key, as
   described in the repository README. Never use a Supabase service-role key in
   a client app.

The function accepts authenticated `POST` requests with this body:

```json
{"from":{"latitude":3.1478,"longitude":101.7100},"to":{"latitude":3.1491,"longitude":101.7113}}
```

It returns `points`, `instructions`, `distance_meters`, and `duration_seconds`.
Each point is `{latitude, longitude}`; each instruction has `point`,
`distance_meters`, and `text`. Coordinates and radius are validated. Destinations
and resulting walking routes are limited to 10 km. Upstream failure is reported
as an error; it never becomes a straight-line walking route.

The route provider's account quotas apply. Anonymous authentication alone is not
rate limiting: add per-user rate limits and monitor provider usage before a public
launch. Live location is sent to Supabase and OpenRouteService only when requesting
walking directions; nearby discovery sends coordinates to Supabase.

## Validation

Pure route validation and ORS parsing tests run without Deno or credentials:

```sh
node --test supabase/functions/walking-route/route.test.mjs
```

Use Node.js 22.18+ or 24+ (native TypeScript type stripping). These tests cover
coordinate rejection, the distance limit, coordinate order, steps, and malformed
upstream responses. They do not establish deployment, database migration success,
Supabase authentication, or provider connectivity. Test those in your own project
after configuration.

The migration, radius boundaries, nearest ordering, invalid-coordinate rejection,
and public read-only permissions can also be checked in a disposable local
PostGIS container:

```sh
bash supabase/tests/run-postgis.sh
```

Docker must be available. The script uses a pinned official PostGIS image digest,
exposes no database port, mounts no existing data, and removes its container when
finished. It creates a blank database with Supabase's `anon` and `authenticated`
roles; it does not emulate the full Supabase platform or deploy anything.
