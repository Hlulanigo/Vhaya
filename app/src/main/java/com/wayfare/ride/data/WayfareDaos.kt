package com.wayfare.ride.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    suspend fun getAll(): List<TripEntity>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TripEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(trips: List<TripEntity>)

    @Query("DELETE FROM trips")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(trips: List<TripEntity>) {
        clearAll()
        if (trips.isNotEmpty()) upsertAll(trips)
    }

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun count(): Int
}

@Dao
interface SavedDestinationDao {
    @Query("SELECT * FROM saved_destinations ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedDestinationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SavedDestinationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SavedDestinationEntity>)

    @Query("DELETE FROM saved_destinations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM saved_destinations")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM saved_destinations")
    suspend fun count(): Int
}

@Dao
interface TrustedContactDao {
    @Query("SELECT * FROM trusted_contacts ORDER BY name ASC")
    fun observeAll(): Flow<List<TrustedContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: TrustedContactEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TrustedContactEntity>)

    @Query("DELETE FROM trusted_contacts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM trusted_contacts")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM trusted_contacts")
    suspend fun count(): Int
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallet_transactions ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<WalletTransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tx: WalletTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(txs: List<WalletTransactionEntity>)

    @Query("DELETE FROM wallet_transactions")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(txs: List<WalletTransactionEntity>) {
        clearAll()
        if (txs.isNotEmpty()) insertAll(txs)
    }

    @Query("SELECT COUNT(*) FROM wallet_transactions")
    suspend fun count(): Int
}

@Dao
interface DriverApplicationDao {
    @Query("SELECT * FROM driver_applications ORDER BY displayName ASC")
    fun observeAll(): Flow<List<DriverApplicationEntity>>

    @Query("SELECT * FROM driver_applications WHERE userId = :userId LIMIT 1")
    suspend fun getById(userId: String): DriverApplicationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DriverApplicationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DriverApplicationEntity>)

    @Query("DELETE FROM driver_applications")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<DriverApplicationEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COUNT(*) FROM driver_applications")
    suspend fun count(): Int
}

@Dao
interface SupportCaseDao {
    @Query("SELECT * FROM support_cases ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SupportCaseEntity>>

    @Query("SELECT * FROM support_cases WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SupportCaseEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SupportCaseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<SupportCaseEntity>)

    @Query("DELETE FROM support_cases")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<SupportCaseEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COALESCE(MAX(id), 100) FROM support_cases")
    suspend fun maxId(): Long

    @Query("SELECT COUNT(*) FROM support_cases")
    suspend fun count(): Int
}

@Dao
interface PayoutDao {
    @Query("SELECT * FROM payout_records ORDER BY recordedAt DESC")
    fun observeAll(): Flow<List<PayoutRecordEntity>>

    @Query("SELECT * FROM payout_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PayoutRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PayoutRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PayoutRecordEntity>)

    @Query("DELETE FROM payout_records")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PayoutRecordEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COALESCE(MAX(id), 500) FROM payout_records")
    suspend fun maxId(): Long

    @Query("SELECT COUNT(*) FROM payout_records")
    suspend fun count(): Int
}

@Dao
interface UserAccountDao {
    @Query("SELECT * FROM user_accounts ORDER BY displayName ASC")
    fun observeAll(): Flow<List<UserAccountEntity>>

    @Query("SELECT * FROM user_accounts WHERE userId = :userId LIMIT 1")
    suspend fun getById(userId: String): UserAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: UserAccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<UserAccountEntity>)

    @Query("DELETE FROM user_accounts")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<UserAccountEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COUNT(*) FROM user_accounts")
    suspend fun count(): Int
}

@Dao
interface PayoutBatchDao {
    @Query("SELECT * FROM payout_batches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PayoutBatchEntity>>

    @Query("SELECT * FROM payout_batches WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PayoutBatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PayoutBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PayoutBatchEntity>)

    @Query("DELETE FROM payout_batches")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PayoutBatchEntity>) {
        clearAll()
        if (items.isNotEmpty()) upsertAll(items)
    }

    @Query("SELECT COUNT(*) FROM payout_batches")
    suspend fun count(): Int
}

@Dao
interface ActivityLogDao {
    @Query("SELECT * FROM activity_logs ORDER BY createdAt DESC LIMIT 80")
    fun observeAll(): Flow<List<ActivityLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ActivityLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityLogEntity>)

    @Query("DELETE FROM activity_logs")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<ActivityLogEntity>) {
        clearAll()
        if (items.isNotEmpty()) insertAll(items)
    }

    @Query("SELECT COUNT(*) FROM activity_logs")
    suspend fun count(): Int
}
