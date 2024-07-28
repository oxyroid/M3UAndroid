package com.m3u.processor.likable

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.m3u.annotation.Exclude
import com.m3u.annotation.Likable
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.withIndent

class LikableSymbolProcessor(
    private val logger: KSPLogger,
    private val codeGenerator: CodeGenerator
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val likableAnnotationName = requireNotNull(Likable::class.qualifiedName)
        val symbols = resolver.getSymbolsWithAnnotation(likableAnnotationName)

        val unableToProcess = symbols.filterNot { it.validate() }
        symbols
            .filter { it is KSClassDeclaration }
            .forEach { symbol ->
                symbol.accept(Visitor(), Unit)
            }
        return unableToProcess.toList()
    }

    private inner class Visitor : KSVisitorVoid() {
        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val isDataClass = Modifier.DATA in classDeclaration.modifiers
            if (!isDataClass) {
                logger.error("@Likeable can only target data class.", classDeclaration)
                return
            }
            val packageName = classDeclaration.packageName.asString()
            val fileName = classDeclaration.simpleName.asString() + "Likeable"
            val properties = classDeclaration
                .getDeclaredProperties()
                .filterNot { property -> property.isAnnotationPresent(Exclude::class) }
                .toList()
            val typeName = classDeclaration.asType(emptyList()).toTypeName()
            val fileSpec = FileSpec.builder(packageName, fileName)
                .addFunction(
                    FunSpec.builder("like")
                        .receiver(typeName)
                        .addModifiers(KModifier.INFIX)
                        .addParameter(
                            ParameterSpec.builder("another", typeName).build()
                        )
                        .returns(Boolean::class)
                        .addCode(
                            CodeBlock.builder().apply {
                                add("return ")
                                properties.forEachIndexed { index, property ->
                                    add("this.${property} == another.${property}")
                                    if (index != properties.lastIndex) {
                                        add(" && \n")
                                    }
                                }
                            }
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("unlike")
                        .addModifiers(KModifier.INFIX)
                        .receiver(typeName)
                        .addParameter("another", typeName)
                        .returns(Boolean::class)
                        .addCode("return this.like(another).not()")
                        .build()
                )
                .addFunction(
                    FunSpec.builder("belong")
                        .addModifiers(KModifier.INFIX)
                        .receiver(typeName)
                        .addParameter(
                            "collection",
                            ClassName("kotlin.collections", "Collection").parameterizedBy(typeName)
                        )
                        .returns(Boolean::class)
                        .addCode(
                            CodeBlock.builder()
                                .add("return collection.any {\n")
                                .withIndent { add("it like this\n") }
                                .add("}")
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("notbelong")
                        .addModifiers(KModifier.INFIX)
                        .receiver(typeName)
                        .addParameter(
                            "collection",
                            ClassName("kotlin.collections", "Collection").parameterizedBy(typeName)
                        )
                        .returns(Boolean::class)
                        .addCode(
                            CodeBlock.builder()
                                .beginControlFlow("return collection.all")
                                .withIndent { add("it unlike this") }
                                .endControlFlow()
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("hold")
                        .addModifiers(KModifier.INFIX)
                        .receiver(
                            ClassName("kotlin.collections", "Collection").parameterizedBy(typeName)
                        )
                        .addParameter("element", typeName)
                        .returns(Boolean::class)
                        .addCode(
                            CodeBlock.builder()
                                .add("return this.any {\n")
                                .withIndent { add("it like element\n") }
                                .add("}")
                                .build()
                        )
                        .build()
                )
                .addFunction(
                    FunSpec.builder("nothold")
                        .addModifiers(KModifier.INFIX)
                        .receiver(
                            ClassName("kotlin.collections", "Collection").parameterizedBy(typeName)
                        )
                        .addParameter("element", typeName)
                        .returns(Boolean::class)
                        .addCode(
                            CodeBlock.builder()
                                .add("return this.all {\n")
                                .withIndent { add("it unlike element\n") }
                                .add("}")
                                .build()
                        )
                        .build()
                )
                .build()
            fileSpec.writeTo(codeGenerator, false)
        }
    }
}