package com.m3u.symbol.visitor

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.writeTo

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class UDFViewModel

class UDFViewModelVisitor(
    private val environment: SymbolProcessorEnvironment
) : KSVisitorVoid() {
    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        val originalClassName = classDeclaration.simpleName.asString()
        val packageName = classDeclaration.containingFile!!.packageName.asString()
        val fileName = "_$originalClassName"
        FileSpec
            .builder(packageName, fileName)
            .addType(
                TypeSpec
                    .classBuilder(fileName)
                    .addModifiers(
                        KModifier.PUBLIC
                    )
                    .addProperty(
                        PropertySpec
                            .builder("hello", Int::class, KModifier.PUBLIC)
                            .initializer("%1L", 114514)
                            .build()
                    )
                    .build()
            )
            .build()
            .writeTo(
                codeGenerator = environment.codeGenerator,
                aggregating = false
            )
    }

    companion object {

    }
}