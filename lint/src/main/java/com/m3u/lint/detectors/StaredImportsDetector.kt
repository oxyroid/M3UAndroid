package com.m3u.lint.detectors

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UImportStatement
import java.util.EnumSet

class StaredImportsDetector : Detector(), SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UImportStatement::class.java)

    override fun createUastHandler(context: JavaContext) = object : UElementHandler() {
        override fun visitImportStatement(node: UImportStatement) {
            val reference = node.importReference ?: return
            if (node.isOnDemand) {
                context.report(
                    issue = StaredImportsIssue,
                    scope = reference,
                    location = context.getLocation(reference),
                    message = "Using special import instead of star-import."
                )
            }
        }
    }

    companion object {
        val StaredImportsIssue = Issue.create(
            id = "StaredImportsIssue",
            briefDescription = "using special imports instead",
            explanation = "using special imports instead of stared imports",
            category = Category.CORRECTNESS,
            severity = Severity.WARNING,
            implementation = Implementation(
                StaredImportsDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES)
            )
        )
    }
}
