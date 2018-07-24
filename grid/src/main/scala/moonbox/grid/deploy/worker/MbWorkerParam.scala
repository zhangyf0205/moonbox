/*-
 * <<
 * Moonbox
 * ==
 * Copyright (C) 2016 - 2018 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

package moonbox.grid.deploy.worker

import moonbox.common.{MbConf, MbLogging}
import moonbox.grid.config._
import moonbox.grid.util.IntParam

import scala.annotation.tailrec

class MbWorkerParam(args: Array[String], val conf: MbConf) extends  MbLogging {
	var clusterName: String = _
	var masters: Seq[String] = _
	var host: String =_
	var port: Int = _

	parse(args.toList)

	if (masters == null || masters.isEmpty) {
		printUsageAndExit(1)
	} else {
		masters.zipWithIndex.foreach {
			case (node, index) => conf.set(s"moonbox.rpc.akka.cluster.seed-nodes.$index", node)
		}
	}
	if (host == null || host == "") {
		host = "localhost"
	}
	conf.set("moonbox.rpc.akka.remote.netty.tcp.hostname", host)
	if (port == 0) {
		logInfo("Using random port as rpc port.")
	}
	conf.set("moonbox.rpc.akka.remote.netty.tcp.port", port.toString)

	conf.set("moonbox.rpc.akka.cluster.roles.0", MbWorker.ROLE)

	private def parseMasters(arg: String): Seq[String] = {
		clusterName = conf.get(CLUSTER_NAME.key, CLUSTER_NAME.defaultValueString)
		arg.stripPrefix("grid://").split(",").map(master =>
			s"akka.tcp://$clusterName@$master")
	}

	@tailrec
	private def parse(args: List[String]): Unit = args match {
		case ("-m" | "--masters") :: value :: tail =>
			masters = parseMasters(value)
			parse(tail)
		case ("-h" | "--host") :: value :: tail =>
			host = value
			parse(tail)
		case ("-p" | "--port") :: IntParam(value) :: tail =>
			port = value
			parse(tail)
		case ("--help") :: tail =>
			printUsageAndExit(0)
		case Nil =>
		case _ =>
			printUsageAndExit(1)
	}

	def akkaConfig: Map[String, String] = {
		for { (key, value) <- AKKA_DEFAULT_CONFIG ++ conf.getAll
			  if key.startsWith("moonbox.rpc.akka")
		} yield (key.stripPrefix("moonbox.rpc."), value)
	}

	private def printUsageAndExit(exitCode: Int): Unit = {
		// scalastyle: off println
		System.err.println(
			"Usage: MbMaster [options]\n" +
				"options:\n" +
				"   -m, --masters : the seed-nodes to be connected to\n" +
				"   -h, --host : the host using for rpc\n" +
				"   -p, --port : the port using for rpc\n" +
				"   --help"
		)
		System.exit(exitCode)
	}
}
