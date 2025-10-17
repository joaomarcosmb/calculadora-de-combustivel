package org.pdm.calculadoradecombustivel

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pdm.calculadoradecombustivel.ui.theme.CalculadoraDeCombustivelTheme
import java.util.Locale
import java.text.NumberFormat
import java.text.ParseException

val BR_LOCALE: Locale = Locale.Builder()
    .setLanguage("pt")
    .setRegion("BR")
    .build()

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
    var alcoholPrice by rememberSaveable { mutableStateOf("") }
    var gasolinePrice by rememberSaveable { mutableStateOf("") }
    var gasStationName by rememberSaveable { mutableStateOf("") }
    var use75Percent by rememberSaveable { mutableStateOf(false) }
    var resultMessage by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Calculadora de Combustível",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = alcoholPrice,
                onValueChange = { alcoholPrice = it },
                label = { Text("Preço do álcool (R$)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            OutlinedTextField(
                value = gasolinePrice,
                onValueChange = { gasolinePrice = it },
                label = { Text("Preço da gasolina (R$)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
        }


        OutlinedTextField(
            value = gasStationName,
            onValueChange = { gasStationName = it },
            label = { Text("Nome do posto (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (use75Percent) "75%" else "70%",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Switch(
                checked = use75Percent,
                onCheckedChange = { use75Percent = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                    uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                    uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (resultMessage.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = resultMessage,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Button(
            onClick = {
                resultMessage = calcularMelhorCombustivel(
                    convPrecoBR(alcoholPrice),
                    convPrecoBR(gasolinePrice),
                    use75Percent,
                    gasStationName
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Calcular",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Calculadora de combustível - By João Marcos Moura",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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