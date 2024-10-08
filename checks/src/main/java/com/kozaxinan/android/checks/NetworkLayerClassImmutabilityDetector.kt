package com.kozaxinan.android.checks

import com.android.tools.lint.detector.api.Category.Companion.CORRECTNESS
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.ERROR
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import org.jetbrains.kotlin.asJava.elements.KtLightMember
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.getContainingUClass

/**
 * Check retrotif interface methods return type for immutability.
 */
@Suppress("UnstableApiUsage")
internal class NetworkLayerClassImmutabilityDetector : RetrofitReturnTypeDetector() {

    override fun createUastHandler(context: JavaContext) = NetworkLayerDtoFieldVisitor(context)

    class NetworkLayerDtoFieldVisitor(private val context: JavaContext) : Visitor(context) {

        override fun visitMethod(node: UMethod) {
            val fields = findAllFieldsOf(node)

            val nonFinalFields = fields.filterNot { it.hasModifierProperty(PsiModifier.FINAL) }
            if (nonFinalFields.isNotEmpty()) {
                val fieldsText =
                    nonFinalFields.map { "${it.name} in ${it.getContainingUClass()?.name}" }
                report(node, "Return type is not immutable. $fieldsText need to be final or val.")
            }

            val nonImmutableFields: List<UField> = fields
                .filter {
                    val kotlinUClass = it.getContainingUClass()
                    kotlinUClass != null && !kotlinUClass.isEnum
                }
                .filter {
                    it.nameContainsMutable() ||
                            (it.javaPsi as? KtLightMember<PsiMember>)?.kotlinTypeName()
                                ?.contains("Mutable") ?: false
                }
            if (nonImmutableFields.isNotEmpty()) {
                val fieldsText =
                    nonImmutableFields.map { "${it.name} in ${it.getContainingUClass()?.name}" }
                report(
                    node,
                    "Return type contains mutable class types. $fieldsText need to be immutable."
                )
            }
        }

        private fun UField.nameContainsMutable(): Boolean = try {
            text.contains("Mutable")
        } catch (exception: Exception) {
            false
        }

        private fun report(node: UMethod, message: String) {
            context.report(
                issue = ISSUE_NETWORK_LAYER_IMMUTABLE_CLASS_RULE,
                scopeClass = node,
                location = context.getNameLocation(node),
                message = message
            )
        }
    }

    companion object {

        val ISSUE_NETWORK_LAYER_IMMUTABLE_CLASS_RULE: Issue = Issue.create(
            id = "NetworkLayerImmutableClassRule",
            briefDescription = "Immutable network layer class",
            explanation = "Data classes used in network layer should be immutable by design.",
            category = CORRECTNESS,
            priority = 8,
            severity = ERROR,
            implementation = Implementation(
                NetworkLayerClassImmutabilityDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
