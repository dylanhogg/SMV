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

import org.scalatest._

import scala.collection.mutable.ListBuffer
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.{AbstractFile, VirtualDirectory}
import scala.tools.nsc.reporters.StoreReporter
import scala.tools.nsc.{Settings, Global}

class ClassCRCTest extends FunSuite with Matchers {
  test("test two classes have different CRC") {
    val crc1 = ClassCRC("org.tresamigos.smv.ClassCRCTest")
    val crc2 = ClassCRC("org.tresamigos.smv.SmvModule")

    assert(crc1.crc != crc2.crc)
  }

  test("test same class has same CRC") {
    val crc1 = ClassCRC("org.tresamigos.smv.ClassCRCTest")
    val crc2 = ClassCRC("org.tresamigos.smv.ClassCRCTest")

    assert(crc1.crc == crc2.crc)
  }

  test("test for invalid class name CRC") {
    intercept[ClassNotFoundException] {
      val crc = ClassCRC("org.tresamigos.class_does_not_exist")
      crc.crc
    }
  }

  def jarpath(name: String) = sys.props("user.home") + s"/.m2/repository/org/scala-lang/${name}/2.10.4/${name}-2.10.4.jar"

  val compiler: Global = {
    val settings = new Settings()
    // settings.processArguments(List("-usejavacp"), processAll = true)
    settings.bootclasspath.append(jarpath("scala-library"))
    // settings.bootclasspath.append(jarpath("scala-reflect"))
    // settings.bootclasspath.append(jarpath("scala-compiler"))
    // settings.bootclasspath.append(sys.props("user.home") + "/.ivy2/cache/org.scala-lang/scala-library/jars/scala-library-2.10.4.jar")
    // settings.bootclasspath.append(sys.props("user.home") + "/.ivy2/cache/org.scala-lang/scala-library/jars/scala-reflect-2.10.4.jar")
    // settings.bootclasspath.append(sys.props("user.home") + "/.ivy2/cache/org.scala-lang/scala-library/jars/scala-compiler-2.10.4.jar")
    val compiler = new Global(settings, new StoreReporter)
    compiler.settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))
    compiler
  }

  // tests that involve compilers can run in sbt, but not in mvn via scalatest-mvn plugin
  // with a NoSuchMethodError thrown in scala.reflect
  // temporarily ignore the 2 tests
  ignore("Adding comments to source code should not change bytecode checksum") {
    val crc1 = """object crc_1 {
    |  def run: Int = 1
    |}""".stripMargin

    val crc2 = "// adding comments should not change checksum\n" + crc1

    val res1 = singleClassBytecode(crc1)
    val res2 = singleClassBytecode(crc2)

    cksum(res1) shouldBe cksum(res2)
  }

  def singleClassBytecode(scalaCode: String): Array[Byte] = compile(scalaCode)(0)._2

  def compile(scalaCode: String): List[(String, Array[Byte])] = {
    new compiler.Run().compileSources(List(new BatchSourceFile("source.scala", scalaCode)))
    getGeneratedClassfiles(compiler.settings.outputDirs.getSingleOutput.get)
  }

  def getGeneratedClassfiles(outDir: AbstractFile): List[(String, Array[Byte])] = {
    def files(dir: AbstractFile): List[(String, Array[Byte])] = {
      val res = ListBuffer.empty[(String, Array[Byte])]
      for (f <- dir.iterator) {
        if (!f.isDirectory) res += ((f.name, f.toByteArray))
        else if (f.name != "." && f.name != "..") res ++= files(f)
      }
      res.toList
    }
    files(outDir)
  }

  def cksum(bytecode: Array[Byte]): Long = ClassCRC.checksum(bytecode).getValue

  /** If the compiled target is a scala object, make sure add a '$' to the classname */
  def sameChecksum(src1: String, src2: String, classname: String): Boolean = {
    val r1 = compile(src1)
    val r2 = compile(src2)

    val currCl = getClass.getClassLoader
    val cl1 = new CompiledClassesFirstClassLoader(r1, currCl)
    val cl2 = new CompiledClassesFirstClassLoader(r2, currCl)

    ClassCRC.checksum(classname, cl1).getValue == (ClassCRC.checksum(classname, cl2)).getValue
  }

  // TODO: tracing changes through reference (instead of inheritance)
  // is not yet working
  ignore("Updating referenced constants should change bytecode checksum") {
    val crc1 = """object Constants {
    |  val Name1 = "value1"
    |}
    |object crc_1 {
    |  def run = Constants.Name1
    |}""".stripMargin

    val crc2 = """object Constants {
    |  val Name1 = "value2"
    |}
    |object crc_1 {
    |  def run = Constants.Name1
    |}""".stripMargin

    val res1 = compile(crc1).filter(_._1 == "crc_1$.class")(0)._2
    val res2 = compile(crc2).filter(_._1 == "crc_1$.class")(0)._2

    cksum(res1) shouldNot equal(cksum(res2))
  }

  ignore("#316: Changing code should result in checksum change") {
    val src1 = "object A { def f1(a:Int):Int = a }"
    val src2 = "object A { def f1(a:Int):Int = a + 1 }"
    sameChecksum(src1, src2, "A$") shouldBe false
  }

  ignore("Changing base class should result in checksum change") {
    val src1 = """
    | trait A
    | trait B extends A
    | trait C
    | abstract class D
    | object E extends D with B with C
    |""".stripMargin
    val src2 = """
    | trait A
    | trait B extends A { def f1(a:Int): Int = a }
    | trait C
    | abstract class D
    | object E extends D with B with C
    |""".stripMargin

    sameChecksum(src1, src2, "E$") shouldBe false
  }
}

/** A classloader for classes compiled during test */
class CompiledClassesFirstClassLoader(compiled: Seq[(String, Array[Byte])],
  parent: ClassLoader) extends ClassLoader(parent) {
  override def findClass(fqn: String) =
    compiled.find(_._1 == fqn + ".class") map (x =>
      defineClass(fqn, x._2, 0, x._2.length)) getOrElse super.findClass(fqn)

  /** ClassCRC calls this method to load the byte array */
  override def getResourceAsStream(fqn: String) = {
    val name = fqn.replace('/', '.')
    compiled.find(_._1 == name) map (x =>
      new java.io.ByteArrayInputStream(x._2)) getOrElse super.getResourceAsStream(name)
  }
}
