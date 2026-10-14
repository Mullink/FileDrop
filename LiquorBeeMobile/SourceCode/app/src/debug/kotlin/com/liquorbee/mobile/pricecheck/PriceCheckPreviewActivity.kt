package com.liquorbee.mobile.pricecheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.liquorbee.mobile.core.network.*
import com.liquorbee.mobile.core.ui.LiquorBeeMobileTheme

// Debug-only fixture for layout/accessibility review. No network access or POS writes.
class PriceCheckPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LiquorBeeMobileTheme {
                var state by remember { mutableStateOf(PriceCheckUiState(
                    editorOpen = true,
                    editorItem = PriceCheckEditDto("preview-item", "preview-1", "Jack Daniel’s Old No. 7 · 750 mL",
                        "082184090466", "100245", 24.99, 18.0, 24, "v1", description = "Tennessee whiskey · 80 proof",
                        categoryId = "cat-whiskey", status = 1, trackInventory = 1, cardPrice = 25.99, msrp = 27.99,
                        discountEligible = true, categoryName = "Whiskey", vendorName = "Southern Glazer’s"),
                    categories = listOf(InventoryCategoryDto("cat-bourbon", "Bourbon"), InventoryCategoryDto("cat-gin", "Gin"),
                        InventoryCategoryDto("cat-rum", "Rum"), InventoryCategoryDto("cat-tequila", "Tequila"), InventoryCategoryDto("cat-whiskey", "Whiskey")),
                    vendors = listOf(PriceCheckVendorDto("v1", "Southern Glazer’s"), PriceCheckVendorDto("v2", "Republic National"))
                )) }
                PriceCheckEditor(state, onNext = { finish() }, onBack = { finish() }, onReload = {}, onSave = { item ->
                    state = state.copy(editorItem = item.copy(revision = item.revision + "-saved"), saveMessage = "Changes saved")
                })
            }
        }
    }
}
