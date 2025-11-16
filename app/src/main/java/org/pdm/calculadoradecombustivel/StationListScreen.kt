package org.pdm.calculadoradecombustivel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

@Composable
fun StationListScreen(
    stations: List<GasStation>,
    onStationClick: (GasStation) -> Unit,
    onEdit: (GasStation) -> Unit,
    onDelete: (GasStation) -> Unit,
    contentPadding: PaddingValues
) {
    var stationPendingDeletion by remember { mutableStateOf<GasStation?>(null) }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.list_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        if (stations.isEmpty()) {
            Text(
                text = stringResource(R.string.list_empty_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(stations, key = { it.id }) { station ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onStationClick(station) }
                            ) {
                                Text(
                                    text = station.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Text(text = stringResource(R.string.list_alcohol_price, formatCurrencyBR(station.alcoholPrice)))
                                Text(text = stringResource(R.string.list_gasoline_price, formatCurrencyBR(station.gasolinePrice)))
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy((-10).dp)) {
                                IconButton(onClick = { onEdit(station) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = stringResource(R.string.list_edit_station_desc)
                                    )
                                }
                                IconButton(onClick = { stationPendingDeletion = station }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = stringResource(R.string.list_delete_station_desc)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    stationPendingDeletion?.let { station ->
        val stationName = station.name.ifBlank {
            stringResource(R.string.list_delete_dialog_default_name)
        }

        AlertDialog(
            onDismissRequest = { stationPendingDeletion = null },
            title = { Text(text = stringResource(R.string.list_delete_dialog_title)) },
            text = {
                Text(
                    text = stringResource(R.string.list_delete_dialog_message, stationName)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(station)
                        stationPendingDeletion = null
                    }
                ) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { stationPendingDeletion = null }) {
                    Text(text = stringResource(R.string.cancel))
                }
            }
        )
    }
}
