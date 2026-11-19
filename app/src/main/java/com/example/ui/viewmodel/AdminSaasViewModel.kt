package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminDashboardKpis
import com.example.data.model.AdminUserDoc
import com.example.data.model.AuditLogDoc
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.ComplaintReplyDoc
import com.example.data.model.ComplaintStatus
import com.example.data.model.ComplaintTicketDoc
import com.example.data.model.EmailOutboxDoc
import com.example.data.model.ModerationHistoryDoc
import com.example.data.model.PackagePriceHistoryDoc
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentSettingsDoc
import com.example.data.model.SubscriptionEngine
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionRenewalDoc
import com.example.data.model.SubscriptionTier
import com.example.data.repository.SaasAdminRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "AdminSaasVM"

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSaasViewModel(
    private val currentUserId: String,
    private val repo: SaasAdminRepository
) : ViewModel() {

    private val _myAdminProfile = MutableStateFlow<AdminUserDoc?>(null)
    val myAdminProfile: StateFlow<AdminUserDoc?> = _myAdminProfile.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _serverClockOffsetMillis = MutableStateFlow<Long?>(null)
    val serverClockOffsetMillis: StateFlow<Long?> = _serverClockOffsetMillis.asStateFlow()

    private val _serverNowMillis = MutableStateFlow(System.currentTimeMillis())
    val serverNowMillis: StateFlow<Long> = _serverNowMillis.asStateFlow()

    val paymentSettings: StateFlow<PaymentSettingsDoc> = repo.observePaymentSettings()
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing payment settings", fs ?: err)
            emit(PaymentSettingsDoc())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), PaymentSettingsDoc())

    val packages: StateFlow<List<SubscriptionPackageDoc>> = repo.observeSubscriptionPackages()
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing packages", fs ?: err)
            emit(repo.defaultPackages())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), repo.defaultPackages())

    val myPaymentRequests: StateFlow<List<PaymentRequestDoc>> = repo.observeMyPaymentRequests(currentUserId)
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            Log.w(TAG, "Error observing my payment requests", fs ?: err)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myRenewals: StateFlow<List<SubscriptionRenewalDoc>> = repo.observeMyRenewals(currentUserId)
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val myComplaints: StateFlow<List<ComplaintTicketDoc>> = repo.observeMyComplaints(currentUserId)
        .catch { err ->
            val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
            if (fs == null && err is CancellationException) throw err
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _selectedTicketId = MutableStateFlow<String?>(null)
    val selectedTicketReplies: StateFlow<List<ComplaintReplyDoc>> = _selectedTicketId
        .flatMapLatest { ticketId ->
            if (ticketId.isNullOrBlank()) flowOf(emptyList())
            else repo.observeComplaintReplies(ticketId).catch { emit(emptyList()) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _allUsers = MutableStateFlow<List<BusinessAccountDoc>>(emptyList())
    val allUsers: StateFlow<List<BusinessAccountDoc>> = _allUsers.asStateFlow()

    private val _allPaymentRequests = MutableStateFlow<List<PaymentRequestDoc>>(emptyList())
    val allPaymentRequests: StateFlow<List<PaymentRequestDoc>> = _allPaymentRequests.asStateFlow()

    private val _allComplaints = MutableStateFlow<List<ComplaintTicketDoc>>(emptyList())
    val allComplaints: StateFlow<List<ComplaintTicketDoc>> = _allComplaints.asStateFlow()

    private val _allRenewals = MutableStateFlow<List<SubscriptionRenewalDoc>>(emptyList())
    val allRenewals: StateFlow<List<SubscriptionRenewalDoc>> = _allRenewals.asStateFlow()

    private val _moderationHistory = MutableStateFlow<List<ModerationHistoryDoc>>(emptyList())
    val moderationHistory: StateFlow<List<ModerationHistoryDoc>> = _moderationHistory.asStateFlow()

    private val _packagePriceHistory = MutableStateFlow<List<PackagePriceHistoryDoc>>(emptyList())
    val packagePriceHistory: StateFlow<List<PackagePriceHistoryDoc>> = _packagePriceHistory.asStateFlow()

    private val _emailOutbox = MutableStateFlow<List<EmailOutboxDoc>>(emptyList())
    val emailOutbox: StateFlow<List<EmailOutboxDoc>> = _emailOutbox.asStateFlow()

    private val _subAdmins = MutableStateFlow<List<AdminUserDoc>>(emptyList())
    val subAdmins: StateFlow<List<AdminUserDoc>> = _subAdmins.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogDoc>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogDoc>> = _auditLogs.asStateFlow()

    val dashboardKpis: StateFlow<AdminDashboardKpis> = combine(
        _allUsers,
        _allPaymentRequests,
        _allComplaints,
        _serverNowMillis
    ) { users, payments, complaints, nowMs ->
        SubscriptionEngine.computeAdminDashboardKpis(users, payments, complaints, nowMs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminDashboardKpis())

    init {
        viewModelScope.launch {
            refreshServerClock()
            val admin = repo.getMyAdminProfile()
            _myAdminProfile.value = admin
            if (admin != null) {
                if (admin.role == "SUPER_ADMIN") {
                    repo.seedDefaultPackagesIfAdmin()
                }
                startAdminStreams(admin)
            }
        }
        // Tick server-offset-adjusted clock every second for live countdown & auto-expiry UI
        viewModelScope.launch {
            while (isActive) {
                val offset = _serverClockOffsetMillis.value
                _serverNowMillis.value = SubscriptionEngine.computeServerNowMillis(
                    deviceNowMillis = System.currentTimeMillis(),
                    serverOffsetMillis = offset
                )
                delay(1000L)
            }
        }
    }

    fun refreshServerClock() {
        viewModelScope.launch {
            repo.syncServerClock(currentUserId).onSuccess { result ->
                _serverClockOffsetMillis.value = result.offsetMillis
                _serverNowMillis.value = result.serverNowMillis
            }
        }
    }

    private fun startAdminStreams(admin: AdminUserDoc) {
        val isSuper = admin.role == "SUPER_ADMIN"
        if (isSuper || admin.canManageUsers || admin.canViewLimitedReports) {
            viewModelScope.launch {
                repo.observeAllBusinessAccounts()
                    .catch { Log.w(TAG, "Admin accounts error", it) }
                    .collect { _allUsers.value = it }
            }
        }
        if (isSuper || admin.canManageUsers) {
            viewModelScope.launch {
                repo.observeModerationHistory()
                    .catch { Log.w(TAG, "Moderation history error", it) }
                    .collect { _moderationHistory.value = it }
            }
        }
        if (isSuper || admin.canVerifyPayments) {
            viewModelScope.launch {
                repo.observeAllPaymentRequests()
                    .catch { Log.w(TAG, "Admin payments error", it) }
                    .collect { _allPaymentRequests.value = it }
            }
            viewModelScope.launch {
                repo.observeAllRenewals()
                    .catch { Log.w(TAG, "Admin renewals error", it) }
                    .collect { _allRenewals.value = it }
            }
        }
        if (isSuper || admin.canReplyComplaints) {
            viewModelScope.launch {
                repo.observeAllComplaints()
                    .catch { Log.w(TAG, "Admin complaints error", it) }
                    .collect { _allComplaints.value = it }
            }
        }
        // Strictly Owner/Super Admin Only Streams (NEVER exposed to Sub-Admins)
        if (isSuper) {
            viewModelScope.launch {
                repo.observePackagePriceHistory()
                    .catch { Log.w(TAG, "Package history error", it) }
                    .collect { _packagePriceHistory.value = it }
            }
            viewModelScope.launch {
                repo.observeEmailOutbox()
                    .catch { Log.w(TAG, "Email outbox error", it) }
                    .collect { _emailOutbox.value = it }
            }
            viewModelScope.launch {
                repo.observeSubAdmins()
                    .catch { Log.w(TAG, "SubAdmins error", it) }
                    .collect { _subAdmins.value = it }
            }
            viewModelScope.launch {
                repo.observeAuditLogs()
                    .catch { Log.w(TAG, "AuditLogs error", it) }
                    .collect { _auditLogs.value = it }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun selectComplaintTicket(ticketId: String?) {
        _selectedTicketId.value = ticketId
    }

    fun submitManualPayment(
        shopName: String,
        method: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        amountBdt: Double,
        lockedUnitMonthlyBdt: Double,
        packageTier: SubscriptionTier,
        durationMonths: Int,
        screenshotUri: String?
    ) {
        viewModelScope.launch {
            val res = repo.submitManualPaymentRequest(
                shopName = shopName,
                method = method,
                senderNumber = senderNumber,
                transactionId = transactionId,
                amountBdt = amountBdt,
                lockedUnitMonthlyBdt = lockedUnitMonthlyBdt,
                packageTier = packageTier,
                durationMonths = durationMonths,
                screenshotUri = screenshotUri
            )
            _statusMessage.value = res.fold(
                onSuccess = { "পেমেন্ট অর্ডার জমা হয়েছে (ইমেইল নোটিফিকেশন ব্যাকএন্ড সেটআপ সাপেক্ষ; Admin যাচাইয়ের পর সক্রিয় হবে)" },
                onFailure = { it.localizedMessage ?: "Failed to submit payment" }
            )
        }
    }

    fun reviewPayment(request: PaymentRequestDoc, approve: Boolean, note: String) {
        viewModelScope.launch {
            val res = repo.reviewPaymentRequest(request, approve, note)
            _statusMessage.value = res.fold(
                onSuccess = {
                    if (approve) "পেমেন্ট অনুমোদিত ও সাবস্ক্রিপশন সক্রিয় হয়েছে"
                    else "পেমেন্ট রিকোয়েস্ট বাতিল হয়েছে"
                },
                onFailure = { it.localizedMessage ?: "Review failed" }
            )
        }
    }

    fun moderateUser(account: BusinessAccountDoc, action: String, reason: String, suspendDays: Int = 7) {
        viewModelScope.launch {
            val res = repo.moderateUserAccount(account, action, reason, suspendDays)
            _statusMessage.value = res.fold(
                onSuccess = {
                    when (action) {
                        "SUSPEND" -> "অ্যাকাউন্ট সাময়িক স্থগিত ($suspendDays দিন) করা হয়েছে"
                        "BAN" -> "অ্যাকাউন্ট স্থায়ী ব্যান করা হয়েছে"
                        else -> "অ্যাকাউন্ট পুনরায় সক্রিয় করা হয়েছে"
                    }
                },
                onFailure = { it.localizedMessage ?: "Moderation action failed" }
            )
        }
    }

    fun updatePackage(pkg: SubscriptionPackageDoc, previousMonthlyBdt: Double) {
        viewModelScope.launch {
            val res = repo.updatePackageConfig(pkg, previousMonthlyBdt)
            _statusMessage.value = res.fold(
                onSuccess = { "${pkg.tier} প্যাকেজ আপডেট ও ইতিহাস সংরক্ষিত হয়েছে" },
                onFailure = { it.localizedMessage ?: "Update failed" }
            )
        }
    }

    fun savePaymentSettings(settings: PaymentSettingsDoc) {
        viewModelScope.launch {
            val res = repo.updatePaymentSettings(settings)
            _statusMessage.value = res.fold(
                onSuccess = { "bKash ও Nagad সেটিংস আপডেট হয়েছে" },
                onFailure = { it.localizedMessage ?: "Payment settings save failed" }
            )
        }
    }

    fun submitComplaint(shopName: String, subject: String, description: String, attachmentUri: String?) {
        viewModelScope.launch {
            val res = repo.submitComplaint(shopName, subject, description, attachmentUri)
            _statusMessage.value = res.fold(
                onSuccess = { code -> "অভিযোগ জমা হয়েছে • Ticket ID: $code" },
                onFailure = { it.localizedMessage ?: "Complaint submission failed" }
            )
        }
    }

    fun replyComplaintAsCustomer(ticket: ComplaintTicketDoc, customerName: String, message: String) {
        viewModelScope.launch {
            val res = repo.replyToComplaint(
                ticket = ticket,
                message = message,
                newStatus = null,
                senderName = customerName,
                senderRole = "CUSTOMER"
            )
            _statusMessage.value = res.fold(
                onSuccess = { "বার্তা পাঠানো হয়েছে" },
                onFailure = { it.localizedMessage ?: "Reply failed" }
            )
        }
    }

    fun replyComplaintAsAdmin(
        ticket: ComplaintTicketDoc,
        message: String,
        newStatus: ComplaintStatus
    ) {
        val admin = _myAdminProfile.value ?: return
        viewModelScope.launch {
            val res = repo.replyToComplaint(
                ticket = ticket,
                message = message,
                newStatus = newStatus,
                senderName = admin.name.ifBlank { "Admin Support" },
                senderRole = admin.role
            )
            _statusMessage.value = res.fold(
                onSuccess = { "উত্তর ও স্ট্যাটাস (${newStatus.labelBn}) আপডেট হয়েছে" },
                onFailure = { it.localizedMessage ?: "Reply failed" }
            )
        }
    }

    fun saveSubAdmin(
        uid: String,
        email: String,
        name: String,
        canVerifyPayments: Boolean,
        canManageUsers: Boolean,
        canReplyComplaints: Boolean,
        canViewLimitedReports: Boolean,
        active: Boolean = true
    ) {
        viewModelScope.launch {
            val res = repo.saveSubAdmin(
                AdminUserDoc(
                    uid = uid.trim(),
                    email = email.trim(),
                    name = name.trim(),
                    role = "SUB_ADMIN",
                    canVerifyPayments = canVerifyPayments,
                    canManageUsers = canManageUsers,
                    canReplyComplaints = canReplyComplaints,
                    canViewLimitedReports = canViewLimitedReports,
                    active = active
                )
            )
            _statusMessage.value = res.fold(
                onSuccess = { "Sub-Admin সংরক্ষিত হয়েছে" },
                onFailure = { it.localizedMessage ?: "Save failed" }
            )
        }
    }
}
