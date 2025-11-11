package cloud.mallne.dicentra.aviator.core.io.adapter

import cloud.mallne.dicentra.aviator.core.io.adapter.request.FormAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.request.NoBodyAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.request.TextAdapter
import cloud.mallne.dicentra.aviator.core.io.adapter.response.TextResponseAdapter

object CommonAdapter {
    val adapters = listOf(TextAdapter, FormAdapter, NoBodyAdapter)
    val deserializers = listOf(TextResponseAdapter)
}