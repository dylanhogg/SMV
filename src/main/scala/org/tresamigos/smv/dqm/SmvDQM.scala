/*
 * This file is licensed under the Apache License, Version 2.0
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

package org.tresamigos.smv
package dqm

import scala.reflect.ManifestFactory
import scala.util.Try

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.udf
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization.{read, write}
import org.apache.commons.lang.StringEscapeUtils.escapeJava

/**
 * DQM class for data quality check and fix
 *
 * Support 2 types of recode level tasks: Rule and Fix. A "rule" is a requirement on a record, if
 * a record can't satisfy a rule, the record will be filtered. A "fix" is a requirement on a field
 * with a default value, so that it can fix a record.
 * DQM also support different "Policies". Policies are requirements on the entire DF level. A policy
 * is a function on (DF, [[org.tresamigos.smv.dqm.DQMState]]). By given a df and the DQMState, which
 * are results from the rules and fixes, a policy determine whether the df is passed the DQM or failed.
 *
 * Create a DQM:
 * {{{
 * val dqm = SmvDQM().
 *   add(DQMRule($"amt" < 1000000.0, "rule1", FailAny)).
 *   add(DQMFix($"age" > 100, lit(100) as "age", "fix1")).
 *   add(DQMFix($"weight" < 5, lit(5) as "weight", "fix2")).
 *   add(FailTotalFixCountPolicy(20))
 * }}}
 * In this example, "amt" field is required to be lower than one million, if any record does not
 * satisfy it, the DF will fail this DQM. The "age" field will be capped to 100, and the "weight"
 * field will be capped on the lower bound to 5.
 * None of the 2 fixes will trigger a DF fail. However, we added a policy which require no more
 * than 20 fixes in the entire DF, otherwise the DF will fail this DQM.
 *
 * Attach DQM to a DF:
 * {{{
 * val dfWithDqm = dqm.attachTasks(df)
 * }}}
 *
 * Check the DQM policies:
 * Since all the rules and fixes are performed when the DF has an action, user need to make sure
 * that there is one and only one action operation happened on the DF. Please note that actions
 * like "count" might be optimized so that transformations which have no impact on "count" might be
 * totally ignored. If there no natural action to be apply, you may need to do convert DF to RDD first
 * {{{
 * dfWithDqm.rdd().count
 * }}}
 * After the action, we can check the policies
 * {{{
 * val result = dqm.validate()
 * }}}
 * The result is a [[org.tresamigos.smv.ValidationResult]]
 **/
class SmvDQM(
    private[smv] val rules: Seq[DQMRule] = Nil,
    private[smv] val fixes: Seq[DQMFix] = Nil,
    private[smv] val policies: Seq[DQMPolicy] = Nil,
    val needAction: Boolean = false
) {

  def add(rule: DQMRule): SmvDQM = {
    val newRules = rules :+ rule
    new SmvDQM(newRules, fixes, policies, true)
  }

  def add(fix: DQMFix): SmvDQM = {
    val newFixes = fixes :+ fix
    new SmvDQM(rules, newFixes, policies, true)
  }

  def add(policy: DQMPolicy): SmvDQM = {
    val newPolicies = policies :+ policy
    new SmvDQM(rules, fixes, newPolicies, needAction)
  }

  def addAction(): SmvDQM = {
    new SmvDQM(rules, fixes, policies, true)
  }
}

object SmvDQM {
  def apply() = new SmvDQM()
}

/**
 * Validates data against DQM rules
 * @param dqm
 */
class DQMValidator(dqm: SmvDQM) {
  import DQMValidator._

  private lazy val app: SmvApp = SmvApp.app

  private val ruleNames = dqm.rules.map { _.name }
  private val fixNames  = dqm.fixes.map { _.name }

  /** Check for duplicated task names */
  require((ruleNames ++ fixNames).size == (ruleNames ++ fixNames).toSet.size)

  private lazy val dqmState: DQMState = {
    new DQMState(app.sc, ruleNames, fixNames)
  }

