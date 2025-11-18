package org.pdm.calculadoradecombustivel

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.pdm.calculadoradecombustivel.data.FuelPreferencesKeys
import org.pdm.calculadoradecombustivel.data.fuelDataStore
import org.pdm.calculadoradecombustivel.ui.theme.CalculadoraDeCombustivelTheme
import java.text.NumberFormat
import java.text.ParseException
import java.util.Locale
import java.util.UUID
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import kotlin.coroutines.resume
import androidx.core.net.toUri
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource

val APP_LOCALE: Locale
    get() {
        val defaultLocale = Locale.getDefault()
        return if (defaultLocale == Locale.ROOT) {
            Locale.Builder()
                .setLanguage("pt")
                .setRegion("BR")
                .build()
        } else {
            defaultLocale
        }
    }

fun currencySymbol(locale: Locale = APP_LOCALE): String {
    return runCatching {
        NumberFormat.getCurrencyInstance(locale).currency?.getSymbol(locale)
    }.getOrNull() ?: "R$"
}

private fun defaultPriceText(locale: Locale = APP_LOCALE): String = formatPriceInput("0", locale)

fun formatPriceFromDouble(value: Double, locale: Locale = APP_LOCALE): String {
    val sanitizedValue = if (value.isFinite() && value >= 0.0) value else 0.0
    val cents = BigDecimal.valueOf(sanitizedValue)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
    return formatPriceInput(cents.toPlainString(), locale)
}

private const val MAX_STATIONS = 10

private enum class AppScreen(@StringRes val labelResId: Int, val icon: ImageVector) {
    HOME(labelResId = R.string.screen_home, icon = Icons.Filled.Home),
    LIST(labelResId = R.string.screen_stations, icon = Icons.AutoMirrored.Filled.List)
}

data class GasStation(
    val id: String,
    val name: String,
    val alcoholPrice: Double,
    val gasolinePrice: Double,
    val location: String,
    val createdAt: Long
)

private fun parseStoredStations(raw: String?, context: Context): List<GasStation> {
    if (raw.isNullOrEmpty()) return emptyList()
    return try {
        val jsonArray = JSONArray(raw)
        mutableListOf<GasStation>().apply {
            for (index in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.optJSONObject(index) ?: continue
                val alcoholPrice = jsonObject.optDouble("alcoholPrice", Double.NaN)
                val gasolinePrice = jsonObject.optDouble("gasolinePrice", Double.NaN)

                if (alcoholPrice.isNaN() || gasolinePrice.isNaN()) continue

                add(
                    GasStation(
                        id = jsonObject.optString("id")
                            .takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                        name = jsonObject.optString("name")
                            .takeIf { it.isNotBlank() } ?: context.getString(R.string.default_station_name),
                        alcoholPrice = alcoholPrice,
                        gasolinePrice = gasolinePrice,
                        location = jsonObject.optString("location")
                            .takeIf { it.isNotBlank() } ?: context.getString(R.string.default_station_location),
                        createdAt = jsonObject.optLong("createdAt").takeIf { it != 0L }
                            ?: System.currentTimeMillis()
                    )
                )
            }
        }
    } catch (e: JSONException) {
        emptyList()
    }
}

private fun List<GasStation>.toJsonStorage(): String {
    val jsonArray = JSONArray()
    forEach { station ->
        val jsonObject = JSONObject().apply {
            put("id", station.id)
            put("name", station.name)
            put("alcoholPrice", station.alcoholPrice)
            put("gasolinePrice", station.gasolinePrice)
            put("location", station.location)
            put("createdAt", station.createdAt)
        }
        jsonArray.put(jsonObject)
    }
    return jsonArray.toString()
}

private suspend fun resolveAddressFromCoordinates(
    context: Context,
    latitude: Double,
    longitude: Double
): String = withContext(Dispatchers.IO) {
    runCatching {
        val geocoder = Geocoder(context, APP_LOCALE)
        val results = geocoder.getFromLocationCompat(latitude, longitude, 1)
        val address = results?.firstOrNull()
        address?.let {
            listOfNotNull(
                it.thoroughfare,
                it.subThoroughfare,
                it.subLocality,
                it.locality,
                it.adminArea
            ).joinToString(", ")
        }
    }.getOrNull() ?: String.format(APP_LOCALE, "%.5f, %.5f", latitude, longitude)
}

