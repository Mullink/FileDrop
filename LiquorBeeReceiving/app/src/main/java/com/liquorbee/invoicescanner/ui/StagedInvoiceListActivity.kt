package com.liquorbee.invoicescanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.databinding.ActivityStagedInvoicesBinding
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.launch

// Native equivalent of ocr-staged-invoice-list.component.ts - same GetStagedInvoices endpoint,
// same fields, tap a row to open StagedInvoiceDetailActivity (matches openDetail() there).
class StagedInvoiceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStagedInvoicesBinding
    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStagedInvoicesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }
        title = "Scanned Invoices"

        session = SessionManager(this)
        binding.recyclerInvoices.layoutManager = LinearLayoutManager(this)

        load()
    }

    override fun onResume() {
        super.onResume()
        // Picks up any change made in the detail screen (save/push/reprice) when navigating back.
        load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textError.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val invoices = api.getStagedInvoices()
                binding.progressLoading.visibility = View.GONE
                binding.textEmpty.visibility = if (invoices.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerInvoices.adapter = StagedInvoiceListAdapter(invoices) { invoice ->
                    startActivity(Intent(this@StagedInvoiceListActivity, StagedInvoiceDetailActivity::class.java)
                        .putExtra(EXTRA_INVOICE_HEADER_ID, invoice.id))
                }
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Failed to load staged invoices: ${e.message}"
            }
        }
    }

    companion object {
        const val EXTRA_INVOICE_HEADER_ID = "invoiceHeaderId"
    }
}
