# Brief: Add Terms of Service + Privacy Policy Acknowledgement to Login Screen

**Priority:** High (required for Play Store production release)  
**File:** `app/src/main/java/com/houseofmmminq/macaco/ui/screens/LoginScreen.kt`

## Rationale

Google Play requires that apps collecting user data display a link to a Privacy Policy. Additionally,
having users acknowledge the Terms of Service at sign-up establishes the legal basis for processing
their data under GDPR (contract performance). This is a passive acknowledgement — no checkbox or
consent banner is required, just visible links.

## Change

At the **bottom of the login form** (below all sign-in buttons, above any bottom padding),
add a `Text` composable with clickable inline links to the Terms of Service and Privacy Policy:

```kotlin
Spacer(Modifier.height(24.dp))
val annotatedString = buildAnnotatedString {
    append(stringResource(R.string.login_tos_prefix)) // "By continuing you agree to our "
    pushStringAnnotation("URL", AppActions.TERMS_URL)
    withStyle(SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )) {
        append(stringResource(R.string.login_tos_link))  // "Terms of Service"
    }
    pop()
    append(stringResource(R.string.login_tos_and))      // " and "
    pushStringAnnotation("URL", AppActions.PRIVACY_POLICY_URL)
    withStyle(SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline
    )) {
        append(stringResource(R.string.login_pp_link))  // "Privacy Policy"
    }
    pop()
    append(".")
}
ClickableText(
    text = annotatedString,
    style = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    ),
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 32.dp),
    onClick = { offset ->
        annotatedString.getStringAnnotations("URL", offset, offset)
            .firstOrNull()?.let { AppActions.openUrl(context, it.item) }
    }
)
```

## Add to AppActions.kt

Add the Terms of Service URL constant (next to the existing `PRIVACY_POLICY_URL`):

```kotlin
const val TERMS_URL = "https://mictroid.github.io/macaco/terms-of-service.html"
```

## Add to strings.xml (and all translation files)

```xml
<string name="login_tos_prefix">By continuing you agree to our </string>
<string name="login_tos_link">Terms of Service</string>
<string name="login_tos_and"> and </string>
<string name="login_pp_link">Privacy Policy</string>
```

For translation files, translate the natural-language strings. The URL constants come from
`AppActions` and do not need translation.

## Imports needed

```kotlin
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import com.houseofmmminq.macaco.util.AppActions
```

## Notes

- This is a passive acknowledgement (standard industry practice); no checkbox is required.
- The Terms of Service page will be hosted at `https://mictroid.github.io/macaco/terms-of-service.html`
  (the HTML file is being created separately and will be committed to the GitHub Pages branch).
- `AppActions.PRIVACY_POLICY_URL` already points to the correct hosted URL.
