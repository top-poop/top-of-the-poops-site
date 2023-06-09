package org.totp.mangling

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.forkhandles.values.Value
import java.math.BigDecimal
import java.math.BigInteger
import java.sql.Connection
import java.sql.Date
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.time.Duration
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

interface WithConnection {
    fun <T> execute(block: NamedQueryBlock<T>): T
}

class NamedQueryBlock<T>(val name: String?, val block: Connection.() -> T) {
    fun prefixedWith(prefix: String): NamedQueryBlock<T> {
        return NamedQueryBlock(prefix + "_" + (name ?: "unnamed"), block)
    }
}

fun datasource() = HikariDataSource(hikariConnectionConfig())

class HikariWithConnection(private val dataSource: Lazy<DataSource>) : WithConnection {

    override fun <T> execute(block: NamedQueryBlock<T>): T = dataSource
        .value
        .run {
            executeInNewTransaction(block.block)
        }

    private fun <T> executeInNewTransaction(block: Connection.() -> T): T {
        return dataSource.value.connection.use { connection ->
            try {
                val t:T = block(connection)

                connection.commit()

                t
            }
            catch (ex:Exception) {
                if ( ! connection.isClosed ) {
                    connection.rollback()
                }
                throw ex
            }
        }
    }
}

fun hikariConnectionConfig(): HikariConfig = hikariConfig().also {
    it.jdbcUrl = "jdbc:postgresql://localhost:5432/gis"
    it.driverClassName = org.postgresql.Driver::class.java.name
    it.username = "docker"
    it.password = "docker"
    it.schema = "public"
    it.connectionTimeout = Duration.ofSeconds(5).toMillis()
}

fun hikariConfig() = HikariConfig().also {
    it.isAutoCommit = false
    it.minimumIdle = 2
    it.maxLifetime = TimeUnit.MINUTES.toMillis(15)
    it.idleTimeout = TimeUnit.MINUTES.toMillis(5)
    it.connectionTestQuery = "SET TRANSACTION READ WRITE"
}


private fun iterable(rs: ResultSet): Iterable<ResultSet> {
    return Iterable {
        object : AbstractIterator<ResultSet>() {
            override fun computeNext() {
                if (rs.next()) {
                    setNext(rs)
                } else {
                    done()
                }
            }
        }
    }
}

fun <T> PreparedStatement.query(extractor: (ResultSet) -> T): List<T> {
    executeQuery().use {
        return iterable(it).map(extractor)
    }
}

inline fun <reified T : Enum<T>> ResultSet.getEnum(n: String): T {
    return enumValueOf(getString(n))
}

@JvmName("getStringValue")
inline fun <reified T : Value<String>> ResultSet.get(n: String, f: (String) -> T): T {
    return f(getString(n))
}

@JvmName("getNullableStringValue")
inline fun <reified T : Value<String>> ResultSet.getNullable(n: String, f: (String) -> T): T? {
    return getString(n)?.let { f(it) }
}

@JvmName("getNullableLocalDateValue")
inline fun <reified T : Value<LocalDate>> ResultSet.getNullable(n: String, f: (LocalDate) -> T): T? {
    return getDate(n)?.let { f(it.toLocalDate()) }
}

@JvmName("getIntValue")
inline fun <reified T : Value<Int>> ResultSet.get(n: String, f: (Int) -> T): T {
    val int = getInt(n)
    if (wasNull()) {
        throw NullPointerException("Got null from column $n")
    }
    return f(int)
}

@JvmName("getBigDecimalValue")
inline fun <reified T : Value<BigDecimal>> ResultSet.get(n: String, f: (BigDecimal) -> T): T {
    return f(getBigDecimal(n))
}

@JvmName("getBigIntegerValue")
inline fun <reified T : Value<BigInteger>> ResultSet.get(n: String, f: (BigInteger) -> T): T {
    return f(getBigDecimal(n).toBigInteger())
}

