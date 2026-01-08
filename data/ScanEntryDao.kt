package com.bassam.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanEntryDao {

    @Insert
    fun insert(entry: ScanEntry)

    @Query("SELECT * FROM scan_entries ORDER BY createdAt DESC")
    fun getAll(): List<ScanEntry>
}
