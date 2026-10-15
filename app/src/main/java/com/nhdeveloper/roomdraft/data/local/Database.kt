package com.nhdeveloper.roomdraft.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val roomType: String,
    val lengthMeters: Double,
    val widthMeters: Double,
    val templateId: String?,
    val createdAt: Long,
    val modifiedAt: Long
)

@Entity(
    tableName = "layouts",
    foreignKeys = [ForeignKey(
        entity = ProjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("projectId")]
)
data class LayoutEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val sortIndex: Int,
    val createdAt: Long
)

@Entity(
    tableName = "furniture",
    foreignKeys = [ForeignKey(
        entity = LayoutEntity::class,
        parentColumns = ["id"],
        childColumns = ["layoutId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("layoutId")]
)
data class FurnitureEntity(
    @PrimaryKey val id: String,
    val layoutId: String,
    val catalogId: String,
    val name: String,
    val centerX: Double,
    val centerY: Double,
    val widthM: Double,
    val depthM: Double,
    val rotationDeg: Int,
    val colorArgb: Long,
    val sortIndex: Int
)

data class FurnitureRow(
    @androidx.room.Embedded val furniture: FurnitureEntity,
    val projectId: String
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY modifiedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: String): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun get(id: String): ProjectEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(project: ProjectEntity)

    @Update
    suspend fun update(project: ProjectEntity)

    @Query("UPDATE projects SET modifiedAt = :time WHERE id = :id")
    suspend fun touch(id: String, time: Long)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAll()
}

@Dao
interface LayoutDao {
    @Query("SELECT * FROM layouts WHERE projectId = :projectId ORDER BY sortIndex, createdAt")
    suspend fun getForProject(projectId: String): List<LayoutEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(layout: LayoutEntity)

    @Query("UPDATE layouts SET name = :name WHERE id = :id")
    suspend fun rename(id: String, name: String)

    @Query("DELETE FROM layouts WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface FurnitureDao {
    @Query("SELECT * FROM furniture WHERE layoutId = :layoutId ORDER BY sortIndex")
    suspend fun getForLayout(layoutId: String): List<FurnitureEntity>

    @Query(
        "SELECT f.* FROM furniture f WHERE f.layoutId = " +
            "(SELECT id FROM layouts WHERE projectId = :projectId ORDER BY sortIndex, createdAt LIMIT 1) " +
            "ORDER BY f.sortIndex"
    )
    fun observeFirstLayout(projectId: String): Flow<List<FurnitureEntity>>

    @Query(
        "SELECT f.*, l.projectId AS projectId FROM furniture f INNER JOIN layouts l ON f.layoutId = l.id " +
            "WHERE l.id = (SELECT l2.id FROM layouts l2 WHERE l2.projectId = l.projectId " +
            "ORDER BY l2.sortIndex, l2.createdAt LIMIT 1) ORDER BY f.sortIndex"
    )
    fun observeAllFirstLayouts(): Flow<List<FurnitureRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<FurnitureEntity>)

    @Query("DELETE FROM furniture WHERE layoutId = :layoutId")
    suspend fun deleteForLayout(layoutId: String)
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `furniture` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
                "`catalogId` TEXT NOT NULL, `name` TEXT NOT NULL, `centerX` REAL NOT NULL, " +
                "`centerY` REAL NOT NULL, `widthM` REAL NOT NULL, `depthM` REAL NOT NULL, " +
                "`rotationDeg` INTEGER NOT NULL, `colorArgb` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_furniture_projectId` ON `furniture` (`projectId`)")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `layouts` (`id` TEXT NOT NULL, `projectId` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_layouts_projectId` ON `layouts` (`projectId`)")
        db.execSQL(
            "INSERT INTO `layouts` (`id`, `projectId`, `name`, `sortIndex`, `createdAt`) " +
                "SELECT 'L_' || `id`, `id`, 'Layout 1', 0, `createdAt` FROM `projects`"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `furniture_new` (`id` TEXT NOT NULL, `layoutId` TEXT NOT NULL, " +
                "`catalogId` TEXT NOT NULL, `name` TEXT NOT NULL, `centerX` REAL NOT NULL, " +
                "`centerY` REAL NOT NULL, `widthM` REAL NOT NULL, `depthM` REAL NOT NULL, " +
                "`rotationDeg` INTEGER NOT NULL, `colorArgb` INTEGER NOT NULL, `sortIndex` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`), FOREIGN KEY(`layoutId`) REFERENCES `layouts`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `furniture_new` (`id`, `layoutId`, `catalogId`, `name`, `centerX`, `centerY`, " +
                "`widthM`, `depthM`, `rotationDeg`, `colorArgb`, `sortIndex`) " +
                "SELECT `id`, 'L_' || `projectId`, `catalogId`, `name`, `centerX`, `centerY`, " +
                "`widthM`, `depthM`, `rotationDeg`, `colorArgb`, `sortIndex` FROM `furniture`"
        )
        db.execSQL("DROP TABLE `furniture`")
        db.execSQL("ALTER TABLE `furniture_new` RENAME TO `furniture`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_furniture_layoutId` ON `furniture` (`layoutId`)")
    }
}

@Database(
    entities = [ProjectEntity::class, LayoutEntity::class, FurnitureEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun layoutDao(): LayoutDao
    abstract fun furnitureDao(): FurnitureDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "roomdraft.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
