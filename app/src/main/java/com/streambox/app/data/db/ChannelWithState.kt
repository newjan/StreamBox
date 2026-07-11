package com.streambox.app.data.db

import androidx.room.Embedded

/** A channel row decorated with per-user state (favorite flag). */
data class ChannelWithState(
    @Embedded val channel: ChannelEntity,
    val isFavorite: Boolean,
)
