package com.liquorbee.wholesale.ui.wholesale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.wholesale.databinding.FragmentOpenOrdersPrintingBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.network.WholesaleLiveOpenOrderDto
import com.liquorbee.wholesale.network.readableMessage
import com.liquorbee.wholesale.printing.PrintHelper
import kotlinx.coroutines.launch

/** SubCustomers/GetLiveOpenOrders - live snapshot straight from the POS (separate from View
 * Orders' DB-cached list), with a "Print" action per row that fetches the order's line items via
 * GetManagementOrderDetail and sends them to whichever USB thermal printer is connected. Was
 * briefly pointed at GetWholesaleOpenOrders instead when this endpoint appeared undeployed - it
 * turned out to be deployed to PROD (hennyadmin.azurewebsites.net), not the dev-liquorbee slot the
 * app was pointed at (see ApiClient.ApiConfig) - switched back once the base URL was corrected. */
class OpenOrdersPrintingFragment : Fragment() {

    private var _binding: FragmentOpenOrdersPrintingBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager
    private var adapter: LiveOrdersPrintAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOpenOrdersPrintingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        binding.recyclerList.layoutManager = LinearLayoutManager(requireContext())
        binding.swipeRefresh.setOnRefreshListener { load() }
        load()
    }

    override fun onResume() {
        super.onResume()
        if (adapter != null) load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val orders = api.getLiveOpenOrders().sortedByDescending { it.createdDate }
                adapter = LiveOrdersPrintAdapter(orders) { order -> printOrder(order) }
                binding.recyclerList.adapter = adapter
                binding.textEmpty.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
                binding.textEmpty.text = "No open orders right now."
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to load orders: ${e.readableMessage()}"
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun printOrder(order: WholesaleLiveOpenOrderDto) {
        val orderId = order.orderId ?: return
        val a = adapter ?: return
        a.setStatus(orderId, "Printing...", isError = false, isPrinting = true)
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val detail = api.getManagementOrderDetail(orderId)
                val storeName = try { api.getAccountSettings().storeName } catch (e: Exception) { null }
                PrintHelper.printOrder(requireContext(), detail, storeName) { success, message ->
                    a.setStatus(orderId, message, isError = !success, isPrinting = false)
                }
            } catch (e: Exception) {
                a.setStatus(orderId, "Failed to print: ${e.readableMessage()}", isError = true, isPrinting = false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
