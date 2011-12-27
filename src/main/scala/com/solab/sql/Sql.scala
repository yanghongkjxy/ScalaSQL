package com.solab.sql

import javax.sql.DataSource
import java.io.{Reader, InputStream}
import java.sql._

/** Clase similar a groovy.sql.Sql pero con un DataSource por default, en vez de una conexión.
 *
 * @param dataSource A DataSource from which to get connections from.
 *
 * @author Enrique Zamudio
 * Date: 09/12/11 14:02
 */
class Sql(val dataSource:DataSource) {

  val conns = new ThreadLocalConnection(dataSource)

  /** Creates a PreparedStatement for the specified connection, with the specified SQL and parameters.
   * @param conn The connection from which to create the PreparedStatement.
   * @param sql An SQL statement.
   * @param params the parameters for the SQL statement. */
  def prepareStatement(conn:Connection, sql:String, params:Any*)={
    val stmt = conn.prepareStatement(sql)
    var count=0
    params.foreach {
      count+=1
      _ match {
        case x:Int => stmt.setInt(count, x)
        case x:Long => stmt.setLong(count, x)
        case x:Short => stmt.setShort(count, x)
        case x:Byte => stmt.setByte(count, x)
        case x:Boolean => stmt.setBoolean(count, x)
        case x:BigDecimal => stmt.setBigDecimal(count, x.bigDecimal)
        case x:String => stmt.setString(count, x)
        case x:Timestamp => stmt.setTimestamp(count, x)
        case x:java.sql.Date => stmt.setDate(count, x)
        case x:java.util.Date => stmt.setDate(count, new java.sql.Date(x.getTime))
        case x:Array[Byte] => stmt.setBytes(count, x)
        case x:Reader => stmt.setClob(count, x)
        case x:InputStream => stmt.setBlob(count, x)
        case x => stmt.setObject(count, x)
      }
    }
    stmt
  }

