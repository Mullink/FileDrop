package com.liquorbee.invoicescanner.ui

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.databinding.ActivityVendorManagementBinding
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.CreateVendorOnlyRequest
import com.liquorbee.invoicescanner.network.CustomerInvoiceTemplateListItemDto
import com.liquorbee.invoicescanner.network.SessionManager
import com.liquorbee.invoicescanner.network.SetTemplateActiveRequest
import kotlinx.coroutines.launch

/**
 * Show/hide (activate/deactivate) which vendors appear in ScanActivity's vendor picker - reuses
 * CustomerInvoiceTemplates/GetTemplates + SetTemplateActive, both already live on the backend.
 * "Add Vendor" is deliberately just a name (CreateVendorOnly) - templates no longer carry a
 * sample-image/field-mapping extraction hint, so there's nothing else to collect here.
 */
class VendorManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVendorManagementBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)
        title = "Manage Vendors"

        session = SessionManager(this)
        binding.recyclerVendors.layoutManager = LinearLayoutManager(this)
        binding.buttonAddVendor.setOnClickListener { promptAddVendor() }
        binding.textBack.setOnClickListener { finish() }

        loadVendors()
    }

    private fun loadVendors() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textError.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val vendors = api.getTemplates().sortedBy { it.vendorName.lowercase() }
                binding.recyclerVendors.adapter = VendorManagementAdapter(vendors) { vendor, isChecked ->
                    toggleVendor(vendor, isChecked)
                }
                binding.progressLoading.visibility = View.GONE
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Could not load vendors: ${e.message}"
            }
        }
    }

    private fun toggleVendor(vendor: CustomerInvoiceTemplateListItemDto, isChecked: Boolean) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.setTemplateActive(vendor.id, SetTemplateActiveRequest(isChecked))
                // Reload rather than mutate in place - CustomerInvoiceTemplateListItemDto's
                // isActive is a val (mirrors the server response shape as-is).
                loadVendors()
            } catch (e: Exception) {
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Failed to update ${vendor.vendorName}: ${e.message}"
                loadVendors() // revert the switch to the real server state
            }
        }
    }

    private fun promptAddVendor() {
        val input = EditText(this).apply { hint = "Vendor name" }
        AlertDialog.Builder(this)
            .setTitle("Add Vendor")
            .setView(input)
            .setPositiveButton("Add") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) addVendor(name)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addVendor(vendorName: String) {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.createVendorOnly(CreateVendorOnlyRequest(vendorName))
                loadVendors()
            } catch (e: Exception) {
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Failed to add vendor: ${e.message}"
            }
        }
    }
}
