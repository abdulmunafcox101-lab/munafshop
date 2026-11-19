package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.BranchEntity
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.EmployeeEntity
import com.example.data.local.NotificationEntity
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.StockLogEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TxLineItem
import com.example.data.local.UserPreferences
import com.example.data.local.VoiceNoteEntity
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.data.model.EmployeeRole
import com.example.data.model.PartyType
import com.example.data.model.ReportPeriod
import com.example.data.model.SubscriptionEngine
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionTier
import com.example.data.model.SyncStatus
import com.example.data.model.TransactionType
import com.example.data.repository.AiAssistantRepository
import com.example.data.repository.BusinessRepository
import com.example.data.repository.VoiceAccountingDraft
import com.example.util.Formatters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class OperatorSession(
    val id: String = "OWNER",
    val name: String = "মালিক (Owner)",
    val role: EmployeeRole = EmployeeRole.OWNER,
    val assignedBranchId: String = "",
    val canSell: Boolean = true,
    val canPurchase: Boolean = true,
    val canViewReports: Boolean = true,
    val canManageStock: Boolean = true
)

data class DashboardMetrics(
    val periodSales: Double = 0.0,
    val periodCashSales: Double = 0.0,
    val periodDueSales: Double = 0.0,
    val periodPurchase: Double = 0.0,
    val periodExpense: Double = 0.0,
    val periodOtherIncome: Double = 0.0,
    val periodDueCollected: Double = 0.0,
    val periodSupplierPaid: Double = 0.0,
    val periodCogs: Double = 0.0,
    val periodNetProfit: Double = 0.0,
    val cashInflow: Double = 0.0,
    val cashOutflow: Double = 0.0,
    val netCashBalance: Double = 0.0,
    val totalReceivable: Double = 0.0,
    val totalPayable: Double = 0.0,
    val todaySales: Double = 0.0,
    val todayPurchase: Double = 0.0,
    val todayExpense: Double = 0.0,
    val todayProfit: Double = 0.0,
    val monthlyProfit: Double = 0.0,
    val lowStockCount: Int = 0,
    val totalStockValue: Double = 0.0,
    val pendingSyncCount: Int = 0
)

data class AiChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

sealed interface VoiceDraftState {
    data object Idle : VoiceDraftState
    data object Listening : VoiceDraftState
    data object Parsing : VoiceDraftState
    data class Ready(val rawCommand: String, val draft: VoiceAccountingDraft) : VoiceDraftState
    data class Error(val message: String) : VoiceDraftState
}

data class BusinessUiState(
    val currentUserId: String = "",
    val userEmail: String = "",
    val profile: BusinessProfileEntity? = null,
    val branches: List<BranchEntity> = emptyList(),
    val products: List<ProductEntity> = emptyList(),
    val parties: List<PartyEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val stockLogs: List<StockLogEntity> = emptyList(),
    val employees: List<EmployeeEntity> = emptyList(),
    val voiceNotes: List<VoiceNoteEntity> = emptyList(),
    val notifications: List<NotificationEntity> = emptyList(),
    val activeBranchId: String = "", // "" means All Branches
    val selectedPeriod: ReportPeriod = ReportPeriod.TODAY,
    val operator: OperatorSession = OperatorSession(),
    val language: AppLanguage = AppLanguage.BN,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val voiceNotifEnabled: Boolean = true,
    val metrics: DashboardMetrics = DashboardMetrics(),
    val isSyncing: Boolean = false,
    val lastSyncMessage: String? = null
)

