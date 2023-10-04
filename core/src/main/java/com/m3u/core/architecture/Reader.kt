package com.m3u.core.architecture

import java.io.File

interface Reader<E> {
    fun read(): List<E>
}

interface FileReader : Reader<File>