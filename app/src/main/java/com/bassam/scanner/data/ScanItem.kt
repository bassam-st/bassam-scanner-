package com.bassam.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_items")
data class ScanItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hsCode: String,
    val name: String,
    val rawText: String,
    val createdAt: Long = System.currentTimeMillis()
)
