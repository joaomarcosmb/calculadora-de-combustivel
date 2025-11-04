package org.pdm.calculadoradecombustivel

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.pdm.calculadoradecombustivel.data.FuelPreferencesKeys
import org.pdm.calculadoradecombustivel.data.fuelDataStore
import org.pdm.calculadoradecombustivel.ui.theme.CalculadoraDeCombustivelTheme
import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

val BR_LOCALE: Locale = Locale.Builder()
    .setLanguage("pt")
    .setRegion("BR")
    .build()

private const val MAX_STATIONS = 10

private enum class AppScreen(val label: String, val icon: ImageVector) {
    HOME(label = "Início", icon = Icons.Filled.Home),
    LIST(label = "Postos", icon = Icons.AutoMirrored.Filled.List)
}

data class GasStation(
    val id: String,
    val name: String,
    val alcoholPrice: Double,
    val gasolinePrice: Double,
    val location: String,
    val createdAt: Long
)

private fun parseStoredStations(raw: String?): List<GasStation> {
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
                            .takeIf { it.isNotBlank() } ?: "Posto sem nome",
                        alcoholPrice = alcoholPrice,
                        gasolinePrice = gasolinePrice,
                        location = jsonObject.optString("location")
                            .takeIf { it.isNotBlank() } ?: "Localização não informada",
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
        val geocoder = Geocoder(context, BR_LOCALE)
        val results = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            geocoder.getFromLocation(latitude, longitude, 1)
        } else {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(latitude, longitude, 1)
        }
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
    }.getOrNull() ?: String.format(BR_LOCALE, "%.5f, %.5f", latitude, longitude)
}

fun formatCurrencyBR(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(BR_LOCALE)
    return formatter.format(value)
}

private fun formatNumberForInput(value: Double): String {
    val formatter = NumberFormat.getNumberInstance(BR_LOCALE).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 3
    }
    return formatter.format(value)
}

