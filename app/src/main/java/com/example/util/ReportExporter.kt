package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.local.TransactionEntity
import java.io.File
import java.io.FileOutputStream

object ReportExporter {

    fun exportPdfReport(
        context: Context,
        shopName: String,
        branchLabel: String,
        periodLabel: String,
        summaryLines: List<Pair<String, String>>,
        transactions: List<TransactionEntity>
    ): Result<File> =
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "munaf_report_${System.currentTimeMillis()}.pdf")
            val pdf = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
            var page = pdf.startPage(pageInfo)
            var canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.parseColor("#0B564F")
                textSize = 18f
                isFakeBoldText = true
            }
            val subPaint = Paint().apply {
                color = Color.DKGRAY
                textSize = 11f
            }
            val bodyPaint = Paint().apply {
                color = Color.BLACK
                textSize = 10f
            }
            val boldPaint = Paint().apply {
                color = Color.BLACK
                textSize = 11f
                isFakeBoldText = true
            }

            var y = 48f
            canvas.drawText("$shopName - MUNAF ONLINE SHOP", 36f, y, titlePaint)
            y += 20f
            canvas.drawText("Branch: $branchLabel  |  Period: $periodLabel", 36f, y, subPaint)
            y += 24f

            canvas.drawText("Financial Summary", 36f, y, boldPaint)
            y += 16f
            summaryLines.forEach { (label, value) ->
                canvas.drawText("$label: $value", 42f, y, bodyPaint)
                y += 15f
            }

            y += 14f
            canvas.drawText("Recent Transactions (${transactions.size})", 36f, y, boldPaint)
            y += 16f

            transactions.take(30).forEach { tx ->
                if (y > 790f) {
                    pdf.finishPage(page)
                    page = pdf.startPage(pageInfo)
                    canvas = page.canvas
                    y = 48f
                }
                val line = "${Formatters.formatDate(tx.txDateMillis)} | ${tx.txType} | ${tx.partyName.ifBlank { "-" }} | Total: ${tx.totalAmount} | Paid: ${tx.paidAmount} [${tx.status}]"
                canvas.drawText(line.take(92), 36f, y, bodyPaint)
                y += 14f
            }

            pdf.finishPage(page)
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun exportCsvReport(
        context: Context,
        shopName: String,
        transactions: List<TransactionEntity>
    ): Result<File> =
        try {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, "munaf_export_${System.currentTimeMillis()}.csv")
            val sb = StringBuilder()
            // UTF-8 BOM so Microsoft Excel renders Bengali characters cleanly
            sb.append('\uFEFF')
            sb.append("Shop,Date,Type,Party,Subtotal,Discount,Total,Paid,Due,COGS,Category,Status,Operator,Note\n")
            transactions.forEach { tx ->
                sb.append(
                    listOf(
                        shopName,
                        Formatters.formatDateTime(tx.txDateMillis),
                        tx.txType,
                        tx.partyName,
                        tx.subtotal.toString(),
                        tx.discount.toString(),
                        tx.totalAmount.toString(),
                        tx.paidAmount.toString(),
                        tx.dueAmount.toString(),
                        tx.cogsTotal.toString(),
                        tx.category,
                        tx.status,
                        tx.operatorName,
                        tx.note
                    ).joinToString(",") { cell -> "\"${cell.replace("\"", "\"\"")}\"" }
                ).append("\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            Result.success(file)
        } catch (e: Exception) {
            Result.failure(e)
        }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }
}
