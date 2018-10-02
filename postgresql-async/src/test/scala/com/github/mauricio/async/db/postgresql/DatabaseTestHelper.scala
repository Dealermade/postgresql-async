/*
 * Copyright 2013 Maurício Linhares
 *
 * Maurício Linhares licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.github.mauricio.async.db.postgresql

import java.io.File
import java.util.concurrent.{TimeUnit, TimeoutException}

import com.github.mauricio.async.db.{Configuration, Connection, QueryResult, SSLConfiguration}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait DatabaseTestHelper {

  def databaseName = Some("netty_driver_test")

  def databasePort = 5432

  def defaultConfiguration = new Configuration(port = databasePort, username = "postgres", database = databaseName)

  def timeTestConfiguration =
    new Configuration(port = databasePort, username = "postgres", database = Some("netty_driver_time_test"))

  def withHandler[T](fn: PostgreSQLConnection => T): T = {
    withHandler(this.defaultConfiguration, fn)
  }

  def withTimeHandler[T](fn: PostgreSQLConnection => T): T = {
    withHandler(this.timeTestConfiguration, fn)
  }

  def withSSLHandler[T](
      mode: SSLConfiguration.Mode.Value,
      host: String = "localhost",
      rootCert: Option[File] = Some(new File("script/postgresql/server.crt")))(fn: PostgreSQLConnection => T): T = {
    val config = new Configuration(host = host,
                                   port = databasePort,
                                   username = "postgres",
                                   database = databaseName,
                                   ssl = SSLConfiguration(mode = mode, rootCert = rootCert))
    withHandler(config, fn)
  }

  def withHandler[T](configuration: Configuration, fn: PostgreSQLConnection => T): T = {

    val handler = new PostgreSQLConnection(configuration)

    try {
      await(handler.connect)
      fn(handler)
    } finally {
      // TODO what timeout we are expecting here, if we don't wait for the Future completion ???
      handleTimeout(handler, handler.disconnect)
    }

  }

  def executeDdl(handler: Connection, data: String, count: Int = 0): Long = {
    val rows = handleTimeout(handler, {
      await(handler.sendQuery(data)).rowsAffected
    })

    if (rows != count) {
      throw new IllegalStateException("We expected %s rows but there were %s".format(count, rows))
    }

    rows
  }

  def executeQuery(handler: Connection, data: String): QueryResult = {
    handleTimeout(handler, {
      await(handler.sendQuery(data))
    })
  }

  def executePreparedStatement(handler: Connection, statement: String, values: Array[Any] = Array.empty[Any]): QueryResult = {
    handleTimeout(handler, {
      await(handler.sendPreparedStatement(statement, values))
    })
  }

  private def handleTimeout[R](handler: Connection, fn: => R): R = {
    try {
      fn
    } catch {
      case e: TimeoutException =>
        throw new IllegalStateException("Timeout executing call from handler -> %s".format(handler), e)
    }
  }

  def await[T](future: Future[T]): T = {
    Await.result(future, Duration(5, TimeUnit.SECONDS))
  }

}
