package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.MunafDatabase
import com.example.data.local.UserPreferences
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.SaasAdminRepository
import com.example.firebase.FirebaseRuntimeConfig
import com.example.firebase.authStateFlow
import com.example.firebase.configureAppCheck
import com.example.firebase.signOutUser
import com.example.i18n.Strings
import com.example.i18n.tr
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AiBusinessAssistantScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.BlockedAccountGate
import com.example.ui.screens.BusinessProfileOnboardingScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FirebaseConfigMissingScreen
import com.example.ui.screens.InventoryScreen
import com.example.ui.screens.KhataLedgerScreen
import com.example.ui.screens.MoreHubScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.TransactionsScreen
import com.example.ui.screens.VoiceAccountingScreen
import com.example.ui.theme.MunafShopTheme
import com.example.ui.viewmodel.AdminSaasViewModel
import com.example.ui.viewmodel.MainViewModel
import com.example.util.VoiceServices
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

private enum class OverlayScreen {
    NONE, REPORTS, VOICE_ACCOUNTING, AI_ASSISTANT, SUBSCRIPTION, ADMIN_PANEL
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ensure Firebase is initialized (from bundled google-services.json or saved runtime config)
        FirebaseRuntimeConfig.ensureInitialized(this)
        // Initialize Firebase App Check immediately after FirebaseApp initialization
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            MunafOnlineShopRoot()
        }
    }
}

