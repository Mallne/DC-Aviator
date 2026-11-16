package cloud.mallne.dicentra.aviator.core.plugins

import cloud.mallne.dicentra.aviator.core.engine.EngineAuthorization
import cloud.mallne.dicentra.aviator.core.engine.EngineTemplate

/** Configures typed authorization once for a reusable template. */
fun <I : Any, O : Any> EngineTemplate<I, O>.withAuthorization(
    authorization: EngineAuthorization<I> = this.authorization,
    vararg plugins: EngineAuthorizationPlugin<I>,
): EngineTemplate<I, O> = object : EngineTemplate<I, O> by this {
    override val authorization: EngineAuthorization<I> = authorization
    override val authorizationPlugins: List<EngineAuthorizationPlugin<I>> =
        this@withAuthorization.authorizationPlugins + plugins
}

fun <I : Any, O : Any> EngineTemplate<I, O>.withAuthorizationPlugin(
    plugin: EngineAuthorizationPlugin<I>,
): EngineTemplate<I, O> = withAuthorization(plugins = arrayOf(plugin))
