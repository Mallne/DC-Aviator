package cloud.mallne.dicentra.aviator.core

import cloud.mallne.dicentra.aviator.model.Info
import cloud.mallne.dicentra.aviator.model.License
import cloud.mallne.dicentra.aviator.model.PathItem
import cloud.mallne.dicentra.aviator.model.Server

interface IAviatorApi {
    val `x-dicentra-aviator`: String // Required: Aviator version (e.g., "1.0.1")
    val info: Info // Required: Metadata about the API
    val server: Server // server where the API is hosted
    val paths: Map<String, PathItem> // Available paths and operations

    fun getAvailableLicenses(): List<License> {
        val licenses = mutableListOf<License>()
        info.license?.let { licenses.add(it) }
        paths.forEach { (_, path) ->
            path.info?.license?.let { licenses.add(it) }
        }
        return licenses
    }
}