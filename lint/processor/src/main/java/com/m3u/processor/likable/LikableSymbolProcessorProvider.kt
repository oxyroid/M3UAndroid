package com.m3u.processor.likable

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class LikableSymbolProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return LikableSymbolProcessor(
            logger = environment.logger,
            codeGenerator = environment.codeGenerator
        )
    }
}
