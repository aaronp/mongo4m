package pipelines.mongo

import java.util.Base64

import io.circe
import io.circe.Decoder.Result
import io.circe._
import io.circe.parser._
import io.circe.syntax._
import org.bson.BsonNumber
import org.bson.codecs.configuration.CodecRegistry
import org.bson.json.{JsonMode, JsonWriterSettings}
import org.bson.types.{Decimal128, ObjectId}
import org.mongodb.scala.bson.collection._
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonArray, BsonDocument, BsonString}
import org.mongodb.scala.{Document, MongoClient}

import scala.util.Try

/**
  * TODO find or create a library which maps BsonValues to circe json types
  */
object BsonUtil {
  def asDocument[A: Encoder](value: A): BsonDocument = {
    asBson(value.asJson) match {
      case doc: BsonDocument => doc
      case other             => sys.error(s"$value was not a document but $other")
    }
  }

  var defaultRegistry: CodecRegistry = MongoClient.DEFAULT_CODEC_REGISTRY

  def bsonAsDocument(bson: Bson)(implicit registry: CodecRegistry = defaultRegistry): BsonDocument = {
    bson.toBsonDocument(classOf[BsonDocument], registry)
  }

  object codec {
    implicit object encoder extends Encoder[Bson] {
      override def apply(a: Bson): Json = {
        val doc = bsonAsDocument(a)
        fromBson(doc).get
      }
    }
    implicit object decoder extends Decoder[Bson] {
      override def apply(c: HCursor): Result[Bson] = {
        c.focus match {
          case Some(json) => Right(asDocument(json))
          case None       => Left(DecodingFailure(s"Couldn't decode $c", c.history))
        }
      }
    }
  }
  def asBson(json: Json): org.bson.BsonValue = {
    import scala.collection.JavaConverters._
    json.fold[org.bson.BsonValue](
      org.bson.BsonNull.VALUE,
      org.bson.BsonBoolean.valueOf,
      (jsonNum: JsonNumber) => numAsBson(jsonNum),
      jsonString => new BsonString(jsonString),
      jsonArray => new BsonArray(jsonArray.map(asBson).asJava),
      (jsonObject: JsonObject) => {
        val bsonMap = jsonObject.toMap.mapValues(asBson)
        BsonDocument(bsonMap)
      }
    )
  }

  def bsonValueAsJson(value: org.bson.BsonValue): Json = {
    def asDouble = {
      Json.fromDouble(value.asDouble().doubleValue()).getOrElse{
        Json.fromBigDecimal(value.asDecimal128().getValue.bigDecimalValue)
      }
    }
    import scala.collection.JavaConverters._
    if (value.isArray) {
      val a: BsonArray = value.asArray()
      val records      = a.getValues.asScala.map(bsonValueAsJson)
      Json.arr(records: _*)
    } else if (value.isBinary) {
      val binaryData = value.asBinary.getData
      val base64     = Base64.getEncoder.encodeToString(binaryData)
      Json.fromString(base64)
    } else if (value.isBoolean) {
      Json.fromBoolean(value.asBoolean().getValue)
    } else if (value.isDateTime) {
      Json.fromLong(value.asDateTime().getValue)
    } else if (value.isDBPointer) {
      Json.obj(
        "pointer" -> Json.fromString(value.asDBPointer().getNamespace),
        "id"      -> Json.fromString(value.asDBPointer().getId.toHexString)
      )
    } else if (value.isDecimal128) {
      asDouble
    } else if (value.isDocument) {
      fromBson(value.asDocument()).get
    } else if (value.isDouble) {
      asDouble
    } else if (value.isInt32) {
      Json.fromInt(value.asInt32().intValue())
    } else if (value.isInt64) {
      Json.fromInt(value.asInt64().intValue())
    } else if (value.isJavaScript) {
      Json.fromString(value.asJavaScript().getCode())
    } else if (value.isNull) {
      Json.Null
    } else if (value.isNumber) {
      asDouble
    } else if (value.isObjectId) {
      Json.fromString(value.asObjectId().getValue.toHexString)
    } else if (value.isRegularExpression) {
      Json.fromString(value.asRegularExpression().getPattern)
    } else if (value.isString) {
      Json.fromString(value.asString().getValue)
    } else if (value.isSymbol) {
      Json.fromString(value.asSymbol().getSymbol)
    } else if (value.isTimestamp) {
      Json.fromLong(value.asTimestamp().getValue)
    } else {
      sys.error(s"Unhandled bson value $value")
    }
  }

  def numAsBson(jsonNum: JsonNumber): BsonNumber = {
    val intOpt  = jsonNum.toInt.map(x => new org.bson.BsonInt32(x))
    def longOpt = jsonNum.toLong.map(x => new org.bson.BsonInt64(x))
    def decOpt = jsonNum.toBigDecimal.map { x: BigDecimal =>
      val one28: Decimal128 = new Decimal128(x.bigDecimal)
      new org.bson.BsonDecimal128(one28)
    }
    intOpt.orElse(longOpt).orElse(decOpt).getOrElse {
      val one28: Decimal128 = new Decimal128(BigDecimal(jsonNum.toDouble).bigDecimal)
      new org.bson.BsonDecimal128(one28)
    }
  }

  def asMutableDocument(json: Json): mutable.Document = mutable.Document(json.noSpaces)

  def parse[A: Decoder](doc: immutable.Document): Either[circe.Error, A] = {
    decode[A](doc.toJson())
  }

  private def cleanBsonNums(obj: JsonObject): Json = {
    def convert(value: Json) = {
      val numOpt = value.asNumber.map(Json.fromJsonNumber)
      numOpt.getOrElse {
        val numberWang = value.asString.map(args4c.unquote).get
        if (numberWang.contains(".")) {
          Json.fromBigDecimal(BigDecimal(numberWang).bigDecimal)
        } else {
          Json.fromLong(numberWang.toLong)
        }
      }
    }
    obj.size match {
      case 1 =>
        obj.toMap.head match {
          case ("$numberDecimal", value) => convert(value)
          case ("$numberLong", value)    => convert(value)
          case (name, value)             => Json.obj(name -> sanitizeFromBson(value))
        }
      case _ => Json.fromJsonObject(obj.mapValues(sanitizeFromBson))
    }
  }

  /**
    * BSON will read back numeric values as:
    * {{{
    *   {
    *    "aLong" : {
    *      "$numberLong" : "123"
    *    }
    *   }
    * }}}
    *
    * Which sucks. This hack turns it into:
    * {{{
    *   {
    *    "aLong" : "123"
    *   }
    * }}}
    *
    * @param bsonValue
    * @return
    */
  def sanitizeFromBson(bsonValue: Json): Json = {
    bsonValue.fold[Json](
      Json.Null,
      Json.fromBoolean,
      Json.fromJsonNumber,
      Json.fromString,
      arr => Json.arr(arr: _*),
      cleanBsonNums
    )
  }

  private val bsonJsonSettings = JsonWriterSettings.builder.outputMode(JsonMode.RELAXED).build
  def idForDocument(doc: Document): String = {
    val oid: ObjectId = doc.getObjectId("_id")
    oid.toHexString
  }
  def fromBson(doc: Document): Try[Json] = {
    val jsonStr: String = doc.toJson(bsonJsonSettings)
    fromBson(jsonStr)
  }

  def fromBson(bson: String): Try[Json] = {
    io.circe.parser.decode[Json](bson).toTry.map(sanitizeFromBson)
  }
}