private suspend fun Geocoder.getFromLocationCompat(
    latitude: Double,
    longitude: Double,
    maxResults: Int
): List<Address>? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        suspendCancellableCoroutine { continuation ->
            getFromLocation(latitude, longitude, maxResults, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    if (continuation.isActive) {
                        continuation.resume(addresses)
                    }
                }

                override fun onError(errorMessage: String?) {
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            })
        }
    } else {
        @Suppress("DEPRECATION")
        getFromLocation(latitude, longitude, maxResults)
    }
}

fun formatCurrencyBR(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(APP_LOCALE)
    return formatter.format(value)
}

fun formatPriceInput(rawValue: String, locale: Locale = APP_LOCALE): String {
    val digitsOnly = rawValue.filter(Char::isDigit)
    val centsValue = digitsOnly.takeIf { it.isNotEmpty() }?.let { BigInteger(it) } ?: BigInteger.ZERO
    val amount = BigDecimal(centsValue, 2)

    val formatter = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
        isGroupingUsed = false
    }
    return formatter.format(amount)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CalculadoraDeCombustivelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CalculadoraDeCombustivelScreen()
                }
            }
        }
    }
}

@Composable
fun CalculadoraDeCombustivelScreen() {
    val context = LocalContext.current
    val dataStore = remember { context.fuelDataStore }
    val coroutineScope = rememberCoroutineScope()
    val defaultPriceDisplay = remember { defaultPriceText() }
    val pricePrefix = remember { currencySymbol() }

    var alcoholPrice by rememberSaveable { mutableStateOf(defaultPriceDisplay) }
    var gasolinePrice by rememberSaveable { mutableStateOf(defaultPriceDisplay) }
    var gasStationName by rememberSaveable { mutableStateOf("") }
    var gasStationLocation by rememberSaveable { mutableStateOf("") }
    var use75Percent by rememberSaveable { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf("") }
    var editingStationId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentScreenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var isRequestingLocation by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun showLocationError(message: String = context.getString(R.string.toast_location_error)) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun updateLocationFromCoordinates(latitude: Double, longitude: Double) {
        coroutineScope.launch {
            try {
                val resolvedLocation = resolveAddressFromCoordinates(context, latitude, longitude)
                gasStationLocation = resolvedLocation
            } finally {
                isRequestingLocation = false
            }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun requestCurrentLocation() {
        if (isRequestingLocation) return
        isRequestingLocation = true
        val cancellationTokenSource = CancellationTokenSource()
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFineLocation && !hasCoarseLocation) {
            isRequestingLocation = false
            showLocationError(context.getString(R.string.toast_location_permission_missing))
            return
        }

        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (location != null) {
                    cancellationTokenSource.cancel()
                    updateLocationFromCoordinates(location.latitude, location.longitude)
                } else {
                    cancellationTokenSource.cancel()
                    fusedLocationClient.lastLocation
                        .addOnSuccessListener { lastLocation ->
                            if (lastLocation != null) {
                                updateLocationFromCoordinates(lastLocation.latitude, lastLocation.longitude)
                            } else {
                                isRequestingLocation = false
                                showLocationError()
                            }
                        }
                        .addOnFailureListener {
                            isRequestingLocation = false
                            showLocationError()
                        }
                }
            }.addOnFailureListener {
                cancellationTokenSource.cancel()
                isRequestingLocation = false
                showLocationError()
            }
        } catch (securityException: SecurityException) {
            cancellationTokenSource.cancel()
            isRequestingLocation = false
            showLocationError()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            requestCurrentLocation()
        } else {
            showLocationError(context.getString(R.string.toast_location_permission_denied))
        }
    }

    fun startLocationRequest() {
        if (isRequestingLocation) return
        when {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                requestCurrentLocation()
            }

            else -> {
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    val savedStations = remember { mutableStateListOf<GasStation>() }
    var stationPendingMap by remember { mutableStateOf<GasStation?>(null) }

    val currentScreen = AppScreen.valueOf(currentScreenName)

    LaunchedEffect(dataStore) {
        dataStore.data.collectLatest { preferences ->
            val storedUse75 = preferences[FuelPreferencesKeys.use75Percent] ?: false
            if (use75Percent != storedUse75) {
                use75Percent = storedUse75
            }

            val storedStations = parseStoredStations(preferences[FuelPreferencesKeys.stations], context)
            if (storedStations != savedStations.toList()) {
                savedStations.clear()
                savedStations.addAll(storedStations)
            }
        }
    }

    fun persistStations() {
        coroutineScope.launch {
            dataStore.edit { prefs ->
                if (savedStations.isEmpty()) {
                    prefs.remove(FuelPreferencesKeys.stations)
                } else {
                    prefs[FuelPreferencesKeys.stations] = savedStations.toJsonStorage()
                }
            }
        }
    }

    fun resetForm() {
        alcoholPrice = defaultPriceDisplay
        gasolinePrice = defaultPriceDisplay
        gasStationName = ""
        gasStationLocation = ""
        editingStationId = null
    }

    fun navigateTo(screen: AppScreen) {
        currentScreenName = screen.name
    }

    fun startEditing(station: GasStation) {
        editingStationId = station.id
        gasStationName = station.name
        gasStationLocation = station.location
        alcoholPrice = formatPriceFromDouble(station.alcoholPrice)
        gasolinePrice = formatPriceFromDouble(station.gasolinePrice)
        resultMessage = ""
        stationPendingMap = null
        navigateTo(AppScreen.HOME)
    }

    fun deleteStation(station: GasStation) {
        val index = savedStations.indexOfFirst { it.id == station.id }
        if (index >= 0) {
            savedStations.removeAt(index)
            persistStations()
            if (editingStationId == station.id) {
                resetForm()
            }
            if (stationPendingMap?.id == station.id) {
                stationPendingMap = null
            }
            Toast.makeText(context, context.getString(R.string.toast_station_deleted), Toast.LENGTH_SHORT).show()
        }
    }

    fun openStationInMaps(station: GasStation) {
        val locationText = station.location.trim()
        val defaultLocation = context.getString(R.string.default_station_location)
        if (locationText.isEmpty() || locationText.equals(defaultLocation, ignoreCase = true)) {
            Toast.makeText(context, context.getString(R.string.toast_station_no_location), Toast.LENGTH_SHORT).show()
            return
        }

        val geoUri = "geo:0,0?q=${Uri.encode(locationText)}".toUri()
        val mapsIntent = Intent(Intent.ACTION_VIEW, geoUri).apply {
            setPackage("com.google.android.apps.maps")
        }

        val fallbackIntent = Intent(Intent.ACTION_VIEW, geoUri)
        val packageManager = context.packageManager
        when {
            mapsIntent.resolveActivity(packageManager) != null -> context.startActivity(mapsIntent)
            fallbackIntent.resolveActivity(packageManager) != null -> context.startActivity(fallbackIntent)
            else -> Toast.makeText(context, context.getString(R.string.toast_no_maps_app), Toast.LENGTH_SHORT).show()
        }
    }

    fun saveStation() {
        val alcoholValue = parsePriceBr(alcoholPrice)
        val gasolineValue = parsePriceBr(gasolinePrice)

        if (alcoholValue == null || gasolineValue == null) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_invalid_values),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (editingStationId == null && savedStations.size >= MAX_STATIONS) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_station_limit_reached, MAX_STATIONS),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val existingStation = editingStationId?.let { currentId ->
            savedStations.firstOrNull { it.id == currentId }
        }

        val updatedStation = GasStation(
            id = existingStation?.id ?: UUID.randomUUID().toString(),
            name = gasStationName.ifBlank { context.getString(R.string.default_station_name) },
            alcoholPrice = alcoholValue,
            gasolinePrice = gasolineValue,
            location = gasStationLocation.ifBlank { context.getString(R.string.default_station_location) },
            createdAt = existingStation?.createdAt ?: System.currentTimeMillis()
        )

        if (existingStation == null) {
            savedStations.add(0, updatedStation)
            Toast.makeText(context, context.getString(R.string.toast_station_saved), Toast.LENGTH_SHORT).show()
        } else {
            val index = savedStations.indexOfFirst { it.id == existingStation.id }
            if (index >= 0) {
                savedStations[index] = updatedStation
                Toast.makeText(context, context.getString(R.string.toast_station_updated), Toast.LENGTH_SHORT).show()
            }
        }

        persistStations()
        resetForm()
    }

    val navItems = remember { AppScreen.entries }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                navItems.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { navigateTo(screen) },
                        icon = {
                            Icon(
                                imageVector = screen.icon,
                                contentDescription = stringResource(screen.labelResId)
                            )
                        },
                        label = { Text(text = stringResource(screen.labelResId)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    pricePrefix = pricePrefix,
                    alcoholPrice = alcoholPrice,
                    onAlcoholPriceChange = { input ->
                        alcoholPrice = formatPriceInput(input)
                    },
                    gasolinePrice = gasolinePrice,
                    onGasolinePriceChange = { input ->
                        gasolinePrice = formatPriceInput(input)
                    },
                    gasStationName = gasStationName,
                    onGasStationNameChange = { gasStationName = it },
                    gasStationLocation = gasStationLocation,
                    onGasStationLocationChange = { gasStationLocation = it },
                    onRequestLocation = { startLocationRequest() },
                    isRequestingLocation = isRequestingLocation,
                    use75Percent = use75Percent,
                    onSwitchChange = { checked ->
                        use75Percent = checked
                        coroutineScope.launch {
                            dataStore.edit { prefs ->
                                prefs[FuelPreferencesKeys.use75Percent] = checked
                            }
                        }
                    },
                    onSaveStation = { saveStation() },
                    onCalculate = {
                        resultMessage = calculateBestFuel(
                            context,
                            parsePriceBr(alcoholPrice),
                            parsePriceBr(gasolinePrice),
                            use75Percent,
                            gasStationName
                        )
                    },
                    resultMessage = resultMessage,
                    isEditing = editingStationId != null,
                    onClearForm = {
                        resetForm()
                        resultMessage = ""
                    },
                    contentPadding = innerPadding
                )
            }

            AppScreen.LIST -> {
                StationListScreen(
                    stations = savedStations,
                    onStationClick = { station ->
                        val locationText = station.location.trim()
                        val defaultLocation = context.getString(R.string.default_station_location)
                        if (locationText.isEmpty() || locationText.equals(defaultLocation, ignoreCase = true)) {
                            Toast.makeText(context, context.getString(R.string.toast_station_no_location), Toast.LENGTH_SHORT).show()
                        } else {
                            stationPendingMap = station
                        }
                    },
                    onEdit = { station -> startEditing(station) },
                    onDelete = { station -> deleteStation(station) },
                    contentPadding = innerPadding
                )
            }
        }
    }

    if (currentScreen == AppScreen.LIST) {
        stationPendingMap?.let { station ->
            AlertDialog(
                onDismissRequest = { stationPendingMap = null },
                title = { Text(text = stringResource(R.string.dialog_open_maps_title)) },
                text = {
                    Text(
                        text = stringResource(R.string.dialog_open_maps_message)
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            openStationInMaps(station)
                            stationPendingMap = null
                        }
                    ) {
                        Text(text = stringResource(R.string.open))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { stationPendingMap = null }) {
                        Text(text = stringResource(R.string.cancel))
                    }
                }
            )
        }
    }
}


