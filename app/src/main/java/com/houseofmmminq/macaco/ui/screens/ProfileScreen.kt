package com.houseofmmminq.macaco.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StarRate
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.animateContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.houseofmmminq.macaco.R
import com.houseofmmminq.macaco.ui.components.MacacoBrandBlock
import com.houseofmmminq.macaco.data.model.AuthProvider
import com.houseofmmminq.macaco.ui.viewmodel.JournalViewModel
import com.houseofmmminq.macaco.util.AppActions
import com.houseofmmminq.macaco.util.ImageStorage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: JournalViewModel,
    onSignOut: () -> Unit,
    onLogin: () -> Unit,
    onSubscription: () -> Unit,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onYearInTravel: () -> Unit,
    onDeleteAccount: (String?, (Result<Unit>) -> Unit) -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val profilePhotoUri by viewModel.profilePhotoUri.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteInProgress by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    var showPhotoSourceSheet by remember { mutableStateOf(false) }
    // Saveable as a string: the camera app backgrounds us and the OS may kill the process;
    // without this the captured photo is lost on return.
    var pendingCameraUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingCameraUri: Uri? = pendingCameraUriString?.let(Uri::parse)

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Copy into our own storage so the photo survives relaunches (the Photo Picker
            // grant is temporary). See ImageStorage.
            ImageStorage.persist(context, uri, ImageStorage.PROFILE, replaceExisting = true)
                ?.let { viewModel.setProfilePhoto(it) }
        }
    }

    // Browse Files — ACTION_OPEN_DOCUMENT with image/* opens the system file picker, which
    // includes Google Drive and other cloud providers (the Photo Picker above does not).
    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            ImageStorage.persist(context, uri, ImageStorage.PROFILE, replaceExisting = true)
                ?.let { viewModel.setProfilePhoto(it) }
        }
    }

    // Camera — captures into the FileProvider temp URI from ImageStorage.
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            pendingCameraUri?.let { uri ->
                ImageStorage.persist(context, uri, ImageStorage.PROFILE, replaceExisting = true)
                    ?.let { viewModel.setProfilePhoto(it) }
            }
        }
        pendingCameraUriString = null
    }

    if (showPhotoSourceSheet) {
        ModalBottomSheet(onDismissRequest = { showPhotoSourceSheet = false }) {
            Text(
                text = stringResource(R.string.profile_change_photo_title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                textAlign = TextAlign.Center
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Gallery — Android system photo picker (device gallery + Google Photos).
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_photo_source_gallery)) },
                leadingContent = { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable {
                    showPhotoSourceSheet = false
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )

            // Browse Files — includes Google Drive and other cloud providers.
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_photo_source_files)) },
                leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                modifier = Modifier.clickable {
                    showPhotoSourceSheet = false
                    documentPicker.launch(arrayOf("image/*"))
                }
            )

            // Camera — capture a new photo.
            ListItem(
                headlineContent = { Text(stringResource(R.string.profile_photo_source_camera)) },
                leadingContent = { Icon(Icons.Outlined.CameraAlt, contentDescription = null) },
                modifier = Modifier.clickable {
                    showPhotoSourceSheet = false
                    val uri = ImageStorage.newCameraTempUri(context)
                    pendingCameraUriString = uri?.toString()
                    uri?.let { cameraPicker.launch(it) }
                }
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.common_sign_out)) },
            text = {
                Text(stringResource(R.string.profile_sign_out_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.signOut()
                    showSignOutDialog = false
                    onSignOut()
                }) {
                    Text(stringResource(R.string.common_sign_out), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    var deletePassword by remember { mutableStateOf("") }
    if (showDeleteAccountDialog) {
        val isEmailAccount = currentUser?.provider == AuthProvider.Email
        AlertDialog(
            onDismissRequest = {
                if (!deleteInProgress) {
                    showDeleteAccountDialog = false; deleteError = null; deletePassword = ""
                }
            },
            title = { Text(stringResource(R.string.profile_delete_account_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_delete_account_body))
                    if (isEmailAccount) {
                        Spacer(Modifier.height(12.dp))
                        // Re-auth is required before deletion; email accounts confirm with
                        // their password (Google accounts re-auth silently).
                        androidx.compose.material3.OutlinedTextField(
                            value = deletePassword,
                            onValueChange = { deletePassword = it; deleteError = null },
                            label = { Text(stringResource(R.string.profile_delete_password_label)) },
                            singleLine = true,
                            enabled = !deleteInProgress,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    deleteError?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteInProgress = true
                        deleteError = null
                        onDeleteAccount(deletePassword.takeIf { isEmailAccount }) { result ->
                            deleteInProgress = false
                            result.fold(
                                // On success the auth listener nulls currentUser → NavGraph shows
                                // LoginScreen; no explicit navigation needed.
                                onSuccess = { showDeleteAccountDialog = false; deletePassword = "" },
                                onFailure = { deleteError = it.message ?: context.getString(R.string.profile_delete_account_error) }
                            )
                        }
                    },
                    enabled = !deleteInProgress && (!isEmailAccount || deletePassword.isNotBlank()),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (deleteInProgress) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.profile_delete_account_confirm))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAccountDialog = false; deleteError = null; deletePassword = "" },
                    enabled = !deleteInProgress
                ) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    val profileScrollState = rememberScrollState()
    val isLandscape = LocalConfiguration.current.screenHeightDp < 480
    // Collapses once the page scrolls — currently only reachable in states where the content
    // (stats + settings buttons) overflows the screen; inert (never true) when everything fits,
    // which today means portrait on a typical phone.
    val collapsed by remember { derivedStateOf { profileScrollState.value > 24 } }
    // Single source of truth for the banner's bottom clearance — reused below for the content
    // pull-up offset so the two can never drift out of sync (that drift was the landscape/collapsed
    // overlap bug: offset was hardcoded to -32dp while this value could be as low as 8dp).
    val bannerBottomPadding = if (collapsed) 8.dp else if (isLandscape) 12.dp else 32.dp

    Scaffold(
        // The branded teal banner runs edge-to-edge under the status bar; opt out of the default
        // top inset and re-apply it inside the banner instead.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // Pinned above the scrollable body — same pattern as Journal/Help & About, so the
            // header stays visible (shrunk to icon-only) instead of scrolling away with the
            // rest of the page once the user scrolls past it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(macacoBrandBackground())
                    .statusBarsPadding()
                    .animateContentSize()
            ) {
                // Profile is a bottom-nav tab (reached via the nav bar), so a back arrow here
                // is redundant — the brand block stands alone, centred. No page-label subtitle,
                // to match Journal's header exactly.
                MacacoBrandBlock(
                    isLandscape = isLandscape,
                    collapsed = collapsed,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(top = 4.dp, bottom = bannerBottomPadding)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(profileScrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // The body must NOT pull up into the banner: the banner is the Scaffold topBar, which
            // Compose draws on top of this body, so any upward offset paints the teal over the top
            // of the avatar (it looked "tucked under the header", worst on tablets). Sit flush
            // below the banner instead — the background ring still gives the avatar breathing room.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            val user = currentUser
            if (user != null) {
                // Tappable avatar circle, with a background-colored ring so it reads over the banner.
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable { showPhotoSourceSheet = true },
                    contentAlignment = Alignment.Center
                ) {
                    // Custom local photo first, then the Google account avatar, then initials.
                    val displayPhotoModel: Any? = profilePhotoUri ?: user.photoUrl
                    if (displayPhotoModel != null) {
                        AsyncImage(
                            model = displayPhotoModel,
                            contentDescription = stringResource(R.string.profile_photo_cd),
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            // If the Google URL fails to load (offline, revoked), fall through to a Person icon.
                            error = rememberVectorPainter(Icons.Default.Person)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(84.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                user.displayName.take(2).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Camera badge (bottom-right)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = stringResource(R.string.profile_change_photo_cd),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    user.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(6.dp))

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = when (user.provider) {
                            AuthProvider.Google -> stringResource(R.string.profile_google_account)
                            AuthProvider.Email -> stringResource(R.string.profile_email_account)
                            AuthProvider.Guest -> stringResource(R.string.profile_guest)
                        },
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }

                Spacer(Modifier.height(10.dp))

                // Number of distinct named trips; the Trips stat is hidden when this is 0 so
                // users who never name a trip aren't shown an empty counter.
                val tripCount = entries
                    .mapNotNull { it.tripName?.trim()?.ifBlank { null } }
                    .distinct()
                    .size

                // Stats card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatItem(value = "${entries.size}", label = stringResource(R.string.profile_memories))

                        if (tripCount > 0) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(40.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant)
                            )
                            StatItem(value = tripCount.toString(), label = stringResource(R.string.profile_trips))
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        StatItem(
                            value = entries.mapNotNull { it.location.ifBlank { null } }
                                .distinct().size.toString(),
                            label = stringResource(R.string.profile_locations)
                        )
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        StatItem(
                            value = entries.sumOf { it.photoUris.size + it.videoUris.size }.toString(),
                            label = stringResource(R.string.profile_media)
                        )
                    }
                }

                // "Member since" moved to the footer, below Delete Account (see action section).
                // Utility + action buttons render as a 2-column grid in the action section
                // below (see ProfileActionTile), so nothing further here.

            } else {
                // Not signed in
                Spacer(Modifier.height(56.dp))
                Text("🔑", fontSize = 64.sp)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.profile_no_account_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.profile_no_account_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            }
            // Seam between the identity zone and the actions zone. Chrome policy: content
            // chrome follows the theme — `secondary` renders macaco gold on the default theme.
            HorizontalDivider(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 24.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            )

            // Action buttons in-flow after the stats (v3), laid out as a compact 2-column grid
            // so Settings / Help / Share / Rate / Subscription / Sign Out all fit.
            val user = currentUser
            if (user != null) {
                val gridSpacing = 6.dp
                // Tablets stay single-column even in landscape (see code-brief-profile-single-column.md — the
                // old 2-pane layout looked cramped), but that column was sized for tall/narrow phone screens.
                // On a tablet in landscape the available height is comparatively short, so the 3-row action
                // grid below was forcing the whole screen to scroll just to reach Delete Account. Reclaim the
                // space by using the tablet's spare width instead: one row of 6 tiles rather than 3 rows of 2.
                val isWideLayout = LocalConfiguration.current.screenWidthDp >= 600 &&
                    LocalConfiguration.current.screenWidthDp > LocalConfiguration.current.screenHeightDp
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = if (isWideLayout) 760.dp else 560.dp)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp)
                        .padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(gridSpacing)
                ) {
                    if (isWideLayout) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileActionTile(Icons.Filled.CalendarMonth, stringResource(R.string.year_recap_action), onClick = onYearInTravel)
                            ProfileActionTile(Icons.Filled.Settings, stringResource(R.string.common_settings), onClick = onSettings)
                            ProfileActionTile(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.drawer_help), onClick = onHelp)
                            ProfileActionTile(Icons.Filled.Share, stringResource(R.string.drawer_share_app), onClick = { AppActions.shareApp(context, entries.size) })
                            ProfileActionTile(Icons.Filled.StarRate, stringResource(R.string.drawer_rate_us), onClick = { AppActions.requestReview(context) })
                            ProfileActionTile(Icons.Outlined.WorkspacePremium, stringResource(R.string.common_subscription), onClick = onSubscription)
                            ProfileActionTile(
                                Icons.AutoMirrored.Filled.Logout,
                                stringResource(R.string.common_sign_out),
                                tint = MaterialTheme.colorScheme.error,
                                onClick = { showSignOutDialog = true }
                            )
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileActionTile(Icons.Filled.CalendarMonth, stringResource(R.string.year_recap_action), onClick = onYearInTravel)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileActionTile(Icons.Filled.Settings, stringResource(R.string.common_settings), onClick = onSettings)
                            ProfileActionTile(Icons.AutoMirrored.Filled.HelpOutline, stringResource(R.string.drawer_help), onClick = onHelp)
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileActionTile(Icons.Filled.Share, stringResource(R.string.drawer_share_app), onClick = { AppActions.shareApp(context, entries.size) })
                            ProfileActionTile(Icons.Filled.StarRate, stringResource(R.string.drawer_rate_us), onClick = { AppActions.requestReview(context) })
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ProfileActionTile(Icons.Outlined.WorkspacePremium, stringResource(R.string.common_subscription), onClick = onSubscription)
                            ProfileActionTile(
                                Icons.AutoMirrored.Filled.Logout,
                                stringResource(R.string.common_sign_out),
                                tint = MaterialTheme.colorScheme.error,
                                onClick = { showSignOutDialog = true }
                            )
                        }
                    }
                }
                // GDPR/Play-required in-app account deletion — de-emphasised full-width text
                // button so it isn't an easy mis-tap.
                TextButton(
                    onClick = { showDeleteAccountDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 24.dp)
                        .padding(top = 2.dp, bottom = 6.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.profile_delete_account))
                }
                // "Member since" as footer metadata under Delete Account (moved here in v3).
                val memberSince = user.createdAt?.let { millis ->
                    java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
                        .format(java.util.Date(millis))
                }
                if (memberSince != null) {
                    Text(
                        text = stringResource(R.string.profile_member_since, memberSince),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(top = 2.dp, bottom = 4.dp)
                    )
                }
            } else {
                Button(
                    onClick = onLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.common_sign_in), fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(4.dp))
        }
      }
    }
}

/** One tile in the Profile portrait 2-column action grid: centred icon over a single-line label. */
@Composable
private fun RowScope.ProfileActionTile(
    icon: ImageVector,
    label: String,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (tint == Color.Unspecified) MaterialTheme.colorScheme.primary else tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = if (tint == Color.Unspecified) MaterialTheme.colorScheme.onSurface else tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

// Not private: reused by YearInTravelScreen's stat grid (same package).
@Composable
internal fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
