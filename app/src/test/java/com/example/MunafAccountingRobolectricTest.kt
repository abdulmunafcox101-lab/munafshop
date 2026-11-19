package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MunafDao
import com.example.data.local.MunafDatabase
import com.example.data.local.PartyEntity
import com.example.data.local.ProductEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.TxLineItem
import com.example.data.model.PartyType
import com.example.data.model.TransactionType
import com.example.firebase.FirebaseRuntimeConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MunafAccountingRobolectricTest {

    private lateinit var db: MunafDatabase
    private lateinit var dao: MunafDao
    private val uid = "user_test_1"
    private val branchMain = "br_main"
    private val branchTwo = "br_two"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MunafDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.munafDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `atomic sale and cancellation accurately update stock and customer due`() = runBlocking {
        val product = ProductEntity(
            id = "p1",
            userId = uid,
            branchId = branchMain,
            name = "Miniket Rice 50kg",
            sku = "RICE-50",
            category = "Grocery",
            costPrice = 3000.0,
            sellPrice = 3500.0,
            stockQty = 10.0,
            minStockAlert = 2.0
        )
        val customer = PartyEntity(
            id = "c1",
            userId = uid,
            branchId = branchMain,
            partyType = PartyType.CUSTOMER.name,
            name = "Karim",
            phone = "01711111111",
            address = "Dhaka"
        )
        dao.upsertProduct(product)
        dao.upsertParty(customer)

        val line = TxLineItem("p1", "Miniket Rice 50kg", 2.0, 3500.0, 3000.0)
        val saleTx = TransactionEntity(
            id = "tx_sale_1",
            userId = uid,
            branchId = branchMain,
            txType = TransactionType.SALE.name,
            partyId = "c1",
            partyName = "Karim",
            subtotal = 7000.0,
            discount = 200.0,
            totalAmount = 6800.0,
            paidAmount = 4000.0,
            dueAmount = 2800.0,
            cogsTotal = 6000.0,
            lineItemsJson = TxLineItem.toJson(listOf(line))
        )

        dao.recordSaleAtomic(saleTx, listOf(line))

        val afterSaleProd = dao.getProductById("p1")!!
        val afterSaleCust = dao.getPartyById("c1")!!
        val logsAfterSale = dao.observeStockLogs(uid).first()

        assertEquals(8.0, afterSaleProd.stockQty, 0.001)
        assertEquals(6800.0, afterSaleCust.totalAmount, 0.001)
        assertEquals(4000.0, afterSaleCust.paidAmount, 0.001)
        assertEquals(2800.0, afterSaleCust.dueAmount, 0.001)
        assertEquals(1, logsAfterSale.size)
        assertEquals(-2.0, logsAfterSale.first().changeQty, 0.001)

        // Collect partial due of 1000
        dao.recordPartyPaymentAtomic(
            TransactionEntity(
                id = "tx_due_1",
                userId = uid,
                branchId = branchMain,
                txType = TransactionType.DUE_COLLECTION.name,
                partyId = "c1",
                partyName = "Karim",
                totalAmount = 1000.0,
                paidAmount = 1000.0
            )
        )
        val afterDueCust = dao.getPartyById("c1")!!
        assertEquals(1800.0, afterDueCust.dueAmount, 0.001)

        // Cancel original sale -> stock must return to 10.0 and customer total reverses
        dao.cancelSaleAtomic("tx_sale_1", uid)
        val cancelledProd = dao.getProductById("p1")!!
        val cancelledTx = dao.getTransactionById("tx_sale_1")!!
        val logsAfterCancel = dao.observeStockLogs(uid).first()

        assertEquals(10.0, cancelledProd.stockQty, 0.001)
        assertEquals("CANCELLED", cancelledTx.status)
        assertEquals(2, logsAfterCancel.size)
    }

    @Test
    fun `atomic purchase and branch stock transfer update inventory and logs`() = runBlocking {
        val product = ProductEntity(
            id = "p2",
            userId = uid,
            branchId = branchMain,
            name = "Soybean Oil 5L",
            sku = "OIL-5L",
            category = "Grocery",
            costPrice = 800.0,
            sellPrice = 920.0,
            stockQty = 5.0,
            minStockAlert = 3.0
        )
        val supplier = PartyEntity(
            id = "s1",
            userId = uid,
            branchId = branchMain,
            partyType = PartyType.SUPPLIER.name,
            name = "Bashundhara Dist",
            phone = "01800000000",
            address = "Chattogram"
        )
        dao.upsertProduct(product)
        dao.upsertParty(supplier)

        val purchaseLine = TxLineItem("p2", "Soybean Oil 5L", 10.0, 810.0, 810.0)
        val purchaseTx = TransactionEntity(
            id = "tx_pur_1",
            userId = uid,
            branchId = branchMain,
            txType = TransactionType.PURCHASE.name,
            partyId = "s1",
            partyName = "Bashundhara Dist",
            subtotal = 8100.0,
            totalAmount = 8100.0,
            paidAmount = 5000.0,
            dueAmount = 3100.0,
            lineItemsJson = TxLineItem.toJson(listOf(purchaseLine))
        )

        dao.recordPurchaseAtomic(purchaseTx, listOf(purchaseLine))

        val updatedProd = dao.getProductById("p2")!!
        val updatedSupplier = dao.getPartyById("s1")!!
        assertEquals(15.0, updatedProd.stockQty, 0.001)
        assertEquals(810.0, updatedProd.costPrice, 0.001)
        assertEquals(3100.0, updatedSupplier.dueAmount, 0.001)

        // Transfer 4 units to branchTwo
        dao.transferStockAtomic(updatedProd, branchTwo, 4.0)
        val allProducts = dao.observeProducts(uid).first()
        val mainProd = allProducts.first { it.branchId == branchMain }
        val targetProd = allProducts.first { it.branchId == branchTwo }

        assertEquals(11.0, mainProd.stockQty, 0.001)
        assertEquals(4.0, targetProd.stockQty, 0.001)
    }

    @Test
    fun `firebase runtime config parses valid json and rejects mismatched package`() {
        val sampleJson = """
            {
              "project_info": {
                "project_number": "1234567890",
                "project_id": "test-project-id",
                "storage_bucket": "test-project-id.appspot.com"
              },
              "client": [
                {
                  "client_info": {
                    "mobilesdk_app_id": "1:1234567890:android:abcdef123456",
                    "android_client_info": {
                      "package_name": "com.aistudio.munafshop.bxrkpq"
                    }
                  },
                  "oauth_client": [
                    {
                      "client_id": "1234567890-web.apps.googleusercontent.com",
                      "client_type": 3
                    }
                  ],
                  "api_key": [
                    { "current_key": "fake-test-api-key-value" }
                  ]
                }
              ]
            }
        """.trimIndent()

        val valid = FirebaseRuntimeConfig.parseAndValidate(sampleJson, "com.aistudio.munafshop.bxrkpq")
        assertEquals(true, valid.isSuccess)
        val parsed = valid.getOrThrow()
        assertEquals("test-project-id", parsed.projectId)
        assertEquals("1:1234567890:android:abcdef123456", parsed.applicationId)
        assertEquals("fake-test-api-key-value", parsed.apiKey)
        assertEquals("1234567890-web.apps.googleusercontent.com", parsed.webClientId)

        val wrongPkg = FirebaseRuntimeConfig.parseAndValidate(sampleJson, "com.other.app")
        assertEquals(true, wrongPkg.isFailure)

        val invalidJson = FirebaseRuntimeConfig.parseAndValidate("not-json", "com.aistudio.munafshop.bxrkpq")
        assertEquals(true, invalidJson.isFailure)
    }
}
