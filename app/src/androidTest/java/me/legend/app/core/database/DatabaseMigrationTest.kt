package me.legend.app.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        requireNotNull(LegendMeDatabase::class.java.canonicalName),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migratesFromVersionOneToCurrentSchema() {
        helper.createDatabase(TEST_DATABASE, 1).close()
        helper.runMigrationsAndValidate(
            TEST_DATABASE,
            15,
            true,
            DatabaseModule.MIGRATION_1_2,
            DatabaseModule.MIGRATION_2_3,
            DatabaseModule.MIGRATION_3_4,
            DatabaseModule.MIGRATION_4_5,
            DatabaseModule.MIGRATION_5_6,
            DatabaseModule.MIGRATION_6_7,
            DatabaseModule.MIGRATION_7_8,
            DatabaseModule.MIGRATION_8_9,
            DatabaseModule.MIGRATION_9_10,
            DatabaseModule.MIGRATION_10_11,
            DatabaseModule.MIGRATION_11_12,
            DatabaseModule.MIGRATION_12_13,
            DatabaseModule.MIGRATION_13_14,
            DatabaseModule.MIGRATION_14_15,
        ).close()
    }

    companion object {
        private const val TEST_DATABASE = "migration-test"
    }
}
