package com.liquorbee.wholesale.ui.wholesale

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.liquorbee.wholesale.databinding.FragmentPlaceOrderBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.CreateGeneralOrderDto
import com.liquorbee.wholesale.network.CreateSubCustomerDraftOrderDto
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.network.SubCustomerCatalogItemDto
import com.liquorbee.wholesale.network.SubCustomerOrderItemDto
import com.liquorbee.wholesale.network.SubCustomerRequestDto
import kotlinx.coroutines.launch

private const val GENERAL_CUSTOMER_LABEL = "General Customer (Retail)"

/** Browse the wholesale catalog and place a draft order - on behalf of a linked account (real
 * negotiated pricing) or as a "General Customer" retail order. Same dedicated catalog endpoint
 * (SubCustomers/GetCatalog) the web uses - not the shared Products/SearchQuanticInventory. */
class PlaceOrderFragment : Fragment() {

    private var _binding: FragmentPlaceOrderBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    private var linkedRequests: List<SubCustomerRequestDto> = emptyList()
    private var currentCustomerId: Int = 0
    private var allItems: List<SubCustomerCatalogItemDto> = emptyList()
    private var adapter: CatalogAdapter? = null
    private var submitting = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaceOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        binding.recyclerCatalog.layoutManager = LinearLayoutManager(requireContext())
        binding.buttonLoadCatalog.setOnClickListener { loadCatalog() }
        binding.buttonSubmitOrder.setOnClickListener { submitOrder() }
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString().orEmpty()) }
        })
        loadAccounts()
    }

    private fun loadAccounts() {
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                currentCustomerId = api.getCurrentCustomerId()
                val data = api.getManagementView()
                linkedRequests = data.requests.filter { it.status == "Linked" }

                val labels = listOf(GENERAL_CUSTOMER_LABEL) + linkedRequests.map {
                    it.toBusinessName ?: it.recipientBusinessName ?: it.toEmailAddress ?: "(Unnamed account)"
                }
                binding.spinnerAccount.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
            } catch (e: Exception) {
                showMessage("Failed to load linked accounts: ${e.message}", isError = true)
            }
        }
    }

    private fun selectedRequest(): SubCustomerRequestDto? {
        val index = binding.spinnerAccount.selectedItemPosition
        return if (index in 1..linkedRequests.size) linkedRequests[index - 1] else null
    }

    private fun loadCatalog() {
        val request = selectedRequest()
        val customerId = request?.fromId ?: currentCustomerId
        val priceListId = request?.priceListId ?: ""

        binding.progressLoading.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                allItems = api.getCatalog(customerId, priceListId)
                adapter = CatalogAdapter(allItems, useSubCustomerPrice = request != null) { updateCartSummary() }
                binding.recyclerCatalog.adapter = adapter
                binding.textEmpty.visibility = if (allItems.isEmpty()) View.VISIBLE else View.GONE
                binding.textEmpty.text = "No items in this catalog."
                updateCartSummary()
            } catch (e: Exception) {
                showMessage("Failed to load catalog: ${e.message}", isError = true)
            } finally {
                binding.progressLoading.visibility = View.GONE
            }
        }
    }

    private fun applyFilter(query: String) {
        val request = selectedRequest()
        val filtered = if (query.isBlank()) allItems else allItems.filter {
            (it.itemName?.contains(query, ignoreCase = true) == true) ||
                (it.itemCode?.contains(query, ignoreCase = true) == true) ||
                (it.upc?.contains(query, ignoreCase = true) == true)
        }
        adapter = CatalogAdapter(filtered, useSubCustomerPrice = request != null) { updateCartSummary() }
        binding.recyclerCatalog.adapter = adapter
    }

    private fun updateCartSummary() {
        val a = adapter ?: return
        binding.textCartTotal.text = "Cart: ${a.cartCount()} items — $%.2f".format(a.cartTotal())
    }

    private fun submitOrder() {
        val a = adapter ?: return
        val lines = a.cartLines()
        if (lines.isEmpty()) {
            showMessage("Add at least one item to the cart.", isError = true)
            return
        }
        if (submitting) return

        val request = selectedRequest()
        val orderItems = lines.map { (item, qty) -> SubCustomerOrderItemDto(item.itemCode ?: "", qty, a.priceFor(item)) }

        submitting = true
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                if (request != null) {
                    api.createDraftOrder(CreateSubCustomerDraftOrderDto(
                        requestId = request.requestId,
                        subCustomerEmail = request.toEmailAddress,
                        notes = null,
                        items = orderItems
                    ))
                } else {
                    api.createGeneralOrder(CreateGeneralOrderDto(notes = null, items = orderItems))
                }
                submitting = false
                showMessage("Order submitted.", isError = false)
                showCelebration()
                loadCatalog()
            } catch (e: Exception) {
                submitting = false
                showMessage("Failed to submit order: ${e.message}", isError = true)
            }
        }
    }

    // Same celebration the web plays after a successful order (management-place-order.component.ts's
    // playSuccessOverlay) - a DB-driven Giphy GIF (dbo.Settings, key Wholesale_OrderPlaced), not a
    // bundled asset. Timing matches the web: visible immediately, starts fading at 3400ms, fully
    // gone by 4600ms. Tapping it dismisses early.
    private fun showCelebration() {
        val b = _binding ?: return
        b.celebrationOverlay.alpha = 1f
        b.celebrationOverlay.visibility = View.VISIBLE
        b.celebrationOverlay.setOnClickListener { dismissCelebration() }

        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val url = api.getWholesaleSetting("Wholesale_OrderPlaced").value
                if (!url.isNullOrBlank() && _binding != null) {
                    Glide.with(this@PlaceOrderFragment).asGif().load(url).into(binding.imageCelebration)
                }
            } catch (e: Exception) {
                // No GIF this time - the dark overlay alone still reads as "submitted", not worth
                // interrupting the user with an error over a purely decorative touch.
            }
        }

        binding.celebrationOverlay.postDelayed({
            binding.celebrationOverlay.animate().alpha(0f).setDuration(1200).withEndAction {
                _binding?.celebrationOverlay?.visibility = View.GONE
            }.start()
        }, 3400)
    }

    private fun dismissCelebration() {
        _binding?.celebrationOverlay?.let {
            it.animate().cancel()
            it.visibility = View.GONE
        }
    }

    private fun showMessage(text: String, isError: Boolean) {
        binding.textMessage.visibility = View.VISIBLE
        binding.textMessage.text = text
        binding.textMessage.setTextColor(if (isError) 0xFFC62828.toInt() else 0xFF2E7D32.toInt())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
