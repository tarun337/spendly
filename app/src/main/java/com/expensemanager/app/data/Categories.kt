package com.expensemanager.app.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class Category(val name: String, val icon: ImageVector, val color: Color)

object Categories {
    val expense = listOf(
        Category("Food", Icons.Rounded.Restaurant, Color(0xFFFF8A65)),
        Category("Groceries", Icons.Rounded.LocalGroceryStore, Color(0xFF81C784)),
        Category("Transport", Icons.Rounded.DirectionsCar, Color(0xFF64B5F6)),
        Category("Fuel", Icons.Rounded.LocalGasStation, Color(0xFFFFB74D)),
        Category("Shopping", Icons.Rounded.ShoppingBag, Color(0xFFF06292)),
        Category("Bills", Icons.Rounded.ReceiptLong, Color(0xFF4DD0E1)),
        Category("Rent", Icons.Rounded.Home, Color(0xFF9575CD)),
        Category("Household Help", Icons.Rounded.CleaningServices, Color(0xFFA1887F)),
        Category("Health", Icons.Rounded.LocalHospital, Color(0xFFE57373)),
        Category("Entertainment", Icons.Rounded.Movie, Color(0xFFBA68C8)),
        Category("Sport", Icons.Rounded.SportsSoccer, Color(0xFF5C6BC0)),
        Category("Travel", Icons.Rounded.Flight, Color(0xFF4FC3F7)),
        Category("Education", Icons.Rounded.School, Color(0xFFAED581)),
        Category("Subscriptions", Icons.Rounded.Subscriptions, Color(0xFFFF7043)),
        Category("Gifts", Icons.Rounded.CardGiftcard, Color(0xFFFFD54F)),
        Category("EMI / Loan", Icons.Rounded.AccountBalance, Color(0xFF90A4AE)),
        Category("Investment", Icons.Rounded.TrendingUp, Color(0xFF26A69A)),
        Category("Family", Icons.Rounded.FamilyRestroom, Color(0xFFFF8FAB)),
        Category("Friends", Icons.Rounded.Groups, Color(0xFF7DD3FC)),
        Category("Other", Icons.Rounded.MoreHoriz, Color(0xFFB0BEC5)),
    )

    val income = listOf(
        Category("Salary", Icons.Rounded.Work, Color(0xFF4ADE80)),
        Category("Business", Icons.Rounded.Storefront, Color(0xFF60A5FA)),
        Category("Freelance", Icons.Rounded.Laptop, Color(0xFFA78BFA)),
        Category("Interest", Icons.Rounded.Savings, Color(0xFFFBBF24)),
        Category("Rental", Icons.Rounded.Apartment, Color(0xFF2DD4BF)),
        Category("Gift", Icons.Rounded.Redeem, Color(0xFFF472B6)),
        Category("Refund", Icons.Rounded.Replay, Color(0xFF38BDF8)),
        Category("Family", Icons.Rounded.FamilyRestroom, Color(0xFFFF8FAB)),
        Category("Friends", Icons.Rounded.Groups, Color(0xFF7DD3FC)),
        Category("Other", Icons.Rounded.MoreHoriz, Color(0xFFB0BEC5)),
    )

    fun forType(type: TxType) = if (type == TxType.EXPENSE) expense else income

    fun find(type: String, name: String): Category =
        forType(if (type == TxType.INCOME.name) TxType.INCOME else TxType.EXPENSE)
            .firstOrNull { it.name == name } ?: expense.last()
}
