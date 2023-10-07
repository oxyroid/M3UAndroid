package com.m3u.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Issue
import com.m3u.lint.detectors.HelperDetector
import com.m3u.lint.detectors.StaredImportsDetector

class M3UIssueRegistry : IssueRegistry() {
    override val issues: List<Issue> = listOf(
        HelperDetector.UseHelperIssue,
        StaredImportsDetector.StaredImportsIssue
    )
    override val vendor: Vendor = Vendor(
        vendorName = "M3UAndroid Project",
        feedbackUrl = "https://t.me/m3u_android_chat",
        contact = "https://t.me/sortBy"
    )
}
