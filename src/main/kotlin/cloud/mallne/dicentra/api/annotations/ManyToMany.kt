package cloud.mallne.dicentra.api.annotations

@Deprecated("can be replaced with dependency", replaceWith = ReplaceWith("io.github.joselion.springr2dbcrelationships.annotations.ManyToMany"))
annotation class ManyToMany(
    val mappedBy: String = "",
)
