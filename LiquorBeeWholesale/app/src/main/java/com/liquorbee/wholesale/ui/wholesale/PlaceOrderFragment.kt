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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var searchJob: Job? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaceOrderBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        binding.recyclerCatalog.layoutManager = LinearLayoutManager(requireContext())
        // Catalogs run 10k-30k rows for some customers - fixed row size skips RecyclerView's
        // extra "did the list size change my layout" measure pass on every notify, and a larger
        // view cache smooths scrolling by keeping more recently-scrolled-off rows ready to reuse.
        binding.recyclerCatalog.setHasFixedSize(true)
        binding.recyclerCatalog.setItemViewCacheSize(24)
        binding.buttonLoadCatalog.setOnClickListener { loadCatalog() }
        binding.buttonSubmitOrder.setOnClickListener { submitOrder() }
        binding.editSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { applyFilter(s?.toString().orEmpty()) }
        })
        loadAccounts()
    }

    // Defaults to General Customer (Retail) - index 0 - and loads its catalog immediately, so
    // staff at the register land on a ready-to-order screen instead of an empty one requiring a
    // manual "Load catalog" tap first.
    private fun loadAccounts() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                currentCustomerId = api.getCurrentCustomerId()
                val data = api.getManagementView()
                linkedRequests = data.requests.filter { it.status == "Linked" }

                val labels = listOf(GENERAL_CUSTOMER_LABEL) + linkedRequests.map {
                    it.toBusinessName ?: it.recipientBusinessName ?: it.toEmailAddress ?: "(Unnamed account)"
                }
                binding.spinnerAccount.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)
                binding.spinnerAccount.setSelection(0)
                loadCatalog()
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
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                // Retrofit's suspend call already does the network/JSON-parse work off the main
                // thread (OkHttp dispatcher + coroutine adapter), so a 10k-30k-row response
                // doesn't block the UI here even without an explicit withContext.
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

    // Debounced + computed off the main thread: with 10k-30k rows, filtering on every keystroke
    // synchronously would jank the UI. The adapter instance is reused (updateItems), not
    // recreated, so re-attaching/re-measuring the whole RecyclerView isn't repeated per keystroke
    // either - only the visible rows actually get rebound.
    private fun applyFilter(query: String) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(250)
            val filtered = withContext(Dispatchers.Default) {
                if (query.isBlank()) allItems else allItems.filter {
                    (it.itemName?.contains(query, ignoreCase = true) == true) ||
                        (it.itemCode?.contains(query, ignoreCase = true) == true) ||
                        (it.upc?.contains(query, ignoreCase = true) == true)
                }
            }
            val current = adapter
            if (current != null) {
                current.updateItems(filtered)
            } else {
                adapter = CatalogAdapter(filtered, useSubCustomerPrice = selectedRequest() != null) { updateCartSummary() }
                binding.recyclerCatalog.adapter = adapter
            }
            binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            binding.textEmpty.text = "No items match \"$query\"."
        }
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
        viewLifecycleOwner.lifecycleScope.launch {
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

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val url = api.getWholesaleSetting("Wholesale_OrderPlaced").value
                android.util.Log.d("Celebration", "Wholesale_OrderPlaced url = $url")
                if (!url.isNullOrBlank() && _binding != null) {
                    // Deliberately NOT .asGif() - that forces strict GIF-only decoding and fails
                    // silently (Glide's own async pipeline, not this coroutine, so a decode error
                    // here never reaches this try/catch) if Giphy's response doesn't sniff as an
                    // exact GIF match. Plain .load() auto-detects format and animates GIFs fine.
                    Glide.with(this@PlaceOrderFragment)
                        .load(url)
                        .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                            override fun onLoadFailed(
                                e: com.bumptech.glide.load.engine.GlideException?, model: Any?,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>, isFirstResource: Boolean
                            ): Boolean {
                                android.util.Log.e("Celebration", "Failed to load celebration GIF: $url", e)
                                return false
                            }
                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable, model: Any,
                                target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                                dataSource: com.bumptech.glide.load.DataSource, isFirstResource: Boolean
                            ): Boolean = false
                        })
                        .into(binding.imageCelebration)
                }
            } catch (e: Exception) {
                android.util.Log.e("Celebration", "Failed to fetch Wholesale_OrderPlaced setting", e)
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
