package com.liquorbee.wholesale.ui.wholesale

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.liquorbee.wholesale.databinding.FragmentSendRequestsBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.CreateSubCustomerRequestDto
import com.liquorbee.wholesale.network.CustomerSenderProfileDto
import com.liquorbee.wholesale.network.QuanticPriceListDto
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.network.SubCustomerContactDto
import kotlinx.coroutines.launch

/** Create a new wholesale invitation - SubCustomers/CreateRequest, with a price list picked from
 * SubCustomers/GetAvailablePriceLists. */
class SendRequestsFragment : Fragment() {

    private var _binding: FragmentSendRequestsBinding? = null
    private val binding get() = _binding!!
    private lateinit var session: SessionManager

    private var priceLists: List<QuanticPriceListDto> = emptyList()
    private var senderProfile: CustomerSenderProfileDto? = null
    private var sending = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSendRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        session = SessionManager(requireContext())
        binding.buttonEditSender.setOnClickListener { showEditSenderDialog() }
        binding.buttonSend.setOnClickListener { sendRequest() }
        loadPriceLists()

        SendRequestPrefill.consume()?.let { (businessName, email, priceListId) ->
            binding.editRecipientBusiness.setText(businessName ?: "")
            binding.editRecipientEmail.setText(email ?: "")
            pendingPriceListIdToSelect = priceListId
        }
    }

    private var pendingPriceListIdToSelect: String? = null

    private fun loadPriceLists() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                priceLists = api.getAvailablePriceLists()
                senderProfile = try { api.getManagementView().senderProfile } catch (e: Exception) { null }

                val labels = priceLists.map { it.name ?: it.priceListId }
                binding.spinnerPriceList.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, labels)

                pendingPriceListIdToSelect?.let { id ->
                    val index = priceLists.indexOfFirst { it.priceListId == id }
                    if (index >= 0) binding.spinnerPriceList.setSelection(index)
                }
            } catch (e: Exception) {
                showMessage("Failed to load price lists: ${e.message}", isError = true)
            }
        }
    }

    private fun showEditSenderDialog() {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }
        fun field(hint: String, value: String?): EditText {
            val e = EditText(context)
            e.hint = hint
            e.setText(value ?: "")
            layout.addView(e)
            return e
        }
        val editName = field("Name", senderProfile?.firstName)
        val editBusiness = field("Business name", senderProfile?.storeName)
        val editEmail = field("Email", senderProfile?.emailAddress)
        val editPhone = field("Phone", senderProfile?.phoneNumber)
        val editCity = field("City", senderProfile?.city)
        val editState = field("State", senderProfile?.state)

        AlertDialog.Builder(context)
            .setTitle("Sender details")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                senderProfile = CustomerSenderProfileDto(
                    firstName = editName.text.toString(),
                    storeName = editBusiness.text.toString(),
                    emailAddress = editEmail.text.toString(),
                    phoneNumber = editPhone.text.toString(),
                    city = editCity.text.toString(),
                    state = editState.text.toString()
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendRequest() {
        val recipientBusiness = binding.editRecipientBusiness.text.toString().trim()
        val recipientEmail = binding.editRecipientEmail.text.toString().trim()
        val notes = binding.editNotes.text.toString().trim()
        val selectedIndex = binding.spinnerPriceList.selectedItemPosition

        if (recipientEmail.isEmpty()) {
            showMessage("Enter a recipient email.", isError = true)
            return
        }
        if (selectedIndex < 0 || selectedIndex >= priceLists.size) {
            showMessage("Select a price list.", isError = true)
            return
        }
        if (sending) return

        val priceList = priceLists[selectedIndex]
        val profile = senderProfile
        val request = CreateSubCustomerRequestDto(
            toEmailAddress = recipientEmail,
            recipientBusinessName = recipientBusiness.ifEmpty { null },
            priceListName = priceList.name,
            priceListId = priceList.priceListId,
            notes = notes.ifEmpty { null },
            senderContact = SubCustomerContactDto(
                name = profile?.firstName,
                businessName = profile?.storeName,
                email = profile?.emailAddress,
                phoneNumber = profile?.phoneNumber,
                city = profile?.city,
                state = profile?.state
            )
        )

        sending = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                api.createSubCustomerRequest(request)
                sending = false
                showMessage("Request sent.", isError = false)
                binding.editRecipientBusiness.setText("")
                binding.editRecipientEmail.setText("")
                binding.editNotes.setText("")
            } catch (e: Exception) {
                sending = false
                showMessage("Failed to send request: ${e.message}", isError = true)
            }
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
