package org.pdm.calculadoradecombustivel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.pdm.calculadoradecombustivel.ui.theme.extendedColors
import androidx.compose.ui.res.stringResource

@Composable
fun HomeScreen(
    pricePrefix: String,
    alcoholPrice: String,
    onAlcoholPriceChange: (String) -> Unit,
    gasolinePrice: String,
    onGasolinePriceChange: (String) -> Unit,
    gasStationName: String,
    onGasStationNameChange: (String) -> Unit,
    gasStationLocation: String,
    onGasStationLocationChange: (String) -> Unit,
    onRequestLocation: () -> Unit,
    isRequestingLocation: Boolean,
    use75Percent: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    onSaveStation: () -> Unit,
    onCalculate: () -> Unit,
    resultMessage: String,
    isEditing: Boolean,
    onClearForm: () -> Unit,
    contentPadding: PaddingValues
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.home_title),
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
                    onValueChange = onAlcoholPriceChange,
                    label = { Text(stringResource(R.string.home_alcohol_price_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    prefix = { Text(pricePrefix) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                OutlinedTextField(
                    value = gasolinePrice,
                    onValueChange = onGasolinePriceChange,
                    label = { Text(stringResource(R.string.home_gasoline_price_label)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    prefix = { Text(pricePrefix) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }

            OutlinedTextField(
                value = gasStationName,
                onValueChange = onGasStationNameChange,
                label = { Text(stringResource(R.string.home_station_name_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            OutlinedTextField(
                value = gasStationLocation,
                onValueChange = onGasStationLocationChange,
                label = { Text(stringResource(R.string.home_station_location_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (isRequestingLocation) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = onRequestLocation) {
                            Icon(
                                imageVector = Icons.Filled.LocationOn,
                                contentDescription = stringResource(R.string.home_use_my_location_desc)
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "70%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Switch(
                    checked = use75Percent,
                    onCheckedChange = onSwitchChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                        uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.padding(10.dp, 0.dp)
                )

                Text(
                    text = "75%",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = if (isEditing) onClearForm else onCalculate,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isEditing) {
                            MaterialTheme.extendedColors.dangerButtonBackground
                        } else {
                            MaterialTheme.colorScheme.secondary
                        },
                        contentColor = MaterialTheme.extendedColors.mainButtonContent
                    )
                ) {
                    Text(
                        text = if (isEditing) stringResource(R.string.cancel) else stringResource(R.string.home_calculate_button),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isEditing) {
                    Button(
                        onClick = onSaveStation,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary,
                            contentColor = Color.Black
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.home_update_station_button),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (resultMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
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
        }

        if (!isEditing) {
            FloatingActionButton(
                onClick = onSaveStation,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.extendedColors.neutralButtonContent
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.home_add_station_fab_desc)
                )
            }
        }
    }
}
