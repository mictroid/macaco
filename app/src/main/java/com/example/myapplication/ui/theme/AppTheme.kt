package com.example.myapplication.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

enum class AppTheme(val key: String, val displayName: String, val swatch: Color) {
    WANDERLOG("wanderlog", "Macaco", Color(0xFF2D6B6B)),
    LAVENDER( "lavender",  "Lavender",  Color(0xFF6750A4)),
    ROSE(     "rose",      "Rose",      Color(0xFFB72F5A)),
    SKY(      "sky",       "Sky",       Color(0xFF0061A4)),
    FOREST(   "forest",    "Forest",    Color(0xFF1A6B4A)),
    PEACH(    "peach",     "Peach",     Color(0xFFC25100)),
    SAND(     "sand",      "Sand",      Color(0xFF7B5800));

    companion object {
        fun fromKey(key: String) = entries.find { it.key == key } ?: WANDERLOG
    }

    fun colorScheme(dark: Boolean): ColorScheme = when (this) {
        WANDERLOG -> if (dark) wanderlogDark else wanderlogLight
        LAVENDER  -> if (dark) lavenderDark  else lavenderLight
        ROSE      -> if (dark) roseDark      else roseLight
        SKY       -> if (dark) skyDark       else skyLight
        FOREST    -> if (dark) forestDark    else forestLight
        PEACH     -> if (dark) peachDark     else peachLight
        SAND      -> if (dark) sandDark      else sandLight
    }
}

// ── Wanderlog (warm teal / leather) ──────────────────────────────────────────
private val wanderlogLight = lightColorScheme(
    primary = Color(0xFF2D6B6B), onPrimary = Color.White,
    primaryContainer = Color(0xFFB2E5E5), onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF7D5A40), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF7D9C0), onSecondaryContainer = Color(0xFF2E1500),
    tertiary = Color(0xFF8B4513), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD9C0), onTertiaryContainer = Color(0xFF2E0D00),
    background = Color(0xFFFFF8F0), onBackground = Color(0xFF1F1B17),
    surface = Color(0xFFFFF8F0), onSurface = Color(0xFF1F1B17),
    surfaceVariant = Color(0xFFEDE0D3), onSurfaceVariant = Color(0xFF4E4036),
    outline = Color(0xFF7F7167), error = Color(0xFFBA1A1A), onError = Color.White,
)
private val wanderlogDark = darkColorScheme(
    primary = Color(0xFF7FDCDC), onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF1F5050), onPrimaryContainer = Color(0xFFB2E5E5),
    secondary = Color(0xFFE0C0A0), onSecondary = Color(0xFF4A2D15),
    secondaryContainer = Color(0xFF63412A), onSecondaryContainer = Color(0xFFF7D9C0),
    tertiary = Color(0xFFFFB889), onTertiary = Color(0xFF4B1E00),
    tertiaryContainer = Color(0xFF6B2E00), onTertiaryContainer = Color(0xFFFFD9C0),
    background = Color(0xFF1A1410), onBackground = Color(0xFFEEE0D5),
    surface = Color(0xFF1A1410), onSurface = Color(0xFFEEE0D5),
    surfaceVariant = Color(0xFF4E4036), onSurfaceVariant = Color(0xFFD2C4B7),
    outline = Color(0xFF9B8E83), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
)

// ── Lavender ──────────────────────────────────────────────────────────────────
private val lavenderLight = pl(
    primary = 0xFF6750A4u, primaryContainer = 0xFFEADDFFu, onPrimaryContainer = 0xFF21005Du,
    secondary = 0xFF625B71u, secondaryContainer = 0xFFE8DEF8u,
    background = 0xFFFEF7FFu, surfaceVariant = 0xFFE7E0ECu, outline = 0xFF79747Eu,
)
private val lavenderDark = pd(
    primary = 0xFFCFBCFFu, onPrimary = 0xFF381E72u,
    primaryContainer = 0xFF4F378Bu, onPrimaryContainer = 0xFFEADDFFu,
    secondary = 0xFFCBC2DBu, onSecondary = 0xFF332D41u,
    secondaryContainer = 0xFF4A4458u, onSecondaryContainer = 0xFFE8DEF8u,
    background = 0xFF1C1B1Fu, surfaceVariant = 0xFF49454Fu, outline = 0xFF938F99u,
)

