package com.m3u.lint.helper

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.Issue
import com.m3u.lint.helper.UseHelperInsteadOfWindowInsetsDetector.Companion.UseHelperIssue

class UseHelperInsteadOfWindowInsetsIssueRegistry : IssueRegistry() {
    override val issues: List<Issue>
        get() = listOf(UseHelperIssue)
    override val vendor: Vendor = Vendor(
        vendorName = "M3UAndroid Project",
        feedbackUrl = "https://t.me/m3u_android_chat",
        contact = "https://t.me/sortBy"
    )
}
