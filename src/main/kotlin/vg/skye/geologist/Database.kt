package vg.skye.geologist

import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtIo
import org.rocksdb.*
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
    init {
        RocksDB.loadLibrary()
    }
    private val dbOptions: DBOptions = DBOptions()
        .setCreateIfMissing(true)
    // TODO: the numbers here are out the wazoo. do some benchmarking
    private val compressionOptions: CompressionOptions = CompressionOptions()
        .setZStdMaxTrainBytes(8 * 1024)
        .setMaxDictBytes(1024)
        .setLevel(1)
        .setEnabled(true)
    private val columnFamilyOptions: ColumnFamilyOptions = ColumnFamilyOptions()
        .optimizeUniversalStyleCompaction()
        .setCompressionType(CompressionType.LZ4_COMPRESSION)
        .setBottommostCompressionType(CompressionType.ZSTD_COMPRESSION)
        .setBottommostCompressionOptions(compressionOptions)
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
        synchronized(columnFamilies) {
            return columnFamilies.computeIfAbsent(SaneByteArray(family)) {
                database.createColumnFamily(ColumnFamilyDescriptor(it.inner, columnFamilyOptions))
            }
        }
    }

    fun writeNbt(key: DatabaseKey, value: NbtCompound) {
        val stream = ByteArrayOutputStream()
        NbtIo.write(value, DataOutputStream(stream))
        val columnFamily = getOrCreateColumnFamily(key.namespace)
        database.put(columnFamily, key.key, stream.toByteArray())
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
        if (exceptions.isNotEmpty()) {
            val e = exceptions.removeLast()
            for (exception in exceptions) {
                e.addSuppressed(exception)
            }
            throw e
        }
    }
}