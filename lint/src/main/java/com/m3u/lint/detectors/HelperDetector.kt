package com.m3u.lint.detectors

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.intellij.psi.PsiMethod
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.getContainingUMethod

class HelperDetector : Detector(), SourceCodeScanner {
    override fun getApplicableMethodNames(): List<String> = listOf("show", "hide")

    override fun visitMethodCall(context: JavaContext, node: UCallExpression, method: PsiMethod) {
        val evaluator = context.evaluator
        val marked =
            node.getContainingUMethod()
                ?.uAnnotations
                ?.find { it.qualifiedName == ANNOTATION_NAME } != null
        if (marked) return
        if (evaluator.isMemberInClass(method, "androidx.core.view.WindowInsetsControllerCompat")) {
            val isStatusBars = node.getArgumentForParameter(0)?.evaluate() == 1
            val visible = node.methodName == "show"
            reportUsage(
                context = context,
                node = node,
                isStatusBars = isStatusBars,
                visible = visible
            )
        }
    }

    private fun reportUsage(
        context: JavaContext,
        node: UCallExpression,
        isStatusBars: Boolean,
        visible: Boolean
    ) {
        val property = if (isStatusBars) "status" else "navigation"
        val value = if (visible) "true" else "false"
        val expression = "helper.${property}BarsVisibility = ${value}.u"
        val data = LintFix.LintFixGroup(
            displayName = "display",
            familyName = "family",
            type = LintFix.GroupType.ALTERNATIVES,
            fixes = listOf(
                LintFix.create()
                    .name("Replace with $expression")
                    .replace()
                    .imports(
                        "com.m3u.core.unspecified.u",
                        "com.m3u.ui.model.Helper"
                    )
                    .with(expression)
                    .autoFix(robot = true, independent = true)
                    .build(),
                LintFix.create()
                    .name("Marked with @$CHILD_ANNOTATION_NAME annotation")
                    .annotate("@$ANNOTATION_NAME")
                    .autoFix(robot = true, independent = false)
                    .build()
            )
        )

        context.report(
            issue = UseHelperIssue,
            location = context.getLocation(node),
            message = EXPLANATION,
            quickfixData = data
        )
    }

    companion object {
        private const val CHILD_ANNOTATION_NAME = "WindowInsetsAllowed"
        private const val ANNOTATION_NAME = "com.m3u.ui.model.Helper.${CHILD_ANNOTATION_NAME}"
        private const val BRIEF_DESCRIPTION = "Helper instead of WindowInsetsControllerCompat"
        val EXPLANATION = """
            Usage of WindowInsetsControllerCompat to control the system bars is not allowed in this project.
            ```kotlin
            // Get helper instance in composable scope
            @Composable
            fun Screen() {
                val helper = LocalHelper.current
            }
            // then use helper to control system bars visibility
            helper.statusBarsVisibility = true.u
            ```
        """.trimIndent()
        val UseHelperIssue = Issue.create(
            id = "UseHelperIssue",
            briefDescription = BRIEF_DESCRIPTION,
            explanation = EXPLANATION,
            category = Category.CORRECTNESS,
            severity = Severity.FATAL,
            implementation = Implementation(
                HelperDetector::class.java,
                Scope.JAVA_FILE_SCOPE
            )
        )
    }
}
