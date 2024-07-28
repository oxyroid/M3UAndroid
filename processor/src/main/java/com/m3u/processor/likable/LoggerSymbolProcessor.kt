package com.m3u.processor.likable

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.validate
import com.m3u.annotation.Logger
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class LoggerSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val loggerGeneratorAnnotationName = requireNotNull(Logger.Generator::class.qualifiedName)
        val symbols = resolver.getSymbolsWithAnnotation(loggerGeneratorAnnotationName)
        val unableToProcess = symbols.filterNot { it.validate() }.toList()
        symbols
            .filter { it is KSClassDeclaration }
            .forEach { symbol ->
                symbol.accept(Visitor(), Unit)
            }
        return unableToProcess
    }

    private inner class Visitor : KSVisitorVoid() {
        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val packageName = classDeclaration.packageName.asString()
            val fileName = classDeclaration.simpleName.asString() + "Logger"
            val typeName = classDeclaration.asType(emptyList()).toTypeName()
            val simpleName = classDeclaration.simpleName.asString()
            if (!classDeclaration.isCompanionObject) {
                logger.error("@Logger.Generator can only target companion object.")
                return
            }
            val propertyName = classDeclaration
                .getAnnotationsByType(Logger.Generator::class)
                .firstOrNull()
                ?.name
                ?: "logger"
            FileSpec.builder(packageName, fileName)
                .addProperty(
                    PropertySpec.builder(
                        name = propertyName,
                        type = Logger::class
                    )
                        .addModifiers(KModifier.INTERNAL)
                        .receiver(typeName)
                        .delegate(
                            CodeBlock.builder()
                                .beginControlFlow(
                                    "lazy(mode = %T.SYNCHRONIZED)",
                                    LazyThreadSafetyMode::class
                                )
                                .beginControlFlow("%T", Logger::class)
                                .add("println(%S + \": \" + it?.toString().orEmpty())", simpleName)
                                .endControlFlow()
                                .endControlFlow()
                                .build()
                        )
                        .build()
                )
                .build()
                .writeTo(codeGenerator, false)
        }
    }
}