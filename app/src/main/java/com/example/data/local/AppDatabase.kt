package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.AnnouncementDao
import com.example.data.local.dao.BillDao
import com.example.data.local.dao.CustomerDao
import com.example.data.local.dao.PackagePlanDao
import com.example.data.local.dao.SettlementDao
import com.example.data.local.dao.StaffDao
import com.example.data.local.dao.SyncQueueDao
import com.example.data.local.dao.TransactionDao
import com.example.data.local.entity.AnnouncementEntity
import com.example.data.local.entity.BillEntity
import com.example.data.local.entity.CustomerEntity
import com.example.data.local.entity.PackagePlanEntity
import com.example.data.local.entity.SettlementBatchEntity
import com.example.data.local.entity.StaffEntity
import com.example.data.local.entity.SyncQueueEntity
import com.example.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        CustomerEntity::class,
        PackagePlanEntity::class,
        TransactionEntity::class,
        StaffEntity::class,
        SettlementBatchEntity::class,
        SyncQueueEntity::class,
        AnnouncementEntity::class,
        BillEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun packagePlanDao(): PackagePlanDao
    abstract fun transactionDao(): TransactionDao
    abstract fun staffDao(): StaffDao
    abstract fun settlementDao(): SettlementDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun billDao(): BillDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "billingku_database"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Populate initial staff accounts only (clean database for customers & packages)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    populateInitialData(database)
                                }
                            }
                        }
                    })
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun populateInitialData(db: AppDatabase) {
            val staffDao = db.staffDao()

            // Staff Accounts (Akun default agar pengguna langsung dapat login)
            staffDao.insertStaff(
                StaffEntity(username = "admin", name = "Budi Hartono (Owner)", role = "ADMIN", phone = "081234567890", pin = "1234")
            )
            staffDao.insertStaff(
                StaffEntity(username = "kasir1", name = "Siti Rahma", role = "KASIR", phone = "081987654321", pin = "1111")
            )
            staffDao.insertStaff(
                StaffEntity(username = "teknisi1", name = "Agus Kurniawan", role = "TEKNISI", phone = "085678901234", pin = "2222")
            )

            // Initial announcement
            val announcementDao = db.announcementDao()
            announcementDao.insertAnnouncement(
                AnnouncementEntity(
                    id = 1L,
                    title = "Layanan Internet Beroperasi Normal",
                    category = "INFO",
                    message = "Seluruh distribusi jaringan WiFi RT/RW Net saat ini berjalan lancar dan optimal. Hubungi admin jika membutuhkan bantuan teknis.",
                    targetScope = "SEMUA",
                    status = "ACTIVE",
                    estimatedEnd = "",
                    createdBy = "Admin"
                )
            )
        }
    }
}

