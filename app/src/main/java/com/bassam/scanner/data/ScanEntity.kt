package com.bassam.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scans")
data class ScanEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hsCode: String,
    val itemName: String,
    val rawText: String,
    val createdAt: Long
)
