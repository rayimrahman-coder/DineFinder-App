# Sign in and account creation

The welcome screen sends email/password requests to Supabase Authentication. It
shows a loading indicator while a request is pending and displays the response
message when a request fails. **Continue** signs in to an existing account; use
**New here? Create an account** to register first. If Supabase requires email
confirmation, confirm the email before signing in.

In the Supabase dashboard, check **Authentication → Users** for the account and
**Authentication → Providers → Email** for the enabled provider. Accounts and
password verification are managed by Supabase Auth, not `public.profiles`.
Missing public restaurant/profile tables do not by themselves prevent an
existing valid Auth account from signing in; run `supabase/setup.sql` to enable
restaurant discovery and private display-name profiles.

A saved email is a display preference, not an authenticated session. Signed-in
sessions currently remain in memory: sign in again after an app process restart.
Logout clears the local account and requests sign-out for the current session.
Guest access does not create a Supabase Auth account.

If login fails, share the exact message shown below the password field. Never
share your password. Wrong credentials, an unconfirmed email, disabled Email
provider, a paused/unavailable project, or an outdated public project key need
different fixes; the client configuration alone does not prove live Auth works.
