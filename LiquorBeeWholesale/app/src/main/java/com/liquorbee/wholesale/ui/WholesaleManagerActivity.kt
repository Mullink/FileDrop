package com.liquorbee.wholesale.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.liquorbee.wholesale.R
import com.liquorbee.wholesale.databinding.ActivityWholesaleManagerBinding
import com.liquorbee.wholesale.network.ApiClient
import com.liquorbee.wholesale.network.SessionManager
import com.liquorbee.wholesale.ui.wholesale.ManageLinksFragment
import com.liquorbee.wholesale.ui.wholesale.OpenOrdersPrintingFragment
import com.liquorbee.wholesale.ui.wholesale.PlaceOrderFragment
import com.liquorbee.wholesale.ui.wholesale.PosCustomersFragment
import com.liquorbee.wholesale.ui.wholesale.SendRequestsFragment
import com.liquorbee.wholesale.ui.wholesale.ViewOrdersFragment
import com.liquorbee.wholesale.ui.wholesale.ViewRequestsFragment
import kotlinx.coroutines.launch

enum class WholesaleTab(val label: String) {
    SEND_REQUESTS("Send Requests"),
    VIEW_REQUESTS("View Requests"),
    MANAGE_LINKS("Manage Links"),
    POS_CUSTOMERS("POS Customers"),
    PLACE_ORDER("Place Order"),
    VIEW_ORDERS("View Orders"),
    OPEN_ORDERS_PRINTING("Open Orders Printing")
}

// Only these three are shown on this POS-terminal app - Send Requests/View Requests/Manage
// Links/POS Customers are back-office tasks left to the web dashboard; the register only needs
// to place orders, check on them, and print them. The fragments for the other four tabs are kept
// in source (see ui/wholesale/) in case this list is reopened later - just not reachable from
// this tab row.
private val VISIBLE_TABS = listOf(WholesaleTab.PLACE_ORDER, WholesaleTab.VIEW_ORDERS, WholesaleTab.OPEN_ORDERS_PRINTING)

/** Native equivalent of the Angular /wholesale/management page's Place Order + View Orders tabs. */
class WholesaleManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWholesaleManagerBinding
    private val tabViews = mutableMapOf<WholesaleTab, android.widget.TextView>()
    private var activeTab: WholesaleTab = WholesaleTab.PLACE_ORDER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWholesaleManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.textBack.setOnClickListener { finish() }

        buildTabRow()
        selectTab(activeTab)
        loadDashboardStats()
    }

    override fun onResume() {
        super.onResume()
        // Refresh after coming back from placing/printing an order elsewhere in the app.
        loadDashboardStats()
    }

    // Same three hero stats as the web's /wholesale/management landing page (Open links, Active
    // links, Open requests) - all derived from the same GetManagementView + GetWholesaleOpenOrders
    // calls the web uses, no separate stats endpoint exists.
    private fun loadDashboardStats() {
        val session = SessionManager(this)
        lifecycleScope.launch {
            try {
                val api = ApiClient.buildAuthenticatedApi(session)
                val management = api.getManagementView()
                val activeLinks = management.requests.count { it.status == "Linked" }
                val openRequests = management.requests.count { it.status == "Proposed" }
                binding.textStatActiveLinks.text = activeLinks.toString()
                binding.textStatOpenRequests.text = openRequests.toString()

                val orders = api.getWholesaleOpenOrders(includeAll = true, headersOnly = true)
                val openOrders = orders.count { it.orderStatus != 3 && it.orderStatus != 4 }
                binding.textStatOpenOrders.text = openOrders.toString()
            } catch (e: Exception) {
                // Purely decorative header stats - a failed refresh just leaves the last-known
                // (or placeholder "—") values in place rather than interrupting the screen.
            }
        }
    }

    private fun buildTabRow() {
        VISIBLE_TABS.forEach { tab ->
            val tabView = android.widget.TextView(this).apply {
                text = tab.label
                textSize = 13f
                setTextColor(getColor(R.color.white))
                setPadding(28, 18, 28, 18)
                isClickable = true
                isFocusable = true
                setOnClickListener { selectTab(tab) }
            }
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.marginEnd = 8
            binding.tabRow.addView(tabView, params)
            tabViews[tab] = tabView
        }
    }

    // Lets a fragment (e.g. POS Customers' "Send request"/"Manage Link" buttons) jump to another
    // tab, same as the web's activeTab = 'send' assignment.
    fun switchToTab(tab: WholesaleTab) {
        tabViews[tab]?.let { selectTab(tab) }
    }

    private fun selectTab(tab: WholesaleTab) {
        activeTab = tab
        tabViews.forEach { (t, view) ->
            view.setBackgroundResource(if (t == tab) R.drawable.bg_button_primary else R.drawable.bg_button_muted)
        }
        val fragment: Fragment = when (tab) {
            WholesaleTab.SEND_REQUESTS -> SendRequestsFragment()
            WholesaleTab.VIEW_REQUESTS -> ViewRequestsFragment()
            WholesaleTab.MANAGE_LINKS -> ManageLinksFragment()
            WholesaleTab.POS_CUSTOMERS -> PosCustomersFragment()
            WholesaleTab.PLACE_ORDER -> PlaceOrderFragment()
            WholesaleTab.VIEW_ORDERS -> ViewOrdersFragment()
            WholesaleTab.OPEN_ORDERS_PRINTING -> OpenOrdersPrintingFragment()
        }
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
