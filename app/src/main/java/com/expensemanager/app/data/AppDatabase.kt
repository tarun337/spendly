package com.expensemanager.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, SplitShare::class, PeopleGroup::class, GroupMember::class, Favourite::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ExpenseDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 -> v2: "Savings A/c" payment mode was replaced by "Debit Card". */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("UPDATE transactions SET paymentMode = 'DEBIT_CARD' WHERE paymentMode = 'SAVINGS'")
            }
        }

        /** v2 -> v3: saved groups and favourite people for splits. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `people_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `group_members` (`groupId` INTEGER NOT NULL, `name` TEXT NOT NULL, `phone` TEXT NOT NULL, " +
                        "PRIMARY KEY(`groupId`, `name`), FOREIGN KEY(`groupId`) REFERENCES `people_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_group_members_groupId` ON `group_members` (`groupId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `favourites` (`name` TEXT NOT NULL, `phone` TEXT NOT NULL, PRIMARY KEY(`name`))")
            }
        }

        /** v3 -> v4: remember which group a split was made for. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `groupId` INTEGER")
            }
        }

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "expenses.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
