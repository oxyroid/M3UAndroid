package com.m3u.data.database.example

import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.model.ColorPack

object ColorPackExample {
    private val packs = listOf(
        ColorPack(0x5E6738, false, "avocado"),
        ColorPack(0x5E6738, true, "mint"),
        ColorPack(0xe69e71, false, "orange"),
        ColorPack(0xe69e71, true, "leather"),
        ColorPack(0xce5b73, false, "cherry"),
        ColorPack(0xce5b73, true, "raspberry"),
    )

    operator fun invoke(db: SupportSQLiteDatabase) {
        val values = packs.joinToString(
            postfix = ";",
            transform = {
                """('${it.argb}', '${if (it.isDark) 1 else 0}', '${it.name}')"""
            }
        )

        db.execSQL(
            """
            INSERT INTO 'color_pack' ('argb', 'dark', 'name')
            VALUES
            $values
            """.trimIndent()
        )
    }
}