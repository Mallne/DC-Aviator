package cloud.mallne.dicentra.api.annotations

@Deprecated("can be replaced with dependency", replaceWith = ReplaceWith("io.github.joselion.springr2dbcrelationships.annotations.OneToMany"))
annotation class OneToMany(
    val mappedBy: String = "",
)
