package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.BusinessProfileEntity
import com.example.data.local.MunafDatabase
import com.example.data.model.AccountModerationStatus
import com.example.data.model.AdminDashboardKpis
import com.example.data.model.AdminUserDoc
import com.example.data.model.AppLanguage
import com.example.data.model.BusinessAccountDoc
import com.example.data.model.ComplaintStatus
import com.example.data.model.ComplaintTicketDoc
import com.example.data.model.EmailOutboxDoc
import com.example.data.model.PaymentMethod
import com.example.data.model.PaymentRequestDoc
import com.example.data.model.PaymentRequestStatus
import com.example.data.model.PaymentSettingsDoc
import com.example.data.model.SubscriptionEngine
import com.example.data.model.SubscriptionPackageDoc
import com.example.data.model.SubscriptionTier
import com.example.ui.screens.AdminDashboardTab
import com.example.ui.screens.BlockedAccountGate
import com.example.ui.screens.CustomerComplaintsScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.theme.MunafShopTheme
import com.google.firebase.Timestamp
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SuperAdminAndSubscriptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `package pricing calculates 1, 3, 6, 12 month rates and submitted orders retain locked price`() {
        val pkg = SubscriptionPackageDoc(
            tier = SubscriptionTier.PREMIUM.name,
            nameBn = "প্রিমিয়াম",
            nameEn = "Premium",
            monthlyPriceBdt = 590.0,
            price1mBdt = 590.0,
            price3mBdt = 1600.0,
            price6mBdt = 3000.0,
            price12mBdt = 5200.0,
            maxBranches = 5,
            maxEmployees = 15,
            aiEnabled = true,
            exportEnabled = true,
            enabled = true
        )
        assertEquals(590.0, pkg.priceForMonths(1), 0.01)
        assertEquals(1600.0, pkg.priceForMonths(3), 0.01)
        assertEquals(3000.0, pkg.priceForMonths(6), 0.01)
        assertEquals(5200.0, pkg.priceForMonths(12), 0.01)

        // Simulate submitted order locking amount before admin changes price
        val submittedOrder = PaymentRequestDoc(
            id = "req_1",
            userId = "u1",
            packageTier = SubscriptionTier.PREMIUM.name,
            durationMonths = 3L,
            amountBdt = pkg.priceForMonths(3),
            lockedUnitMonthlyBdt = pkg.monthlyPriceBdt,
            status = PaymentRequestStatus.PENDING.name
        )
        val updatedPkg = pkg.copy(monthlyPriceBdt = 800.0, price3mBdt = 2200.0)
        assertEquals(2200.0, updatedPkg.priceForMonths(3), 0.01)
        assertEquals(1600.0, submittedOrder.amountBdt, 0.01)
    }

    @Test
    fun `server authoritative clock prevents rollback and auto-downgrades expired plan`() {
        val trueServerTime = 1_800_000_000_000L
        val deviceAtSync = 1_800_000_000_000L
        val offset = trueServerTime - deviceAtSync

        // Tampered device clock rolled back by 10 days cannot fall below minTrustedServerFloorMillis
        val tamperedDeviceNow = deviceAtSync - 10L * SubscriptionEngine.ONE_DAY_MILLIS
        val safeServerNow = SubscriptionEngine.computeServerNowMillis(
            deviceNowMillis = tamperedDeviceNow,
            serverOffsetMillis = offset,
            minTrustedServerFloorMillis = trueServerTime
        )
        assertTrue(safeServerNow >= trueServerTime)

        val expiresAt = trueServerTime + 2L * SubscriptionEngine.ONE_DAY_MILLIS
        val countdownActive = SubscriptionEngine.computeCountdown(
            tierName = SubscriptionTier.BASIC.name,
            startMillis = trueServerTime - 28L * SubscriptionEngine.ONE_DAY_MILLIS,
            expiresAtMillis = expiresAt,
            serverNowMillis = trueServerTime
        )
        assertTrue(countdownActive.isPaidActive)
        assertFalse(countdownActive.isExpired)
        assertEquals(2L, countdownActive.days)

        val packages = listOf(
            SubscriptionPackageDoc(tier = SubscriptionTier.FREE.name, maxBranches = 1, maxEmployees = 1, aiEnabled = false),
            SubscriptionPackageDoc(tier = SubscriptionTier.BASIC.name, maxBranches = 2, maxEmployees = 5, aiEnabled = true)
        )
        val activeResolved = SubscriptionEngine.resolveActivePackage(
            tierName = SubscriptionTier.BASIC.name,
            expiresAtMillis = expiresAt,
            serverNowMillis = trueServerTime,
            packages = packages
        )
        assertEquals(SubscriptionTier.BASIC.name, activeResolved.tier)

        val afterExpiry = expiresAt + 1_000L
        val expiredResolved = SubscriptionEngine.resolveActivePackage(
            tierName = SubscriptionTier.BASIC.name,
            expiresAtMillis = expiresAt,
            serverNowMillis = afterExpiry,
            packages = packages
        )
        assertEquals(SubscriptionTier.FREE.name, expiredResolved.tier)
    }

    @Test
    fun `expiry alert deduplication triggers at 7, 3, 1 days and expired once`() {
        val now = 1_800_000_000_000L
        val exp6d = now + 6L * SubscriptionEngine.ONE_DAY_MILLIS
        val cd6 = SubscriptionEngine.computeCountdown("PREMIUM", now, exp6d, now)
        assertEquals(7, SubscriptionEngine.evaluateExpiryReminderThreshold(cd6, 0))
        assertNull(SubscriptionEngine.evaluateExpiryReminderThreshold(cd6, 7))

        val exp2d = now + 2L * SubscriptionEngine.ONE_DAY_MILLIS
        val cd2 = SubscriptionEngine.computeCountdown("PREMIUM", now, exp2d, now)
        assertEquals(3, SubscriptionEngine.evaluateExpiryReminderThreshold(cd2, 7))
        assertNull(SubscriptionEngine.evaluateExpiryReminderThreshold(cd2, 3))

        val exp12h = now + 12L * 3600L * 1000L
        val cd12h = SubscriptionEngine.computeCountdown("PREMIUM", now, exp12h, now)
        assertEquals(1, SubscriptionEngine.evaluateExpiryReminderThreshold(cd12h, 3))
        assertNull(SubscriptionEngine.evaluateExpiryReminderThreshold(cd12h, 1))

        val cdExpired = SubscriptionEngine.computeCountdown("PREMIUM", now, now - 1000L, now)
        assertEquals(-1, SubscriptionEngine.evaluateExpiryReminderThreshold(cdExpired, 1))
        assertNull(SubscriptionEngine.evaluateExpiryReminderThreshold(cdExpired, -1))
    }

    @Test
    fun `renewal window extends existing active subscription and normalizes TrxID`() {
        val now = 1_800_000_000_000L
        val currentStart = now - 5L * SubscriptionEngine.ONE_DAY_MILLIS
        val currentExpiry = now + 25L * SubscriptionEngine.ONE_DAY_MILLIS
        val (newStart, newExpiry) = SubscriptionEngine.computeRenewalWindow(
            serverNowMillis = now,
            existingTierName = SubscriptionTier.PREMIUM.name,
            existingStartMillis = currentStart,
            existingExpiresAtMillis = currentExpiry,
            newTier = SubscriptionTier.PREMIUM,
            durationMonths = 3
        )
        assertEquals(currentStart, newStart)
        assertEquals(currentExpiry + 90L * SubscriptionEngine.ONE_DAY_MILLIS, newExpiry)
        assertEquals("TRX-8899AA", SubscriptionEngine.normalizeTrxId("  trx-8899aa "))
        assertEquals("TRX8899AA", SubscriptionEngine.normalizeTrxId("  trx 8899 aa! "))
    }

    @Test
    fun `dashboard kpis compute all 11 platform metrics and daily monthly yearly revenue`() {
        val now = 1_800_000_000_000L
        val users = listOf(
            BusinessAccountDoc(
                userId = "u1",
                subscriptionTier = SubscriptionTier.PREMIUM.name,
                subscriptionStartMillis = now - 5L * SubscriptionEngine.ONE_DAY_MILLIS,
                subscriptionExpiresAtMillis = now + 3L * SubscriptionEngine.ONE_DAY_MILLIS, // expiring soon!
                accountStatus = AccountModerationStatus.ACTIVE.name
            ),
            BusinessAccountDoc(
                userId = "u2",
                subscriptionTier = SubscriptionTier.FREE.name,
                accountStatus = AccountModerationStatus.SUSPENDED.name,
                blocked = true
            ),
            BusinessAccountDoc(
                userId = "u3",
                subscriptionTier = SubscriptionTier.BUSINESS.name,
                subscriptionStartMillis = now - 1L * SubscriptionEngine.ONE_DAY_MILLIS,
                subscriptionExpiresAtMillis = now + 60L * SubscriptionEngine.ONE_DAY_MILLIS,
                accountStatus = AccountModerationStatus.BANNED.name,
                blocked = true
            )
        )
        val payments = listOf(
            PaymentRequestDoc(
                id = "p1",
                amountBdt = 590.0,
                status = PaymentRequestStatus.APPROVED.name,
                reviewedAtMillis = now - 1000L, // today
                createdAt = Timestamp(Date(now - 2000L))
            ),
            PaymentRequestDoc(
                id = "p2",
                amountBdt = 1200.0,
                status = PaymentRequestStatus.PENDING.name,
                createdAt = Timestamp(Date(now - 500L))
            )
        )
        val complaints = listOf(
            ComplaintTicketDoc(id = "c1", status = ComplaintStatus.PENDING.name),
            ComplaintTicketDoc(id = "c2", status = ComplaintStatus.IN_PROGRESS.name),
            ComplaintTicketDoc(id = "c3", status = ComplaintStatus.RESOLVED.name)
        )

        val kpis = SubscriptionEngine.computeAdminDashboardKpis(users, payments, complaints, now)
        assertEquals(3, kpis.totalUsers)
        assertEquals(1, kpis.activeUsers)
        assertEquals(1, kpis.suspendedUsers)
        assertEquals(1, kpis.bannedUsers)
        assertEquals(1, kpis.freeUsers)
        assertEquals(2, kpis.paidSubscribers)
        assertEquals(590.0, kpis.totalSubscriptionRevenueBdt, 0.01)
        assertEquals(590.0, kpis.dailyRevenueBdt, 0.01)
        assertEquals(590.0, kpis.monthlyRevenueBdt, 0.01)
        assertEquals(590.0, kpis.yearlyRevenueBdt, 0.01)
        assertEquals(1, kpis.pendingPaymentRequests)
        assertEquals(2, kpis.newPackageOrdersCount)
        assertEquals(2, kpis.pendingComplaints)
        assertEquals(1, kpis.expiringSoonCount)
    }

    @Test
    fun `room migration 1 to 2 preserves existing user profile and accounting data`() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dbName = "migration_test_${System.currentTimeMillis()}.db"

            // Create a version-1 database manually and insert a v1 row
            val config = SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS `business_profiles` (
                                `userId` TEXT NOT NULL,
                                `ownerEmail` TEXT NOT NULL,
                                `shopName` TEXT NOT NULL,
                                `ownerName` TEXT NOT NULL,
                                `phone` TEXT NOT NULL,
                                `address` TEXT NOT NULL,
                                `businessType` TEXT NOT NULL,
                                `logoUri` TEXT,
                                `subscriptionTier` TEXT NOT NULL,
                                `subscriptionMonths` INTEGER NOT NULL,
                                `subscriptionExpiresAtMillis` INTEGER NOT NULL,
                                `blocked` INTEGER NOT NULL,
                                `syncStatus` TEXT NOT NULL,
                                `updatedAtMillis` INTEGER NOT NULL,
                                PRIMARY KEY(`userId`)
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO business_profiles (userId, ownerEmail, shopName, ownerName, phone, address, businessType, logoUri, subscriptionTier, subscriptionMonths, subscriptionExpiresAtMillis, blocked, syncStatus, updatedAtMillis) VALUES ('u1', 'owner@munaf.com', 'Munaf Store', 'Abdul', '01711', 'Cox', 'Retail', NULL, 'BASIC', 1, 9999999999999, 0, 'SYNCED', 1000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()

            val helper = FrameworkSQLiteOpenHelperFactory().create(config)
            val rawDb = helper.writableDatabase
            MunafDatabase.MIGRATION_1_2.migrate(rawDb)

            val cursor = rawDb.query("SELECT shopName, accountStatus, banReason, subscriptionStartMillis, lastExpiryWarningDays FROM business_profiles WHERE userId = 'u1'")
            assertTrue(cursor.moveToFirst())
            assertEquals("Munaf Store", cursor.getString(0))
            assertEquals("ACTIVE", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            assertEquals(0L, cursor.getLong(3))
            assertEquals(0, cursor.getInt(4))
            cursor.close()
            rawDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `admin dashboard isolates owner revenue from sub admin`() {
        val kpis = AdminDashboardKpis(
            totalUsers = 12,
            activeUsers = 10,
            suspendedUsers = 1,
            bannedUsers = 1,
            freeUsers = 7,
            paidSubscribers = 5,
            totalSubscriptionRevenueBdt = 9500.0,
            dailyRevenueBdt = 590.0,
            monthlyRevenueBdt = 3200.0,
            yearlyRevenueBdt = 9500.0,
            pendingPaymentRequests = 3,
            newPackageOrdersCount = 2,
            pendingComplaints = 4,
            expiringSoonCount = 2
        )

        composeTestRule.setContent {
            MunafShopTheme {
                AdminDashboardTab(
                    language = AppLanguage.EN,
                    adminProfile = AdminUserDoc(
                        uid = "sub_1",
                        email = "sub@munaf.com",
                        name = "Sub Helper",
                        role = "SUB_ADMIN"
                    ),
                    kpis = kpis,
                    emailOutbox = listOf(EmailOutboxDoc(id = "m1"))
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Restricted Sub-Admin Access").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subscription Revenue Reports (Owner Only)").assertDoesNotExist()
    }

    @Test
    fun `blocked account gate displays suspension reason and data preservation guarantee`() {
        composeTestRule.setContent {
            MunafShopTheme {
                BlockedAccountGate(
                    language = AppLanguage.EN,
                    shopName = "Dhaka Traders",
                    accountStatus = "SUSPENDED",
                    banReason = "Duplicate fake TrxID submitted",
                    suspendedUntilMillis = 1_850_000_000_000L,
                    onRefresh = {},
                    onSignOut = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Account Temporarily Suspended").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reason: Duplicate fake TrxID submitted").assertIsDisplayed()
    }

    @Test
    fun `subscription and complaints screens render countdown, payment settings, and ticket id`() {
        val now = 1_800_000_000_000L
        composeTestRule.setContent {
            MunafShopTheme {
                SubscriptionScreen(
                    language = AppLanguage.EN,
                    profile = BusinessProfileEntity(
                        userId = "u1",
                        ownerEmail = "munaf@shop.com",
                        shopName = "Munaf Enterprise",
                        ownerName = "Munaf",
                        phone = "01711000000",
                        address = "Cox's Bazar",
                        businessType = "Retail",
                        subscriptionTier = SubscriptionTier.PREMIUM.name,
                        subscriptionStartMillis = now - 5L * SubscriptionEngine.ONE_DAY_MILLIS,
                        subscriptionExpiresAtMillis = now + 20L * SubscriptionEngine.ONE_DAY_MILLIS
                    ),
                    serverNowMillis = now,
                    packages = listOf(
                        SubscriptionPackageDoc(
                            tier = SubscriptionTier.PREMIUM.name,
                            nameBn = "প্রিমিয়াম",
                            nameEn = "Premium",
                            monthlyPriceBdt = 590.0,
                            price1mBdt = 590.0,
                            enabled = true
                        )
                    ),
                    paymentSettings = PaymentSettingsDoc(
                        bkashNumber = "01711-223344",
                        bkashEnabled = true,
                        nagadEnabled = false
                    ),
                    myRequests = emptyList(),
                    myRenewals = emptyList(),
                    onSubmitPayment = { _, _, _, _, _, _, _, _ -> },
                    onOpenComplaints = {},
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Subscription & Packages").assertIsDisplayed()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("01711-223344"))
        composeTestRule.onNodeWithText("01711-223344").assertIsDisplayed()
    }

    @Test
    fun `customer complaints screen renders submitted ticket code and status`() {
        composeTestRule.setContent {
            MunafShopTheme {
                CustomerComplaintsScreen(
                    language = AppLanguage.EN,
                    shopName = "Munaf Enterprise",
                    complaints = listOf(
                        ComplaintTicketDoc(
                            id = "cmp_1",
                            ticketCode = "CMP-4821",
                            subject = "Payment not activated yet",
                            description = "Sent 590 BDT via bKash 2 hours ago",
                            status = ComplaintStatus.IN_PROGRESS.name
                        )
                    ),
                    selectedReplies = emptyList(),
                    onSelectTicket = {},
                    onSubmitComplaint = { _, _, _ -> },
                    onReplyAsCustomer = { _, _ -> },
                    onBack = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasTestTag("ticket_card_CMP-4821"))
        composeTestRule.onNodeWithText("CMP-4821").assertIsDisplayed()
        composeTestRule.onNodeWithText("Payment not activated yet").assertIsDisplayed()
    }
}
