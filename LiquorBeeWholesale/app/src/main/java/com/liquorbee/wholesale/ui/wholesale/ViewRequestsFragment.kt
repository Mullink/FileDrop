package com.liquorbee.wholesale.ui.wholesale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.liquorbee.wholesale.databinding.FragmentWholesaleListBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.network.SubCustomerRequestDto
import com.liquorbee.wholesale.network.UpdateSubCustomerRequestStatusDto
import kotlinx.coroutines.launch

/** Every invitation this store has sent or received - GetManagementView's `requests` array. */
class ViewRequestsFragment : Fragment() {

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
                val data = api.getManagementView()
                val requests = data.requests.sortedByDescending { it.createDate }
                binding.recyclerList.adapter = RequestsAdapter(requests) { request -> confirmCancel(request) }
                binding.textEmpty.visibility = if (requests.isEmpty()) View.VISIBLE else View.GONE
                binding.textEmpty.text = "No requests yet."
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to load requests: ${e.message}"
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun confirmCancel(request: SubCustomerRequestDto) {
        AlertDialog.Builder(requireContext())
            .setTitle("Cancel Request")
            .setMessage("Cancel the wholesale invitation to ${request.toEmailAddress}?")
            .setPositiveButton("Cancel Request") { _, _ -> cancelRequest(request) }
            .setNegativeButton("Keep It", null)
            .show()
    }

    private fun cancelRequest(request: SubCustomerRequestDto) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateRequestStatus(UpdateSubCustomerRequestStatusDto(request.requestId, "Deleted", request.toEmailAddress))
                load()
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to cancel: ${e.message}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
