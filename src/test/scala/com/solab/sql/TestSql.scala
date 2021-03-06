package com.solab.sql

import org.specs2.SpecificationWithJUnit
import org.apache.commons.dbcp.BasicDataSource
import org.specs2.specification.Step
import java.util.Date
import java.sql.{Time, Timestamp, Connection}

/** Tests for the Sql component.
 *
 * @author Enrique Zamudio
 * Date: 27/12/11 18:32
 */
class TestSql extends SpecificationWithJUnit { def is =

  "Test all the methods in Sql component" ^ Step(setup())             ^
  "Insert a simple row"                   ! insertSimpleRow           ^
  "Insert a row and get generated key"    ! insertRowWithGeneratedKey ^
  "Update existing row"                   ! updateExistingRow         ^
  "Update no rows"                        ! updateNothing             ^
  "Delete no rows"                        ! deleteNothing             ^
  "Delete a row"                          ! deleteOneRow              ^
  "Delete several rows"                   ! deleteManyRows            ^
  "Query first row"                       ! queryFirstRow             ^
  "Query boolean"                         ! queryBoolean              ^
  "Query decimal"                         ! queryDecimal              ^
  "Query Int"                             ! queryInt                  ^
  "Query Long"                            ! queryLong                 ^
  "Query String"                          ! queryString               ^
  "Query other value"                     ! queryValue                ^
  "Query several rows"                    ! queryRows                 ^
  "Query limited rows"                    ! limitedQueries            ^
  "Query raw rows"                        ! rawQuery                  ^
  "Transaction with rollback"             ! txRollback                ^
  "Transaction with commit"               ! txCommit                  ^
                                          Step(shutdown())            ^
                                          end

  var ds:BasicDataSource=_
  lazy val testRowKey1=insertRow(refdate1)
  lazy val testRowKey2=insertRow(refdate1)
  val refdate1=2168856789L
  val refdate2=1325089720000L
  val refdate3=2168867000L
  val refdate4=2160000000L
  val refdate5=2161234560L
  lazy val sql = new Sql(ds)

