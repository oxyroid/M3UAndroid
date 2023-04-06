package com.m3u.core.architecture.reader

import java.io.File

interface Reader<E> {
    fun read(): List<E>
}

interface FileReader : Reader<File>