@JvmName("getLocalDateValue")
inline fun <reified T : Value<LocalDate>> ResultSet.get(n: String, f: (LocalDate) -> T): T {
    return f(getDate(n).toLocalDate())
}

fun PreparedStatement.set(n: Int, s: String?) {
    if (s == null) {
        setNull(n, Types.VARCHAR)
    } else {
        setString(n, s)
    }
}

@JvmName("setLocalDateValue")
fun PreparedStatement.set(n: Int, v: Value<LocalDate>?) {
    if ( v == null ) {
        setNull(n, Types.DATE)
    }
    else {
        set(n, v.value)
    }
}

@JvmName("setStringValue")
fun PreparedStatement.set(n: Int, v: Value<String>?) {
    if ( v == null ) {
        setNull(n, Types.VARCHAR)
    }
    else {
        set(n, v.value)
    }
}

@JvmName("setBigDecimalValue")
fun PreparedStatement.set(n: Int, v: Value<BigDecimal>?) {
    if ( v == null ) {
        setNull(n, Types.DECIMAL)
    }
    else {
        set(n, v.value)
    }
}

@JvmName("setBigIntegerValue")
fun PreparedStatement.set(n: Int, v: Value<BigInteger>?) {
    if ( v == null ) {
        setNull(n, Types.BIGINT)
    }
    else {
        set(n, v.value)
    }
}

fun PreparedStatement.set(n: Int, b: Boolean?) {
    if (b == null) {
        setNull(n, Types.BOOLEAN)
    } else {
        setBoolean(n, b)
    }
}

fun PreparedStatement.set(n: Int, l: Long?) {
    if (l == null) {
        setNull(n, Types.INTEGER)
    } else {
        setLong(n, l)
    }
}

fun PreparedStatement.set(n: Int, bd: BigDecimal?) {
    if (bd == null) {
        setNull(n, Types.DECIMAL)
    } else {
        setBigDecimal(n, bd)
    }
}

fun PreparedStatement.set(n: Int, bd: BigInteger?) {
    if (bd == null) {
        setNull(n, Types.BIGINT)
    } else {
        setBigDecimal(n, bd.toBigDecimal())
    }
}

fun PreparedStatement.set(n: Int, d: LocalDate?) {
    if (d == null) {
        setNull(n, Types.DATE)
    } else {
        setDate(n, Date.valueOf(d))
    }
}

fun <T> Connection.single(sql: String, bind: (PreparedStatement) -> Unit = {}, mapper: (ResultSet) -> T): T {
    return query(sql, bind, mapper).first()
}

fun <T> Connection.querySingle(sql: String, bind: (PreparedStatement) -> Unit = {}, mapper: (ResultSet) -> T): T? {
    return query(sql, bind, mapper).firstOrNull()
}

fun <T> Connection.query(sql: String, bind: (PreparedStatement) -> Unit = {}, mapper: (ResultSet) -> T): List<T> {
    return prepareStatement(sql).use {
        bind(it)
        it.query(mapper)
    }
}

fun Connection.update(sql: String, bind: (PreparedStatement) -> Unit ): Int {
    return prepareStatement(sql).use {
        bind(it)
        it.executeUpdate()
    }
}

fun Connection.update(sql: String, bind: (PreparedStatement) -> Unit, follow: Connection.(ResultSet) -> Unit = {} ): Int {
    return prepareStatement(sql).use {
        bind(it)
        val hasResultSet = it.execute()
        if ( hasResultSet ) {
            val rs = it.resultSet
            if ( rs.next() ) {
                follow(rs)
            }
        }
        it.updateCount
    }
}

fun <T> Connection.updates(sql: String, things: Iterable<T>, bind: (PreparedStatement, T) -> Unit): List<Int> {
    return prepareStatement(sql).use { ps ->
        things.forEach { thing ->
            bind(ps, thing)
            ps.addBatch()
        }
        ps.executeBatch().toList()
    }
}