fun formatDateBR(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", BR_LOCALE)
    return formatter.format(Date(timestamp))
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

    var alcoholPrice by rememberSaveable { mutableStateOf("") }
    var gasolinePrice by rememberSaveable { mutableStateOf("") }
    var gasStationName by rememberSaveable { mutableStateOf("") }
    var gasStationLocation by rememberSaveable { mutableStateOf("") }
    var use75Percent by rememberSaveable { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf("") }
    var editingStationId by rememberSaveable { mutableStateOf<String?>(null) }
    var currentScreenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var isRequestingLocation by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun showLocationError(message: String = "Não foi possível obter a localização.") {
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
            showLocationError("Permissão de localização ausente.")
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
            showLocationError("Permissão de localização negada.")
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
    var selectedStation by remember { mutableStateOf<GasStation?>(null) }

    val currentScreen = AppScreen.valueOf(currentScreenName)

    LaunchedEffect(dataStore) {
        dataStore.data.collectLatest { preferences ->
            val storedUse75 = preferences[FuelPreferencesKeys.use75Percent] ?: false
            if (use75Percent != storedUse75) {
                use75Percent = storedUse75
            }

            val storedStations = parseStoredStations(preferences[FuelPreferencesKeys.stations])
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
        alcoholPrice = ""
        gasolinePrice = ""
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
        alcoholPrice = formatNumberForInput(station.alcoholPrice)
        gasolinePrice = formatNumberForInput(station.gasolinePrice)
        resultMessage = ""
        selectedStation = null
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
            if (selectedStation?.id == station.id) {
                selectedStation = null
            }
            Toast.makeText(context, "Posto excluído.", Toast.LENGTH_SHORT).show()
        }
    }

    fun saveStation() {
        val alcoholValue = convPrecoBR(alcoholPrice)
        val gasolineValue = convPrecoBR(gasolinePrice)

        if (alcoholValue == null || gasolineValue == null) {
            Toast.makeText(
                context,
                "Informe valores válidos para salvar o posto.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        if (editingStationId == null && savedStations.size >= MAX_STATIONS) {
            Toast.makeText(
                context,
                "Limite de $MAX_STATIONS postos atingido.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val existingStation = editingStationId?.let { currentId ->
            savedStations.firstOrNull { it.id == currentId }
        }

        val updatedStation = GasStation(
            id = existingStation?.id ?: UUID.randomUUID().toString(),
            name = gasStationName.ifBlank { "Posto sem nome" },
            alcoholPrice = alcoholValue,
            gasolinePrice = gasolineValue,
            location = gasStationLocation.ifBlank { "Localização não informada" },
            createdAt = existingStation?.createdAt ?: System.currentTimeMillis()
        )

        if (existingStation == null) {
            savedStations.add(0, updatedStation)
            Toast.makeText(context, "Posto salvo com sucesso!", Toast.LENGTH_SHORT).show()
        } else {
            val index = savedStations.indexOfFirst { it.id == existingStation.id }
            if (index >= 0) {
                savedStations[index] = updatedStation
                Toast.makeText(context, "Posto atualizado!", Toast.LENGTH_SHORT).show()
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
                                contentDescription = screen.label
                            )
                        },
                        label = { Text(text = screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            AppScreen.HOME -> {
                HomeScreen(
                    alcoholPrice = alcoholPrice,
                    onAlcoholPriceChange = { alcoholPrice = it },
                    gasolinePrice = gasolinePrice,
                    onGasolinePriceChange = { gasolinePrice = it },
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
                        resultMessage = calcularMelhorCombustivel(
                            convPrecoBR(alcoholPrice),
                            convPrecoBR(gasolinePrice),
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
                    onStationClick = { station -> selectedStation = station },
                    onEdit = { station -> startEditing(station) },
                    onDelete = { station -> deleteStation(station) },
                    contentPadding = innerPadding
                )
            }
        }
    }

    if (currentScreen == AppScreen.LIST) {
        selectedStation?.let { station ->
            AlertDialog(
                onDismissRequest = { selectedStation = null },
                title = { Text(text = station.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = "Álcool: ${formatCurrencyBR(station.alcoholPrice)}")
                        Text(text = "Gasolina: ${formatCurrencyBR(station.gasolinePrice)}")
                        Text(text = "Localização: ${station.location}")
                        Text(text = "Data do cadastro: ${formatDateBR(station.createdAt)}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedStation = null }) {
                        Text(text = "Fechar")
                    }
                }
            )
        }
    }
}


fun calcularMelhorCombustivel(
    alcoholPrice: Double?,
    gasolinePrice: Double?,
    use75Percent: Boolean,
    gasStationName: String
): String {
    if (alcoholPrice == null || gasolinePrice == null) {
        return "Por favor, insira valores válidos para ambos os combustíveis."
    }

    if (alcoholPrice <= 0 || gasolinePrice <= 0) {
        return "Os preços devem ser maiores que zero."
    }

    val threshold = if (use75Percent) 75.0 else 70.0
    val ratio = (alcoholPrice / gasolinePrice) * 100.0
    val ratioFormatted = String.format(BR_LOCALE, "%.1f", ratio)
    val stationInfo = if (gasStationName.isNotBlank()) " no posto $gasStationName" else ""

    return when {
        ratio <= threshold -> {
            "✅ Abasteça com ÁLCOOL$stationInfo!\n" +
                    "O álcool está ${ratioFormatted}% do preço da gasolina, " +
                    "abaixo do limite de ${(threshold).toInt()}%."
        }

        else -> {
            "✅ Abasteça com GASOLINA$stationInfo!\n" +
                    "O álcool está ${ratioFormatted}% do preço da gasolina, " +
                    "acima do limite de ${(threshold).toInt()}%."
        }
    }
}

fun convPrecoBR(value: String): Double? {
    return try {
        val formatador = NumberFormat.getInstance(BR_LOCALE)
        formatador.parse(value)?.toDouble()
    } catch (e: ParseException) {
        null
    }
}
