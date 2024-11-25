package cloud.mallne.dicentra.api.license

enum class LicenseType {
    TRIAL, // Will expire after a certain amount of time
    SUBSCRIPTION, // Will expire after a certain amount of time, but renews automatically if not cancelled
    ADMIN, // Will never expire, but the license is managed completely by warden
    FLOATING // Is a Children of a Subscription, often this is used for a company license
}