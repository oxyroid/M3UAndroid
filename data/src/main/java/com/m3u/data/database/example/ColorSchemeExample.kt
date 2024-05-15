package com.m3u.data.database.example

import androidx.sqlite.db.SupportSQLiteDatabase
import com.m3u.data.database.model.ColorScheme

object ColorSchemeExample {
    val schemes = listOf(
        ColorScheme(0x5E6738, false, "avocado"),
        ColorScheme(0x5E6738, true, "mint"),
        ColorScheme(0xe69e71, false, "orange"),
        ColorScheme(0xe69e71, true, "leather"),
        ColorScheme(0xce5b73, false, "cherry"),
        ColorScheme(0xce5b73, true, "raspberry"),
    )

    operator fun invoke(
        db: SupportSQLiteDatabase,
        schemes: List<ColorScheme> = this.schemes
    ) {
        val values = schemes.joinToString(
            postfix = ";"
        ) {
            """('${it.argb}', '${if (it.isDark) 1 else 0}', '${it.name}')"""
        }

        db.execSQL(
            """
            INSERT INTO 'color_pack' ('argb', 'dark', 'name')
            VALUES
            $values
            """.trimIndent()
        )
    }
}