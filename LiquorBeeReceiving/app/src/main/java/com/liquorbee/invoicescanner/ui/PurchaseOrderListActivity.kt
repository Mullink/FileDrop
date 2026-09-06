package com.liquorbee.invoicescanner.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.invoicescanner.databinding.ActivityPurchaseOrdersBinding
import com.liquorbee.invoicescanner.network.ApiClient
import com.liquorbee.invoicescanner.network.MarkDailyHabitDoneRequest
import com.liquorbee.invoicescanner.network.SessionManager
import kotlinx.coroutines.launch

// Native equivalent of the order-list half of pos-purchase-orders.ts (getInvoiceHeaders()) - same
// GetAllStagedPurchaseOrders endpoint, same fields. Full parity (create order, sync, price-change
// review, markup settings) is NOT attempted here - this is view list + tap into
// PurchaseOrderDetailActivity for viewing/receiving, the core in-store workflow.
class PurchaseOrderListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPurchaseOrdersBinding
    private lateinit var session: SessionManager
    private var fetchOrderCount: Int = FETCH_ORDER_COUNT_VALUES[0]

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPurchaseOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }
        title = "Purchase Orders"

        session = SessionManager(this)
        binding.recyclerOrders.layoutManager = LinearLayoutManager(this)

        // Matches the web's Fetch Orders dropdown exactly (orderCounts in pos-purchase-orders.ts) -
        // same options, same default (25).
        binding.spinnerFetchCount.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, FETCH_ORDER_COUNT_LABELS)
        binding.spinnerFetchCount.setSelection(FETCH_ORDER_COUNT_VALUES.indexOf(fetchOrderCount))
        binding.spinnerFetchCount.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newCount = FETCH_ORDER_COUNT_VALUES[position]
                if (newCount != fetchOrderCount) {
                    fetchOrderCount = newCount
                    load()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        load()
        markReceivingHabitDone()
    }

    // Matches the web's pos-purchase-orders.ts ngOnInit exactly: this screen already eagerly loads
    // staged orders regardless of how the user navigated here - that alone satisfies the "Receive
    // Orders" daily habit for today. Best-effort/fire-and-forget, same as the web (no success or
    // error handling - a failed habit ping should never surface to the user or block anything).
    private fun markReceivingHabitDone() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.markDailyHabitDone(MarkDailyHabitDoneRequest("Receiving"))
            } catch (e: Exception) {
                // best effort
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Picks up any change made in the detail screen (save/receive/delete) when navigating back.
        load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textError.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val orders = api.getAllStagedPurchaseOrders(fetchOrderCount)
                binding.progressLoading.visibility = View.GONE
                binding.textEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                binding.textOrderCount.text = "${orders.size}"
                binding.recyclerOrders.adapter = PurchaseOrderListAdapter(orders) { order ->
                    startActivity(Intent(this@PurchaseOrderListActivity, PurchaseOrderDetailActivity::class.java)
                        .putExtra(EXTRA_INVOICE_ID, order.invoiceId))
                }
            } catch (e: Exception) {
                binding.progressLoading.visibility = View.GONE
                binding.textOrderCount.text = ""
                binding.textError.visibility = View.VISIBLE
                binding.textError.text = "Failed to load purchase orders: ${e.message}"
            }
        }
    }

    companion object {
        const val EXTRA_INVOICE_ID = "invoiceId"
        // Matches the web's orderCounts exactly (pos-purchase-orders.ts) - "ALL" uses the same
        // 9999999 sentinel value the web sends for GetAllStagedPurchaseOrders' fetchOrders param.
        private val FETCH_ORDER_COUNT_VALUES = listOf(25, 50, 100, 150, 200, 250, 9999999)
        private val FETCH_ORDER_COUNT_LABELS = listOf("25", "50", "100", "150", "200", "250", "ALL")
    }
}
