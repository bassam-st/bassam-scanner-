package com.bassam.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanDao {

    @Insert
    fun insert(item: ScanEntity)

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    fun getAllSync(): List<ScanEntity>

    @Query("DELETE FROM scans")
    fun deleteAll()
}
