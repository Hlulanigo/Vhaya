package com.wayfare.ride.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TripEntity::class,
        SavedDestinationEntity::class,
        TrustedContactEntity::class,
        WalletTransactionEntity::class,
        DriverApplicationEntity::class,
        SupportCaseEntity::class,
        PayoutRecordEntity::class,
        UserAccountEntity::class,
        PayoutBatchEntity::class,
        ActivityLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(WayfareTypeConverters::class)
abstract class WayfareDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun savedDestinationDao(): SavedDestinationDao
    abstract fun trustedContactDao(): TrustedContactDao
    abstract fun walletDao(): WalletDao
    abstract fun driverApplicationDao(): DriverApplicationDao
    abstract fun supportCaseDao(): SupportCaseDao
    abstract fun payoutDao(): PayoutDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun payoutBatchDao(): PayoutBatchDao
    abstract fun activityLogDao(): ActivityLogDao

    companion object {
        @Volatile
        private var INSTANCE: WayfareDatabase? = null

        fun getInstance(context: Context): WayfareDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    WayfareDatabase::class.java,
                    "vhaya_local.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
