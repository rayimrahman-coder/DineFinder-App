package com.example.dinefinder

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.dinefinder.ar.ARNavigationActivity
import com.example.dinefinder.ar.NavigationDestination
import com.example.dinefinder.places.PlaceAttribution
import com.example.dinefinder.places.PlaceDetailsResult
import com.example.dinefinder.places.PlacesClient
import com.example.dinefinder.places.PlacesFailure
import com.example.dinefinder.profile.ProfileContent
import com.example.dinefinder.profile.ProfileSelection
import com.example.dinefinder.profile.RestaurantProfileContract
import com.example.dinefinder.profile.mergeProfileContent
import com.example.dinefinder.profile.profileWebUri
import com.example.dinefinder.settings.ApiKeyStore
import com.example.dinefinder.settings.GoogleMapsAppIdentity
import com.example.dinefinder.settings.UserPreferences
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Only restaurant type, rating and known halal status are shown; online data stays in memory. */
class RestaurantProfileActivity : AppCompatActivity() {
    private var selection: ProfileSelection? = null
    private var active = false
    private var requestGeneration = 0
    private var activeKey = ""
    private var detailsJob: Job? = null
    private val apiKeyStore by lazy { ApiKeyStore(this) }
    private val placesClient by lazy { PlacesClient.create() }
    private val handler = Handler(Looper.getMainLooper())
    private val configurationWatchdog = object : Runnable {
        override fun run() {
            if (!active) return
            val key = apiKeyStore.apiKey()
            if (key != activeKey) loadOnlineRating(key)
            handler.postDelayed(this, 2_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DineFinder_Profile)
        delegate.localNightMode = savedNightMode()
        super.onCreate(savedInstanceState)
        // Validate the destination before creating a Places client or starting navigation.
        selection = RestaurantProfileContract.read(intent)
        if (selection == null) {
            Toast.makeText(this, R.string.restaurant_profile_invalid, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        setContentView(R.layout.activity_restaurant_profile)
        val root = findViewById<View>(R.id.restaurantProfileRoot)
        disableSavedViewState(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<View>(R.id.restaurantProfileBack).setOnClickListener {
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                finish()
            }.start()
        }
        findViewById<View>(R.id.profileNavigateButton).setOnClickListener {
            it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                startNavigation()
            }.start()
        }
        renderContent(mergeProfileContent(selection!!.restaurant, null))
    }

    override fun onResume() {
        super.onResume()
        if (selection == null || isFinishing) return
        val mode = savedNightMode()
        if (delegate.localNightMode != mode) {
            delegate.localNightMode = mode
            return
        }
        active = true
        loadOnlineRating(apiKeyStore.apiKey())
        handler.removeCallbacks(configurationWatchdog)
        handler.postDelayed(configurationWatchdog, 2_000)
    }

    override fun onPause() {
        active = false
        cancelOnlineRequests()
        activeKey = ""
        handler.removeCallbacks(configurationWatchdog)
        super.onPause()
    }

    override fun onDestroy() {
        cancelOnlineRequests()
        super.onDestroy()
    }

    private fun savedNightMode() = if (UserPreferences(this).isDarkMode()) {
        AppCompatDelegate.MODE_NIGHT_YES
    } else {
        AppCompatDelegate.MODE_NIGHT_NO
    }

    private fun renderContent(content: ProfileContent) {
        val selected = selection ?: return
        text(R.id.profileRestaurantName).text = content.name
        text(R.id.profileRestaurantCuisine).text = content.cuisine.ifBlank {
            getString(R.string.restaurant_profile_category_unknown)
        }

        val distanceMeters = selected.distanceMeters
        val address = selected.restaurant.address.trim()
        val distanceStr = if (distanceMeters > 0) {
            if (distanceMeters >= 1000) String.format("%.1f km", distanceMeters / 1000.0)
            else "${distanceMeters.toInt()} m"
        } else null
        val distanceAddressText = listOfNotNull(distanceStr, address.takeIf { it.isNotBlank() }).joinToString(" • ")
        val distanceRow = findViewById<View>(R.id.profileDistanceRow)
        val distanceDivider = findViewById<View>(R.id.profileDistanceDivider)
        if (distanceAddressText.isNotBlank()) {
            distanceRow?.visibility = View.VISIBLE
            distanceDivider?.visibility = View.VISIBLE
            text(R.id.profileRestaurantDistance).text = distanceAddressText
        } else {
            distanceRow?.visibility = View.GONE
            distanceDivider?.visibility = View.GONE
        }

        val hours = selected.restaurant.hours.trim()
        val price = selected.restaurant.priceRange.trim()
        val hoursPriceText = listOfNotNull(hours.takeIf { it.isNotBlank() }, price.takeIf { it.isNotBlank() }).joinToString(" • ")
        val hoursRow = findViewById<View>(R.id.profileHoursRow)
        val hoursDivider = findViewById<View>(R.id.profileHoursDivider)
        if (hoursPriceText.isNotBlank()) {
            hoursRow?.visibility = View.VISIBLE
            hoursDivider?.visibility = View.VISIBLE
            text(R.id.profileRestaurantHours).text = hoursPriceText
        } else {
            hoursRow?.visibility = View.GONE
            hoursDivider?.visibility = View.GONE
        }
        text(R.id.profileRestaurantRating).apply {
            text = content.rating?.let { rating ->
                content.ratingCount?.let { count ->
                    getString(R.string.restaurant_profile_rating_count, rating.toDouble(), count)
                } ?: getString(R.string.restaurant_profile_rating, rating.toDouble())
            } ?: getString(R.string.restaurant_profile_rating_unknown)
            setTextColor(ContextCompat.getColor(context,
                if (content.rating == null) R.color.profile_muted else R.color.profile_ink))
        }
        text(R.id.profileRestaurantHalal).apply {
            setText(when (content.halal) {
                true -> R.string.restaurant_profile_halal
                false -> R.string.restaurant_profile_not_halal
                null -> R.string.restaurant_profile_halal_unknown
            })
            setTextColor(ContextCompat.getColor(context, when (content.halal) {
                true -> R.color.profile_accent
                false -> R.color.profile_ink
                null -> R.color.profile_muted
            }))
        }
        val credits = (selected.attributions + content.attributions)
            .filter { it.provider.isNotBlank() }
            .map { it.copy(providerUri = profileWebUri(it.providerUri)) }
            .distinct()
        text(R.id.profileGoogleAttribution).visibility =
            if (selected.placeId != null || credits.isNotEmpty()) View.VISIBLE else View.GONE
        findViewById<ViewGroup>(R.id.profileProviderAttributions).apply {
            removeAllViews()
            credits.forEach { attribution ->
                addView(attributionView(
                    getString(R.string.restaurant_profile_provider, attribution.provider), attribution.providerUri
                ))
            }
        }
    }
    private fun loadOnlineRating(key: String) {
        // Disabled: No longer using Google Places API.
        val selected = selection ?: return
        renderContent(mergeProfileContent(selected.restaurant, null))
        text(R.id.profileOnlineStatus).visibility = View.GONE
    }

    private fun isCurrent(generation: Int, key: String) = active &&
        generation == requestGeneration && key == activeKey && apiKeyStore.apiKey() == key

    private fun cancelOnlineRequests() {
        requestGeneration++
        detailsJob?.cancel()
        detailsJob = null
    }

    private fun failureMessage(reason: PlacesFailure): Int = when (reason) {
        PlacesFailure.MISSING_KEY -> R.string.restaurant_profile_online_key
        PlacesFailure.CONFIG -> R.string.restaurant_profile_online_config
        PlacesFailure.QUOTA -> R.string.restaurant_profile_online_quota
        PlacesFailure.NO_RESULTS -> R.string.restaurant_profile_online_missing
        PlacesFailure.NETWORK -> R.string.restaurant_profile_online_network
    }

    private fun startNavigation() {
        val restaurant = selection?.restaurant ?: return
        val destination = NavigationDestination.create(restaurant.name, restaurant.latitude, restaurant.longitude)
        if (destination == null) {
            Toast.makeText(this, R.string.restaurant_profile_invalid, Toast.LENGTH_LONG).show()
            return
        }
        startActivity(Intent(this, ARNavigationActivity::class.java).apply {
            putExtra("restaurant_name", destination.name)
            putExtra("restaurant_lat", destination.latitude)
            putExtra("restaurant_lng", destination.longitude)
        })
    }

    private fun attributionView(label: String, uri: String?) = TextView(this).apply {
        text = label
        textSize = 14f
        setTypeface(typeface, Typeface.NORMAL)
        setTextColor(ContextCompat.getColor(context, R.color.google_maps_attribution))
        setPadding(0, dp(8), 0, dp(8))
        isSaveEnabled = false
        isSaveFromParentEnabled = false
        profileWebUri(uri)?.let { safe ->
            minHeight = dp(48)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setOnClickListener { openLink(safe) }
        }
    }

    private fun openLink(uri: String) {
        val safe = profileWebUri(uri) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.restaurant_profile_link_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun disableSavedViewState(view: View) {
        view.isSaveEnabled = false
        view.isSaveFromParentEnabled = false
        if (view is ViewGroup) for (index in 0 until view.childCount) disableSavedViewState(view.getChildAt(index))
    }

    private fun text(id: Int): TextView = findViewById(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        fun intent(context: Context, restaurant: Restaurant, distanceMeters: Double,
            placeId: String? = null, attributions: List<PlaceAttribution> = emptyList()): Intent =
            RestaurantProfileContract.fill(Intent(context, RestaurantProfileActivity::class.java),
                restaurant, distanceMeters, placeId, attributions)
    }
}
