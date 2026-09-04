package com.denis.habitlab.shared.data.local

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "experiments",
    indices = [Index(value = ["active_slot"], unique = true)],
)
internal data class ExperimentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "status")
    val status: String,
    /** ACTIVE maps to 1 and DRAFT to NULL; the unique index permits at most one active record. */
    @ColumnInfo(name = "active_slot")
    val activeSlot: Int?,
    @ColumnInfo(name = "created_utc_millis")
    val createdUtcMillis: Long,
    @ColumnInfo(name = "created_offset_seconds")
    val createdOffsetSeconds: Int,
    @ColumnInfo(name = "created_local_date")
    val createdLocalDate: String,
    @ColumnInfo(name = "updated_utc_millis")
    val updatedUtcMillis: Long,
    @ColumnInfo(name = "updated_offset_seconds")
    val updatedOffsetSeconds: Int,
    @ColumnInfo(name = "updated_local_date")
    val updatedLocalDate: String,
) {
    init {
        require(
            (status == ACTIVE_STATUS && activeSlot == ACTIVE_SLOT) ||
                (status == DRAFT_STATUS && activeSlot == null),
        ) { "Persisted experiment status and active slot must agree" }
    }
}

@Entity(
    tableName = "daily_check_ins",
    primaryKeys = ["experiment_id", "check_in_local_date"],
    foreignKeys = [
        ForeignKey(
            entity = ExperimentEntity::class,
            parentColumns = ["id"],
            childColumns = ["experiment_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["experiment_id"])],
)
internal data class CheckInEntity(
    @ColumnInfo(name = "experiment_id")
    val experimentId: String,
    @ColumnInfo(name = "check_in_local_date")
    val checkInLocalDate: String,
    @ColumnInfo(name = "outcome")
    val outcome: String,
    @ColumnInfo(name = "occurred_utc_millis")
    val occurredUtcMillis: Long?,
    @ColumnInfo(name = "occurred_offset_seconds")
    val occurredOffsetSeconds: Int?,
    @ColumnInfo(name = "recorded_utc_millis")
    val recordedUtcMillis: Long,
    @ColumnInfo(name = "recorded_offset_seconds")
    val recordedOffsetSeconds: Int,
    @ColumnInfo(name = "recorded_local_date")
    val recordedLocalDate: String,
) {
    init {
        require(
            when (outcome) {
                PERFORMED_OUTCOME -> occurredUtcMillis != null && occurredOffsetSeconds != null
                SKIPPED_OUTCOME -> occurredUtcMillis == null && occurredOffsetSeconds == null
                else -> false
            },
        ) { "Persisted check-in outcome and occurrence fields must agree" }
    }
}

private const val ACTIVE_STATUS = "ACTIVE"
private const val DRAFT_STATUS = "DRAFT"
private const val ACTIVE_SLOT = 1
private const val PERFORMED_OUTCOME = "PERFORMED"
private const val SKIPPED_OUTCOME = "SKIPPED"