  /** Creates a PreparedStatement from the specified connection, with the specified SQL and parameters,
   * configured to return the generated keys resulting from executing an insert statement.
   * @param conn The connection from which to create the PreparedStatement.
   * @param sql An insert statement.
   * @param params The parameters for the insert statement. */
  def prepareInsertStatement(conn:Connection, sql:String, params:Any*)={
    val stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)
    var count=0
    params.foreach {
      count+=1
      _ match {
        case x:Int => stmt.setInt(count, x)
        case x:Long => stmt.setLong(count, x)
        case x:Short => stmt.setShort(count, x)
        case x:Byte => stmt.setByte(count, x)
        case x:Boolean => stmt.setBoolean(count, x)
        case x:BigDecimal => stmt.setBigDecimal(count, x.bigDecimal)
        case x:String => stmt.setString(count, x)
        case x:Timestamp => stmt.setTimestamp(count, x)
        case x:java.sql.Date => stmt.setDate(count, x)
        case x:java.util.Date => stmt.setDate(count, new java.sql.Date(x.getTime))
        case x:Array[Byte] => stmt.setBytes(count, x)
        case x:Reader => stmt.setClob(count, x)
        case x:InputStream => stmt.setBlob(count, x)
        case x => stmt.setObject(count, x)
      }
    }
    stmt
  }

  /** Ejecuta una sentencia SQL parametrizada, con una conexión del DataSource. */
  def execute(sql:String, params:Any*):Boolean={
    val conn = conns.get()
    var rval=false
    try {
      val stmt = prepareStatement(conn.connection(), sql, params)
      try {
        rval=stmt.execute()
      } finally stmt.close()
    } finally conn.close()
    rval
  }

  /** Executes the specified SQL as an update statement
   * @param sql The statement to execute
   * @param params The parameters for the SQL statement.
   * @return The number of rows that were updated (the result of calling executeUpdate on the PreparedStatement). */
  def executeUpdate(sql:String, params:Any*):Int={
    val conn = conns.get()
    var rval= -1
    try {
      val stmt = prepareStatement(conn.connection(), sql, params)
      try {
        rval=stmt.executeUpdate()
      } finally stmt.close()
    } finally conn.close()
    rval
  }

  /** Executes an insert statement. The difference with executeUpdate is that this method returns the generated keys
   * resulting from the inserted rows, if any.
   * @param sql The insert statement.
   * @param params The parameters for the insert statement.
   * @return The generated keys, if any. */
  def executeInsert(sql:String, params:Any*):IndexedSeq[IndexedSeq[Any]]={
    val conn = conns.get()
    try {
      val stmt = prepareStatement(conn.connection(), sql, params)
      try {
        val count = 1 to stmt.executeUpdate()
        val rs = stmt.getGeneratedKeys
        try {
          val rango = 1 to rs.getMetaData.getColumnCount
          count.map { rowIndex =>
            rs.next()
            rango.map { rs.getObject(_) }
          }
        } finally rs.close()
      } finally stmt.close()
    } finally conn.close()
  }

  /** Executes a parameterized query and calls a function with each row, passing it the ResultSet (so the function
   * does not need to call next).
   * @param sql The query to run.
   * @param params The parameters to pass to the query.
   * @param body The function to call for each row. It is passed the ResultSet. */
  def eachRawRow(sql:String, params:Any*)(body: ResultSet => Unit) {
    val conn = conns.get()
    try {
      val stmt = prepareStatement(conn.connection(), sql, params)
      try {
        val rs = stmt.executeQuery()
        try {
          while (rs.next()) {
            body(rs)
          }
        } finally rs.close()
      } finally stmt.close()
    } finally conn.close()
  }

  /** Runs a parameterized query and calls a function with each row, passing the row as a Map.
   * @param sql The query to run.
   * @param params The parameters to pass to the query.
   * @param body A function to be called for each row, taking a Map[String,Any] as parameter.
   */
  def eachRow(sql:String, params:Any*)(body: Map[String,Any] => Unit) {
    eachRawRow(sql, params) { rs:ResultSet =>
      val meta = rs.getMetaData
      val range = 1 to meta.getColumnCount
      while (rs.next()) {
        val row = range.map { mapColumn(rs, meta, _) }.toMap
        body(row)
      }
    }
  }

  /** Returns a List of all the rows returned by the query. Each row is a Map with the column names as keys. */
  def rows(sql:String, params:Any*):List[Map[String, Any]]={
    var rows:List[Map[String, Any]] = Nil
    eachRow(sql, params) { m =>
      rows = rows:+m
    }
    rows
  }

  /** Returns the first row, if any, for the specified query. */
  def firstRow(sql:String, params:Any*):Option[Map[String, Any]]={
    val conn = conns.get()
    try {
      val stmt = prepareStatement(conn.connection(), sql, params)
      stmt.setMaxRows(1)
      try {
        val rs = stmt.executeQuery()
        try {
          if (rs.next()) {
            val meta = rs.getMetaData
            Some((1 to meta.getColumnCount).map { mapColumn(rs, meta, _) }.toMap)
          } else {
            None
          }
        } finally rs.close()
      } finally stmt.close()
    } finally conn.close()
  }

  /** Creates a connection, executes the function within an open transaction, and commits the transaction at the end,
   * or rolls back if an exception is thrown.
   * @param body The function to execute with the open transaction. It is passed the connection as an argument. */
  def withTransaction(body: Connection => Unit) {
    val conn = conns.get()
    conn.beginTransaction()
    var ok = false
    try {
      body(conn.connection())
      ok = true
    } finally {
      try {
        if (ok) conn.commit() else conn.rollback()
      } finally {
        conn.close()
      }
    }
  }

  /** Creates and returns a Tuple2 with the specified column's name and its value. If the value is null, the returned
   * value is None.
   * @param rs An open ResultSet
   * @param meta The ResultSet's metadata
   * @param idx The column index (starting at 1). */
  def mapColumn(rs:ResultSet, meta:ResultSetMetaData, idx:Int):(String, Any)={
    val v = rs.getObject(idx)
    (meta.getColumnName(idx) -> (if (rs.wasNull()) None else v))
  }

}
