package com.bassam.scanner.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "scan_entries")
data class ScanEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val hsCode: String,
    val createdAt: Long = System.currentTimeMillis()
)
