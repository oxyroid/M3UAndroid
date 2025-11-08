package com.m3u.data.security

import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ENTERPRISE FEATURE: Checksum Validator
 *
 * Validates data integrity using cryptographic checksums.
 * Useful for verifying downloaded files, playlist integrity, etc.
 */
@Singleton
class ChecksumValidator @Inject constructor() {

    private val timber = Timber.tag("ChecksumValidator")

    /**
     * Calculate SHA-256 checksum of a byte array
     *
     * @param data The data to checksum
     * @return Hexadecimal string representation of the checksum
     */
    fun calculateSHA256(data: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(data)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            timber.e(e, "Failed to calculate SHA-256 checksum")
            ""
        }
    }

    /**
     * Calculate SHA-256 checksum of a string
     *
     * @param data The string to checksum
     * @return Hexadecimal string representation of the checksum
     */
    fun calculateSHA256(data: String): String {
        return calculateSHA256(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * Validate that data matches an expected checksum
     *
     * @param data The data to validate
     * @param expectedChecksum The expected SHA-256 checksum (hex string)
     * @return true if checksums match, false otherwise
     */
    fun validate(data: ByteArray, expectedChecksum: String): Boolean {
        val actualChecksum = calculateSHA256(data)
        val isValid = actualChecksum.equals(expectedChecksum, ignoreCase = true)

        if (!isValid) {
            timber.w("Checksum validation failed")
            timber.w("Expected: $expectedChecksum")
            timber.w("Actual: $actualChecksum")
        }

        return isValid
    }

    /**
     * Validate that a string matches an expected checksum
     *
     * @param data The string to validate
     * @param expectedChecksum The expected SHA-256 checksum (hex string)
     * @return true if checksums match, false otherwise
     */
    fun validate(data: String, expectedChecksum: String): Boolean {
        return validate(data.toByteArray(Charsets.UTF_8), expectedChecksum)
    }

    /**
     * Calculate MD5 checksum (for legacy compatibility)
     *
     * @param data The data to checksum
     * @return Hexadecimal string representation of the MD5 checksum
     */
    @Suppress("DEPRECATION")
    fun calculateMD5(data: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hashBytes = digest.digest(data)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            timber.e(e, "Failed to calculate MD5 checksum")
            ""
        }
    }

    /**
     * Verification result for backup integrity checks
     */
    data class VerificationResult(
        val success: Boolean,
        val expectedChecksum: String?,
        val actualChecksum: String?,
        val message: String = ""
    ) {
        fun isCorrupted(): Boolean {
            return expectedChecksum != null && actualChecksum != null &&
                   !expectedChecksum.equals(actualChecksum, ignoreCase = true)
        }
    }

    /**
     * Save checksum metadata for a file
     *
     * @param file The file to create checksum metadata for
     * @param checksum The checksum to save
     * @return true if metadata was saved successfully
     */
    fun saveChecksumMetadata(file: java.io.File, checksum: String): Boolean {
        return try {
            val metadataFile = java.io.File(file.parentFile, "${file.name}.checksum")
            metadataFile.writeText(checksum)
            timber.d("Saved checksum metadata: ${metadataFile.absolutePath}")
            true
        } catch (e: Exception) {
            timber.e(e, "Failed to save checksum metadata")
            false
        }
    }

    /**
     * Verify backup file integrity using checksum metadata if available
     *
     * @param file The backup file to verify
     * @return VerificationResult with details about the verification
     */
    fun verifyBackupIntegrity(file: java.io.File): VerificationResult {
        return try {
            val metadataFile = java.io.File(file.parentFile, "${file.name}.checksum")

            if (!metadataFile.exists()) {
                timber.d("No checksum metadata found for ${file.name}")
                return VerificationResult(
                    success = false,
                    expectedChecksum = null,
                    actualChecksum = null,
                    message = "No checksum metadata available"
                )
            }

            val expectedChecksum = metadataFile.readText().trim()
            val actualChecksum = calculateSHA256(file.readBytes())

            val isValid = expectedChecksum.equals(actualChecksum, ignoreCase = true)

            VerificationResult(
                success = isValid,
                expectedChecksum = expectedChecksum,
                actualChecksum = actualChecksum,
                message = if (isValid) "Checksum verification passed" else "Checksum mismatch detected"
            )
        } catch (e: Exception) {
            timber.e(e, "Failed to verify backup integrity")
            VerificationResult(
                success = false,
                expectedChecksum = null,
                actualChecksum = null,
                message = "Verification failed: ${e.message}"
            )
        }
    }
}
