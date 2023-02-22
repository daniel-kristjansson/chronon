package ai.chronon.online

import org.apache.spark.sql.catalyst.InternalRow
import org.apache.spark.sql.catalyst.expressions.GenericInternalRow
import org.apache.spark.sql.catalyst.util.{ArrayBasedMapData, ArrayData, GenericArrayData, MapData}
import org.apache.spark.sql.types
import org.apache.spark.unsafe.types.UTF8String

import java.util
import scala.collection.mutable
import scala.util.ScalaJavaConversions.IteratorOps

object SparkInternalRowConversions {
  // the identity function
  private def id(x: Any): Any = x

  // recursively convert sparks byte array based internal row to chronon's fetcher result type (map[string, any])
  // The purpose of this class is to be used on fetcher output in a fetching context
  // we take a data type and build a function that operates on actual value
  // we want to build functions where we only branch at construction time, but not at function execution time.
  def from(dataType: types.DataType): Any => Any = {
    val unguardedFunc: Any => Any = dataType match {
      case types.MapType(keyType, valueType, _) =>
        val keyConverter = from(keyType)
        val valueConverter = from(valueType)

        def mapConverter(x: Any): Any = {
          val mapData = x.asInstanceOf[MapData]
          val result = new util.HashMap[Any, Any]()
          val size = mapData.numElements()
          val keys = mapData.keyArray()
          val values = mapData.valueArray()
          var idx = 0
          while (idx < size) {
            result.put(keyConverter(keys.get(idx, keyType)), valueConverter(values.get(idx, valueType)))
            idx += 1
          }
          result
        }

        mapConverter
      case types.ArrayType(elementType, _) =>
        val elementConverter = from(elementType)

        def arrayConverter(x: Any): Any = {
          val arrayData = x.asInstanceOf[ArrayData]
          val size = arrayData.numElements()
          val result = new util.ArrayList[Any](size)
          var idx = 0
          while (idx < size) {
            result.add(elementConverter(arrayData.get(idx, elementType)))
            idx += 1
          }
          result
        }

        arrayConverter
      case types.StructType(fields) =>
        val funcs = fields.map { _.dataType }.map { from }
        val types = fields.map { _.dataType }
        val names = fields.map { _.name }
        val size = funcs.length

        def structConverter(x: Any): Any = {
          val internalRow = x.asInstanceOf[InternalRow]
          val result = new mutable.HashMap[Any, Any]()
          var idx = 0
          while (idx < size) {
            val value = internalRow.get(idx, types(idx))
            result.put(names(idx), funcs(idx)(value))
            idx += 1
          }
          result.toMap
        }

        structConverter
      case types.StringType =>
        def stringConvertor(x: Any): Any = x.asInstanceOf[UTF8String].toString

        stringConvertor
      case _ => id
    }
    def guardedFunc(x: Any): Any = if (x == null) x else unguardedFunc(x)
    guardedFunc
  }

  // recursively convert fetcher result type - map[string, any] to internalRow.
  // The purpose of this class is to be used on fetcher output in a fetching context
  // we take a data type and build a function that operates on actual value
  // we want to build functions where we only branch at construction time, but not at function execution time.
  def to(dataType: types.DataType): Any => Any = {
    val unguardedFunc: Any => Any = dataType match {
      case types.MapType(keyType, valueType, _) =>
        val keyConverter = to(keyType)
        val valueConverter = to(valueType)

        def mapConverter(x: Any): Any = {
          val mapData = x.asInstanceOf[util.HashMap[Any, Any]]
          val keyArray: ArrayData = new GenericArrayData(
            mapData.entrySet().iterator().toScala.map(_.getKey).map(keyConverter).toArray)
          val valueArray: ArrayData = new GenericArrayData(
            mapData.entrySet().iterator().toScala.map(_.getValue).map(valueConverter).toArray)
          new ArrayBasedMapData(keyArray, valueArray)
        }

        mapConverter
      case types.ArrayType(elementType, _) =>
        val elementConverter = to(elementType)

        def arrayConverter(x: Any): Any = {
          val arrayData = x.asInstanceOf[util.ArrayList[Any]]
          new GenericArrayData(arrayData.iterator().toScala.map(elementConverter).toArray)
        }

        arrayConverter
      case types.StructType(fields) =>
        val funcs = fields.map { _.dataType }.map { to }
        val names = fields.map { _.name }

        def structConverter(x: Any): Any = {
          val structMap = x.asInstanceOf[Map[Any, Any]]
          val valueArr =
            names.iterator.zip(funcs.iterator).map { case (name, func) => structMap.get(name).map(func).orNull }.toArray
          new GenericInternalRow(valueArr)
        }

        structConverter
      case types.StringType =>
        def stringConvertor(x: Any): Any = { UTF8String.fromString(x.asInstanceOf[String]) }

        stringConvertor
      case _ => id
    }
    def guardedFunc(x: Any): Any = if (x == null) x else unguardedFunc(x)
    guardedFunc
  }
}
