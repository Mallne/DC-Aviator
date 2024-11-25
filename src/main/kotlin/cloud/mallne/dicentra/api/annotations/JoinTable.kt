package cloud.mallne.dicentra.api.annotations

annotation class JoinTable(
    val name: String,
    val joinColumns: Array<JoinColumn>,
    val inverseJoinColumns: Array<JoinColumn>
)
