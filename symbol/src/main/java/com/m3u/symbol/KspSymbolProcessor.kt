package com.m3u.symbol

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.m3u.symbol.visitor.UDFViewModel
import com.m3u.symbol.visitor.UDFViewModelVisitor

class KspSymbolProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    private val visitor = UDFViewModelVisitor(environment)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(UDFViewModel::class.qualifiedName!!)
        val deferred = symbols.toMutableList()
        symbols
            .filterIsInstance<KSClassDeclaration>()
            .onEach { symbol ->
                if (symbol.validate()) {
                    symbol.accept(visitor, Unit)
                    deferred -= symbol
                }
            }
        return deferred
    }
}
