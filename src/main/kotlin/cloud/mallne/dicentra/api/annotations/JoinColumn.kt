package cloud.mallne.dicentra.api.annotations

annotation class JoinColumn(
    val name: String,
    val nullable: Boolean = false,
    val referencedColumnName: String = ""
)