// ── Rose ──────────────────────────────────────────────────────────────────────
private val roseLight = pl(
    primary = 0xFFB72F5Au, primaryContainer = 0xFFFFD9E4u, onPrimaryContainer = 0xFF3E0018u,
    secondary = 0xFF775460u, secondaryContainer = 0xFFFFD9E4u,
    background = 0xFFFFF8F8u, surfaceVariant = 0xFFF3DDE4u, outline = 0xFF84737Au,
)
private val roseDark = pd(
    primary = 0xFFFFB1C8u, onPrimary = 0xFF66002Eu,
    primaryContainer = 0xFF8B1A3Fu, onPrimaryContainer = 0xFFFFD9E4u,
    secondary = 0xFFE3BDC9u, onSecondary = 0xFF44272Fu,
    secondaryContainer = 0xFF5D3D47u, onSecondaryContainer = 0xFFFFD9E4u,
    background = 0xFF201018u, surfaceVariant = 0xFF524347u, outline = 0xFF9C8B92u,
)

// ── Sky ───────────────────────────────────────────────────────────────────────
private val skyLight = pl(
    primary = 0xFF0061A4u, primaryContainer = 0xFFD1E4FFu, onPrimaryContainer = 0xFF001D36u,
    secondary = 0xFF535F70u, secondaryContainer = 0xFFD7E3F8u,
    background = 0xFFF8FAFFu, surfaceVariant = 0xFFDFE2EBu, outline = 0xFF73777Fu,
)
private val skyDark = pd(
    primary = 0xFF9FCAFFu, onPrimary = 0xFF003258u,
    primaryContainer = 0xFF00497Du, onPrimaryContainer = 0xFFD1E4FFu,
    secondary = 0xFFBAC8DBu, onSecondary = 0xFF273141u,
    secondaryContainer = 0xFF3D4758u, onSecondaryContainer = 0xFFD7E3F8u,
    background = 0xFF1A1C1Eu, surfaceVariant = 0xFF43474Eu, outline = 0xFF8D9199u,
)

// ── Forest ────────────────────────────────────────────────────────────────────
private val forestLight = pl(
    primary = 0xFF1A6B4Au, primaryContainer = 0xFFA8F5C8u, onPrimaryContainer = 0xFF002113u,
    secondary = 0xFF4C6358u, secondaryContainer = 0xFFCEE9DAu,
    background = 0xFFF4FBF6u, surfaceVariant = 0xFFDBE5DCu, outline = 0xFF6F7971u,
)
private val forestDark = pd(
    primary = 0xFF8DD8A6u, onPrimary = 0xFF003823u,
    primaryContainer = 0xFF005232u, onPrimaryContainer = 0xFFA8F5C8u,
    secondary = 0xFFB3CCBAu, onSecondary = 0xFF1E352Bu,
    secondaryContainer = 0xFF344D41u, onSecondaryContainer = 0xFFCEE9DAu,
    background = 0xFF171D19u, surfaceVariant = 0xFF414E45u, outline = 0xFF89938Au,
)

// ── Peach ─────────────────────────────────────────────────────────────────────
private val peachLight = pl(
    primary = 0xFFC25100u, primaryContainer = 0xFFFFDBC9u, onPrimaryContainer = 0xFF401300u,
    secondary = 0xFF755847u, secondaryContainer = 0xFFFFDBC9u,
    background = 0xFFFFF8F5u, surfaceVariant = 0xFFF2DDD3u, outline = 0xFF87786Fu,
)
private val peachDark = pd(
    primary = 0xFFFFB595u, onPrimary = 0xFF662900u,
    primaryContainer = 0xFF933A00u, onPrimaryContainer = 0xFFFFDBC9u,
    secondary = 0xFFE6C0A9u, onSecondary = 0xFF432C1Cu,
    secondaryContainer = 0xFF5C4131u, onSecondaryContainer = 0xFFFFDBC9u,
    background = 0xFF201A16u, surfaceVariant = 0xFF52443Du, outline = 0xFFA08D84u,
)

