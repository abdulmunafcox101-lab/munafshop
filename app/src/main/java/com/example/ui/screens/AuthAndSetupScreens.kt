package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.AppLanguage
import com.example.firebase.FirebaseRuntimeConfig
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.getAppSigningSha1
import com.example.firebase.onGoogleSignInClicked
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.theme.EmeraldPrimary
import com.example.ui.theme.EmeraldPrimaryLight

@Composable
fun AuthScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val appSha1 = remember(context) { getAppSigningSha1(context) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var shaCopied by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        attemptAutoSignIn(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = onAuthSuccess,
            onUnauthenticated = {},
            scope = scope
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Language Toggle Header
            Row(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Language",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = language == AppLanguage.BN,
                    onClick = { onLanguageChange(AppLanguage.BN) },
                    label = { Text("বাংলা") },
                    modifier = Modifier.testTag("lang_bn_chip")
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = language == AppLanguage.EN,
                    onClick = { onLanguageChange(AppLanguage.EN) },
                    label = { Text("English") },
                    modifier = Modifier.testTag("lang_en_chip")
                )
            }

            Spacer(Modifier.height(12.dp))

            // Hero Banner Card
            Card(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_shop_banner_1795030796453),
                        contentDescription = "Munaf Online Shop Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        EmeraldPrimary.copy(alpha = 0.35f),
                                        EmeraldPrimary.copy(alpha = 0.90f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            text = Strings.appTitle(language),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = Strings.tagline(language),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Key Feature Pills
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeatureHighlightRow(
                    icon = Icons.Default.AccountBalanceWallet,
                    title = tr(language, "দৈনিক বিক্রি, ক্রয় ও বাকি খাতা", "Daily Sales, Purchase & Due Khata"),
                    subtitle = tr(language, "TallyKhata-র মতো সহজ হিসাব ও PDF/Excel রিপোর্ট", "Easy ledger, instant profit & PDF/Excel exports")
                )
                FeatureHighlightRow(
                    icon = Icons.Default.CloudSync,
                    title = tr(language, "অফলাইন ফার্স্ট ও ক্লাউড সিঙ্ক", "Offline-First + Auto Cloud Sync"),
                    subtitle = tr(language, "ইন্টারনেট ছাড়াই ব্যবসা চালান, অনলাইনে এলে নিরাপদ সিঙ্ক", "Sell offline anytime; auto-syncs safely to your account")
                )
                FeatureHighlightRow(
                    icon = Icons.Default.Mic,
                    title = tr(language, "ভয়েস হিসাব ও AI বিজনেস সহায়ক", "Voice Accounting & AI Assistant"),
                    subtitle = tr(language, "বাংলায় কথা বলে হিসাব ও স্মার্ট ব্যবসার পরামর্শ", "Speak in Bengali/English to draft entries & get insights")
                )
                FeatureHighlightRow(
                    icon = Icons.Default.Security,
                    title = tr(language, "ব্যবসাভিত্তিক সম্পূর্ণ নিরাপত্তা", "Isolated Business Data Security"),
                    subtitle = tr(language, "প্রতিটি ব্যবসার ডেটা আলাদা ও শাখা/কর্মচারী নিয়ন্ত্রণ", "Strict data isolation, branch & role-based permissions")
                )
            }

            Spacer(Modifier.height(24.dp))

            if (errorMessage != null) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errorMessage.orEmpty(),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (!appSha1.isNullOrBlank()) {
                            Text(
                                text = tr(
                                    language,
                                    "এই APK-র SHA-1: $appSha1\nGitHub থেকে তৈরি APK-তে সাইন-ইন না হলে Firebase Console > Project Settings > Your apps (${context.packageName}) > Add fingerprint-এ এই SHA-1 যোগ করুন।",
                                    "APK SHA-1: $appSha1\nIf Google Sign-In fails on a GitHub-built APK, add this SHA-1 in Firebase Console > Project Settings > Your apps (${context.packageName}) > Add fingerprint."
                                ),
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall
                            )
                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(appSha1))
                                    shaCopied = true
                                },
                                modifier = Modifier.testTag("copy_sha1_button")
                            ) {
                                Text(
                                    if (shaCopied) tr(language, "SHA-1 কপি হয়েছে", "SHA-1 Copied")
                                    else tr(language, "SHA-1 কপি করুন", "Copy SHA-1")
                                )
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    isLoading = true
                    errorMessage = null
                    onGoogleSignInClicked(
                        context = context,
                        credentialManager = credentialManager,
                        onAuthSuccess = {
                            isLoading = false
                            onAuthSuccess()
                        },
                        onAuthError = { msg ->
                            isLoading = false
                            errorMessage = msg
                        },
                        scope = scope,
                        onAuthCancelled = { isLoading = false }
                    )
                },
                enabled = !isLoading,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("google_sign_in_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = Strings.signInWithGoogle(language),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = tr(
                    language,
                    "আপনার ব্যবসার হিসাব সুরক্ষিত রাখতে Google অ্যাকাউন্ট দিয়ে লগইন করুন।",
                    "Sign in with Google to isolate and sync your business ledger safely."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 500.dp)
            )
        }
    }
}

