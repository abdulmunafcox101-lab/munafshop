const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_owner_1";
const BOB_UID = "bob_owner_2";
const SUPER_ADMIN_UID = "munaf_super_admin";
const SUPER_ADMIN_EMAIL = "abdulmunafcox101@gmail.com";
const SUB_ADMIN_UID = "sub_admin_1";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function nowPast() {
  return new Date(Date.now() - 1000);
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("business_accounts").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("subscription_packages").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("products").get()
  );
  await assertFails(
    unauthDb.collection("payment_requests").doc("req_1").set({
      userId: ALICE_UID,
      shopName: "Fake",
    })
  );
});

test("Business account creation, profile update, and privilege escalation prevention", async () => {
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
    .firestore();

  const created = new Date(Date.now() - 5000);
  await assertSucceeds(
    aliceDb.collection("business_accounts").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerEmail: "alice@example.com",
      shopName: "Alice Store",
      ownerName: "Alice",
      phone: "01711000000",
      address: "Dhaka",
      businessType: "Grocery",
      subscriptionTier: "FREE",
      subscriptionMonths: 0,
      subscriptionExpiresAtMillis: 0,
      blocked: false,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Owner can update shop profile fields
  await assertSucceeds(
    aliceDb.collection("business_accounts").doc(ALICE_UID).update({
      shopName: "Alice Super Mart",
      updatedAt: nowPast(),
    })
  );

  // Owner CANNOT self-upgrade subscriptionTier or unblock themselves
  await assertFails(
    aliceDb.collection("business_accounts").doc(ALICE_UID).update({
      subscriptionTier: "BUSINESS",
      updatedAt: nowPast(),
    })
  );

  // Owner CANNOT inject ghost field
  await assertFails(
    aliceDb.collection("business_accounts").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: nowPast(),
    })
  );

  // Bob cannot read or write Alice's account
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("business_accounts").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("business_accounts").doc(ALICE_UID).update({
      shopName: "Hijacked",
      updatedAt: nowPast(),
    })
  );
});

