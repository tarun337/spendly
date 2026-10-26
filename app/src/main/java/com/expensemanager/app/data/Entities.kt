package com.expensemanager.app.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class TxType { EXPENSE, INCOME }

enum class PaymentMode(val label: String) {
    UPI("UPI"),
    CASH("Cash"),
    DEBIT_CARD("Debit Card"),
    CREDIT_CARD("Credit Card");

    companion object {
        val DEFAULT = UPI
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * A single expense or income entry.
 *
 * Amounts are stored in minor units (paise / cents) to avoid floating point errors.
 * [date] is the day the money actually moved (epoch day).
 * [accountMonth] is the month the entry is counted in (yyyyMM) — normally the month
 * of [date], but can be moved, e.g. salary paid on the 30th that belongs to next month.
 */
@Entity(tableName = "transactions", indices = [Index("accountMonth"), Index("date")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val category: String,
    val paymentMode: String,
    val note: String,
    val date: Long,
    val accountMonth: Int,
    val createdAt: Long = System.currentTimeMillis(),
    /** Saved group this split was made for (e.g. Flatmates); null if none. */
    val groupId: Long? = null,
)

/** A share of a split expense owed by another person. */
@Entity(
    tableName = "split_shares",
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("transactionId"), Index("personName")],
)
data class SplitShare(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val personName: String,
    val phone: String = "",
    val amount: Long,
    val settled: Boolean = false,
    val settledOn: Long? = null,
)

data class TransactionWithShares(
    @Embedded val tx: TransactionEntity,
    @Relation(parentColumn = "id", entityColumn = "transactionId")
    val shares: List<SplitShare>,
) {
    val isExpense get() = tx.type == TxType.EXPENSE.name
    val isSplit get() = shares.isNotEmpty()

    /** The user's own share once everyone has paid back. */
    val myShare get() = tx.amount - shares.sumOf { it.amount }
    val pendingFromOthers get() = shares.filter { !it.settled }.sumOf { it.amount }

    /**
     * What counts as spent right now: the full amount minus only the split money
     * already received. Pending shares stay in the expense until they are settled.
     */
    val effectiveExpense get() = tx.amount - shares.filter { it.settled }.sumOf { it.amount }
}

/** A saved set of people (e.g. "Flatmates") that can be added to a split in one tap. */
@Entity(tableName = "people_groups")
data class PeopleGroup(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
)

@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "name"],
    foreignKeys = [
        ForeignKey(entity = PeopleGroup::class, parentColumns = ["id"], childColumns = ["groupId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("groupId")],
)
data class GroupMember(val groupId: Long, val name: String, val phone: String = "")

data class GroupWithMembers(
    @Embedded val group: PeopleGroup,
    @Relation(parentColumn = "id", entityColumn = "groupId")
    val members: List<GroupMember>,
)

/** A person pinned to the top of the split picker. */
@Entity(tableName = "favourites")
data class Favourite(@PrimaryKey val name: String, val phone: String = "")

/** A person picked for a split / group / favourite (contact or free-typed name). */
data class Person(val name: String, val phone: String = "")

/**
 * Best-matching saved group for a set of split participants: the largest group whose
 * members are all included. Used for splits that weren't explicitly made via a group.
 */
fun detectGroup(names: List<String>, groups: List<GroupWithMembers>): GroupWithMembers? =
    groups.filter { g -> g.members.isNotEmpty() && g.members.all { m -> names.any { it.equals(m.name, ignoreCase = true) } } }
        .maxByOrNull { it.members.size }