// ── Sand ──────────────────────────────────────────────────────────────────────
private val sandLight = pl(
    primary = 0xFF7B5800u, primaryContainer = 0xFFFFDEA0u, onPrimaryContainer = 0xFF271900u,
    secondary = 0xFF6A5D3Fu, secondaryContainer = 0xFFF4E1BBu,
    background = 0xFFFFFBF0u, surfaceVariant = 0xFFECE1CFu, outline = 0xFF9C8F72u,
)
private val sandDark = pd(
    primary = 0xFFF8BE48u, onPrimary = 0xFF402D00u,
    primaryContainer = 0xFF5D4200u, onPrimaryContainer = 0xFFFFDEA0u,
    secondary = 0xFFD7C5A0u, onSecondary = 0xFF382F17u,
    secondaryContainer = 0xFF504530u, onSecondaryContainer = 0xFFF4E1BBu,
    background = 0xFF1E1B13u, surfaceVariant = 0xFF4D4638u, outline = 0xFFB7AA92u,
)

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun pl(
    primary: ULong, primaryContainer: ULong, onPrimaryContainer: ULong,
    secondary: ULong, secondaryContainer: ULong,
    background: ULong, surfaceVariant: ULong, outline: ULong,
) = lightColorScheme(
    primary = Color(primary.toLong()), onPrimary = Color.White,
    primaryContainer = Color(primaryContainer.toLong()), onPrimaryContainer = Color(onPrimaryContainer.toLong()),
    secondary = Color(secondary.toLong()), onSecondary = Color.White,
    secondaryContainer = Color(secondaryContainer.toLong()), onSecondaryContainer = Color(onPrimaryContainer.toLong()),
    tertiary = Color(secondary.toLong()), onTertiary = Color.White,
    tertiaryContainer = Color(secondaryContainer.toLong()), onTertiaryContainer = Color(onPrimaryContainer.toLong()),
    background = Color(background.toLong()), onBackground = Color(0xFF1C1B1F),
    surface = Color(background.toLong()), onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(surfaceVariant.toLong()), onSurfaceVariant = Color(0xFF49454E),
    outline = Color(outline.toLong()), error = Color(0xFFB3261E), onError = Color.White,
    errorContainer = Color(0xFFF9DEDC), onErrorContainer = Color(0xFF410E0B),
)

private fun pd(
    primary: ULong, onPrimary: ULong,
    primaryContainer: ULong, onPrimaryContainer: ULong,
    secondary: ULong, onSecondary: ULong,
    secondaryContainer: ULong, onSecondaryContainer: ULong,
    background: ULong, surfaceVariant: ULong, outline: ULong,
) = darkColorScheme(
    primary = Color(primary.toLong()), onPrimary = Color(onPrimary.toLong()),
    primaryContainer = Color(primaryContainer.toLong()), onPrimaryContainer = Color(onPrimaryContainer.toLong()),
    secondary = Color(secondary.toLong()), onSecondary = Color(onSecondary.toLong()),
    secondaryContainer = Color(secondaryContainer.toLong()), onSecondaryContainer = Color(onSecondaryContainer.toLong()),
    tertiary = Color(secondary.toLong()), onTertiary = Color(onSecondary.toLong()),
    tertiaryContainer = Color(secondaryContainer.toLong()), onTertiaryContainer = Color(onSecondaryContainer.toLong()),
    background = Color(background.toLong()), onBackground = Color(0xFFE6E1E5),
    surface = Color(background.toLong()), onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(surfaceVariant.toLong()), onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(outline.toLong()), error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)
