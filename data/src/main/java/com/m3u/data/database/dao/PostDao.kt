package com.m3u.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.m3u.data.database.entity.Post
import kotlinx.coroutines.flow.Flow

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(post: Post)

    @Delete
    suspend fun delete(post: Post)

    @Query("SELECT * FROM posts ORDER BY id")
    fun observeAll(): Flow<List<Post>>

    @Query("SELECT * FROM posts WHERE status = ${Post.STATUS_UNREAD} OR standard = -1 ORDER BY id")
    fun observeActivePosts(): Flow<List<Post>>

    @Query("UPDATE posts SET status = ${Post.STATUS_READ} WHERE id = :id")
    suspend fun read(id: Int)

    @Query("DELETE FROM posts")
    suspend fun clear()
}