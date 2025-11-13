package cloud.mallne.dicentra.aviator.model

import cloud.mallne.dicentra.polyfill.Validation
import kotlinx.serialization.Serializable

@Serializable
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String? = null,
    val buildmetadata: String? = null,
) : Comparable<SemVer> {
    init {
        prerelease?.ifEmpty {
            throw IllegalArgumentException("prerelease can not be empty, use null")
        }
        buildmetadata?.ifEmpty {
            throw IllegalArgumentException("buildmetadata can not be empty, use null")
        }
    }

    operator fun inc(): SemVer = copy(patch = patch + 1)
    operator fun contains(other: SemVer): Boolean {
        if (other.major > major) return false
        if (other.minor > minor) return false
        if (other.patch > patch) return false
        if (other.prerelease != null && other.prerelease != prerelease) return false
        if (other.buildmetadata != null && other.buildmetadata != buildmetadata) return false
        return true
    }

    override operator fun compareTo(other: SemVer): Int {
        val cmpMa = this.major.compareTo(other.major)
        val cmpMi = this.minor.compareTo(other.minor)
        val cmpP = this.patch.compareTo(other.patch)
        val suffixSb = StringBuilder()
        if (prerelease != null) {
            suffixSb.append(prerelease)
        }
        if (buildmetadata != null) {
            suffixSb.append("+")
            suffixSb.append(buildmetadata)
        }
        val suffix = suffixSb.toString()
        val suffixSbO = StringBuilder()
        if (other.prerelease != null) {
            suffixSbO.append(other.prerelease)
        }
        if (other.buildmetadata != null) {
            suffixSbO.append("+")
            suffixSbO.append(other.buildmetadata)
        }
        val suffixO = suffixSbO.toString()
        val cmpS = suffix.compareTo(suffixO)

        if (cmpMa != 0) return cmpMa
        if (cmpMi != 0) return cmpMi
        if (cmpP != 0) return cmpP
        //SpecialCase: patch == o.patch but prerealease == null && o.prerelease != null then this is newer than o
        if (Validation.Null.one(prerelease, other.prerelease)) return cmpS * -1
        return cmpS
    }

    override operator fun equals(other: Any?): Boolean {
        if (other !is SemVer) return false
        if (other.major != major) return false
        if (other.minor != minor) return false
        if (other.patch != patch) return false
        if (other.prerelease != null && other.prerelease != prerelease) return false
        if (other.buildmetadata != null && other.buildmetadata != buildmetadata) return false
        return true
    }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("$major.$minor.$patch")
        if (prerelease != null) {
            sb.append("-$prerelease")
        }
        if (buildmetadata != null) {
            sb.append("+$buildmetadata")
        }
        return sb.toString()
    }

    companion object {
        operator fun invoke(value: String): SemVer? = constructSemVer(value)
        fun constructSemVer(value: String): SemVer? {
            val regex =
                Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$")
            val match = regex.find(value)
            if (match != null) {
                val major = match.groups[1]?.value?.toIntOrNull()
                val minor = match.groups[2]?.value?.toIntOrNull()
                val patch = match.groups[3]?.value?.toIntOrNull()
                val pre = match.groups[4]?.value?.ifEmpty { null }
                val meta = match.groups[5]?.value?.ifEmpty { null }

                if (major != null && minor != null && patch != null) {
                    return SemVer(major, minor, patch, pre, meta)
                }
            }
            return null
        }
    }

    override fun hashCode(): Int {
        var result = major
        result = 31 * result + minor
        result = 31 * result + patch
        result = 31 * result + (prerelease?.hashCode() ?: 0)
        result = 31 * result + (buildmetadata?.hashCode() ?: 0)
        return result
    }
}
