package com.simpletodo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.simpletodo.AppGraph
import com.simpletodo.data.ThemeMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The SimpleTodo look: warm amber brand colour, soft neutral greys, generous corner radii.
 *
 * Dynamic (wallpaper) colour is deliberately not used. The amber is the product's identity and
 * shows up identically on the widgets, which sit next to other apps on the home screen; letting
 * the system recolour the app but not the widget would break that pairing.
 */

// The scheme's `primary` is the *deep* amber, not the pastel: Material hands `primary` to text and
// foreground roles (a TextButton's label, a cursor, a progress spinner) where a pastel would be
// unreadable. Every place the brand is a fill reaches for TodoAccents.colorAt(0) instead.
private val BrandAmber = Color(0xFF7A5400)
private val BrandPastel = Color(0xFFFBD87A)
private val BrandInk = Color(0xFF2A2418)

/** Exposed so the Glance widgets can theme themselves from the same two schemes. */
val TodoLightColors = lightColorScheme(
    primary = BrandAmber,
    onPrimary = Color.White,
    primaryContainer = BrandPastel,
    onPrimaryContainer = BrandInk,
    secondary = Color(0xFF6B6F76),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8EAEF),
    onSecondaryContainer = Color(0xFF2B2E33),
    tertiary = Color(0xFF6B3FBF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6ABF5),
    onTertiaryContainer = BrandInk,
    // Pure white, the same white the launcher icon is drawn on, so the cat in the app bar and the
    // cat on the home screen are the same mark on the same ground.
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF191A1D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF191A1D),
    surfaceVariant = Color(0xFFECEEF2),
    onSurfaceVariant = Color(0xFF6A6E76),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFBFC),
    surfaceContainer = Color(0xFFF1F3F6),
    surfaceContainerHigh = Color(0xFFEAECF1),
    surfaceContainerHighest = Color(0xFFE3E6EC),
    outline = Color(0xFFC3C7CE),
    outlineVariant = Color(0xFFE2E5EA),
    // Softened from a signal red, but not into a pastel: this one is only ever used as the label of
    // a destructive action, and it has to stay readable to mean anything.
    error = Color(0xFFC2504F),
    onError = Color.White,
    errorContainer = Color(0xFFF9D9D7),
    onErrorContainer = Color(0xFF4A1315),
)

val TodoDarkColors = darkColorScheme(
    // On a dark surface the pastel is the readable tone, so `primary` and its container converge.
    primary = BrandPastel,
    onPrimary = BrandInk,
    primaryContainer = BrandPastel,
    onPrimaryContainer = BrandInk,
    secondary = Color(0xFFB9BEC7),
    onSecondary = Color(0xFF23262B),
    secondaryContainer = Color(0xFF2C3037),
    onSecondaryContainer = Color(0xFFDCE0E7),
    tertiary = Color(0xFFC6ABF5),
    onTertiary = BrandInk,
    tertiaryContainer = Color(0xFFC6ABF5),
    onTertiaryContainer = BrandInk,
    background = Color(0xFF0F1013),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF14161A),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF2A2E34),
    onSurfaceVariant = Color(0xFF9AA0A9),
    surfaceContainerLowest = Color(0xFF0B0C0F),
    surfaceContainerLow = Color(0xFF14161A),
    surfaceContainer = Color(0xFF1A1D22),
    surfaceContainerHigh = Color(0xFF22252B),
    surfaceContainerHighest = Color(0xFF2B2F35),
    outline = Color(0xFF585E67),
    outlineVariant = Color(0xFF34383F),
    error = Color(0xFFF2A6A2),
    onError = Color(0xFF4A1315),
    errorContainer = Color(0xFF5E2523),
    onErrorContainer = Color(0xFFF9D9D7),
)

// The certificate list ships inside ui-text-google-fonts. With non-transitive R classes it has to
// be addressed through that library's own R, not the app's.
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = androidx.compose.ui.text.googlefonts.R.array.com_google_android_gms_fonts_certs,
)

private val poppins = GoogleFont("Poppins")

/**
 * Poppins is fetched through the platform's downloadable-font provider rather than bundled, which
 * keeps ~400 KB of TTF out of the APK. Compose renders in the platform default until it arrives,
 * and simply stays there on devices without the provider — no crash, no blank text.
 */
private val Brand = FontFamily(
    Font(googleFont = poppins, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = poppins, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = poppins, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = poppins, fontProvider = fontProvider, weight = FontWeight.Bold),
)

private val TodoTypography = Typography().run {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = Brand),
        displayMedium = displayMedium.copy(fontFamily = Brand),
        displaySmall = displaySmall.copy(fontFamily = Brand),
        headlineLarge = headlineLarge.copy(fontFamily = Brand, fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontFamily = Brand, fontWeight = FontWeight.Bold),
        headlineSmall = headlineSmall.copy(fontFamily = Brand, fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(
            fontFamily = Brand,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = titleMedium.copy(fontFamily = Brand, fontWeight = FontWeight.SemiBold),
        titleSmall = titleSmall.copy(fontFamily = Brand, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontFamily = Brand, fontSize = 15.sp, lineHeight = 21.sp),
        bodyMedium = bodyMedium.copy(fontFamily = Brand, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = bodySmall.copy(fontFamily = Brand, fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = labelLarge.copy(fontFamily = Brand, fontWeight = FontWeight.Medium),
        labelMedium = labelMedium.copy(fontFamily = Brand, fontWeight = FontWeight.Medium),
        labelSmall = labelSmall.copy(fontFamily = Brand, fontWeight = FontWeight.Medium),
    )
}

private val TodoShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * The dark/light decision for the whole app: the user's stored choice, falling back to the system
 * setting when they have not made one.
 *
 * Read here rather than threaded down from each activity, so the quick-add sheet and the widget
 * configuration screen honour the preference too without either of them having to know about it.
 */
@Composable
fun rememberIsDarkTheme(): Boolean {
    val mode by AppGraph.get(LocalContext.current).theme.mode.collectAsState()
    return when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
}

@Composable
fun SimpleTodoTheme(
    darkTheme: Boolean = rememberIsDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) TodoDarkColors else TodoLightColors,
        typography = TodoTypography,
        shapes = TodoShapes,
        content = content,
    )
}