test("Owner-isolated subcollections (products, parties, transactions, stock_logs) enforce isolation and schema", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = new Date(Date.now() - 5000);

  // Create product
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("products")
      .doc("prod_1")
      .set({
        userId: ALICE_UID,
        branchId: "branch_main",
        name: "Miniket Rice 25kg",
        sku: "RICE-25",
        category: "Grocery",
        costPrice: 1650,
        sellPrice: 1850,
        stockQty: 20,
        minStockAlert: 5,
        createdAt: ts,
        updatedAt: ts,
      })
  );

  // List works for Alice on her subcollection
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("products").get()
  );

  // Bob cannot list or read Alice's products
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("products").get()
  );

  // Sale transaction create & cancel terminal locking
  const txRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("transactions")
    .doc("tx_1");
  await assertSucceeds(
    txRef.set({
      userId: ALICE_UID,
      branchId: "branch_main",
      txType: "SALE",
      partyId: "cust_1",
      partyName: "Rahim",
      subtotal: 1850,
      discount: 50,
      totalAmount: 1800,
      paidAmount: 1000,
      dueAmount: 800,
      cogsTotal: 1650,
      category: "Sales",
      note: "Cash + Due",
      status: "COMPLETED",
      operatorName: "Owner",
      lineItemsJson: "[]",
      txDateMillis: Date.now(),
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Cancel the transaction
  await assertSucceeds(
    txRef.update({
      status: "CANCELLED",
      updatedAt: nowPast(),
    })
  );

  // Terminal state lock: once CANCELLED, cannot re-open
  await assertFails(
    txRef.update({
      status: "COMPLETED",
      updatedAt: nowPast(),
    })
  );

  // Stock log is immutable once created
  const logRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("stock_logs")
    .doc("log_1");
  await assertSucceeds(
    logRef.set({
      userId: ALICE_UID,
      branchId: "branch_main",
      productId: "prod_1",
      productName: "Miniket Rice 25kg",
      changeQty: -1,
      reason: "SALE",
      referenceTxId: "tx_1",
      createdAt: ts,
    })
  );
  await assertFails(logRef.update({ changeQty: 10 }));
  await assertFails(logRef.delete());
});

test("Email spoofing test and Super Admin / Sub Admin payment approval flow", async () => {
  // Spoofed email with email_verified = false MUST be rejected
  const spoofAdminDb = testEnv
    .authenticatedContext("attacker_uid", {
      email: SUPER_ADMIN_EMAIL,
      email_verified: false,
    })
    .firestore();

  await assertFails(
    spoofAdminDb.collection("subscription_packages").doc("pkg_basic").set({
      tier: "BASIC",
      nameBn: "বেসিক",
      nameEn: "Basic",
      monthlyPriceBdt: 299,
      maxBranches: 2,
      maxEmployees: 3,
      aiEnabled: true,
      exportEnabled: true,
      updatedAt: nowPast(),
    })
  );

  // Real verified Super Admin succeeds
  const superAdminDb = testEnv
    .authenticatedContext(SUPER_ADMIN_UID, {
      email: SUPER_ADMIN_EMAIL,
      email_verified: true,
    })
    .firestore();

  await assertSucceeds(
    superAdminDb.collection("subscription_packages").doc("pkg_basic").set({
      tier: "BASIC",
      nameBn: "বেসিক",
      nameEn: "Basic",
      monthlyPriceBdt: 299,
      maxBranches: 2,
      maxEmployees: 3,
      aiEnabled: true,
      exportEnabled: true,
      updatedAt: nowPast(),
    })
  );

  // Super Admin creates Sub-Admin with payment verification permission only
  const ts = new Date(Date.now() - 5000);
  await assertSucceeds(
    superAdminDb.collection("admins").doc(SUB_ADMIN_UID).set({
      uid: SUB_ADMIN_UID,
      email: "support@munafshop.com",
      name: "Support Officer",
      role: "SUB_ADMIN",
      canVerifyPayments: true,
      canManageUsers: false,
      canViewLimitedReports: true,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice submits manual bKash payment request
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    aliceDb.collection("business_accounts").doc(ALICE_UID).set({
      userId: ALICE_UID,
      ownerEmail: "alice@example.com",
      shopName: "Alice Store",
      ownerName: "Alice",
      phone: "01711000000",
      address: "Dhaka",
      businessType: "Grocery",
      subscriptionTier: "FREE",
      subscriptionMonths: 0,
      subscriptionExpiresAtMillis: 0,
      blocked: false,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    aliceDb.collection("payment_requests").doc("pay_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      shopName: "Alice Store",
      method: "BKASH",
      senderNumber: "01711000000",
      transactionId: "BKA99887766",
      amountBdt: 299,
      packageTier: "BASIC",
      durationMonths: 1,
      status: "PENDING",
      adminNote: "",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Unfiltered query on payment_requests fails for Alice, filtered query succeeds
  await assertFails(aliceDb.collection("payment_requests").get());
  await assertSucceeds(
    aliceDb
      .collection("payment_requests")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  // Alice cannot approve her own payment
  await assertFails(
    aliceDb.collection("payment_requests").doc("pay_1").update({
      status: "APPROVED",
      adminNote: "Self approved",
      updatedAt: nowPast(),
    })
  );

  // Sub-Admin with canVerifyPayments=true CAN approve payment & activate subscription
  const subAdminDb = testEnv
    .authenticatedContext(SUB_ADMIN_UID, {
      email: "support@munafshop.com",
      email_verified: true,
    })
    .firestore();
  await assertSucceeds(
    subAdminDb.collection("payment_requests").doc("pay_1").update({
      status: "APPROVED",
      adminNote: "Verified bKash TrxID",
      updatedAt: nowPast(),
    })
  );

  // Terminal lock: cannot re-update an already APPROVED payment
  await assertFails(
    subAdminDb.collection("payment_requests").doc("pay_1").update({
      status: "REJECTED",
      adminNote: "Changed mind",
      updatedAt: nowPast(),
    })
  );

  // Sub-Admin CANNOT block users (canManageUsers=false) or read Super Admin audit logs
  await assertFails(
    subAdminDb.collection("business_accounts").doc(ALICE_UID).update({
      blocked: true,
      updatedAt: nowPast(),
    })
  );
  await assertFails(subAdminDb.collection("audit_logs").get());

  // Super Admin CAN write and read audit logs
  await assertSucceeds(
    superAdminDb.collection("audit_logs").doc("audit_1").set({
      actorUid: SUPER_ADMIN_UID,
      actorEmail: SUPER_ADMIN_EMAIL,
      action: "APPROVE_PAYMENT",
      targetId: "pay_1",
      details: "Approved BASIC 1m",
      createdAt: nowPast(),
    })
  );
  await assertSucceeds(superAdminDb.collection("audit_logs").get());
});

test("Global Duplicate TrxID prevention & mandatory Rejection reason", async () => {
  const ts = nowPast();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const superAdminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();

  // Alice registers TrxID TRX999888
  await assertSucceeds(
    aliceDb.collection("trx_registry").doc("TRX999888").set({
      userId: ALICE_UID,
      trxKey: "TRX999888",
      createdAt: ts,
    })
  );

  // Bob CANNOT re-use or overwrite TRX999888 globally
  await assertFails(
    bobDb.collection("trx_registry").doc("TRX999888").set({
      userId: BOB_UID,
      trxKey: "TRX999888",
      createdAt: nowPast(),
    })
  );
  // Bob CANNOT snoop trx_registry either
  await assertFails(bobDb.collection("trx_registry").doc("TRX999888").get());

  // Bob submits a fresh order
  await assertSucceeds(
    bobDb.collection("payment_requests").doc("pay_bob_1").set({
      userId: BOB_UID,
      userEmail: "bob@example.com",
      shopName: "Bob Mart",
      method: "NAGAD",
      senderNumber: "01811000000",
      transactionId: "NGD776655",
      amountBdt: 599,
      lockedUnitMonthlyBdt: 599,
      packageTier: "PREMIUM",
      durationMonths: 1,
      status: "PENDING",
      adminNote: "",
      reviewedByEmail: "",
      reviewedAtMillis: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Rejecting WITHOUT a reason must FAIL
  await assertFails(
    superAdminDb.collection("payment_requests").doc("pay_bob_1").update({
      status: "REJECTED",
      adminNote: "",
      reviewedByEmail: SUPER_ADMIN_EMAIL,
      reviewedAtMillis: Date.now(),
      updatedAt: nowPast(),
    })
  );

  // Rejecting WITH a valid reason succeeds
  await assertSucceeds(
    superAdminDb.collection("payment_requests").doc("pay_bob_1").update({
      status: "REJECTED",
      adminNote: "Transaction ID not matched in Nagad statement",
      reviewedByEmail: SUPER_ADMIN_EMAIL,
      reviewedAtMillis: Date.now(),
      updatedAt: nowPast(),
    })
  );
});

test("Complaints, Moderation with reason, Payment Settings, Price History & Email Outbox RBAC", async () => {
  const ts = nowPast();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID, { email: "bob@example.com", email_verified: true }).firestore();
  const subAdminDb = testEnv.authenticatedContext(SUB_ADMIN_UID, { email: "support@munafshop.com", email_verified: true }).firestore();
  const superAdminDb = testEnv.authenticatedContext(SUPER_ADMIN_UID, { email: SUPER_ADMIN_EMAIL, email_verified: true }).firestore();

  // Super Admin saves bKash/Nagad payment settings; Sub-Admin and customers cannot mutate
  await assertSucceeds(
    superAdminDb.collection("app_config").doc("payment_settings").set({
      bkashAccountName: "MUNAF SHOP",
      bkashNumber: "01712345678",
      bkashEnabled: true,
      nagadAccountName: "MUNAF SHOP",
      nagadNumber: "01812345678",
      nagadEnabled: true,
      instructionsBn: "Send money করে TrxID দিন",
      instructionsEn: "Send money and submit TrxID",
      updatedByEmail: SUPER_ADMIN_EMAIL,
      updatedAt: ts,
    })
  );
  await assertSucceeds(aliceDb.collection("app_config").doc("payment_settings").get());
  await assertFails(
    aliceDb.collection("app_config").doc("payment_settings").update({ bkashNumber: "01900000000", updatedAt: nowPast() })
  );

  // Super Admin creates Sub-Admin with canReplyComplaints=true
  await assertSucceeds(
    superAdminDb.collection("admins").doc(SUB_ADMIN_UID).set({
      uid: SUB_ADMIN_UID,
      email: "support@munafshop.com",
      name: "Support Officer",
      role: "SUB_ADMIN",
      canVerifyPayments: true,
      canManageUsers: false,
      canReplyComplaints: true,
      canViewLimitedReports: true,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Sub-Admin CANNOT self-elevate to SUPER_ADMIN or read package_price_history / email_outbox
  await assertFails(
    subAdminDb.collection("admins").doc(SUB_ADMIN_UID).update({ role: "SUPER_ADMIN", updatedAt: nowPast() })
  );
  await assertFails(subAdminDb.collection("package_price_history").get());
  await assertFails(subAdminDb.collection("email_outbox").get());

  // Customer Alice creates a complaint
  await assertSucceeds(
    aliceDb.collection("complaints").doc("tkt_1").set({
      ticketCode: "TKT-100200",
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      shopName: "Alice Store",
      subject: "Sync issue",
      description: "Payment took time to confirm",
      status: "PENDING",
      lastReplyText: "",
      lastReplyBy: "",
      lastReplyAtMillis: 0,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  // Bob cannot read Alice's complaint; Alice cannot resolve her own complaint
  await assertFails(bobDb.collection("complaints").doc("tkt_1").get());
  await assertFails(
    aliceDb.collection("complaints").doc("tkt_1").update({ status: "RESOLVED", updatedAt: nowPast() })
  );

  // Sub-Admin with canReplyComplaints CAN reply and mark IN_PROGRESS / RESOLVED
  await assertSucceeds(
    subAdminDb.collection("complaints").doc("tkt_1").collection("replies").doc("rep_1").set({
      ticketId: "tkt_1",
      senderUid: SUB_ADMIN_UID,
      senderName: "Support Officer",
      senderRole: "SUB_ADMIN",
      message: "Checking your TrxID now.",
      createdAt: nowPast(),
    })
  );
  await assertSucceeds(
    subAdminDb.collection("complaints").doc("tkt_1").update({
      status: "IN_PROGRESS",
      lastReplyText: "Checking your TrxID now.",
      lastReplyBy: "Support Officer",
      lastReplyAtMillis: Date.now(),
      updatedAt: nowPast(),
    })
  );

  // Moderation without reason FAILS, with reason SUCCEEDS
  await assertFails(
    superAdminDb.collection("moderation_history").doc("mod_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      shopName: "Alice Store",
      action: "BAN",
      reason: "",
      suspendedUntilMillis: 0,
      actorUid: SUPER_ADMIN_UID,
      actorEmail: SUPER_ADMIN_EMAIL,
      createdAt: nowPast(),
    })
  );
  await assertSucceeds(
    superAdminDb.collection("moderation_history").doc("mod_1").set({
      userId: ALICE_UID,
      userEmail: "alice@example.com",
      shopName: "Alice Store",
      action: "BAN",
      reason: "Fake TrxID submitted repeatedly",
      suspendedUntilMillis: 0,
      actorUid: SUPER_ADMIN_UID,
      actorEmail: SUPER_ADMIN_EMAIL,
      createdAt: nowPast(),
    })
  );

  // Alice queues an owner email alert on payment order; only Super Admin can list email_outbox
  await assertSucceeds(
    aliceDb.collection("email_outbox").doc("mail_1").set({
      recipientEmail: SUPER_ADMIN_EMAIL,
      emailType: "ORDER_ALERT_OWNER",
      subject: "New BASIC 1m Order",
      bodyPreview: "Shop Alice Store paid 299 BDT",
      relatedUserId: ALICE_UID,
      relatedOrderId: "pay_1",
      status: "PENDING_BACKEND_CONFIG",
      statusNote: "Pending backend SMTP config",
      createdAt: nowPast(),
    })
  );
  await assertFails(aliceDb.collection("email_outbox").get());
  await assertSucceeds(superAdminDb.collection("email_outbox").get());
});

