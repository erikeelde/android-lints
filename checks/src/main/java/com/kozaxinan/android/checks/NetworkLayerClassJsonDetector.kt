package com.kozaxinan.android.checks

import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.INFORMATIONAL
import com.intellij.lang.jvm.JvmParameter
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UAnnotated
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass

/**
 * Check retrofit interface methods return type for JsonName and Moshi's Json/JsonClass annotation.
 */
@Suppress("UnstableApiUsage")
internal class NetworkLayerClassJsonDetector : RetrofitReturnTypeDetector() {

    override fun createUastHandler(context: JavaContext) = NetworkLayerDtoFieldVisitor(context)

    class NetworkLayerDtoFieldVisitor(private val context: JavaContext) : Visitor(context) {
        override fun visitMethod(node: UMethod) {
            val allFields: Set<UField> =
                findAllFieldsOf(node)
                    .filterNot { !it.isStatic && it.getContainingUClass()?.isEnum == true }
                    .toSet()

            val classes: Set<PsiClass> = allFields
                .mapNotNull { it.getContainingUClass() }
                .toSet()

            val checkedFields: MutableSet<String?> = allFields
                .filterNot(::hasJsonNameAnnotation)
                .map { it.name }
                .toMutableSet()

            if (checkedFields.isNotEmpty()) {
                val constructorParamsWithJson: MutableSet<String> = mutableSetOf()

                val constructorParameter: List<JvmParameter> = classes
                    .mapNotNull { it.constructors.firstOrNull()?.parameters }
                    .fold(mutableListOf()) { acc, arrayOfJvmParameters ->
                        acc.apply {
                            addAll(arrayOfJvmParameters)
                        }
                    }

                constructorParameter.forEach { parameter ->
                    val name = parameter.name
                    val hasAnnotation = parameter.annotations.any { annotation ->
                        annotation
                            .qualifiedName
                            ?.endsWith("Json") == true
                    }
                    if (name != null && hasAnnotation) {
                        constructorParamsWithJson.add(name)
                    }
                }
                checkedFields -= constructorParamsWithJson
            }
            if (checkedFields.isNotEmpty()) {
                context.report(
                    issue = ISSUE_NETWORK_LAYER_CLASS_JSON_RULE,
                    scopeClass = node,
                    location = context.getNameLocation(node),
                    message = "Return type doesn't have `@Json` annotation for $checkedFields fields"
                )
            }

            val checkedClasses =
                classes.filterNot { hasJsonClassAnnotation(it.annotations) }.map { it.name }

            if (checkedClasses.isNotEmpty()) {
                context.report(
                    issue = ISSUE_NETWORK_LAYER_CLASS_JSON_CLASS_RULE,
                    scopeClass = node,
                    location = context.getNameLocation(node),
                    message = "Return type doesn't have `@JsonClass` annotation for $checkedClasses classes"
                )
            }

            val bodyParameters = findAllBodyParametersOf(node)

            bodyParameters.forEach { bodyParameter ->
                val bodyParameterType = bodyParameter.type
                if (bodyParameterType is PsiClassType) {
                    val innerFields = findAllInnerFields(bodyParameterType)
                        .filterNot { !it.isStatic && it.getContainingUClass()?.isEnum == true }
                        .mapNotNull { it.getContainingUClass() }
                        .filterNot { hasJsonClassAnnotation(it.annotations) }

                    if (innerFields.isNotEmpty()) {
                        context.report(
                            issue = ISSUE_NETWORK_LAYER_CLASS_JSON_CLASS_BODY_RULE,
                            scopeClass = node,
                            location = context.getNameLocation(bodyParameter),
                            message = "Body parameter doesn't have `@JsonClass` annotation for ${innerFields.map { it.name }} classes"
                        )
                    }
                }
            }
        }

        private fun hasJsonNameAnnotation(field: UField): Boolean {
            return context
                .evaluator
                .getAllAnnotations(field as UAnnotated, true)
                .mapNotNull { uAnnotation -> uAnnotation.qualifiedName }
                .any { it.endsWith("Json") }
        }

        private fun hasJsonClassAnnotation(annotations: Array<PsiAnnotation>): Boolean {
            return annotations
                .mapNotNull { uAnnotation -> uAnnotation.qualifiedName }
                .any { it.endsWith("JsonClass") }
        }
    }

    companion object {

        val ISSUE_NETWORK_LAYER_CLASS_JSON_RULE: Issue = Issue.create(
            id = "NetworkLayerClassJsonRule",
            briefDescription = "Json annotated network layer class",
            explanation = "Data classes used in network layer should use Json annotation for Moshi. Adding annotation prevents obfuscation errors.",
            category = CORRECTNESS,
            priority = 5,
            severity = INFORMATIONAL,
            implementation = Implementation(
                NetworkLayerClassJsonDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        val ISSUE_NETWORK_LAYER_CLASS_JSON_CLASS_RULE: Issue = Issue.create(
            id = "NetworkLayerClassJsonClassRule",
            briefDescription = "Json annotated network layer class",
            explanation = "Data classes used in network layer should use `@JsonClass` annotation for Moshi. Adding annotation prevents obfuscation errors.",
            category = CORRECTNESS,
            priority = 5,
            severity = INFORMATIONAL,
            implementation = Implementation(
                NetworkLayerClassJsonDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
        val ISSUE_NETWORK_LAYER_CLASS_JSON_CLASS_BODY_RULE: Issue = Issue.create(
            id = "NetworkLayerBodyClassJsonClassRule",
            briefDescription = "Json annotated network layer class",
            explanation = "Data classes used in network layer should use `@JsonClass` annotation for Moshi. Adding annotation prevents obfuscation errors.",
            category = CORRECTNESS,
            priority = 5,
            severity = INFORMATIONAL,
            implementation = Implementation(
                NetworkLayerClassJsonDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
