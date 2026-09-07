package com.liquorbee.wholesale.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.wholesale.databinding.ActivityOrderDetailBinding
import com.liquorbee.wholesale.network.readableMessage
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.PatchManagementOrderDto
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.network.WholesaleOpenOrderDto
import com.liquorbee.wholesale.printing.PrintHelper
import com.liquorbee.wholesale.ui.wholesale.orderStatusLabel
import kotlinx.coroutines.launch

const val EXTRA_ORDER_ID = "extra_order_id"

/** SubCustomers/GetManagementOrderDetail - view/edit/delete a single wholesale order. Only these
 * two actions exist server-side for this screen (no accept/ready-for-pickup/delivery-fee/payment-
 * link endpoints - confirmed against the backend, they simply don't exist yet). */
class OrderDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderDetailBinding
    private lateinit var session: SessionManager
    private var orderId: String = ""
    private var order: WholesaleOpenOrderDto? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOrderDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }

        session = SessionManager(this)
        orderId = intent.getStringExtra(EXTRA_ORDER_ID) ?: ""
        binding.recyclerLines.layoutManager = LinearLayoutManager(this)
        binding.buttonSave.setOnClickListener { saveChanges() }
        binding.buttonDelete.setOnClickListener { confirmDelete() }
        binding.buttonPrint.setOnClickListener { printReceipt() }

        load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val detail = api.getManagementOrderDetail(orderId)
                order = detail
                render(detail)
            } catch (e: Exception) {
                showMessage("Failed to load this order: ${e.readableMessage()}", isError = true)
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    private fun render(o: WholesaleOpenOrderDto) {
        binding.textOrderNumber.text = o.orderNumber ?: o.orderId ?: "(no order #)"
        binding.textStatus.text = orderStatusLabel(o.orderStatus)
        binding.textStatus.setBackgroundColor(Color.parseColor(if (o.orderStatus == 2) "#2E6B4F" else if (o.orderStatus >= 3) "#888888" else "#3B6FB0"))
        binding.textCustomer.text = listOfNotNull(o.customerName, o.customerEmail, o.customerPhone).filter { it.isNotBlank() }.joinToString(" • ").ifEmpty { "—" }
        binding.textFinancials.text = "Total: $%.2f   Paid: $%.2f   Balance: $%.2f".format(o.total, o.amountPaid, o.balance)
        binding.textDate.text = o.createdDate?.substringBefore('T')?.substringBefore(' ') ?: ""
        binding.editNotes.setText(o.notes ?: "")
        binding.recyclerLines.adapter = com.liquorbee.wholesale.ui.OrderDetailLineAdapter(o.items ?: emptyList())
    }

    private fun saveChanges() {
        val o = order ?: return
        if (saving) return
        saving = true
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.patchManagementOrder(PatchManagementOrderDto(
                    orderId = orderId,
                    items = o.items ?: emptyList(),
                    notes = binding.editNotes.text.toString()
                ))
                saving = false
                showMessage("Changes saved.", isError = false)
                load()
            } catch (e: Exception) {
                saving = false
                showMessage("Failed to save changes: ${e.readableMessage()}", isError = true)
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Order")
            .setMessage("Delete this order? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteOrder() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteOrder() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.deleteManagementOrder(orderId)
                finish()
            } catch (e: Exception) {
                showMessage("Failed to delete this order: ${e.readableMessage()}", isError = true)
            }
        }
    }

    private fun printReceipt() {
        val o = order ?: return
        lifecycleScope.launch {
            val storeName = try {
                ApiClient.buildAuthenticatedApi(session).getAccountSettings().storeName
            } catch (e: Exception) { null }
            PrintHelper.printOrder(this@OrderDetailActivity, o, storeName) { success, message ->
                showMessage(message, isError = !success)
            }
        }
    }

    private fun showMessage(text: String, isError: Boolean) {
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.text = text
        binding.textMessage.setTextColor(if (isError) 0xFFC62828.toInt() else 0xFF2E7D32.toInt())
    }
}
