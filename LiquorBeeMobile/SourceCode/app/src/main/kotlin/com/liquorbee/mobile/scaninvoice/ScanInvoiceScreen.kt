package com.liquorbee.mobile.scaninvoice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liquorbee.mobile.core.network.HumanQueueVendorDto
import com.liquorbee.mobile.core.ui.LiquorBeeBlue
import com.liquorbee.mobile.core.ui.LiquorBeeNavy

@Composable
fun ScanInvoiceScreen(viewModel: ScanInvoiceViewModel, onBack: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        viewModel.onCameraResult(success)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            cameraLauncher.launch(viewModel.createCameraCaptureUri())
        }
    }
    fun launchCamera() {
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            cameraLauncher.launch(viewModel.createCameraCaptureUri())
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Invoice") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LiquorBeeNavy, titleContentColor = LiquorBeeBlue)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                if (uiState.successMessage != null) {
                    SuccessBanner(message = uiState.successMessage!!, onDismiss = viewModel::dismissMessage)
                }
            }

            item {
                StepCard(step = 1, title = "Select Vendor", subtitle = "Choose the vendor for this invoice") {
                    VendorDropdown(
                        vendors = uiState.vendors,
                        selectedVendorId = uiState.selectedVendorId,
                        loading = uiState.vendorsLoading,
                        onVendorSelected = viewModel::onVendorSelected,
                        onNewVendorRequested = viewModel::openNewVendorDialog
                    )
                }
            }

            item {
                StepCard(step = 2, title = "Invoice Photos", subtitle = "Add every page of the invoice") {
                    // Camera-only by design - no gallery/upload picker, so every photo submitted
                    // here is guaranteed to be a fresh capture of the physical invoice in hand.
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextButton(onClick = { launchCamera() }) {
                            Icon(Icons.Filled.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.padding(start = 4.dp))
                            Text("Take Photo")
                        }
                    }

                    if (uiState.processingPhoto) {
                        Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            Text("Processing photo…", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    if (uiState.pages.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.pages.size) { index ->
                                val page = uiState.pages[index]
                                Box(modifier = Modifier.size(90.dp)) {
                                    page.thumbnail?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.LightGray, RoundedCornerShape(8.dp))
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removePage(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50))
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                StepCard(step = 3, title = "Submit", subtitle = "Your invoice will be queued and staged for you shortly") {
                    if (uiState.error != null) {
                        Text(uiState.error!!, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    androidx.compose.material3.Button(
                        onClick = viewModel::submit,
                        enabled = uiState.canSubmit,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (uiState.submitting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Submit for Scanning")
                        }
                    }
                }
            }
        }
    }

    if (uiState.showNewVendorDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissNewVendorDialog,
            title = { Text("New Vendor") },
            text = {
                OutlinedTextField(
                    value = uiState.newVendorName,
                    onValueChange = viewModel::onNewVendorNameChanged,
                    label = { Text("Vendor Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::createVendor, enabled = uiState.newVendorName.isNotBlank() && !uiState.creatingVendor) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissNewVendorDialog) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun StepCard(step: Int, title: String, subtitle: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(LiquorBeeBlue, RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("$step", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
                Spacer(modifier = Modifier.padding(start = 10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.padding(top = 12.dp))
            content()
        }
    }
}

@Composable
private fun VendorDropdown(
    vendors: List<HumanQueueVendorDto>,
    selectedVendorId: Int?,
    loading: Boolean,
    onVendorSelected: (Int) -> Unit,
    onNewVendorRequested: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = vendors.firstOrNull { it.id == selectedVendorId }?.vendorName ?: ""

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            label = { Text("Vendor") },
            trailingIcon = {
                if (loading) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.ExpandMore, contentDescription = null)
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            vendors.forEach { vendor ->
                DropdownMenuItem(
                    text = { Text(vendor.vendorName) },
                    onClick = {
                        onVendorSelected(vendor.id)
                        expanded = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("+ New Vendor…", fontWeight = FontWeight.Bold, color = LiquorBeeBlue) },
                onClick = {
                    expanded = false
                    onNewVendorRequested()
                }
            )
        }
    }
}

@Composable
private fun SuccessBanner(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F8EE)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(message, color = Color(0xFF1E7E42), modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color(0xFF1E7E42))
            }
        }
    }
}