@Composable
private fun MunafOnlineShopRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { UserPreferences(context.applicationContext) }
    val language by prefs.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.BN)
    val themeMode by prefs.themeModeFlow.collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
    var isFirebaseInitialized by remember(context) {
        mutableStateOf(FirebaseRuntimeConfig.ensureInitialized(context))
    }

    MunafShopTheme(themeMode = themeMode) {
        if (!isFirebaseInitialized) {
            FirebaseConfigMissingScreen(
                language = language,
                onLanguageChange = { newLang -> scope.launch { prefs.setLanguage(newLang) } },
                onConfigured = {
                    configureAppCheck(context, null)
                    isFirebaseInitialized = FirebaseRuntimeConfig.ensureInitialized(context)
                }
            )
            return@MunafShopTheme
        }

        val currentUser by remember { Firebase.auth.authStateFlow() }
            .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

        val voiceServices = remember(context) { VoiceServices(context) }
        DisposableEffect(voiceServices) {
            onDispose { voiceServices.release() }
        }

        val user = currentUser
        if (user == null) {
            AuthScreen(
                language = language,
                onLanguageChange = { newLang -> scope.launch { prefs.setLanguage(newLang) } },
                onAuthSuccess = {}
            )
        } else {
            key(user.uid) {
                AuthenticatedBusinessShell(
                    userId = user.uid,
                    userEmail = user.email.orEmpty(),
                    userDisplayName = user.displayName.orEmpty(),
                    prefs = prefs,
                    voiceServices = voiceServices
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedBusinessShell(
    userId: String,
    userEmail: String,
    userDisplayName: String,
    prefs: UserPreferences,
    voiceServices: VoiceServices
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val mainViewModel: MainViewModel = viewModel(
        key = "main_$userId",
        factory = viewModelFactory {
            initializer {
                val dao = MunafDatabase.getInstance(context.applicationContext).munafDao()
                MainViewModel(
                    currentUserId = userId,
                    userEmail = userEmail,
                    userDisplayName = userDisplayName,
                    repository = BusinessRepository(context.applicationContext, dao),
                    prefs = prefs,
                    aiRepository = AiAssistantRepository()
                )
            }
        }
    )

    val adminViewModel: AdminSaasViewModel = viewModel(
        key = "admin_$userId",
        factory = viewModelFactory {
            initializer {
                AdminSaasViewModel(
                    currentUserId = userId,
                    repo = SaasAdminRepository(context.applicationContext)
                )
            }
        }
    )

    val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
    val toastMsg by mainViewModel.toastMessage.collectAsStateWithLifecycle()
    val voiceSpeakEvent by mainViewModel.voiceSpeakEvent.collectAsStateWithLifecycle()
    val aiMessages by mainViewModel.aiMessages.collectAsStateWithLifecycle()
    val aiStreaming by mainViewModel.aiStreaming.collectAsStateWithLifecycle()
    val voiceDraftState by mainViewModel.voiceDraftState.collectAsStateWithLifecycle()

    val packages by adminViewModel.packages.collectAsStateWithLifecycle()
    val myPaymentRequests by adminViewModel.myPaymentRequests.collectAsStateWithLifecycle()
    val adminProfile by adminViewModel.myAdminProfile.collectAsStateWithLifecycle()
    val allUsers by adminViewModel.allUsers.collectAsStateWithLifecycle()
    val allPayments by adminViewModel.allPaymentRequests.collectAsStateWithLifecycle()
    val subAdmins by adminViewModel.subAdmins.collectAsStateWithLifecycle()
    val auditLogs by adminViewModel.auditLogs.collectAsStateWithLifecycle()
    val adminStatusMsg by adminViewModel.statusMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(toastMsg) {
        val msg = toastMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearToast()
        }
    }

    LaunchedEffect(adminStatusMsg) {
        val msg = adminStatusMsg
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            adminViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(voiceSpeakEvent) {
        val event = voiceSpeakEvent
        if (event != null) {
            voiceServices.speakNotification(
                text = event.second,
                language = uiState.language,
                enabled = uiState.voiceNotifEnabled
            )
        }
    }

    val activePkg = mainViewModel.activeTier(packages)

    // If profile has not been created yet, show Business Onboarding
    if (uiState.profile == null) {
        BusinessProfileOnboardingScreen(
            language = uiState.language,
            defaultOwnerName = userDisplayName,
            onSave = { shopName, ownerName, phone, address, businessType, logoUri ->
                mainViewModel.saveBusinessProfile(shopName, ownerName, phone, address, businessType, logoUri)
            }
        )
        return
    }

    // If account was blocked by Super/Sub-Admin, show clear status gate with sign-out & refresh
    if (uiState.profile?.blocked == true && adminProfile == null) {
        BlockedAccountGate(
            language = uiState.language,
            shopName = uiState.profile?.shopName.orEmpty(),
            onRefresh = mainViewModel::refreshFromCloud,
            onSignOut = { signOutUser(credentialManager, scope) }
        )
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var tradeInitialTab by rememberSaveable { mutableIntStateOf(0) }
    var overlay by rememberSaveable { mutableStateOf(OverlayScreen.NONE) }

    if (overlay != OverlayScreen.NONE) {
        BackHandler { overlay = OverlayScreen.NONE }
    } else if (selectedTab != 0) {
        BackHandler { selectedTab = 0 }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (overlay == OverlayScreen.NONE) {
                val lang = uiState.language
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Icon(Icons.Default.Home, contentDescription = Strings.navDashboard(lang)) },
                        label = { Text(Strings.navDashboard(lang)) },
                        modifier = Modifier.testTag("nav_dashboard")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = Strings.navTransactions(lang)) },
                        label = { Text(Strings.navTransactions(lang)) },
                        modifier = Modifier.testTag("nav_trade")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = Strings.navKhata(lang)) },
                        label = { Text(Strings.navKhata(lang)) },
                        modifier = Modifier.testTag("nav_khata")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        icon = { Icon(Icons.Default.Inventory2, contentDescription = Strings.navStock(lang)) },
                        label = { Text(Strings.navStock(lang)) },
                        modifier = Modifier.testTag("nav_stock")
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { selectedTab = 4 },
                        icon = { Icon(Icons.Default.Menu, contentDescription = Strings.navMore(lang)) },
                        label = { Text(Strings.navMore(lang)) },
                        modifier = Modifier.testTag("nav_more")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (overlay) {
                OverlayScreen.REPORTS -> ReportsScreen(
                    state = uiState,
                    exportAllowed = activePkg.exportEnabled || adminProfile != null,
                    onSelectPeriod = mainViewModel::selectPeriod,
                    onSelectBranch = mainViewModel::selectBranch,
                    onBack = { overlay = OverlayScreen.NONE },
                    onMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                )

                OverlayScreen.VOICE_ACCOUNTING -> VoiceAccountingScreen(
                    language = uiState.language,
                    draftState = voiceDraftState,
                    voiceServices = voiceServices,
                    onStartListening = mainViewModel::setVoiceListening,
                    onSpeechCaptured = mainViewModel::parseVoiceCommand,
                    onSpeechError = mainViewModel::setVoiceError,
                    onConfirmDraft = { draft ->
                        mainViewModel.confirmVoiceDraft(draft)
                        overlay = OverlayScreen.NONE
                    },
                    onReset = mainViewModel::resetVoiceDraft,
                    onBack = { overlay = OverlayScreen.NONE }
                )

                OverlayScreen.AI_ASSISTANT -> AiBusinessAssistantScreen(
                    language = uiState.language,
                    aiAllowed = activePkg.aiEnabled || adminProfile != null,
                    messages = aiMessages,
                    isStreaming = aiStreaming,
                    onAsk = mainViewModel::askAiAssistant,
                    onBack = { overlay = OverlayScreen.NONE }
                )

                OverlayScreen.SUBSCRIPTION -> SubscriptionScreen(
                    language = uiState.language,
                    currentTierName = uiState.profile?.subscriptionTier ?: "FREE",
                    shopName = uiState.profile?.shopName ?: "MUNAF ONLINE SHOP",
                    packages = packages,
                    myRequests = myPaymentRequests,
                    onSubmitPayment = { method, sender, trxId, amount, tier, months, screenshotUri ->
                        adminViewModel.submitManualPayment(
                            shopName = uiState.profile?.shopName ?: "MUNAF ONLINE SHOP",
                            method = method,
                            senderNumber = sender,
                            transactionId = trxId,
                            amountBdt = amount,
                            packageTier = tier,
                            durationMonths = months,
                            screenshotUri = screenshotUri
                        )
                    },
                    onBack = { overlay = OverlayScreen.NONE }
                )

                OverlayScreen.ADMIN_PANEL -> {
                    val admin = adminProfile
                    if (admin != null) {
                        AdminPanelScreen(
                            language = uiState.language,
                            adminProfile = admin,
                            allUsers = allUsers,
                            allPayments = allPayments,
                            packages = packages,
                            subAdmins = subAdmins,
                            auditLogs = auditLogs,
                            onReviewPayment = adminViewModel::reviewPayment,
                            onToggleBlock = adminViewModel::toggleUserBlock,
                            onUpdatePackage = adminViewModel::updatePackage,
                            onSaveSubAdmin = adminViewModel::saveSubAdmin,
                            onBack = { overlay = OverlayScreen.NONE }
                        )
                    } else {
                        overlay = OverlayScreen.NONE
                    }
                }

                OverlayScreen.NONE -> when (selectedTab) {
                    0 -> DashboardScreen(
                        state = uiState,
                        onSelectBranch = mainViewModel::selectBranch,
                        onSelectPeriod = mainViewModel::selectPeriod,
                        onSyncNow = mainViewModel::syncNow,
                        onOpenNewSale = { tradeInitialTab = 0; selectedTab = 1 },
                        onOpenNewPurchase = { tradeInitialTab = 1; selectedTab = 1 },
                        onOpenDueCollect = { selectedTab = 2 },
                        onOpenExpense = { tradeInitialTab = 2; selectedTab = 1 },
                        onOpenVoiceAccounting = { overlay = OverlayScreen.VOICE_ACCOUNTING },
                        onOpenAiAssistant = { overlay = OverlayScreen.AI_ASSISTANT },
                        onOpenReports = { overlay = OverlayScreen.REPORTS },
                        onOpenNotifications = { selectedTab = 4 }
                    )

                    1 -> TransactionsScreen(
                        state = uiState,
                        initialTab = tradeInitialTab,
                        onRecordSale = mainViewModel::recordSale,
                        onRecordPurchase = mainViewModel::recordPurchase,
                        onRecordIncomeExpense = mainViewModel::recordIncomeOrExpense,
                        onCancelTransaction = mainViewModel::cancelTransaction
                    )

                    2 -> KhataLedgerScreen(
                        state = uiState,
                        voiceServices = voiceServices,
                        onAddParty = mainViewModel::saveParty,
                        onRecordPartyPayment = mainViewModel::recordPartyPayment,
                        onSaveVoiceNote = mainViewModel::saveVoiceNote,
                        onDeleteVoiceNote = mainViewModel::deleteVoiceNote
                    )

                    3 -> InventoryScreen(
                        state = uiState,
                        onSaveProduct = mainViewModel::saveProduct,
                        onAdjustStock = mainViewModel::adjustStock,
                        onTransferStock = mainViewModel::transferStock
                    )

                    else -> MoreHubScreen(
                        state = uiState,
                        activePackage = activePkg,
                        adminProfile = adminProfile,
                        voiceServices = voiceServices,
                        onOpenReports = { overlay = OverlayScreen.REPORTS },
                        onOpenSubscription = { overlay = OverlayScreen.SUBSCRIPTION },
                        onOpenAiAssistant = { overlay = OverlayScreen.AI_ASSISTANT },
                        onOpenVoiceAccounting = { overlay = OverlayScreen.VOICE_ACCOUNTING },
                        onOpenAdminPanel = { overlay = OverlayScreen.ADMIN_PANEL },
                        onSaveProfile = mainViewModel::saveBusinessProfile,
                        onCreateBranch = { n, a, p -> mainViewModel.createBranch(n, a, p, packages) },
                        onSaveEmployee = { n, ph, r, pin, br, s, p, rep, st, sal ->
                            mainViewModel.saveEmployee(n, ph, r, pin, br, s, p, rep, st, sal, packages)
                        },
                        onDeleteEmployee = mainViewModel::deleteEmployee,
                        onSwitchToOwner = mainViewModel::switchToOwner,
                        onSwitchToEmployee = mainViewModel::switchToEmployee,
                        onDeleteVoiceNote = mainViewModel::deleteVoiceNote,
                        onMarkNotificationsRead = mainViewModel::markNotificationsRead,
                        onChangeLanguage = mainViewModel::setLanguage,
                        onChangeTheme = mainViewModel::setThemeMode,
                        onToggleVoiceNotif = mainViewModel::setVoiceNotifications,
                        onSyncNow = mainViewModel::syncNow,
                        onSignOut = {
                            signOutUser(credentialManager, scope)
                        }
                    )
                }
            }
        }
    }
}
