import 'package:flutter/material.dart';
import 'package:supabase_flutter/supabase_flutter.dart';

import 'app/dinefinder_app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  const url = String.fromEnvironment('SUPABASE_URL');
  const key = String.fromEnvironment('SUPABASE_ANON_KEY');
  SupabaseClient? client;
  if (url.isNotEmpty && key.isNotEmpty) {
    try {
      await Supabase.initialize(url: url, publishableKey: key);
      client = Supabase.instance.client;
    } on Object {
      // A connection/configuration failure must not prevent trying the demo.
      // Do not log credentials or include them in an error message.
    }
  }
  runApp(DineFinderApp(client: client));
}
