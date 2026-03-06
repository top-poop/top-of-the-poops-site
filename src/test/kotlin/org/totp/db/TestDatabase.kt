package org.totp.db


val testDbConnection = HikariWithConnection(lazy { datasource() })

