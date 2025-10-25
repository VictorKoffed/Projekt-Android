package com.victorkoffed.projektandroid.ui.screens.bean

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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

// Återanvändbar date formatter
@SuppressLint("ConstantLocale")
private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeanScreen(
    vm: BeanViewModel,
    onBeanClick: (Long) -> Unit // <-- ÄNDRAD PARAMETER
) {
    val beans by vm.allBeans.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    // --- BORTTAGET ---
    // var beanToEdit by remember { mutableStateOf<Bean?>(null) }
    // var beanToDelete by remember { mutableStateOf<Bean?>(null) }
    // --- SLUT BORTTAGET ---

    Scaffold(
        topBar = { TopAppBar(title = { Text("Beans") }) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = { showAddDialog = true }) { Text("Add new bean") }
            Spacer(modifier = Modifier.height(16.dp))

            if (beans.isEmpty()) {
                Text("No beans added yet.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(beans) { bean ->
                        BeanCard(
                            bean = bean,
                            onClick = { onBeanClick(bean.id) } // <-- ÄNDRAD
                            // onDeleteClick tas bort
                        )
                    }
                }
            }
        }

        // Dialog för att lägga till (Behålls)
        if (showAddDialog) {
            AddBeanDialog(
                onDismiss = { showAddDialog = false },
                onAddBean = { name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes ->
                    vm.addBean(name, roaster, roastDateStr, initialWeightStr, remainingWeight, notes)
                    showAddDialog = false
                }
            )
        }

        // --- BORTTAGET ---
        // Dialog för att redigera (Flyttad till BeanDetailScreen)
        // Dialog för att ta bort (Flyttad till BeanDetailScreen)
        // --- SLUT BORTTAGET ---
    }
}

// BeanCard (UPPDATERAD - onDeleteClick är borta)
@Composable
fun BeanCard(
    bean: Bean,
    onClick: () -> Unit
    // onDeleteClick: () -> Unit // <-- BORTTAGEN
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), // onClick är nu för navigering
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(bean.name, style = MaterialTheme.typography.titleMedium)
                bean.roaster?.let { Text("Roaster: $it", style = MaterialTheme.typography.bodyMedium) }

                // --- NY LOGIK FÖR DATUM OCH ÅLDER ---
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
                    Text("Roast Date: $dateStr $ageStr", style = MaterialTheme.typography.bodySmall)
                }
                // --- SLUT PÅ NY LOGIK ---

                Text("Remaining: %.1f g".format(bean.remainingWeightGrams), style = MaterialTheme.typography.bodyMedium)
                bean.initialWeightGrams?.let { Text("Initial: %.1f g".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
                bean.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodySmall) }
            }
            // --- BORTTAGEN ---
            // IconButton(onClick = onDeleteClick, ...)
            // --- SLUT BORTTAGEN ---
        }
    }
}


// AddBeanDialog (Oförändrad, finns kvar i BeanScreen)
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
                            contentDescription = "Select dateSelect date",
                            modifier = Modifier.clickable { datePickerDialog.show() }
                        )
                    }
                )
                OutlinedTextField(
                    value = initialWeightStr, onValueChange = { initialWeightStr = it },
                    label = { Text("Initial Weight (g)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = remainingWeightStr, onValueChange = { remainingWeightStr = it },
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
                        onAddBean(name, roaster.takeIf { it.isNotBlank() }, roastDateStr.takeIf { it.isNotBlank() }, initialWeightStr.takeIf { it.isNotBlank() }, remainingWeight, notes.takeIf { it.isNotBlank() })
                    }
                },
                enabled = name.isNotBlank() && (remainingWeightStr.toDoubleOrNull() ?: -1.0) >= 0.0
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// --- BORTTAGET ---
// EditBeanDialog (Flyttad)
// DeleteConfirmationDialog (Flyttad)
// --- SLUT BORTTAGET ---