  def insertSimpleRow()={
    sql.executeUpdate("INSERT INTO scala_sql_test2 VALUES(?, ?, ?)", 1, "test1", 1) must be equalTo(1)
  }
  def insertRowWithGeneratedKey()={
    val keys = sql.executeInsert("INSERT INTO scala_sql_test1 (string, date, tstamp, colint,coldec) VALUES (?, ?, ?, ?, ?)",
      "test2", new Date, new Timestamp(refdate2), 2, BigDecimal(3))
    (keys.size must be equalTo(1)) and (keys(0).size must be equalTo(1)) and (keys(0)(0).asInstanceOf[Long] must be greaterThan(0))
  }
  def updateExistingRow()={
    val keys = sql.executeInsert("INSERT INTO scala_sql_test1 (string, date, tstamp, colint,coldec) VALUES (?, ?, ?, ?, ?)",
      "test3", new Date, new Timestamp(refdate2), 3, BigDecimal(4))
    val count = sql.executeUpdate("UPDATE scala_sql_test1 SET colbit=true WHERE pkey=?", keys(0)(0))
    val flag = sql.queryForBoolean("SELECT colbit FROM scala_sql_test1 WHERE pkey=?", keys(0)(0))
    (count must be equalTo(1)) and (flag must beSome[Boolean]) and (flag.get must beTrue)
  }
  def updateNothing()={
    sql.executeUpdate("UPDATE scala_sql_test1 SET colbit=true WHERE pkey=-1000") must be equalTo(0)
  }
  def deleteNothing()={
    sql.executeUpdate("DELETE FROM scala_sql_test1 WHERE pkey=-1000") must be equalTo(0)
  }
  def deleteOneRow()={
    val keys = sql.executeInsert("INSERT INTO scala_sql_test1 (string, date, tstamp, colint,coldec) VALUES (?, ?, ?, ?, ?)",
      "test3", new Date, new Timestamp(refdate2), 3, BigDecimal(4))
    sql.executeUpdate("DELETE FROM scala_sql_test1 WHERE pkey=?", keys(0)(0)) must be equalTo(1)
  }
  def deleteManyRows()={
    4 to 6 foreach { i =>
      sql.executeInsert("INSERT INTO scala_sql_test1 (string, date, tstamp, colint,coldec) VALUES (?, ?, ?, ?, ?)",
        "test" + i, new Date, new Timestamp(refdate3), i, BigDecimal(i+1))
    }
    sql.executeUpdate("DELETE FROM scala_sql_test1 WHERE tstamp=?", new Timestamp(refdate3)) must be equalTo(3)
  }
  def queryFirstRow()={
    val existing = sql.firstRow("SELECT * FROM scala_sql_test1 WHERE pkey=?", testRowKey1)
    val absent   = sql.firstRow("SELECT * FROM scala_sql_test1 WHERE pkey=-5000")
    (absent must beNone) and (existing must beSome[Map[String, Any]]) and (existing.get("pkey") must be equalTo(testRowKey1))
  }
  def queryBoolean()={
    val k = insertRow(refdate2)
    val v1 = sql.queryForBoolean("SELECT colbit FROM scala_sql_test1 WHERE pkey=?", k)
    sql.executeUpdate("UPDATE scala_sql_test1 SET colbit=NULL WHERE pkey=?", k)
    val v2 = sql.queryForBoolean("SELECT colbit FROM scala_sql_test1 WHERE pkey=?", k)
    val v3 = sql.queryForBoolean("SELECT colbit FROM scala_sql_test1 WHERE pkey=-5000")
    (v1 must beSome[Boolean]) and (v1.get must beFalse) and (v2 must beNone) and (v3 must beNone)
  }
  def queryDecimal()={
    val k = insertRow(refdate2)
    val v1 = sql.queryForDecimal("SELECT coldec FROM scala_sql_test1 WHERE pkey=?", k)
    sql.executeUpdate("UPDATE scala_sql_test1 SET coldec=NULL WHERE pkey=?", k)
    val v2 = sql.queryForDecimal("SELECT coldec FROM scala_sql_test1 WHERE pkey=?", k)
    (v1 must beSome[BigDecimal]) and (v1.get must be equalTo(BigDecimal(345))) and (v2 must beNone)
  }
  def queryInt()={
    val k = insertRow(refdate2)
    val v1 = sql.queryForInt("SELECT colint FROM scala_sql_test1 WHERE pkey=?", k)
    sql.executeUpdate("UPDATE scala_sql_test1 SET colint=NULL WHERE pkey=?", k)
    val v2 = sql.queryForInt("SELECT colint FROM scala_sql_test1 WHERE pkey=?", k)
    (v1 must beSome[Int]) and (v1.get must be equalTo(234)) and (v2 must beNone)
  }
  def queryLong()={
    val k = insertRow(refdate2)
    val v1 = sql.queryForLong("SELECT colint FROM scala_sql_test1 WHERE pkey=?", k)
    sql.executeUpdate("UPDATE scala_sql_test1 SET colint=NULL WHERE pkey=?", k)
    val v2 = sql.queryForLong("SELECT colint FROM scala_sql_test1 WHERE pkey=?", k)
    (v1 must beSome[Long]) and (v1.get must be equalTo(234L)) and (v2 must beNone)
  }
  def queryString()={
    val k = insertRow(refdate2)
    val v1 = sql.queryForString("SELECT string FROM scala_sql_test1 WHERE pkey=?", k)
    sql.executeUpdate("UPDATE scala_sql_test1 SET string=NULL WHERE pkey=?", k)
    val v2 = sql.queryForString("SELECT string FROM scala_sql_test1 WHERE pkey=?", k)
    (v1 must beSome[String]) and (v1.get must be equalTo("test_row")) and (v2 must beNone)
  }
  def queryValue()={
    val k = insertRow(refdate2)
    val v1 = sql.queryForValue[Timestamp]("SELECT tstamp FROM scala_sql_test1 WHERE pkey=?", k)
    sql.executeUpdate("UPDATE scala_sql_test1 SET tstamp=NULL WHERE pkey=?", k)
    val v2 = sql.queryForValue[Timestamp]("SELECT tstamp FROM scala_sql_test1 WHERE pkey=-5000")
    val v3 = sql.queryForValue[Timestamp]("SELECT tstamp FROM scala_sql_test1 WHERE pkey=?", k)
    (v1 must beSome[Timestamp]) and (v1.get.getTime must be equalTo(refdate2)) and (v2 must beNone) and (v3 must beNone)
  }
  def queryRows()={
    val rows1 = sql.rows("SELECT * FROM scala_sql_test1")
    val rows2 = sql.rows("SELECT pkey, string, date, tstamp, colbit FROM scala_sql_test1 WHERE tstamp=?", new Timestamp(refdate1))
    (rows1.size must be greaterThanOrEqualTo(rows2.size)) and (rows2.size must be greaterThanOrEqualTo(2)) and
    (rows2.exists( r => r("pkey") == testRowKey1 ) must beTrue) and (rows2.exists( r => r("pkey") == testRowKey2 ) must beTrue)
  }
  def rawQuery()={
    var (k1found, k2found, k3found, k4found) = (false,false, false, false)
    sql.eachRow("SELECT * FROM scala_sql_test1 WHERE tstamp=?", new Timestamp(refdate1)) { row =>
      k1found |= row("pkey") == testRowKey1
      k2found |= row("pkey") == testRowKey2
    }
    sql.eachRawRow("SELECT pkey FROM scala_sql_test1") { rs =>
      k3found |= rs.getLong(1) == testRowKey1
      k4found |= rs.getLong(1) == testRowKey2
    }
    k1found must beTrue and (k2found must beTrue) and (k3found must beTrue) and (k4found must beTrue)
  }

