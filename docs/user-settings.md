Open **Profile > Settings** to change the displayed username or toggle dark mode.
Save the username to update both Profile and the Home greeting. Names are trimmed
and must contain 1–40 characters; invalid input leaves the saved name unchanged.
Dark mode changes immediately and also applies to discovery and navigation controls.

The **Google Maps API** section lets you save or remove your own Google Maps
Platform API key for **Places API (New)** restaurant search and available restaurant
ratings, plus **Routes API** walking directions.
The key is masked by default and encrypted with Android Keystore in device-local
storage excluded from backups. This key is shared across local profiles, and
removing it stops new online restaurant and route requests. Saved Room restaurants
remain available within the **1 km** camera discovery radius without a key or
internet. Camera and precise location permissions, a fresh GPS fix and reliable
compass orientation are still needed. Use **How to set up your own API key** or
**Profile > Help** for
the tutorial, including how to restrict the key using the installed build’s
signing certificate. The advanced restriction instructions are collapsed in Help.
Saving a key stores it locally; Google checks it when an online request is made.
See [API key setup](google-maps-api-key.md).

These preferences are saved on the device. Each demo sign-in email has its own
username; the guest profile also supports a custom name. Logging out preserves
the name for the next sign-in. Dark mode is shared across profiles and stays
selected after logout or restarting the app. Existing profiles retain their
email-derived name until a custom name is saved. These settings do not change
the sign-in email or create an online account.

`settings.UserPreferences` uses the existing `dinefinder_profile` preferences file.
The legacy `email` key remains compatible, while names use normalized email keys
and `dark_mode` stores the appearance choice. No restaurant database migration is
needed. Compose reads the choice at startup and updates the theme as it changes;
Camera activities apply the saved AppCompat night mode before inflating their layouts.

Run `bash ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug` to
check preference persistence, username editing, Settings navigation, appearance
changes after recreation, and readable AR resources in light and dark mode.
