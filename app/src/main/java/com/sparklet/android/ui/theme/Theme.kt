package com.sparklet.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SparkletColorScheme = lightColorScheme()

@Composable
fun SparkletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SparkletColorScheme, content = content)
}
