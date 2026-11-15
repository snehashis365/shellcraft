package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HostDao {
    @Query("SELECT * FROM ssh_hosts ORDER BY COALESCE(lastConnectedAtMs, 0) DESC, label ASC")
    fun observeHosts(): Flow<List<HostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHost(host: HostEntity)

    @Query("DELETE FROM ssh_hosts WHERE id = :id")
    suspend fun deleteHost(id: String)

    @Query("UPDATE ssh_hosts SET lastConnectedAtMs = :timestampMs WHERE id = :id")
    suspend fun updateLastConnected(id: String, timestampMs: Long)
}

@Dao
interface PluginDao {
    @Query("SELECT * FROM cli_plugins ORDER BY updatedAtEpochMs DESC, displayName ASC")
    fun observePlugins(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM cli_plugins WHERE LOWER(commandName) = LOWER(:commandName) LIMIT 1")
    suspend fun findByCommandName(commandName: String): PluginEntity?

    @Query("SELECT COUNT(*) FROM cli_plugins")
    suspend fun countPlugins(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlugin(plugin: PluginEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlugins(plugins: List<PluginEntity>)

    @Query("DELETE FROM cli_plugins WHERE id = :id")
    suspend fun deletePlugin(id: String)
}

@Dao
interface ExecutionLogDao {
    @Query("SELECT * FROM execution_logs ORDER BY timestampMs DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<ExecutionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLogEntity)

    @Query("DELETE FROM execution_logs")
    suspend fun clearLogs()
}