class MainViewModel(
    private val currentUserId: String,
    private val userEmail: String,
    private val userDisplayName: String,
    private val repository: BusinessRepository,
    private val prefs: UserPreferences,
    private val aiRepository: AiAssistantRepository
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(ReportPeriod.TODAY)
    private val _isSyncing = MutableStateFlow(false)
    private val _lastSyncMessage = MutableStateFlow<String?>(null)
    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _voiceSpeakEvent = MutableStateFlow<Pair<Long, String>?>(null)
    val voiceSpeakEvent: StateFlow<Pair<Long, String>?> = _voiceSpeakEvent.asStateFlow()

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _aiStreaming = MutableStateFlow(false)
    val aiStreaming: StateFlow<Boolean> = _aiStreaming.asStateFlow()

    private val _voiceDraftState = MutableStateFlow<VoiceDraftState>(VoiceDraftState.Idle)
    val voiceDraftState: StateFlow<VoiceDraftState> = _voiceDraftState.asStateFlow()

    private val coreDataFlow = combine(
        repository.observeBusinessProfile(currentUserId),
        repository.observeBranches(currentUserId),
        repository.observeProducts(currentUserId),
        repository.observeParties(currentUserId),
        repository.observeTransactions(currentUserId)
    ) { profile, branches, products, parties, transactions ->
        CoreBundle(profile, branches, products, parties, transactions)
    }

    private val secondaryDataFlow = combine(
        repository.observeStockLogs(currentUserId),
        repository.observeEmployees(currentUserId),
        repository.observeVoiceNotes(currentUserId),
        repository.observeNotifications(currentUserId)
    ) { logs, employees, voiceNotes, notifications ->
        SecondaryBundle(logs, employees, voiceNotes, notifications)
    }

    private val prefsFlow = combine(
        prefs.languageFlow,
        prefs.themeModeFlow,
        prefs.voiceNotifFlow,
        prefs.activeBranchIdFlow,
        prefs.activeOperatorIdFlow
    ) { lang, theme, voiceEnabled, branchId, operatorId ->
        PrefsBundle(lang, theme, voiceEnabled, branchId, operatorId)
    }

    val uiState: StateFlow<BusinessUiState> = combine(
        coreDataFlow,
        secondaryDataFlow,
        prefsFlow,
        _selectedPeriod,
        _isSyncing,
        _lastSyncMessage
    ) { arr ->
        val core = arr[0] as CoreBundle
        val sec = arr[1] as SecondaryBundle
        val pref = arr[2] as PrefsBundle
        val period = arr[3] as ReportPeriod
        val syncing = arr[4] as Boolean
        val syncMsg = arr[5] as String?

        val operator = resolveOperator(pref.operatorId, sec.employees)
        val effectiveBranchId = if (operator.role != EmployeeRole.OWNER && operator.assignedBranchId.isNotBlank()) {
            operator.assignedBranchId
        } else {
            pref.branchId
        }

        val metrics = computeMetrics(
            transactions = core.transactions,
            products = core.products,
            parties = core.parties,
            branches = core.branches,
            employees = sec.employees,
            profile = core.profile,
            branchId = effectiveBranchId,
            period = period
        )

        BusinessUiState(
            currentUserId = currentUserId,
            userEmail = userEmail,
            profile = core.profile,
            branches = core.branches,
            products = core.products,
            parties = core.parties,
            transactions = core.transactions,
            stockLogs = sec.logs,
            employees = sec.employees,
            voiceNotes = sec.voiceNotes,
            notifications = sec.notifications,
            activeBranchId = effectiveBranchId,
            selectedPeriod = period,
            operator = operator,
            language = pref.lang,
            themeMode = pref.theme,
            voiceNotifEnabled = pref.voiceEnabled,
            metrics = metrics,
            isSyncing = syncing,
            lastSyncMessage = syncMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = BusinessUiState(currentUserId = currentUserId, userEmail = userEmail)
    )

    init {
        refreshFromCloud()
    }

    fun refreshFromCloud() {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.pullFromCloud(currentUserId)
            val summary = repository.syncPendingToCloud(currentUserId)
            _isSyncing.value = false
            _lastSyncMessage.value = if (summary.failedCount == 0) {
                if (summary.syncedCount > 0) "Synced ${summary.syncedCount} items" else null
            } else {
                "Pending retry (${summary.failedCount}): ${summary.lastError ?: "Offline"}"
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            val summary = repository.syncPendingToCloud(currentUserId)
            repository.pullFromCloud(currentUserId)
            _isSyncing.value = false
            val msg = if (summary.failedCount == 0) {
                if (uiState.value.language == AppLanguage.BN) "ক্লাউড সিঙ্ক সম্পন্ন হয়েছে" else "Cloud sync completed"
            } else {
                "Offline / Retry needed (${summary.failedCount})"
            }
            _lastSyncMessage.value = msg
            _toastMessage.value = msg
        }
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun setLanguage(lang: AppLanguage) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefs.setThemeMode(mode) }
    }

    fun setVoiceNotifications(enabled: Boolean) {
        viewModelScope.launch { prefs.setVoiceNotificationsEnabled(enabled) }
    }

    fun selectBranch(branchId: String) {
        viewModelScope.launch { prefs.setActiveBranchId(branchId) }
    }

    fun selectPeriod(period: ReportPeriod) {
        _selectedPeriod.value = period
    }

    fun switchToOwner() {
        viewModelScope.launch { prefs.setActiveOperatorId("OWNER") }
    }

    fun switchToEmployee(employee: EmployeeEntity, enteredPin: String): Boolean {
        return if (employee.pinCode == enteredPin && employee.active) {
            viewModelScope.launch { prefs.setActiveOperatorId(employee.id) }
            true
        } else {
            false
        }
    }

    fun activeTier(
        packages: List<SubscriptionPackageDoc>,
        serverNowMillis: Long = System.currentTimeMillis()
    ): SubscriptionPackageDoc {
        val profile = uiState.value.profile
        return SubscriptionEngine.resolveActivePackage(
            tierName = profile?.subscriptionTier,
            expiresAtMillis = profile?.subscriptionExpiresAtMillis ?: 0L,
            serverNowMillis = serverNowMillis,
            packages = packages
        )
    }

    fun evaluateSubscriptionExpiryAlerts(serverNowMillis: Long) {
        val profile = uiState.value.profile ?: return
        if (profile.subscriptionTier == SubscriptionTier.FREE.name) return
        val countdown = SubscriptionEngine.computeCountdown(
            tierName = profile.subscriptionTier,
            startMillis = profile.subscriptionStartMillis,
            expiresAtMillis = profile.subscriptionExpiresAtMillis,
            serverNowMillis = serverNowMillis
        )
        val triggered = SubscriptionEngine.evaluateExpiryReminderThreshold(
            countdown = countdown,
            lastNotifiedThreshold = profile.lastExpiryWarningDays
        ) ?: return

        viewModelScope.launch {
            repository.saveBusinessProfile(profile.copy(lastExpiryWarningDays = triggered))
            val isBn = uiState.value.language == AppLanguage.BN
            val (title, body) = if (triggered == -1) {
                if (isBn) {
                    "সাবস্ক্রিপশনের মেয়াদ শেষ হয়েছে" to "আপনার ${profile.subscriptionTier} প্ল্যানের মেয়াদ শেষ হওয়ায় ফ্রি প্ল্যানে নেমেছে। পূর্বের সকল হিসাব সুরক্ষিত আছে।"
                } else {
                    "Subscription Expired" to "Your ${profile.subscriptionTier} plan expired and downgraded to Free. All historical accounting data remains safe."
                }
            } else {
                if (isBn) {
                    "মেয়াদ শেষের সতর্কতা ($triggered দিন বাকি)" to "আপনার ${profile.subscriptionTier} প্যাকেজের মেয়াদ মাত্র $triggered দিন (বা কম) বাকি। সময়মতো নবায়ন করুন।"
                } else {
                    "Subscription Expiring ($triggered Day(s) Left)" to "Your ${profile.subscriptionTier} plan expires in $triggered day(s) or less. Renew to keep paid features."
                }
            }
            repository.addLocalNotification(
                userId = currentUserId,
                title = title,
                body = body,
                category = "SUBSCRIPTION_EXPIRY"
            )
        }
    }

    fun saveBusinessProfile(
        shopName: String,
        ownerName: String,
        phone: String,
        address: String,
        businessType: String,
        logoUri: String?
    ) {
        viewModelScope.launch {
            val existing = uiState.value.profile
            val profile = BusinessProfileEntity(
                userId = currentUserId,
                ownerEmail = userEmail,
                shopName = shopName.trim(),
                ownerName = ownerName.trim().ifBlank { userDisplayName },
                phone = phone.trim(),
                address = address.trim(),
                businessType = businessType.trim().ifBlank { "Retail & General" },
                logoUri = logoUri ?: existing?.logoUri,
                subscriptionTier = existing?.subscriptionTier ?: SubscriptionTier.FREE.name,
                subscriptionMonths = existing?.subscriptionMonths ?: 0,
                subscriptionStartMillis = existing?.subscriptionStartMillis ?: 0L,
                subscriptionExpiresAtMillis = existing?.subscriptionExpiresAtMillis ?: 0L,
                blocked = existing?.blocked ?: false,
                accountStatus = existing?.accountStatus ?: "ACTIVE",
                banReason = existing?.banReason ?: "",
                suspendedUntilMillis = existing?.suspendedUntilMillis ?: 0L,
                lastExpiryWarningDays = existing?.lastExpiryWarningDays ?: 0
            )
            repository.saveBusinessProfile(profile)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) {
                "ব্যবসার প্রোফাইল সংরক্ষিত হয়েছে"
            } else {
                "Business profile saved"
            }
        }
    }

    fun createBranch(
        name: String,
        address: String,
        phone: String,
        packages: List<SubscriptionPackageDoc>
    ) {
        viewModelScope.launch {
            val pkg = activeTier(packages)
            val currentCount = uiState.value.branches.size
            if (currentCount >= pkg.maxBranches) {
                _toastMessage.value = if (uiState.value.language == AppLanguage.BN) {
                    "আপনার ${pkg.nameBn} প্যাকেজে সর্বোচ্চ ${pkg.maxBranches}টি শাখা অনুমোদিত। প্যাকেজ আপগ্রেড করুন।"
                } else {
                    "Your ${pkg.nameEn} plan allows up to ${pkg.maxBranches} branch(es). Please upgrade."
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            val branch = BranchEntity(
                id = "br_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = currentUserId,
                name = name.trim(),
                address = address.trim(),
                phone = phone.trim(),
                mainBranch = currentCount == 0,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.saveBranch(branch)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "নতুন শাখা যোগ হয়েছে" else "Branch added"
        }
    }

    fun saveProduct(
        existingId: String?,
        branchId: String,
        name: String,
        sku: String,
        category: String,
        costPrice: Double,
        sellPrice: Double,
        stockQty: Double,
        minStockAlert: Double,
        photoUri: String?
    ) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val targetBranch = branchId.ifBlank {
                uiState.value.branches.firstOrNull()?.id ?: "br_main_$currentUserId".take(40)
            }
            val prod = ProductEntity(
                id = existingId ?: "prod_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = currentUserId,
                branchId = targetBranch,
                name = name.trim(),
                sku = sku.trim(),
                category = category.trim().ifBlank { "General" },
                costPrice = costPrice.coerceAtLeast(0.0),
                sellPrice = sellPrice.coerceAtLeast(0.0),
                stockQty = stockQty.coerceAtLeast(0.0),
                minStockAlert = minStockAlert.coerceAtLeast(0.0),
                photoUri = photoUri,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.saveProduct(prod)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "পণ্য সংরক্ষিত হয়েছে" else "Product saved"
        }
    }

    fun adjustStock(product: ProductEntity, delta: Double, reason: String) {
        viewModelScope.launch {
            repository.adjustStock(product, delta, reason)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "স্টক আপডেট হয়েছে" else "Stock adjusted"
        }
    }

    fun transferStock(sourceProduct: ProductEntity, targetBranchId: String, qty: Double) {
        viewModelScope.launch {
            repository.transferStock(sourceProduct, targetBranchId, qty)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "স্টক স্থানান্তর সম্পন্ন" else "Stock transferred"
        }
    }

    fun saveParty(
        partyType: PartyType,
        name: String,
        phone: String,
        address: String,
        openingDue: Double = 0.0
    ) {
        viewModelScope.launch {
            val branchId = resolveWorkingBranchId()
            val now = System.currentTimeMillis()
            val party = PartyEntity(
                id = "pty_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = currentUserId,
                branchId = branchId,
                partyType = partyType.name,
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                totalAmount = openingDue.coerceAtLeast(0.0),
                paidAmount = 0.0,
                dueAmount = openingDue.coerceAtLeast(0.0),
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.saveParty(party)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "খাতা সংরক্ষিত হয়েছে" else "Ledger party saved"
        }
    }

    fun recordSale(
        lineItems: List<TxLineItem>,
        customTotalIfNoItems: Double,
        discount: Double,
        paidAmount: Double,
        party: PartyEntity?,
        walkInName: String,
        note: String
    ) {
        viewModelScope.launch {
            if (!uiState.value.operator.canSell) {
                _toastMessage.value = "Permission denied for Sale"
                return@launch
            }
            val subtotal = if (lineItems.isNotEmpty()) lineItems.sumOf { it.lineTotal } else customTotalIfNoItems
            val cleanDiscount = discount.coerceIn(0.0, subtotal)
            val total = (subtotal - cleanDiscount).coerceAtLeast(0.0)
            val cleanPaid = paidAmount.coerceIn(0.0, total)
            val due = (total - cleanPaid).coerceAtLeast(0.0)
            val cogs = lineItems.sumOf { it.unitCost * it.quantity }
            val branchId = resolveWorkingBranchId()
            val now = System.currentTimeMillis()

            val tx = TransactionEntity(
                id = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = currentUserId,
                branchId = branchId,
                txType = TransactionType.SALE.name,
                partyId = party?.id.orEmpty(),
                partyName = party?.name ?: walkInName.ifBlank {
                    if (uiState.value.language == AppLanguage.BN) "নগদ কাস্টমার" else "Cash Customer"
                },
                subtotal = subtotal,
                discount = cleanDiscount,
                totalAmount = total,
                paidAmount = cleanPaid,
                dueAmount = due,
                cogsTotal = cogs,
                category = "Sales",
                note = note.trim(),
                status = "COMPLETED",
                operatorName = uiState.value.operator.name,
                lineItemsJson = TxLineItem.toJson(lineItems),
                txDateMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.recordSale(tx, lineItems)
            val voiceMsg = if (uiState.value.language == AppLanguage.BN) {
                "নতুন বিক্রি সংরক্ষিত হয়েছে, মোট ${total.toInt()} টাকা।"
            } else {
                "New sale recorded for ${total.toInt()} Taka."
            }
            triggerVoice(voiceMsg)
            _toastMessage.value = voiceMsg
        }
    }

    fun recordPurchase(
        lineItems: List<TxLineItem>,
        customTotalIfNoItems: Double,
        paidAmount: Double,
        supplier: PartyEntity?,
        supplierName: String,
        note: String
    ) {
        viewModelScope.launch {
            if (!uiState.value.operator.canPurchase) {
                _toastMessage.value = "Permission denied for Purchase"
                return@launch
            }
            val total = if (lineItems.isNotEmpty()) lineItems.sumOf { it.lineTotal } else customTotalIfNoItems
            val cleanPaid = paidAmount.coerceIn(0.0, total)
            val due = (total - cleanPaid).coerceAtLeast(0.0)
            val branchId = resolveWorkingBranchId()
            val now = System.currentTimeMillis()

            val tx = TransactionEntity(
                id = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = currentUserId,
                branchId = branchId,
                txType = TransactionType.PURCHASE.name,
                partyId = supplier?.id.orEmpty(),
                partyName = supplier?.name ?: supplierName.ifBlank { "Supplier" },
                subtotal = total,
                discount = 0.0,
                totalAmount = total,
                paidAmount = cleanPaid,
                dueAmount = due,
                cogsTotal = 0.0,
                category = "Purchase",
                note = note.trim(),
                status = "COMPLETED",
                operatorName = uiState.value.operator.name,
                lineItemsJson = TxLineItem.toJson(lineItems),
                txDateMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.recordPurchase(tx, lineItems)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) {
                "ক্রয় হিসাব ও স্টক সংরক্ষিত হয়েছে"
            } else {
                "Purchase & stock saved"
            }
        }
    }

    fun recordPartyPayment(party: PartyEntity, amount: Double, note: String) {
        viewModelScope.launch {
            if (amount <= 0.0) return@launch
            val isCustomer = party.partyType == PartyType.CUSTOMER.name
            val txType = if (isCustomer) TransactionType.DUE_COLLECTION else TransactionType.SUPPLIER_PAYMENT
            val now = System.currentTimeMillis()
            val tx = TransactionEntity(
                id = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = currentUserId,
                branchId = party.branchId.ifBlank { resolveWorkingBranchId() },
                txType = txType.name,
                partyId = party.id,
                partyName = party.name,
                subtotal = amount,
                discount = 0.0,
                totalAmount = amount,
                paidAmount = amount,
                dueAmount = 0.0,
                cogsTotal = 0.0,
                category = txType.name,
                note = note.trim(),
                status = "COMPLETED",
                operatorName = uiState.value.operator.name,
                lineItemsJson = "[]",
                txDateMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.recordPartyPayment(tx)
            val msg = if (uiState.value.language == AppLanguage.BN) {
                if (isCustomer) "${party.name} থেকে ${amount.toInt()} টাকা বাকি আদায় হয়েছে"
                else "${party.name} কে ${amount.toInt()} টাকা পরিশোধ হয়েছে"
            } else {
                "${party.name}: ${amount.toInt()} BDT recorded"
            }
            triggerVoice(msg)
            _toastMessage.value = msg
        }
    }

    fun recordIncomeOrExpense(
        isExpense: Boolean,
        amount: Double,
        category: String,
        note: String
    ) {
        viewModelScope.launch {
            if (amount <= 0.0) return@launch
            val now = System.currentTimeMillis()
            val txType = if (isExpense) TransactionType.EXPENSE else TransactionType.INCOME
            val tx = TransactionEntity(
                id = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = currentUserId,
                branchId = resolveWorkingBranchId(),
                txType = txType.name,
                partyId = "",
                partyName = category,
                subtotal = amount,
                discount = 0.0,
                totalAmount = amount,
                paidAmount = amount,
                dueAmount = 0.0,
                cogsTotal = 0.0,
                category = category,
                note = note.trim(),
                status = "COMPLETED",
                operatorName = uiState.value.operator.name,
                lineItemsJson = "[]",
                txDateMillis = now,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.recordSimpleTransaction(tx)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "হিসাব সংরক্ষিত হয়েছে" else "Entry saved"
        }
    }

    fun cancelTransaction(txId: String) {
        viewModelScope.launch {
            repository.cancelTransaction(txId)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) {
                "লেনদেন বাতিল ও স্টক সমন্বয় করা হয়েছে"
            } else {
                "Transaction cancelled & stock restored"
            }
        }
    }

    fun saveEmployee(
        name: String,
        phone: String,
        role: EmployeeRole,
        pinCode: String,
        branchId: String,
        canSell: Boolean,
        canPurchase: Boolean,
        canViewReports: Boolean,
        canManageStock: Boolean,
        monthlySalary: Double,
        packages: List<SubscriptionPackageDoc>
    ) {
        viewModelScope.launch {
            val pkg = activeTier(packages)
            if (uiState.value.employees.size >= pkg.maxEmployees) {
                _toastMessage.value = if (uiState.value.language == AppLanguage.BN) {
                    "আপনার ${pkg.nameBn} প্যাকেজে সর্বোচ্চ ${pkg.maxEmployees} জন কর্মচারী অনুমোদিত।"
                } else {
                    "Your ${pkg.nameEn} plan allows up to ${pkg.maxEmployees} employee(s)."
                }
                return@launch
            }
            val now = System.currentTimeMillis()
            val emp = EmployeeEntity(
                id = "emp_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                userId = currentUserId,
                name = name.trim(),
                phone = phone.trim(),
                role = role.name,
                pinCode = pinCode.trim().ifBlank { "1234" },
                branchId = branchId.ifBlank { resolveWorkingBranchId() },
                canSell = canSell,
                canPurchase = canPurchase,
                canViewReports = canViewReports,
                canManageStock = canManageStock,
                monthlySalary = monthlySalary.coerceAtLeast(0.0),
                active = true,
                createdAtMillis = now,
                updatedAtMillis = now
            )
            repository.saveEmployee(emp)
            _toastMessage.value = if (uiState.value.language == AppLanguage.BN) "কর্মচারী যুক্ত হয়েছে" else "Employee saved"
        }
    }

    fun deleteEmployee(empId: String) {
        viewModelScope.launch {
            repository.deleteEmployee(empId)
            if (uiState.value.operator.id == empId) switchToOwner()
        }
    }

    fun saveVoiceNote(title: String, filePath: String, durationSec: Int, partyId: String, partyLabel: String) {
        viewModelScope.launch {
            repository.saveVoiceNote(
                VoiceNoteEntity(
                    id = "vn_${UUID.randomUUID().toString().replace("-", "").take(14)}",
                    userId = currentUserId,
                    title = title.ifBlank { "Voice Memo" },
                    filePath = filePath,
                    durationSec = durationSec,
                    linkedPartyId = partyId,
                    linkedLabel = partyLabel
                )
            )
        }
    }

    fun deleteVoiceNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteVoiceNote(noteId, currentUserId)
        }
    }

    fun markNotificationsRead() {
        viewModelScope.launch {
            repository.markAllNotificationsRead(currentUserId)
        }
    }

    // --- Voice Accounting Draft (Never auto-saves without confirmation) ---

    fun setVoiceListening() {
        _voiceDraftState.value = VoiceDraftState.Listening
    }

    fun setVoiceError(msg: String) {
        _voiceDraftState.value = VoiceDraftState.Error(msg)
    }

    fun resetVoiceDraft() {
        _voiceDraftState.value = VoiceDraftState.Idle
    }

    fun parseVoiceCommand(spokenText: String) {
        if (spokenText.isBlank()) return
        viewModelScope.launch {
            _voiceDraftState.value = VoiceDraftState.Parsing
            val result = aiRepository.parseVoiceCommandToDraft(spokenText, uiState.value.language)
            result.onSuccess { draft ->
                _voiceDraftState.value = VoiceDraftState.Ready(spokenText, draft)
            }.onFailure {
                // Offline fallback deterministic parser for instant Bengali/English draft preview
                val fallback = localFallbackVoiceParse(spokenText, uiState.value.language)
                _voiceDraftState.value = VoiceDraftState.Ready(spokenText, fallback)
            }
        }
    }

    fun confirmVoiceDraft(draft: VoiceAccountingDraft) {
        val state = uiState.value
        val matchedParty = if (draft.partyName.isNotBlank()) {
            state.parties.firstOrNull {
                it.name.contains(draft.partyName, ignoreCase = true)
            }
        } else null

        when (draft.txType) {
            TransactionType.SALE -> {
                val isDue = draft.note.contains("বাকি", true) || draft.note.contains("due", true)
                recordSale(
                    lineItems = emptyList(),
                    customTotalIfNoItems = draft.amount,
                    discount = 0.0,
                    paidAmount = if (isDue) 0.0 else draft.amount,
                    party = matchedParty,
                    walkInName = draft.partyName,
                    note = draft.note
                )
            }
            TransactionType.DUE_COLLECTION -> {
                if (matchedParty != null) {
                    recordPartyPayment(matchedParty, draft.amount, draft.note)
                } else {
                    recordIncomeOrExpense(false, draft.amount, "Due Collection (${draft.partyName})", draft.note)
                }
            }
            TransactionType.PURCHASE -> {
                recordPurchase(
                    lineItems = emptyList(),
                    customTotalIfNoItems = draft.amount,
                    paidAmount = draft.amount,
                    supplier = matchedParty,
                    supplierName = draft.partyName,
                    note = draft.note
                )
            }
            TransactionType.SUPPLIER_PAYMENT -> {
                if (matchedParty != null) {
                    recordPartyPayment(matchedParty, draft.amount, draft.note)
                } else {
                    recordIncomeOrExpense(true, draft.amount, "Supplier Payment", draft.note)
                }
            }
            TransactionType.INCOME -> {
                recordIncomeOrExpense(false, draft.amount, draft.itemName.ifBlank { "Other Income" }, draft.note)
            }
            TransactionType.EXPENSE -> {
                recordIncomeOrExpense(true, draft.amount, draft.itemName.ifBlank { "General Expense" }, draft.note)
            }
        }
        _voiceDraftState.value = VoiceDraftState.Idle
    }

    // --- AI Business Assistant (Read-Only, Strictly Business-Scoped) ---

    fun askAiAssistant(question: String) {
        if (question.isBlank() || _aiStreaming.value) return
        val snapshot = buildAuthorizedBusinessSnapshot()
        val userMsg = AiChatMessage(isUser = true, text = question.trim())
        val botMsgId = UUID.randomUUID().toString()
        _aiMessages.update { it + userMsg + AiChatMessage(id = botMsgId, isUser = false, text = "") }
        _aiStreaming.value = true

        viewModelScope.launch {
            try {
                aiRepository.askBusinessAssistantStream(
                    question = question.trim(),
                    businessContextSnapshot = snapshot,
                    language = uiState.value.language
                ).collect { accumulated ->
                    _aiMessages.update { list ->
                        list.map { if (it.id == botMsgId) it.copy(text = accumulated) else it }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                _aiMessages.update { list ->
                    list.map {
                        if (it.id == botMsgId) it.copy(
                            text = "AI request timed out. Please try again.",
                            isError = true
                        ) else it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _aiMessages.update { list ->
                    list.map {
                        if (it.id == botMsgId) it.copy(
                            text = e.localizedMessage ?: "AI Assistant unavailable right now.",
                            isError = true
                        ) else it
                    }
                }
            } finally {
                _aiStreaming.value = false
            }
        }
    }

    private fun buildAuthorizedBusinessSnapshot(): String {
        val state = uiState.value
        val m = state.metrics
        val topDueCustomers = state.parties
            .filter { it.partyType == PartyType.CUSTOMER.name && it.dueAmount > 0 }
            .sortedByDescending { it.dueAmount }
            .take(5)
            .joinToString("; ") { "${it.name}: ${Formatters.bdt(it.dueAmount)}" }

        val lowStockItems = state.products
            .filter { it.stockQty <= it.minStockAlert }
            .take(5)
            .joinToString("; ") { "${it.name} (${it.stockQty.toInt()} left)" }

        val topProducts = state.products
            .sortedByDescending { it.sellPrice * it.stockQty }
            .take(5)
            .joinToString("; ") { "${it.name} (Stock:${it.stockQty.toInt()}, Sell:${it.sellPrice})" }

        return """
            Shop Name: ${state.profile?.shopName ?: "My Shop"}
            Branch Scope: ${state.branches.firstOrNull { it.id == state.activeBranchId }?.name ?: "All Branches"}
            Today's Sales: ${Formatters.bdt(m.todaySales)}
            Today's Purchase: ${Formatters.bdt(m.todayPurchase)}
            Today's Expense: ${Formatters.bdt(m.todayExpense)}
            Today's Net Profit: ${Formatters.bdt(m.todayProfit)}
            Monthly Net Profit: ${Formatters.bdt(m.monthlyProfit)}
            Cash Balance: ${Formatters.bdt(m.netCashBalance)}
            Total Customer Due (Receivable): ${Formatters.bdt(m.totalReceivable)}
            Total Supplier Due (Payable): ${Formatters.bdt(m.totalPayable)}
            Low Stock Items: ${lowStockItems.ifBlank { "None" }}
            Top Inventory: ${topProducts.ifBlank { "None" }}
            Top Debtors: ${topDueCustomers.ifBlank { "None" }}
            Total Products: ${state.products.size}, Total Transactions: ${state.transactions.size}
        """.trimIndent()
    }

    private fun triggerVoice(message: String) {
        if (uiState.value.voiceNotifEnabled) {
            _voiceSpeakEvent.value = System.currentTimeMillis() to message
        }
    }

    private fun resolveWorkingBranchId(): String {
        val state = uiState.value
        return state.activeBranchId.ifBlank {
            state.branches.firstOrNull()?.id ?: "br_main_$currentUserId".take(40)
        }
    }

    private fun resolveOperator(operatorId: String, employees: List<EmployeeEntity>): OperatorSession {
        if (operatorId == "OWNER") return OperatorSession()
        val emp = employees.firstOrNull { it.id == operatorId && it.active } ?: return OperatorSession()
        return OperatorSession(
            id = emp.id,
            name = emp.name,
            role = EmployeeRole.fromName(emp.role),
            assignedBranchId = emp.branchId,
            canSell = emp.canSell,
            canPurchase = emp.canPurchase,
            canViewReports = emp.canViewReports,
            canManageStock = emp.canManageStock
        )
    }

    private fun computeMetrics(
        transactions: List<TransactionEntity>,
        products: List<ProductEntity>,
        parties: List<PartyEntity>,
        branches: List<BranchEntity>,
        employees: List<EmployeeEntity>,
        profile: BusinessProfileEntity?,
        branchId: String,
        period: ReportPeriod
    ): DashboardMetrics {
        val activeTx = transactions.filter {
            it.status == "COMPLETED" && (branchId.isBlank() || it.branchId == branchId)
        }
        val periodStart = Formatters.periodStartMillis(period)
        val periodTx = activeTx.filter { it.txDateMillis >= periodStart }

        val sales = periodTx.filter { it.txType == TransactionType.SALE.name }
        val purchases = periodTx.filter { it.txType == TransactionType.PURCHASE.name }
        val expenses = periodTx.filter { it.txType == TransactionType.EXPENSE.name }
        val incomes = periodTx.filter { it.txType == TransactionType.INCOME.name }
        val dueCollections = periodTx.filter { it.txType == TransactionType.DUE_COLLECTION.name }
        val supplierPayments = periodTx.filter { it.txType == TransactionType.SUPPLIER_PAYMENT.name }

        val periodSales = sales.sumOf { it.totalAmount }
        val periodCashSales = sales.sumOf { it.paidAmount }
        val periodDueSales = sales.sumOf { it.dueAmount }
        val periodPurchase = purchases.sumOf { it.totalAmount }
        val periodExpense = expenses.sumOf { it.totalAmount }
        val periodOtherIncome = incomes.sumOf { it.totalAmount }
        val periodDueCollected = dueCollections.sumOf { it.paidAmount }
        val periodSupplierPaid = supplierPayments.sumOf { it.paidAmount }
        val periodCogs = sales.sumOf { it.cogsTotal }
        // True Net Profit = Sales - COGS - Expenses + Other Income (not mixing due collections!)
        val periodNetProfit = (periodSales - periodCogs - periodExpense + periodOtherIncome)

        // All-time Cash Balance for selected branch scope
        val allSalesCash = activeTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.paidAmount }
        val allDueCol = activeTx.filter { it.txType == TransactionType.DUE_COLLECTION.name }.sumOf { it.paidAmount }
        val allOtherInc = activeTx.filter { it.txType == TransactionType.INCOME.name }.sumOf { it.totalAmount }
        val allPurchasePaid = activeTx.filter { it.txType == TransactionType.PURCHASE.name }.sumOf { it.paidAmount }
        val allSupplierPaid = activeTx.filter { it.txType == TransactionType.SUPPLIER_PAYMENT.name }.sumOf { it.paidAmount }
        val allExpense = activeTx.filter { it.txType == TransactionType.EXPENSE.name }.sumOf { it.totalAmount }
        val totalInflow = allSalesCash + allDueCol + allOtherInc
        val totalOutflow = allPurchasePaid + allSupplierPaid + allExpense

        val todayStart = Formatters.periodStartMillis(ReportPeriod.TODAY)
        val monthStart = Formatters.periodStartMillis(ReportPeriod.MONTH)
        val todayTx = activeTx.filter { it.txDateMillis >= todayStart }
        val monthTx = activeTx.filter { it.txDateMillis >= monthStart }

        val todaySalesVal = todayTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.totalAmount }
        val todayCogsVal = todayTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.cogsTotal }
        val todayPurchaseVal = todayTx.filter { it.txType == TransactionType.PURCHASE.name }.sumOf { it.totalAmount }
        val todayExpenseVal = todayTx.filter { it.txType == TransactionType.EXPENSE.name }.sumOf { it.totalAmount }
        val todayIncomeVal = todayTx.filter { it.txType == TransactionType.INCOME.name }.sumOf { it.totalAmount }

        val monthSalesVal = monthTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.totalAmount }
        val monthCogsVal = monthTx.filter { it.txType == TransactionType.SALE.name }.sumOf { it.cogsTotal }
        val monthExpenseVal = monthTx.filter { it.txType == TransactionType.EXPENSE.name }.sumOf { it.totalAmount }
        val monthIncomeVal = monthTx.filter { it.txType == TransactionType.INCOME.name }.sumOf { it.totalAmount }

        val scopedProducts = products.filter { branchId.isBlank() || it.branchId == branchId }
        val scopedParties = parties.filter { branchId.isBlank() || it.branchId == branchId }

        val pendingCount =
            (if (profile != null && profile.syncStatus != SyncStatus.SYNCED.name) 1 else 0) +
                branches.count { it.syncStatus != SyncStatus.SYNCED.name } +
                products.count { it.syncStatus != SyncStatus.SYNCED.name } +
                parties.count { it.syncStatus != SyncStatus.SYNCED.name } +
                transactions.count { it.syncStatus != SyncStatus.SYNCED.name } +
                employees.count { it.syncStatus != SyncStatus.SYNCED.name }

        return DashboardMetrics(
            periodSales = periodSales,
            periodCashSales = periodCashSales,
            periodDueSales = periodDueSales,
            periodPurchase = periodPurchase,
            periodExpense = periodExpense,
            periodOtherIncome = periodOtherIncome,
            periodDueCollected = periodDueCollected,
            periodSupplierPaid = periodSupplierPaid,
            periodCogs = periodCogs,
            periodNetProfit = periodNetProfit,
            cashInflow = periodCashSales + periodDueCollected + periodOtherIncome,
            cashOutflow = purchases.sumOf { it.paidAmount } + periodSupplierPaid + periodExpense,
            netCashBalance = totalInflow - totalOutflow,
            totalReceivable = scopedParties.filter { it.partyType == PartyType.CUSTOMER.name }.sumOf { it.dueAmount },
            totalPayable = scopedParties.filter { it.partyType == PartyType.SUPPLIER.name }.sumOf { it.dueAmount },
            todaySales = todaySalesVal,
            todayPurchase = todayPurchaseVal,
            todayExpense = todayExpenseVal,
            todayProfit = todaySalesVal - todayCogsVal - todayExpenseVal + todayIncomeVal,
            monthlyProfit = monthSalesVal - monthCogsVal - monthExpenseVal + monthIncomeVal,
            lowStockCount = scopedProducts.count { it.stockQty <= it.minStockAlert },
            totalStockValue = scopedProducts.sumOf { it.stockQty * it.costPrice },
            pendingSyncCount = pendingCount
        )
    }

    private fun localFallbackVoiceParse(raw: String, lang: AppLanguage): VoiceAccountingDraft {
        val normalized = raw
            .replace('০', '0').replace('১', '1').replace('২', '2')
            .replace('৩', '3').replace('৪', '4').replace('৫', '5')
            .replace('৬', '6').replace('৭', '7').replace('৮', '8').replace('৯', '9')
        val numberRegex = Regex("""\d+(\.\d+)?""")
        val amount = numberRegex.find(normalized)?.value?.toDoubleOrNull() ?: 0.0
        val lower = normalized.lowercase()
        val txType = when {
            lower.contains("জমা") || lower.contains("আদায়") || lower.contains("collect") -> TransactionType.DUE_COLLECTION
            lower.contains("খরচ") || lower.contains("বিল") || lower.contains("ভাড়া") || lower.contains("expense") -> TransactionType.EXPENSE
            lower.contains("ক্রয়") || lower.contains("কিনে") || lower.contains("purchase") -> TransactionType.PURCHASE
            lower.contains("আয়") || lower.contains("income") -> TransactionType.INCOME
            else -> TransactionType.SALE
        }
        val needsClarification = amount <= 0.0
        val question = if (needsClarification) {
            if (lang == AppLanguage.BN) "কত টাকার লেনদেন তা স্পষ্ট নয়, অনুগ্রহ করে পরিমাণ লিখুন।"
            else "Amount was unclear. Please enter or confirm the amount."
        } else ""
        return VoiceAccountingDraft(
            txType = txType,
            amount = amount,
            partyName = "",
            itemName = "",
            note = raw,
            needsClarification = needsClarification,
            clarificationQuestion = question
        )
    }

    private data class CoreBundle(
        val profile: BusinessProfileEntity?,
        val branches: List<BranchEntity>,
        val products: List<ProductEntity>,
        val parties: List<PartyEntity>,
        val transactions: List<TransactionEntity>
    )

    private data class SecondaryBundle(
        val logs: List<StockLogEntity>,
        val employees: List<EmployeeEntity>,
        val voiceNotes: List<VoiceNoteEntity>,
        val notifications: List<NotificationEntity>
    )

    private data class PrefsBundle(
        val lang: AppLanguage,
        val theme: AppThemeMode,
        val voiceEnabled: Boolean,
        val branchId: String,
        val operatorId: String
    )
}
