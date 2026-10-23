package com.shubham.spendtracker.data

import com.shubham.spendtracker.parser.TxnSource
import com.shubham.spendtracker.parser.TxnType
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backup format: a plain CSV that opens in any spreadsheet. `timestamp` (epoch ms) is the
 * source of truth on import; `date` is only for people reading the file.
 */
object CsvBackup {
    val HEADER = listOf(
        "date", "type", "amount", "merchant", "category", "account", "upi_ref",
        "source", "source_app", "timestamp", "message", "note", "tags",
    )

    // Backups made before notes and tags existed.
    private val HEADER_V1 = HEADER.take(11)

    private val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun write(txns: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): String = buildString {
        appendLine(HEADER.joinToString(","))
        txns.sortedBy { it.timestamp }.forEach { t ->
            appendLine(
                listOf(
                    Instant.ofEpochMilli(t.timestamp).atZone(zone).format(dateFmt),
                    t.type.name,
                    BigDecimal.valueOf(t.amountPaise, 2).toPlainString(),
                    t.merchant.orEmpty(),
                    t.category,
                    t.accountLast4.orEmpty(),
                    t.upiRef.orEmpty(),
                    t.source.name,
                    t.sourceApp,
                    t.timestamp.toString(),
                    t.rawText,
                    t.note.orEmpty(),
                    t.tags.orEmpty(),
                ).joinToString(",") { escape(it) },
            )
        }
    }

    /** Parses a backup. Throws [IllegalArgumentException] if the file isn't one of ours. */
    fun read(text: String): List<Transaction> {
        val rows = parse(text.removePrefix("﻿")).filter { row -> row.any { it.isNotEmpty() } }
        val header = rows.firstOrNull()
        require(header == HEADER || header == HEADER_V1) { "Not a Spend Tracker backup" }
        val width = header!!.size
        return rows.drop(1).mapIndexed { i, r ->
            require(r.size == width) { "Row ${i + 2} has ${r.size} columns, expected $width" }
            Transaction(
                amountPaise = BigDecimal(r[2]).movePointRight(2).longValueExact(),
                type = TxnType.valueOf(r[1]),
                merchant = r[3].ifEmpty { null },
                accountLast4 = r[5].ifEmpty { null },
                source = TxnSource.valueOf(r[7]),
                sourceApp = r[8],
                timestamp = r[9].toLong(),
                upiRef = r[6].ifEmpty { null },
                category = r[4],
                rawText = r[10],
                note = r.getOrNull(11)?.ifEmpty { null },
                tags = r.getOrNull(12)?.ifEmpty { null },
            )
        }
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

    /** RFC 4180: quoted fields may contain commas, doubled quotes and newlines. */
    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
                quoted && c == '"' -> quoted = false
                quoted -> field.append(c)
                c == '"' -> quoted = true
                c == ',' -> { row.add(field.toString()); field.clear() }
                c == '\n' || c == '\r' -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) { row.add(field.toString()); rows.add(row) }
        return rows
    }
}
