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

package org.apache.spark.sql.execution.datasources

import org.apache.spark.sql.catalyst.analysis.MultiInstanceRelation
import org.apache.spark.sql.catalyst.catalog.CatalogTable
import org.apache.spark.sql.catalyst.expressions.{AttributeMap, AttributeReference}
import org.apache.spark.sql.catalyst.plans.logical.{LeafNode, LogicalPlan, Statistics}
import org.apache.spark.sql.execution.datasources.mbjdbc.MbJDBCRelation
import org.apache.spark.sql.internal.SQLConf
import org.apache.spark.sql.sources.BaseRelation
import org.apache.spark.util.Utils

/**
  * Used to link a [[BaseRelation]] in to a logical query plan.
  */
case class LogicalRelation(
							  relation: BaseRelation,
							  output: Seq[AttributeReference],
							  catalogTable: Option[CatalogTable])
	extends LeafNode with MultiInstanceRelation {

  // Logical Relations are distinct if they have different output for the sake of transformations.
  override def equals(other: Any): Boolean = other match {
	case l @ LogicalRelation(otherRelation, _, _) => relation == otherRelation && output == l.output
	case _ => false
  }

  override def hashCode: Int = {
	com.google.common.base.Objects.hashCode(relation, output)
  }

  // Only care about relation when canonicalizing.
  override def preCanonicalized: LogicalPlan = copy(catalogTable = None)

  @transient override def computeStats(conf: SQLConf): Statistics = {
	catalogTable.flatMap(_.stats.map(_.toPlanStats(output))).getOrElse(
	  Statistics(sizeInBytes = relation.sizeInBytes, rowCount = relation match {
		case jdbc: MbJDBCRelation => jdbc.rowCount
		case _ => None
	  }))
  }

  /** Used to lookup original attribute capitalization */
  val attributeMap: AttributeMap[AttributeReference] = AttributeMap(output.map(o => (o, o)))

  /**
	* Returns a new instance of this LogicalRelation. According to the semantics of
	* MultiInstanceRelation, this method returns a copy of this object with
	* unique expression ids. We respect the `expectedOutputAttributes` and create
	* new instances of attributes in it.
	*/
  override def newInstance(): LogicalRelation = {
	this.copy(output = output.map(_.newInstance()))
  }

  override def refresh(): Unit = relation match {
	case fs: HadoopFsRelation => fs.location.refresh()
	case _ =>  // Do nothing.
  }

  override def simpleString: String = s"Relation[${Utils.truncatedString(output, ",")}] $relation"
}

object LogicalRelation {
  def apply(relation: BaseRelation): LogicalRelation = {
	/*relation match {
	  case jdbc: MbJDBCRelation =>
		val output = relation.schema.toAttributes.map {
		  case a: AttributeReference =>
			AttributeReference(name = a.name,
			  dataType = a.dataType,
			  nullable = a.nullable,
			  metadata = a.metadata
			)(exprId = a.exprId, qualifier = Some(jdbc.jdbcOptions.table))
		}
		LogicalRelation(relation, output, None)
	  case _ => LogicalRelation(relation, relation.schema.toAttributes, None)
	}*/
	LogicalRelation(relation, relation.schema.toAttributes, None)
  }


  def apply(relation: BaseRelation, table: CatalogTable): LogicalRelation =
	/*relation match {
	case jdbc: MbJDBCRelation =>
	  val output = relation.schema.toAttributes.map {
		case a: AttributeReference =>
		  AttributeReference(name = a.name,
			dataType = a.dataType,
			nullable = a.nullable,
			metadata = a.metadata
		  )(exprId = a.exprId, qualifier = Some(jdbc.jdbcOptions.table))
	  }
	  LogicalRelation(relation, output, Some(table))
	case _ =>
  }*/
  LogicalRelation(relation, relation.schema.toAttributes, Some(table))
}