@Composable
private fun FeatureHighlightRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun BusinessProfileOnboardingScreen(
    language: AppLanguage,
    defaultOwnerName: String,
    onSave: (
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        businessType: String,
        logoUri: String?
    ) -> Unit
) {
    var shopName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf(defaultOwnerName) }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("মুদি ও জেনারেল স্টোর / Retail") }
    var logoUri by remember { mutableStateOf<Uri?>(null) }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) logoUri = uri }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(EmeraldPrimaryLight.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (logoUri != null) {
                                AsyncImage(
                                    model = logoUri,
                                    contentDescription = "Shop Logo",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Default.Storefront,
                                    contentDescription = "Shop Icon",
                                    tint = EmeraldPrimary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(language, "ব্যবসার প্রোফাইল তৈরি করুন", "Setup Business Profile"),
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                text = tr(language, "আপনার দোকানের তথ্য দিয়ে শুরু করুন", "Enter your shop details to start"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.testTag("pick_shop_logo_button")
                    ) {
                        Text(tr(language, "দোকানের লোগো নির্বাচন করুন", "Select Shop Logo"))
                    }

                    OutlinedTextField(
                        value = shopName,
                        onValueChange = { shopName = it },
                        label = { Text(tr(language, "দোকানের নাম *", "Shop Name *")) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("shop_name_input")
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = { ownerName = it },
                        label = { Text(tr(language, "মালিকের নাম *", "Owner Name *")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text(tr(language, "মোবাইল নম্বর (যেমন: 017...)", "Mobile Number")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(tr(language, "দোকানের ঠিকানা", "Shop Address")) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = businessType,
                        onValueChange = { businessType = it },
                        label = { Text(tr(language, "ব্যবসার ধরন", "Business Type")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (shopName.isNotBlank()) {
                                onSave(
                                    shopName,
                                    ownerName.ifBlank { "Owner" },
                                    phone,
                                    address,
                                    businessType,
                                    logoUri?.toString()
                                )
                            }
                        },
                        enabled = shopName.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("save_business_profile_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(tr(language, "ব্যবসা শুরু করুন", "Start Business"))
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedAccountGate(
    language: AppLanguage,
    shopName: String,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tr(language, "অ্যাকাউন্ট সাময়িকভাবে স্থগিত", "Account Temporarily Suspended"),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            language,
                            "$shopName অ্যাকাউন্টটি অ্যাডমিন কর্তৃক ব্লক করা হয়েছে। আপনার পূর্বের হিসাব সুরক্ষিত আছে। সাপোর্টের সাথে যোগাযোগ করুন।",
                            "$shopName has been blocked by an administrator. Your historical data remains safe. Please contact support."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        textAlign = TextAlign.Center
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = onRefresh, modifier = Modifier.weight(1f)) {
                            Text(tr(language, "আবার যাচাই", "Re-check"))
                        }
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f).testTag("blocked_sign_out_button")
                        ) {
                            Text(tr(language, "লগআউট", "Sign Out"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FirebaseConfigMissingScreen(
    language: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    onConfigured: () -> Unit = {}
) {
    val context = LocalContext.current
    var jsonInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val jsonFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val readResult = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to read selected file")
        }
        readResult.fold(
            onSuccess = { text ->
                jsonInput = text
                FirebaseRuntimeConfig.saveAndInitialize(context, text).fold(
                    onSuccess = {
                        errorMsg = null
                        onConfigured()
                    },
                    onFailure = { err ->
                        errorMsg = err.localizedMessage ?: tr(
                            language,
                            "সঠিক google-services.json ফাইল নয়।",
                            "Invalid google-services.json file."
                        )
                    }
                )
            },
            onFailure = { err ->
                errorMsg = err.localizedMessage ?: tr(
                    language,
                    "ফাইল পড়া যায়নি।",
                    "Failed to read file."
                )
            }
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                FilterChip(
                    selected = language == AppLanguage.BN,
                    onClick = { onLanguageChange(AppLanguage.BN) },
                    label = { Text("বাংলা") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = language == AppLanguage.EN,
                    onClick = { onLanguageChange(AppLanguage.EN) },
                    label = { Text("English") }
                )
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = tr(
                            language,
                            "Firebase কনফিগারেশন সেটআপ",
                            "Firebase Configuration Setup"
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = tr(
                            language,
                            "এই APK বিল্ডে google-services.json সংযুক্ত ছিল না। এখনই সরাসরি ফোনে থাকা google-services.json ফাইল বেছে নিন অথবা নিচে JSON কোড পেস্ট করে চালু করুন।",
                            "This APK was built without google-services.json. Select your google-services.json file from your phone or paste its JSON below to activate Google Sign-In & Cloud sync immediately."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            jsonFilePicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pick_google_services_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            tr(
                                language,
                                "ফাইল থেকে google-services.json লোড করুন",
                                "Select google-services.json File"
                            )
                        )
                    }

                    OutlinedTextField(
                        value = jsonInput,
                        onValueChange = {
                            jsonInput = it
                            errorMsg = null
                        },
                        label = {
                            Text(
                                tr(
                                    language,
                                    "অথবা এখানে google-services.json পেস্ট করুন",
                                    "Or paste google-services.json content here"
                                )
                            )
                        },
                        placeholder = { Text("{\"project_info\": { ... }}") },
                        minLines = 4,
                        maxLines = 7,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_services_json_input")
                    )

                    if (!errorMsg.isNullOrBlank()) {
                        Text(
                            text = errorMsg.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            FirebaseRuntimeConfig.saveAndInitialize(context, jsonInput).fold(
                                onSuccess = {
                                    errorMsg = null
                                    onConfigured()
                                },
                                onFailure = { err ->
                                    errorMsg = err.localizedMessage ?: tr(
                                        language,
                                        "সঠিক google-services.json JSON দিন।",
                                        "Please enter valid google-services.json content."
                                    )
                                }
                            )
                        },
                        enabled = jsonInput.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("apply_firebase_config_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            tr(
                                language,
                                "কনফিগ সেভ ও অ্যাপ চালু করুন",
                                "Save Config & Continue"
                            )
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tr(
                                language,
                                "টিপস: ভবিষ্যতের প্রতিটি APK বিল্ডে অটো-কনফিগ পেতে GitHub রিপোর Settings > Secrets > Actions-এ GOOGLE_SERVICES_JSON যোগ করুন (প্যাকেজ: ${context.packageName})।",
                                "Tip: To bundle automatically in future builds, add GOOGLE_SERVICES_JSON in GitHub Settings > Secrets > Actions (package: ${context.packageName})."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }
}



