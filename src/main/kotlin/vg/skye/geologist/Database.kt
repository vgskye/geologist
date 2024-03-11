package vg.skye.geologist

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import org.rocksdb.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

data class DatabaseKey(
    val namespace: ByteArray,
    val key: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DatabaseKey

        if (!namespace.contentEquals(other.namespace)) return false
        if (!key.contentEquals(other.key)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = namespace.contentHashCode()
        result = 31 * result + key.contentHashCode()
        return result
    }
}

class Database(path: Path): AutoCloseable {
    companion object {
        init {
            RocksDB.loadLibrary()
        }
        @JvmField
        val LOGGER: Logger = LoggerFactory.getLogger("geologist")
        @JvmStatic
        fun deleteDatabase(path: Path) {
            Options().use {
                RocksDB.destroyDB(path.toString(), it)
            }
        }
    }

    private data class SaneByteArray(
        val inner: ByteArray
    ) {
        override fun hashCode(): Int = inner.contentHashCode()
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as SaneByteArray

            return inner.contentEquals(other.inner)
        }
    }
    private val dbOptions: DBOptions = DBOptions()
        .setCreateIfMissing(true)
        .setIncreaseParallelism(6)
        .setBytesPerSync(1048576)
    // TODO: the numbers here are out the wazoo. do some benchmarking
    private val compressionOptions: CompressionOptions = CompressionOptions()
        .setZStdMaxTrainBytes(8 * 1024)
        .setMaxDictBytes(1024)
        .setLevel(6)
        .setEnabled(true)
    private val filter: Filter = BloomFilter(10.0, false)
    private val tableFormatConfig: TableFormatConfig = BlockBasedTableConfig()
        .setBlockSize(16 * 1024)
        .setCacheIndexAndFilterBlocks(true)
        .setPinL0FilterAndIndexBlocksInCache(true)
        .setFormatVersion(5)
        .setFilterPolicy(filter)
        .setOptimizeFiltersForMemory(true)
    private val columnFamilyOptions: ColumnFamilyOptions = ColumnFamilyOptions()
        .setLevelCompactionDynamicLevelBytes(true)
        .setCompressionType(CompressionType.LZ4_COMPRESSION)
        .setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION)
        .setBottommostCompressionOptions(compressionOptions)
        .setTableFormatConfig(tableFormatConfig)
        .setCompactionPriority(CompactionPriority.MinOverlappingRatio)
    private val columnFamilies: ConcurrentMap<SaneByteArray, ColumnFamilyHandle> = ConcurrentHashMap()
    private val database: RocksDB
    init {
        var families = RocksDB
            .listColumnFamilies(Options(dbOptions, columnFamilyOptions), path.toString())
            .map {
                ColumnFamilyDescriptor(it, columnFamilyOptions)
            }
        if (families.isEmpty()) {
            families = listOf(
                ColumnFamilyDescriptor(RocksDB.DEFAULT_COLUMN_FAMILY, columnFamilyOptions)
            )
        }
        LOGGER.info("Found column families: {}", families.map { String(it.name) })
        val handles: MutableList<ColumnFamilyHandle> = ArrayList(families.size)
        database = RocksDB.open(
            dbOptions,
            path.toString(),
            families,
            handles
        )
        columnFamilies.putAll(families.map {
            SaneByteArray(it.name)
        }.zip(handles))
    }

    private fun getOrCreateColumnFamily(family: ByteArray): ColumnFamilyHandle {
        return columnFamilies.computeIfAbsent(SaneByteArray(family)) {
            LOGGER.info("Creating absent column family: {}", String(it.inner))
            database.createColumnFamily(ColumnFamilyDescriptor(it.inner, columnFamilyOptions))
        }
    }

    fun writeNbt(key: DatabaseKey, value: NbtCompound) {
        val stream = ByteArrayOutputStream()
        NbtIo.write(value, DataOutputStream(stream))
        val columnFamily = getOrCreateColumnFamily(key.namespace)
        database.put(columnFamily, key.key, stream.toByteArray())
    }

    fun writeBytes(key: DatabaseKey, value: ByteArray) {
        val columnFamily = getOrCreateColumnFamily(key.namespace)
        database.put(columnFamily, key.key, value)
    }

    fun remove(key: DatabaseKey) {
        val columnFamily = getOrCreateColumnFamily(key.namespace)
        database.delete(columnFamily, key.key)
    }

    fun readNbt(key: DatabaseKey): NbtCompound? {
        val columnFamily = getOrCreateColumnFamily(key.namespace)
        val data = database.get(columnFamily, key.key) ?: return null
        val stream = ByteArrayInputStream(data)
        return NbtIo.read(DataInputStream(stream))
    }

    fun readBytes(key: DatabaseKey): ByteArray? {
        val columnFamily = getOrCreateColumnFamily(key.namespace)
        return database.get(columnFamily, key.key)
    }

    fun listKeys(namespace: ByteArray): List<ByteArray> {
        val result = mutableListOf<ByteArray>()
        val columnFamily = getOrCreateColumnFamily(namespace)
        database.newIterator(columnFamily).use {
            it.seekToFirst()
            while (it.isValid) {
                result += it.key()
            }
        }
        return result
    }

    fun flush() {
        FlushOptions()
            .setWaitForFlush(true)
            .use {
                database.flush(it)
            }
    }

    override fun close() {
        val exceptions: MutableList<Throwable> = mutableListOf()
        for (handle in columnFamilies.values) {
            try {
                handle.close()
            } catch (e: Throwable) {
                exceptions += e
            }
        }
        try {
            database.close()
        } catch (e: Throwable) {
            exceptions += e
        }
        try {
            dbOptions.close()
        } catch (e: Throwable) {
            exceptions += e
        }
        try {
            columnFamilyOptions.close()
        } catch (e: Throwable) {
            exceptions += e
        }
        try {
            compressionOptions.close()
        } catch (e: Throwable) {
            exceptions += e
        }
        try {
            filter.close()
        } catch (e: Throwable) {
            exceptions += e
        }
        if (exceptions.isNotEmpty()) {
            val e = exceptions.removeFirst()
            for (exception in exceptions) {
                e.addSuppressed(exception)
            }
            throw e
        }
    }
}