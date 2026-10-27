package com.expensemanager.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {

    @Transaction
    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionWithShares>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionWithShares?

    @Insert
    suspend fun insertTx(tx: TransactionEntity): Long

    /** Bulk insert used by CSV import (single transaction, so it's fast and all-or-nothing). */
    @Insert
    suspend fun insertTxs(txs: List<TransactionEntity>)

    /** SMS already imported, so a rescan doesn't offer them again. */
    @Query("SELECT smsRef FROM transactions WHERE smsRef IS NOT NULL")
    suspend fun smsRefs(): List<String>

    @Update
    suspend fun updateTx(tx: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTx(id: Long)

    @Insert
    suspend fun insertShares(shares: List<SplitShare>)

    @Query("DELETE FROM split_shares WHERE transactionId = :txId")
    suspend fun deleteSharesFor(txId: Long)

    @Query("UPDATE split_shares SET settled = :settled, settledOn = :settledOn WHERE id IN (:ids)")
    suspend fun setSettled(ids: List<Long>, settled: Boolean, settledOn: Long?)

    @Query("SELECT DISTINCT personName FROM split_shares ORDER BY personName")
    fun observePeople(): Flow<List<String>>

    /** Save a transaction and replace its split shares atomically. */
    @Transaction
    suspend fun upsertWithShares(tx: TransactionEntity, shares: List<SplitShare>): Long {
        val id = if (tx.id == 0L) insertTx(tx) else tx.id.also { updateTx(tx) }
        deleteSharesFor(id)
        if (shares.isNotEmpty()) insertShares(shares.map { it.copy(id = 0, transactionId = id) })
        return id
    }

    // ---- Groups ----

    @Transaction
    @Query("SELECT * FROM people_groups ORDER BY name COLLATE NOCASE")
    fun observeGroups(): Flow<List<GroupWithMembers>>

    @Insert
    suspend fun insertGroup(group: PeopleGroup): Long

    @Update
    suspend fun updateGroup(group: PeopleGroup)

    @Query("DELETE FROM people_groups WHERE id = :id")
    suspend fun deleteGroup(id: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMember>)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun deleteMembers(groupId: Long)

    /** Creates or updates a group and replaces its member list atomically. */
    @Transaction
    suspend fun saveGroup(group: PeopleGroup, members: List<GroupMember>) {
        val id = if (group.id == 0L) insertGroup(group) else group.id.also { updateGroup(group) }
        deleteMembers(id)
        if (members.isNotEmpty()) insertMembers(members.map { it.copy(groupId = id) })
    }

    // ---- Favourites ----

    @Query("SELECT * FROM favourites ORDER BY name COLLATE NOCASE")
    fun observeFavourites(): Flow<List<Favourite>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavourite(f: Favourite)

    @Query("DELETE FROM favourites WHERE name = :name COLLATE NOCASE")
    suspend fun deleteFavourite(name: String)
}