  /** create policies from tasks. Filter out NoOpDQMPolicy */
  private lazy val policiesFromTasks: Seq[DQMPolicy] =
    (dqm.rules ++ dqm.fixes).map { _.createPolicy() }.filter(_ != NoOpDQMPolicy)

  private lazy val allPolicies = policiesFromTasks ++ dqm.policies

  /** since rule need to log the reference columns, need to plus them before check and remove after*/
  private def attachRules(df: DataFrame): DataFrame = {
    if (dqm.rules.isEmpty) df
    else {
      val ruleColTriplets = dqm.rules.map { _.createCheckCol(dqmState) }
      val plusCols        = ruleColTriplets.map { _._1 }
      val filterCol       = ruleColTriplets.map { _._2 }.reduce(_ && _)
      val minusCols       = ruleColTriplets.map { _._3 }

      df.smvSelectPlus(plusCols: _*).where(filterCol).smvSelectMinus(minusCols: _*)
    }
  }

  private def attachFixes(df: DataFrame): DataFrame = {
    if (dqm.fixes.isEmpty) df
    else {
      val fixCols = dqm.fixes.map { _.createFixCol(dqmState) }
      df.selectWithReplace(fixCols: _*)
    }
  }

  /** add overall record counter and rules and fixes */
  def attachTasks(df: DataFrame): DataFrame = {
    val _dqmState = dqmState
    val totalCountCol = udf({ () =>
      _dqmState.addRec()
      true
    })

    val dfWithRules = attachRules(df.where(totalCountCol()))
    attachFixes(dfWithRules)
  }

  private[smv] def createParserValidator() = {
    new ParserValidation(dqmState)
  }

  private def terminateAtError(result: DqmValidationResult) = {
    if (!result.passed) {
      throw new SmvDqmValidationError(result)
    }
  }

  private[smv] def totalRecords(): Long = {
    dqmState.getRecCountNotConcluded()
  }

  /**
   * Entrypoint for validating data
   */
  def validate() = {
    val result = applyPolicies()
    terminateAtError(result)
    result
  }

  /**
   * Appl DQM policies to DataFrame and return result
   */
  def applyPolicies(): DqmValidationResult = {
    /** need to take a snapshot on the DQMState before validation, since validation step could
     * have actions on the DF, which will change the accumulators of the DQMState*/
    val snapshot = dqmState.snapshot()

    val results = allPolicies.map { p =>
      (p.name, p.policy(dqmState))
    }

    val passed = results.isEmpty || results.map { _._2 }.reduce(_ && _)
    val errorMessages = results.map { r =>
      (r._1, r._2.toString)
    }
    val checkLog = dqmState.getAllLog()

    DqmValidationResult(passed, snapshot, errorMessages, checkLog)
  }
}

object DQMValidator {
  def readPersistedValidationFile(path: String): Try[DqmValidationResult] =
    Try(DqmValidationResult.fromJson(SmvHDFS.readFromFile(path)))
}

/**
 * DqmValidator will generate DqmValidationResult, which has
 * @param passed whether the validation passed or not
 * @param errorMessages detailed messages for sub results which the passed flag depends on
 * @param checkLog useful logs for reporting
 **/
case class DqmValidationResult(
    passed: Boolean,
    dqmStateSnapshot: DqmStateSnapshot,
    errorMessages: Seq[(String, String)] = Nil,
    checkLog: Seq[String] = Nil
) {
  def toJSON() = write(this)(DefaultFormats)

  def isEmpty() = passed && checkLog.isEmpty
}

/** construct DqmValidationResult from JSON string */
private[smv] object DqmValidationResult {
  def fromJson(jsonStr: String) = {
    // get the java class for the case class, instead of its companion object
    val klass = new DqmValidationResult(true, null).getClass
    read[DqmValidationResult](jsonStr)(DefaultFormats, ManifestFactory.classType(klass))
  }
}

class SmvDqmValidationError(dqmValidationResult: DqmValidationResult, cause: Throwable = null)
  extends SmvRuntimeException(dqmValidationResult.toJSON(), cause)
