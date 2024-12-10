package cloud.mallne.dicentra.aviator.core.helper

/**
 * Convert the given object to string with each line indented by 4 spaces
 * (except the first line).
 */
fun toIndentedString(o: Any?): String {
    if (o == null) {
        return "null"
    }
    return o.toString().replace("\n", "\n    ")
}