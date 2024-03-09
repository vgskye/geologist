package vg.skye.geologist

interface DatabaseHolder {
    fun `geologist$getDatabase`(): Database
}

interface NeedsConfiguration {
    fun `geologist$setDatabaseAndNamespace`(database: Database, namespace: ByteArray)
}