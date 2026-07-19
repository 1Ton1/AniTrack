package com.darren.anitrackpulse.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromAnimeStatus(value: AnimeStatus): String = value.name

    @TypeConverter
    fun toAnimeStatus(value: String): AnimeStatus = AnimeStatus.valueOf(value)
}
