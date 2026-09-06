package com.liquorbee.mobile.labelprinting

data class LabelFilterOption(val value: Int, val label: String)

// Exact filter codes/labels from the web app's pos-label-printing.html <mat-select> - these are
// opaque server-side codes (negative values have special meaning, e.g. -44 = non-ABC only,
// -1/1/3/7... = "shipped in the past N days", -4001.. = bottle-size buckets), not something to
// invent independently, so kept in this exact order/value pairing.
val LABEL_FILTER_OPTIONS = listOf(
    LabelFilterOption(-44, "Show Non-ABC Items Only"),
    LabelFilterOption(-1, "Show ABC Items Shipped Today"),
    LabelFilterOption(1, "Show ABC Items Shipped Past 1 Day"),
    LabelFilterOption(3, "Show ABC Items Shipped Past 3 Days"),
    LabelFilterOption(7, "Show ABC Items Shipped Past 7 Days"),
    LabelFilterOption(14, "Show ABC Items Shipped Past 14 Days"),
    LabelFilterOption(21, "Show ABC Items Shipped Past 21 Days"),
    LabelFilterOption(28, "Show ABC Items Shipped Past 28 Days"),
    LabelFilterOption(30, "Show ABC Items Shipped Past 1 MONTH"),
    LabelFilterOption(90, "Show ABC Items Shipped Past 3 MONTHS"),
    LabelFilterOption(180, "Show ABC Items Shipped Past 6 MONTHS"),
    LabelFilterOption(365, "Show ABC Items Shipped Past 12 MONTHS"),
    LabelFilterOption(0, "Show All Items"),
    LabelFilterOption(-4001, "Show 50ml ABC Items"),
    LabelFilterOption(-4002, "Show 100ml ABC Items"),
    LabelFilterOption(-40021, "Show 187ml ABC Items"),
    LabelFilterOption(-4003, "Show 200ml ABC Items"),
    LabelFilterOption(-4004, "Show 250ml ABC Items"),
    LabelFilterOption(-4005, "Show 355ml ABC Items"),
    LabelFilterOption(-4006, "Show 375ml ABC Items"),
    LabelFilterOption(-4007, "Show 500ml ABC Items"),
    LabelFilterOption(-4008, "Show 700ml ABC Items"),
    LabelFilterOption(-4009, "Show 750ml ABC Items"),
    LabelFilterOption(-4010, "Show 1L ABC Items"),
    LabelFilterOption(-4011, "Show 1.5L ABC Items"),
    LabelFilterOption(-4012, "Show 1.75L ABC Items"),
    LabelFilterOption(-4013, "Show 2L ABC Items"),
    LabelFilterOption(-4014, "Show 3L ABC Items"),
    LabelFilterOption(-4015, "Show 4L ABC Items"),
    LabelFilterOption(-4016, "Show 5L ABC Items")
)
