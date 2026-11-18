package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminUserDoc
import com.example.data.model.AuditLogDoc
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionTier
import com.example.data.repository.SaasAdminRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "AdminSaasVM"

class AdminSaasViewModel(
    private val currentUserId: String,
    private val repo: SaasAdminRepository
) : ViewModel() {

    private val _myAdminProfile = MutableStateFlow<AdminUserDoc?>(null)
    val myAdminProfile: StateFlow<AdminUserDoc?> = _myAdminProfile.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

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

    private val _allUsers = MutableStateFlow<List<BusinessAccountDoc>>(emptyList())
    val allUsers: StateFlow<List<BusinessAccountDoc>> = _allUsers.asStateFlow()

    private val _allPaymentRequests = MutableStateFlow<List<PaymentRequestDoc>>(emptyList())
    val allPaymentRequests: StateFlow<List<PaymentRequestDoc>> = _allPaymentRequests.asStateFlow()

    private val _subAdmins = MutableStateFlow<List<AdminUserDoc>>(emptyList())
    val subAdmins: StateFlow<List<AdminUserDoc>> = _subAdmins.asStateFlow()

    private val _auditLogs = MutableStateFlow<List<AuditLogDoc>>(emptyList())
    val auditLogs: StateFlow<List<AuditLogDoc>> = _auditLogs.asStateFlow()

    init {
        viewModelScope.launch {
            val admin = repo.getMyAdminProfile()
            _myAdminProfile.value = admin
            if (admin != null) {
                if (admin.role == "SUPER_ADMIN") {
                    repo.seedDefaultPackagesIfAdmin()
                }
                startAdminStreams(admin)
            }
        }
    }

    private fun startAdminStreams(admin: AdminUserDoc) {
        val isSuper = admin.role == "SUPER_ADMIN"
        if (isSuper || admin.canManageUsers || admin.canViewLimitedReports) {
            viewModelScope.launch {
                repo.observeAllBusinessAccounts()
                    .catch { err ->
                        val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                        if (fs == null && err is CancellationException) throw err
                        Log.w(TAG, "Admin accounts error", fs ?: err)
                    }
                    .collect { _allUsers.value = it }
            }
        }
        if (isSuper || admin.canVerifyPayments) {
            viewModelScope.launch {
                repo.observeAllPaymentRequests()
                    .catch { err ->
                        val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                        if (fs == null && err is CancellationException) throw err
                        Log.w(TAG, "Admin payments error", fs ?: err)
                    }
                    .collect { _allPaymentRequests.value = it }
            }
        }
        if (isSuper) {
            viewModelScope.launch {
                repo.observeSubAdmins()
                    .catch { err ->
                        val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                        if (fs == null && err is CancellationException) throw err
                        Log.w(TAG, "SubAdmins error", fs ?: err)
                    }
                    .collect { _subAdmins.value = it }
            }
            viewModelScope.launch {
                repo.observeAuditLogs()
                    .catch { err ->
                        val fs = generateSequence(err) { it.cause }.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
                        if (fs == null && err is CancellationException) throw err
                        Log.w(TAG, "AuditLogs error", fs ?: err)
                    }
                    .collect { _auditLogs.value = it }
            }
        }
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun submitManualPayment(
        shopName: String,
        method: PaymentMethod,
        senderNumber: String,
        transactionId: String,
        amountBdt: Double,
        packageTier: SubscriptionTier,
        durationMonths: Int,
        screenshotUri: String?
    ) {
        viewModelScope.launch {
            val res = repo.submitManualPaymentRequest(
                shopName, method, senderNumber, transactionId,
                amountBdt, packageTier, durationMonths, screenshotUri
            )
            _statusMessage.value = res.fold(
                onSuccess = { "পেমেন্ট রিকোয়েস্ট জমা হয়েছে (Admin যাচাইয়ের পর সক্রিয় হবে)" },
                onFailure = { it.localizedMessage ?: "Failed to submit payment" }
            )
        }
    }

    fun reviewPayment(request: PaymentRequestDoc, approve: Boolean, note: String) {
        viewModelScope.launch {
            val res = repo.reviewPaymentRequest(request, approve, note)
            _statusMessage.value = res.fold(
                onSuccess = { if (approve) "Payment Approved & Activated" else "Payment Rejected" },
                onFailure = { it.localizedMessage ?: "Review failed" }
            )
        }
    }

    fun toggleUserBlock(account: BusinessAccountDoc, blocked: Boolean) {
        viewModelScope.launch {
            val res = repo.toggleUserBlock(account, blocked)
            _statusMessage.value = res.fold(
                onSuccess = { if (blocked) "User Blocked" else "User Unblocked" },
                onFailure = { it.localizedMessage ?: "Action failed" }
            )
        }
    }

    fun updatePackage(pkg: SubscriptionPackageDoc) {
        viewModelScope.launch {
            val res = repo.updatePackageConfig(pkg)
            _statusMessage.value = res.fold(
                onSuccess = { "Package ${pkg.tier} updated" },
                onFailure = { it.localizedMessage ?: "Update failed" }
            )
        }
    }

    fun saveSubAdmin(
        uid: String,
        email: String,
        name: String,
        canVerifyPayments: Boolean,
        canManageUsers: Boolean,
        canViewLimitedReports: Boolean
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
                    canViewLimitedReports = canViewLimitedReports,
                    active = true
                )
            )
            _statusMessage.value = res.fold(
                onSuccess = { "Sub-Admin saved" },
                onFailure = { it.localizedMessage ?: "Save failed" }
            )
        }
    }
}
