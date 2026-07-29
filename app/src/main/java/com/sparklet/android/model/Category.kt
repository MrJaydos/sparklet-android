package com.sparklet.android.model

import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val slug: String,
    val name: String,
    val colorHex: String,
    val icon: String,
)
