package com.sparklet.android.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Ports the web client's fixed dark theme (sparklet/src/app/globals.css and the
// Tailwind neutral/violet/emerald/red shades used across
// src/components/feed/*.tsx), matching sparklet-ios's Theme.swift value for
// value. The web app has no light mode, so this app doesn't either — until
// 2026-09-11 Android ran on Compose's default lightColorScheme, which made it
// the only client that wasn't dark.
//
// Named after the Tailwind shade each mirrors, not the sRGB value, so this
// stays easy to diff against the web class names it was ported from.
object SparkletColors {
    // globals.css: --background: #0a0a0a
    val Background = Color(0xFF0A0A0A)

    // Card/input panels — neutral-900; slightly lighter panelAlt — neutral-800
    // (e.g. unmatched category chips); borders — neutral-700.
    val Panel = Color(0xFF171717)
    val PanelAlt = Color(0xFF262626)
    val Border = Color(0xFF404040)

    // Text — neutral-100/300/400/500.
    val TextPrimary = Color(0xFFF5F5F5)
    val TextSecondary = Color(0xFFD4D4D4)
    val TextTertiary = Color(0xFFA3A3A3)
    val TextMuted = Color(0xFF737373)

    // Brand accent — violet-600/500/300.
    val Accent = Color(0xFF7C3AED)
    val AccentBright = Color(0xFF8B5CF6)
    val AccentText = Color(0xFFC4B5FD)

    // Answer states — emerald-500/300 and red-500/300, exactly QuizView.tsx's
    // "border-emerald-500 bg-emerald-500/15 text-emerald-300" and its red twin.
    val Success = Color(0xFF10B981)
    val SuccessText = Color(0xFF6EE7B7)
    val Danger = Color(0xFFEF4444)
    val DangerText = Color(0xFFFCA5A5)
}

// Parses a Category.colorHex string ("#38bdf8", from the backend's
// prisma/seed.ts). Falls back to the brand accent for anything malformed
// rather than a jarring black/white default — same contract as iOS's
// Color(hexString:). android.graphics.Color.parseColor throws on bad input,
// so this must not be left uncaught: a single bad seed value would otherwise
// crash the feed.
fun categoryColor(colorHex: String): Color =
    runCatching {
        val normalized = if (colorHex.startsWith("#")) colorHex else "#$colorHex"
        Color(android.graphics.Color.parseColor(normalized))
    }.getOrDefault(SparkletColors.Accent)

private val SparkletColorScheme = darkColorScheme(
    primary = SparkletColors.Accent,
    onPrimary = Color.White,
    primaryContainer = SparkletColors.AccentBright,
    onPrimaryContainer = Color.White,
    background = SparkletColors.Background,
    onBackground = SparkletColors.TextPrimary,
    surface = SparkletColors.Background,
    onSurface = SparkletColors.TextPrimary,
    surfaceVariant = SparkletColors.PanelAlt,
    onSurfaceVariant = SparkletColors.TextSecondary,
    surfaceContainer = SparkletColors.Panel,
    outline = SparkletColors.Border,
    error = SparkletColors.Danger,
    onError = Color.White,
)

@Composable
fun SparkletTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SparkletColorScheme) {
        // Explicit Surface rather than relying on the Activity's window
        // background: screens here are bare Columns with no background of
        // their own, so without this the window colour shows through.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = SparkletColors.Background,
            content = content,
        )
    }
}
