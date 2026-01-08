package com.bassam.scanner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScanDao {

    @Insert
    suspend fun insert(item: ScanItem)

    @Query("SELECT * FROM scan_items ORDER BY createdAt DESC LIMIT :limit")
    suspend fun latest(limit: Int = 50): List<ScanItem>

    @Query("""
        SELECT * FROM scan_items
        WHERE hsCode LIKE :q OR name LIKE :q
        ORDER BY createdAt DESC
        LIMIT 50
    """)
    suspend fun search(q: String): List<ScanItem>

    @Query("DELETE FROM scan_items")
    suspend fun clearAll()
}
