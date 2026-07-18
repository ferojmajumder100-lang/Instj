package com.example

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "proxies")
data class ProxyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val type: String = "HTTP", // HTTP or SOCKS5
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ProxyDao {
    @Query("SELECT * FROM proxies ORDER BY createdAt DESC")
    fun getAllProxies(): Flow<List<ProxyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyEntity)

    @Update
    suspend fun updateProxy(proxy: ProxyEntity)

    @Delete
    suspend fun deleteProxy(proxy: ProxyEntity)

    @Query("SELECT * FROM proxies WHERE id = :id LIMIT 1")
    suspend fun getProxyById(id: Int): ProxyEntity?
}

@Database(entities = [ProxyEntity::class], version = 1, exportSchema = false)
abstract class ProxyDatabase : RoomDatabase() {
    abstract fun proxyDao(): ProxyDao

    companion object {
        @Volatile
        private var INSTANCE: ProxyDatabase? = null

        fun getDatabase(context: Context): ProxyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ProxyDatabase::class.java,
                    "proxy_manager_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ProxyRepository(private val proxyDao: ProxyDao) {
    val allProxies: Flow<List<ProxyEntity>> = proxyDao.getAllProxies()

    suspend fun insert(proxy: ProxyEntity) {
        proxyDao.insertProxy(proxy)
    }

    suspend fun update(proxy: ProxyEntity) {
        proxyDao.updateProxy(proxy)
    }

    suspend fun delete(proxy: ProxyEntity) {
        proxyDao.deleteProxy(proxy)
    }

    suspend fun getProxyById(id: Int): ProxyEntity? {
        return proxyDao.getProxyById(id)
    }
}
