package com.awd.teledrive.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GitHubReleaseAsset(
    val name: String = "",
    val browser_download_url: String = "",
    val size: Long = 0L
)

@Serializable
data class GitHubRelease(
    val tag_name: String = "",
    val name: String = "",
    val body: String = "",
    val html_url: String = "",
    val prerelease: Boolean = false,
    val assets: List<GitHubReleaseAsset> = emptyList()
)
