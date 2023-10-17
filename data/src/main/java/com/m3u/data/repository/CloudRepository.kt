package com.m3u.data.repository

import com.m3u.core.wrapper.Stored

interface CloudRepository<S : Stored<S, R>, R> : ReadWriteRepository<S, Int>

