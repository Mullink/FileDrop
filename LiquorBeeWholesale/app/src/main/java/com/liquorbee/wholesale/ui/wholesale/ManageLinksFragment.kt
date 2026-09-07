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
import com.liquorbee.wholesale.network.SubCustomerDto
import com.liquorbee.wholesale.network.UpdateSubCustomerRequestStatusDto
import kotlinx.coroutines.launch

/** Currently-linked wholesale accounts - GetManagementView's `linkedAccounts` array. Unlinking
 * finds the matching Linked request (by email) and transitions it to Deleted - there is no
 * separate "unlink" endpoint. */
class ManageLinksFragment : Fragment() {

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
                val links = data.linkedAccounts
                binding.recyclerList.adapter = LinksAdapter(links) { account ->
                    val linkedRequest = data.requests.firstOrNull { it.status == "Linked" && it.toEmailAddress == account.emailAddress }
                    confirmUnlink(account, linkedRequest?.requestId)
                }
                binding.textEmpty.visibility = if (links.isEmpty()) View.VISIBLE else View.GONE
                binding.textEmpty.text = "No linked wholesale accounts yet."
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to load links: ${e.message}"
            } finally {
                binding.progressLoading.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun confirmUnlink(account: SubCustomerDto, requestId: String?) {
        if (requestId == null) return
        AlertDialog.Builder(requireContext())
            .setTitle("Unlink Account")
            .setMessage("Unlink ${account.storeName ?: account.emailAddress}? They'll no longer be able to place wholesale orders with you.")
            .setPositiveButton("Unlink") { _, _ -> unlink(requestId, account.emailAddress) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun unlink(requestId: String, email: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.updateRequestStatus(UpdateSubCustomerRequestStatusDto(requestId, "Deleted", email))
                load()
            } catch (e: Exception) {
                binding.textEmpty.visibility = View.VISIBLE
                binding.textEmpty.text = "Failed to unlink: ${e.message}"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
