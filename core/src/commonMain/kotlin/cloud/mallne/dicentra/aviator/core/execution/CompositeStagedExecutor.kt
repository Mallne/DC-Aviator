package cloud.mallne.dicentra.aviator.core.execution

import cloud.mallne.dicentra.aviator.core.plugins.PluginStagedExecutor
import kotlinx.serialization.Serializable

interface CompositeStagedExecutor<CTX : AviatorExecutionContext<in O, in B>, O : @Serializable Any, B : @Serializable Any> :
    StagedExecutor<CTX, O, B>, PluginStagedExecutor<CTX, O, B>