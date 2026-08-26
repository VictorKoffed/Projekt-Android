package com.victorkoffed.projektandroid.ui.screens.bean

import android.annotation.SuppressLint
import android.app.DatePickerDialog
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.victorkoffed.projektandroid.data.db.Bean
import com.victorkoffed.projektandroid.ui.viewmodel.bean.BeanViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@SuppressLint("ConstantLocale")
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

/**
 * Screen presenting an inventory overview of coffee beans.
 * Organizes stock into active and archived partitions, handles manual creation dialogs,
 * and exposes entry points to detailed bean analytics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeanScreen(
    vm: BeanViewModel,
    onBeanClick: (Long) -> Unit,
    onMenuClick: () -> Unit
) {
    val activeBeans by vm.allBeans.collectAsState()
    val archivedBeans by vm.archivedBeans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Beans") },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showAddDialog = true }, modifier = Modifier.padding(top = 16.dp)) {
                Text("Add new bean")
            }
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (activeBeans.isEmpty()) {
                    item {
                        Text(
                            "No active beans added yet.",
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    items(activeBeans, key = { it.id }) { bean ->
                        BeanCard(
                            bean = bean,
                            onClick = { onBeanClick(bean.id) }
                        )
                    }
                }

                if (archivedBeans.isNotEmpty()) {
                    item {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 16.dp),
                            thickness = DividerDefaults.Thickness,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "Archived Beans",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(archivedBeans, key = { "archived-${it.id}" }) { bean ->
                        BeanCard(
                            bean = bean,
                            isArchived = true,
                            onClick = { onBeanClick(bean.id) }
                        )
                    }
                }
            }
        }

        if (showAddDialog) {
            AddBeanDialog(
                onDismiss = { showAddDialog = false },
                onAddBean = { name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes ->
                    vm.addBean(name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes)
                    showAddDialog = false
                }
            )
        }
    }
}

/**
 * Summary card for an individual coffee bean.
 * Applies muted visual styling for archived entities to distinguish depleted inventory.
 */
@Composable
fun BeanCard(
    bean: Bean,
    isArchived: Boolean = false,
    onClick: () -> Unit
) {
    val cardAlpha = if (isArchived) 0.7f else 1.0f
    val textColor = if (isArchived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isArchived) 1.dp else 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = cardAlpha)
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bean.name, style = MaterialTheme.typography.titleMedium, color = textColor)
                bean.roaster?.let { Text("Roaster: $it", style = MaterialTheme.typography.bodyMedium, color = textColor) }

                bean.roastDate?.let { roastDate ->
                    val diffMillis = System.currentTimeMillis() - roastDate.time
                    val daysOld = TimeUnit.MILLISECONDS.toDays(diffMillis)
                    val dateStr = dateFormat.format(roastDate)
                    val ageStr = when {
                        daysOld < 0 -> "(Future date)"
                        daysOld == 0L -> "(Roasted today)"
                        daysOld == 1L -> "(1 day old)"
                        else -> "($daysOld days old)"
                    }
                    Text("Roast Date: $dateStr $ageStr", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.8f))
                }

                if (!isArchived) {
                    Text("Remaining: %.1f g".format(bean.remainingWeightGrams), style = MaterialTheme.typography.bodyMedium, color = textColor)
                }
                bean.initialWeightGrams?.let { Text("Initial: %.1f g".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline.copy(alpha = cardAlpha)) }
                bean.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.8f)) }
            }
            if (isArchived) {
                Icon(
                    Icons.Default.Unarchive,
                    contentDescription = "Archived (Tap to view)",
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * Input dialog for registering new coffee beans.
 * Enforces validation rules ensuring mandatory name presence and non-negative initial stock.
 */
@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBeanDialog(
    onDismiss: () -> Unit,
    onAddBean: (name: String, roaster: String?, roastDateStr: String?, initialWeightStr: String?, remainingWeight: Double, notes: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var roaster by remember { mutableStateOf("") }
    var roastDateStr by remember { mutableStateOf("") }
    var initialWeightStr by remember { mutableStateOf("") }
    var remainingWeightStr by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    val context = LocalContext.current
    val calendar = Calendar.getInstance()
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = remember {
        DatePickerDialog(
            context,
            { _, selectedYear: Int, selectedMonth: Int, selectedDayOfMonth: Int ->
                roastDateStr = "$selectedYear-${String.format("%02d", selectedMonth + 1)}-${String.format("%02d", selectedDayOfMonth)}"
            },
            year,
            month,
            day
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add new bean") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name *") }, singleLine = true)
                OutlinedTextField(value = roaster, onValueChange = { roaster = it }, label = { Text("Roaster") }, singleLine = true)
                OutlinedTextField(
                    value = roastDateStr,
                    onValueChange = {},
                    label = { Text("Roast Date: (yyyy-mm-dd)") },
                    placeholder = { Text("Select date...") },
                    singleLine = true,
                    readOnly = true,
                    modifier = Modifier.clickable(onClick = {
                        datePickerDialog.show()
                    }),
                    trailingIcon = {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = "Select date",
                            modifier = Modifier.clickable { datePickerDialog.show() }
                        )
                    }
                )
                OutlinedTextField(
                    value = initialWeightStr,
                    onValueChange = { newValue ->
                        initialWeightStr = newValue
                        remainingWeightStr = newValue
                    },
                    label = { Text("Initial Weight (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = remainingWeightStr,
                    onValueChange = { remainingWeightStr = it },
                    label = { Text("Current Weight (g) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes") })
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val remainingWeight = remainingWeightStr.toDoubleOrNull()
                    if (name.isNotBlank() && remainingWeight != null && remainingWeight >= 0) {
                        onAddBean(
                            name,
                            roaster.takeIf { it.isNotBlank() },
                            roastDateStr.takeIf { it.isNotBlank() },
                            initialWeightStr.takeIf { it.isNotBlank() },
                            remainingWeight,
                            notes.takeIf { it.isNotBlank() }
                        )
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}