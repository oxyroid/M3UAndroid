package com.m3u.processor.likable

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import com.m3u.annotation.MyDataClass
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import com.squareup.kotlinpoet.withIndent

class MyDataClassSymbolProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val myDataClassName = requireNotNull(MyDataClass::class.qualifiedName)
        val symbols = resolver.getSymbolsWithAnnotation(myDataClassName)
        val unableToProcess = symbols.filterNot { it.validate() }
        symbols
            .filter { it is KSClassDeclaration }
            .forEach { it.accept(Visitor(), Unit) }
        return unableToProcess.toList()
    }

    private inner class Visitor : KSVisitorVoid() {
        @OptIn(KspExperimental::class)
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val packageName = classDeclaration.packageName.asString()
            val fileName = classDeclaration.simpleName.asString() + "_MyDataClass"
            val typeName = classDeclaration.asType(emptyList()).toTypeName()
            val myDataClassAnnotation =
                classDeclaration.getAnnotationsByType(MyDataClass::class).first()
            val functionName = myDataClassAnnotation.name
            val isDataClass = Modifier.DATA in classDeclaration.modifiers
            if (isDataClass && functionName == "copy") {
                logger.error(
                    "@MyDataClass shouldn't target data class with \"copy\" name.",
                    classDeclaration
                )
                return
            }
            val jvmOverload = myDataClassAnnotation.jvmOverload
            val params = classDeclaration
                .primaryConstructor
                ?.parameters
                ?.mapNotNull { it.asParam() }
                .orEmpty()
            FileSpec.builder(packageName, fileName)
                .addFunction(
                    FunSpec.builder(functionName)
                        .receiver(typeName)
                        .addParameters(
                            params.map {
                                ParameterSpec.builder(it.name, it.type)
                                    .defaultValue("this.${it.name}")
                                    .build()
                            }
                        )
                        .returns(typeName)
                        .apply {
                            if (jvmOverload) addAnnotation(JvmOverloads::class)
                        }
                        .addCode(
                            CodeBlock.builder()
                                .add("return %T(\n", typeName)
                                .withIndent {
                                    params.forEachIndexed { index, param ->
                                        val key = param.name
                                        add("$key = $key")
                                        if (index != params.lastIndex) {
                                            add(",")
                                        }
                                        add("\n")
                                    }
                                }
                                .add(")")
                                .build()
                        )
                        .build()
                )
                .build()
                .writeTo(codeGenerator, false)
        }
    }
}

private data class Param(
    val name: String,
    val type: TypeName,
)

private fun KSValueParameter.asParam(): Param? {
    return Param(
        name?.asString() ?: return null,
        type.toTypeName()
    )
}
