package com.liquorbee.wholesale.ui.wholesale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.wholesale.databinding.FragmentWholesaleListBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.ui.WholesaleManagerActivity
import com.liquorbee.wholesale.ui.WholesaleTab
import kotlinx.coroutines.launch

/** This store's own POS customers, flagging which ones are already linked for wholesale -
 * GetWholesaleQuanticCustomers. Converting one to a wholesale account just prefills and jumps to
 * Send Requests - there's no dedicated "link a POS customer" endpoint. */
class PosCustomersFragment : Fragment() {

    private var _binding: FragmentWholesaleListBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWholesaleListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        binding.swipeRefresh.setOnRefreshListener { load() }
        binding.recyclerList.layoutManager = LinearLayoutManager(requireContext())
        load()
    }

    private fun load() {
        binding.progressLoading.visibility = View.VISIBLE
        binding.textEmpty.visibility = View.GONE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val customers = api.getWholesaleQuanticCustomers()
                binding.recyclerList.adapter = PosCustomersAdapter(
                    customers,
                    onSendRequest = { c ->
                        SendRequestPrefill.businessName = c.displayName
                        SendRequestPrefill.email = c.emailAddress
                        SendRequestPrefill.priceListId = c.priceListId
                        (activity as? WholesaleManagerActivity)?.switchToTab(WholesaleTab.SEND_REQUESTS)
                    },
                    onManageLink = { (activity as? WholesaleManagerActivity)?.switchToTab(WholesaleTab.MANAGE_LINKS) }
                )
                binding.textEmpty.visibility = if (customers.isEmpty()) View.VISIBLE else View.GONE
                binding.textEmpty.text = "No POS customers found. Make sure your POS customer list is configured."
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to load POS customers: ${e.message}"
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
