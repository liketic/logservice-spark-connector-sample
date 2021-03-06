/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.openservices.examples.streaming

import com.aliyun.openservices.aliyun.log.producer.{Callback, Result}
import com.aliyun.openservices.log.common.LogItem
import com.aliyun.openservices.loghub.client.config.LogHubCursorPosition

import org.apache.spark.SparkConf
import org.apache.spark.streaming.{Milliseconds, StreamingContext}
import org.apache.spark.streaming.aliyun.logservice.LoghubUtils
import org.apache.spark.streaming.aliyun.logservice.writer._

object TestLoghubWriter {

  def main(args: Array[String]): Unit = {
    val loghubProject = ""
    val logStore = ""
    val targetLogstore = "test-logstore"
    val loghubGroupName = "test-access-log"
    val endpoint = ""
    val accessKeyId = ""
    val accessKeySecret = ""
    val batchInterval = Milliseconds(5 * 1000)
    val zkAddress = if (args.length >= 9) args(8) else "localhost:2181"

    val conf = new SparkConf().setAppName("Test write data to Loghub")
      .setMaster("local[1]")
      .set("spark.streaming.loghub.maxRatePerShard", "10")
      .set("spark.loghub.batchGet.step", "1")
    val zkParas = Map("zookeeper.connect" -> zkAddress)
    val ssc = new StreamingContext(conf, batchInterval)

    val loghubStream = LoghubUtils.createDirectStream(
      ssc,
      loghubProject,
      logStore,
      loghubGroupName,
      accessKeyId,
      accessKeySecret,
      endpoint,
      zkParas,
      LogHubCursorPosition.BEGIN_CURSOR)

    val producerConfig = Map(
      "sls.project" -> loghubProject,
      "sls.logstore" -> targetLogstore,
      "access.key.id" -> accessKeyId,
      "access.key.secret" -> accessKeySecret,
      "sls.endpoint" -> endpoint,
      "sls.ioThreadCount" -> "2"
    )

    val lines = loghubStream.map(x => x)

    def transformFunc(x: String): LogItem = {
      val r = new LogItem()
      r.PushBack("key", x)
      r
    }

    val callback = new Callback with Serializable {
      override def onCompletion(result: Result): Unit = {
        // scalastyle:off
        println(s"Send result ${result.isSuccessful}")
        // scalastyle:on
      }
    }

    lines.writeToLoghub(
      producerConfig,
      "topic",
      "streaming",
      transformFunc, Option.apply(callback))

    ssc.checkpoint("/tmp/spark/streaming") // set checkpoint directory
    ssc.start()
    ssc.awaitTermination()
  }
}
