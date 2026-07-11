package com.streambox.app.data.db

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "programmes",
    primaryKeys = ["tvgId", "startMs"],
    indices = [Index("stopMs")],
)
data class ProgrammeEntity(
    val tvgId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
)
