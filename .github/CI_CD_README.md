# CI/CD Pipeline Documentation

This repository uses a comprehensive CI/CD pipeline to ensure code quality, security, and reliable deployments.

## 🚀 Workflow Overview

### Main Workflows

1. **Android CI/CD Pipeline** (`android.yml`) - Main build and deployment workflow
2. **Pull Request Validation** (`pr-validation.yml`) - Lightweight checks for PRs
3. **Security Analysis** (`security.yml`) - CodeQL security scanning and dependency review
4. **Dependency Updates** (`dependency-updates.yml`) - Automated dependency management
5. **Release Automation** (`release.yml`) - Automated release creation and publishing

## 📋 Workflow Details

### Android CI/CD Pipeline

**Triggers:**
- Push to `master` branch (excluding documentation files)
- Manual workflow dispatch with optional test skipping

**Jobs:**

#### 1. Code Quality & Security 🔍
- Gradle wrapper validation
- Lint checks with detailed reporting
- Security vulnerability scanning
- Dependency analysis

#### 2. Build & Test 🏗️
- **Matrix Strategy**: Builds both `smartphone` and `tv` variants
- Unit test execution (when enabled)
- Baseline profile generation for performance optimization
- Debug and Release APK compilation
- APK size reporting
- Test report and baseline profile artifact upload

#### 3. Deploy & Notify 🚀
- **Conditional**: Only runs on `master` branch (not PRs)
- Combines all build artifacts
- Uploads to Telegram channel (if configured)
- Long-term artifact retention (90 days)

#### 4. Build Summary 📊
- Generates comprehensive build status report
- Lists all generated artifacts
- Provides quick status overview

### Pull Request Validation

**Purpose**: Fast feedback for pull requests without full CI overhead

**Features:**
- Quick lint checks
- Code compilation validation
- Debug APK builds
- Automatic PR status comments
- Change analysis (Kotlin, Gradle, Workflow changes)

### Security Analysis

**Components:**
- **CodeQL Analysis**: Static security analysis for Java/Kotlin
- **Dependency Review**: Automated dependency vulnerability checking
- **Secret Scanning**: Trivy-based security scanning
- **SARIF Reports**: Security findings uploaded to GitHub Security tab

**Schedule**: Weekly security scans (Sundays at 3 AM UTC)

### Dependency Updates

**Features:**
- Weekly dependency update checks (Mondays at 9 AM UTC)
- Automated issue creation for available updates
- Security audit with vulnerability reporting
- Stable release filtering (excludes alpha/beta/rc versions)

**Manual Trigger**: Available for immediate dependency checks

### Release Automation

**Triggers:**
- Git tags matching `v*` pattern
- Manual dispatch with custom tag and pre-release options

**Process:**
1. Extract version from tag or input
2. Run final quality checks and linting
3. Generate optimized baseline profiles
4. Build signed release APKs (if signing configured)
5. Generate checksums for verification
6. Create GitHub release with artifacts
7. Upload APKs and checksums as release assets
8. Send notification to Telegram

## 🛠 Build Tools & Quality Checks

### Enabled Gradle Plugins

1. **Versions Plugin**: Dependency update tracking
2. **OWASP Dependency Check**: Security vulnerability scanning
3. **Detekt**: Static code analysis for Kotlin
4. **Spotless**: Code formatting and style enforcement

### Configuration Files

- **Detekt**: `config/detekt/detekt.yml` - Code analysis rules
- **CodeQL**: `.github/codeql/codeql-config.yml` - Security analysis configuration

## 📊 Artifacts & Reports

### Build Artifacts
- **APKs**: Debug and Release builds for smartphone and TV
- **Baseline Profiles**: Performance optimization profiles
- **Test Reports**: Unit test results and coverage

### Quality Reports
- **Lint Reports**: HTML and XML format lint analysis
- **Security Reports**: Dependency vulnerability analysis
- **Detekt Reports**: Static analysis findings
- **CodeQL Reports**: Security analysis results

## 🔧 Environment Configuration

### Required Secrets
- `BOT_TOKEN`: Telegram bot token for notifications
- `CHAT_ID`: Telegram chat ID for notifications
- `API_ID`: Telegram API ID
- `API_HASH`: Telegram API hash

### Optional Configuration
- APK signing keys for release builds
- Additional notification channels
- Custom deployment targets

## 📈 Performance Optimizations

### Caching Strategy
- **Gradle Build Cache**: Enabled with cleanup
- **Dependency Cache**: Shared across jobs with read-only mode for subsequent jobs
- **Dependency Graph**: Generated and submitted for GitHub Insights

### Resource Management
- **Timeouts**: All jobs have appropriate timeout limits
- **Concurrency**: Intelligent cancellation of outdated runs
- **Matrix Builds**: Parallel execution for different variants

### Build Optimizations
- **Configuration Cache**: Enabled for faster Gradle execution
- **Parallel Builds**: Optimized JVM settings
- **Incremental Builds**: Leverages Gradle's incremental compilation

## 🔒 Security Features

### Dependency Security
- Weekly vulnerability scans
- Critical vulnerability alerting
- Automated dependency review for PRs
- OWASP dependency checking

### Code Security
- CodeQL static analysis
- Secret scanning with Trivy
- Security findings integration with GitHub Security

### Access Control
- Workflow permissions follow principle of least privilege
- Separate jobs for different security levels
- Artifact access controls

## 📝 Maintenance

### Regular Tasks
- **Weekly**: Dependency updates check
- **Weekly**: Security vulnerability scan
- **Per PR**: Code quality validation
- **Per Release**: Full security audit

### Monitoring
- Build status summaries in GitHub Actions
- Security findings in GitHub Security tab
- Dependency insights in GitHub Insights
- Performance metrics through baseline profiles

## 🚨 Troubleshooting

### Common Issues
1. **Build Failures**: Check lint reports and compilation errors
2. **Security Alerts**: Review dependency-check reports
3. **Test Failures**: Examine test reports artifacts
4. **Dependency Issues**: Check dependency-updates workflow

### Debug Tips
- Enable debug logging with `--debug` in Gradle commands
- Check individual job logs for specific failures
- Review artifact uploads for detailed reports
- Use manual workflow dispatch for testing changes

## 🔄 Workflow Dependencies

```
android.yml (Main Pipeline)
├── code-quality (runs first)
├── build-and-test (depends on code-quality)
├── deploy-and-notify (depends on build-and-test, master only)
└── build-summary (runs after all, always)

pr-validation.yml (PR only)
├── pr-checks (validates PR changes)
└── changed-files (analyzes changes)

security.yml (Scheduled + PR)
├── analyze (CodeQL security analysis)
├── dependency-review (PR only)
└── secret-scan (security scanning)

dependency-updates.yml (Scheduled)
├── update-dependencies (Monday mornings)
└── security-audit (weekly security check)

release.yml (Tag-triggered)
├── build-release (creates release builds)
├── create-release (depends on build-release)
└── notify-release (depends on create-release)
```

This comprehensive CI/CD setup ensures code quality, security, and reliable deployments while providing fast feedback for development workflow.