fun calculateBestFuel(
    context: Context,
    alcoholPrice: Double?,
    gasolinePrice: Double?,
    use75Percent: Boolean,
    gasStationName: String
): String {
    if (alcoholPrice == null || gasolinePrice == null) {
        return context.getString(R.string.calculation_error_invalid_values)
    }

    if (alcoholPrice <= 0 || gasolinePrice <= 0) {
        return context.getString(R.string.calculation_error_zero_values)
    }

    val threshold = if (use75Percent) 75.0 else 70.0
    val ratio = (alcoholPrice / gasolinePrice) * 100.0
    val ratioFormatted = String.format(APP_LOCALE, "%.1f", ratio)
    val stationInfo = if (gasStationName.isNotBlank()) {
        context.getString(R.string.calculation_station_info, gasStationName)
    } else {
        ""
    }

    return when {
        ratio <= threshold -> {
            context.getString(
                R.string.calculation_result_alcohol,
                stationInfo,
                ratioFormatted,
                threshold.toInt() )
        }

        else -> {
            context.getString(
                R.string.calculation_result_gasoline,
                stationInfo,
                ratioFormatted,
                threshold.toInt() )
        }
    }
}

fun parsePriceBr(value: String): Double? {
    return try {
        val formater = NumberFormat.getInstance(APP_LOCALE)
        formater.parse(value)?.toDouble()
    } catch (e: ParseException) {
        null
    }
}
