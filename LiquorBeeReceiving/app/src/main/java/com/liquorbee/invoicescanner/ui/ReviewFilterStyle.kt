package com.liquorbee.invoicescanner.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.Button

internal fun styleFilterButton(button: Button, selected: Boolean) {
    button.backgroundTintList=ColorStateList.valueOf(if(selected) Color.rgb(89,49,155) else Color.rgb(237,233,246))
    button.setTextColor(if(selected) Color.WHITE else Color.rgb(62,43,92))
    button.isSelected=selected
    button.contentDescription=button.text.toString()+if(selected) ", selected" else ""
}