  def limitedQueries()={
    val q1 = sql.rows(2,0,"SELECT * FROM scala_sql_test1 ORDER BY pkey")
    val q2 = sql.rows(1,1,"SELECT * FROM scala_sql_test1 ORDER BY pkey")
    val q3 = sql.rows(5,10000,"SELECT * FROM scala_sql_test1 ORDER BY pkey")
    q1.size must be equalTo(2) and (q2.size must be equalTo(1)) and (q1(1) must be equalTo(q2.head)) and (q3 must beEmpty)
  }
  def txRollback()={
    sql.withTransaction { conn =>
      insertRow(refdate4)
      insertRow(refdate4)
      insertRow(refdate4)
      sql.executeUpdate("UPDATE nonexisting_table SET bogus_field=5")
    } must throwAn[Exception] and
    (sql.queryForInt("SELECT count(*) FROM scala_sql_test1 WHERE tstamp=?", new Timestamp(refdate4)) must be equalTo(Some(0)))
  }
  def txCommit()={
    sql.withTransaction { conn =>
      insertRow(refdate5)
      insertRow(refdate5)
      insertRow(refdate5)
    }
    sql.queryForInt("SELECT count(*) FROM scala_sql_test1 WHERE tstamp=?", new Timestamp(refdate5)) must be equalTo(Some(3))
  }

  def setup() {
    //Create a pooled datasource for a test database
    ds = new BasicDataSource
    ds.setDriverClassName("org.h2.Driver")
    ds.setUrl("jdbc:h2:mem:scala_sql_tests;MODE=PostgreSQL")
    ds.setDefaultAutoCommit(true)
    ds.setUsername("sa")
    ds.setPassword("")
    ds.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)
    ds.setInitialSize(Runtime.getRuntime.availableProcessors)
    //Now create some tables
    sql.execute("""
    CREATE TABLE scala_sql_test1(
      pkey SERIAL PRIMARY KEY,
      string VARCHAR(40),
      date   DATE,
      time   TIME,
      tstamp TIMESTAMP,
      colint INTEGER,
      coldec NUMERIC(12,4),
      colbit BOOLEAN
    )""")
    sql.execute("""
    CREATE TABLE scala_sql_test2(
      pkey INTEGER PRIMARY KEY,
      string VARCHAR(200),
      colint INTEGER NOT NULL
    )""")
    //This is just to create the rows
    require(testRowKey1 < testRowKey2)
  }

  def insertRow(ts:Long):Long={
    sql.executeInsert("INSERT INTO scala_sql_test1(string, date, time, tstamp, colint, coldec, colbit) VALUES('test_row', ?, ?, ?, 234, 345.00, false)",
      new Date(ts), new Time(ts), new Timestamp(ts))(0)(0).asInstanceOf[Long]
  }
  def shutdown() {
    ds.close()
  }

}
