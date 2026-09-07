package com.liquorbee.wholesale.ui.wholesale

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.wholesale.databinding.FragmentViewOrdersBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.network.WholesaleOpenOrderDto
import com.liquorbee.wholesale.ui.EXTRA_ORDER_ID
import com.liquorbee.wholesale.ui.OrderDetailActivity
import kotlinx.coroutines.launch

/** SubCustomers/GetWholesaleOpenOrders(headersOnly). "My terminal only" and "Show closed" are
 * BOTH client-side filters (matching the web exactly) - not query params on the list endpoint. */
class ViewOrdersFragment : Fragment() {

    private var _binding: FragmentViewOrdersBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    private var allOrders: List<WholesaleOpenOrderDto> = emptyList()
    private var preferredTerminalId: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentViewOrdersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        binding.recyclerList.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.checkboxMyTerminal.setOnCheckedChangeListener { _, _ -> applyFilters() }
        binding.checkboxShowClosed.setOnCheckedChangeListener { _, _ -> applyFilters() }
        load()
    }

    override fun onResume() {
        super.onResume()
        // Refresh after backing out of an order that may have just been edited/deleted.
        if (allOrders.isNotEmpty()) load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                allOrders = api.getWholesaleOpenOrders(includeAll = true, headersOnly = true)
                preferredTerminalId = try {
                    api.getPosCustomerSettings().wholesaleSettings?.preferredOrderingTerminal
                } catch (e: Exception) { null }
                applyFilters()
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to load orders: ${e.message}"
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun applyFilters() {
        var filtered = allOrders.sortedByDescending { it.createdDate }
        if (binding.checkboxMyTerminal.isChecked && !preferredTerminalId.isNullOrBlank()) {
            filtered = filtered.filter { it.serviceAreaId == preferredTerminalId }
        }
        if (!binding.checkboxShowClosed.isChecked) {
            filtered = filtered.filter { it.orderStatus != 3 && it.orderStatus != 4 }
        }
        binding.recyclerList.adapter = OrdersAdapter(filtered) { order ->
            val id = order.orderId ?: return@OrdersAdapter
            startActivity(Intent(requireContext(), OrderDetailActivity::class.java).putExtra(EXTRA_ORDER_ID, id))
        }
        binding.textEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.textEmpty.text = "No orders match these filters."
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
