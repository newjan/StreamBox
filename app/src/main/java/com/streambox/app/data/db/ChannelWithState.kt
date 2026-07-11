package com.streambox.app.data.db

import androidx.room.Embedded

/** A channel row decorated with per-user state (favorite flag, health). */
data class ChannelWithState(
    @Embedded val channel: ChannelEntity,
    val isFavorite: Boolean,
    /** [HealthStatus] value; null when the stream was never checked. */
    val healthStatus: Int? = null,
)
