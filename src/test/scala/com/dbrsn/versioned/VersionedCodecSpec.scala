package com.dbrsn.versioned

import org.scalatest.{ Assertion, FlatSpec, Matchers }
import scodec.codecs._
import scodec.{ Codec, Err }
import shapeless.{ ::, HNil }

object CodecRoundTripSpec extends Matchers {
  @inline def throwError(err: Err): Nothing = throw new Exception(s"${err.message}: ${err.messageWithContext}")

  def roundTrip[T](data: T, codec: Codec[T]): Assertion = {
    val in = codec.encode(data).fold(throwError, identity)
    val out = codec.decode(in).fold(throwError, identity).value
    out shouldBe data
  }
}

class VersionedCodecSpec extends FlatSpec with Matchers {

  import com.dbrsn.versioned.CodecRoundTripSpec._

  private final val CodecV1: Codec[Data] = (utf8_32 :: uint16).xmap(
    { case str :: int :: HNil => Data(str, int, None, 0.0) },
    msg => msg.str1 :: msg.int :: HNil
  )

  private final val CodecV2: Codec[Data] = (utf8_32 :: uint16 :: double).xmap(
    { case str :: int :: dbl :: HNil => Data(str, int, None, dbl) },
    msg => msg.str1 :: msg.int :: msg.double :: HNil
  )

  private final val CodecV3: Codec[Data] = (utf8_32 :: uint16 :: optional(bool, utf8_32) :: double).xmap(
    { case str1 :: int :: str2 :: dbl :: HNil => Data(str1, int, str2, dbl) },
    msg => msg.str1 :: msg.int :: msg.str2 :: msg.double :: HNil
  )

  private case class Data(str1: String, int: Int, str2: Option[String], double: Double)

  private val versionedCodecV1: Codec[Data] = new VersionedCodec[Data](
    entityIdentity = "Data",
    currentVersion = 1,
    codecByVersion = {
      case 1 => CodecV1
    }
  )

  private val versionedCodecV2: Codec[Data] = new VersionedCodec[Data](
    entityIdentity = "Data",
    currentVersion = 2,
    codecByVersion = {
      case 1 => CodecV1
      case 2 => CodecV2
    }
  )

  private val versionedCodecV3: Codec[Data] = new VersionedCodec[Data](
    entityIdentity = "Data",
    currentVersion = 3,
    codecByVersion = {
      case 1 => CodecV1
      case 2 => CodecV2
      case 3 => CodecV3
    }
  )

  private val data = Data("test1", 2, Some("test3"), 0.4)

  it should "round-trip (encode/decode) custom message for existed version" in {
    val msgV1 = versionedCodecV1.encode(data).fold(throwError, identity)
    versionedCodecV1.decodeValue(msgV1).fold(throwError, identity) shouldBe Data("test1", 2, None, 0.0)

    val msgV2 = versionedCodecV2.encode(data).fold(throwError, identity)
    versionedCodecV2.decodeValue(msgV1).fold(throwError, identity) shouldBe Data("test1", 2, None, 0.0)
    versionedCodecV2.decodeValue(msgV2).fold(throwError, identity) shouldBe Data("test1", 2, None, 0.4)

    val msgV3 = versionedCodecV3.encode(data).fold(throwError, identity)
    versionedCodecV3.decodeValue(msgV1).fold(throwError, identity) shouldBe Data("test1", 2, None, 0.0)
    versionedCodecV3.decodeValue(msgV2).fold(throwError, identity) shouldBe Data("test1", 2, None, 0.4)
    versionedCodecV3.decodeValue(msgV3).fold(throwError, identity) shouldBe data

    roundTrip(data, versionedCodecV3)
  }

  it should "return error if message is not started with entity" in {
    val msg = CodecV3.encode(data).fold(throwError, identity)
    versionedCodecV3.decodeValue(msg).fold(Left(_), Right(_)) shouldBe Left(Err("Entity Identity test1 should be Data"))
    versionedCodecV2.decodeValue(msg).fold(Left(_), Right(_)) shouldBe Left(Err("Entity Identity test1 should be Data"))
    versionedCodecV1.decodeValue(msg).fold(Left(_), Right(_)) shouldBe Left(Err("Entity Identity test1 should be Data"))
  }

  it should "return error if version is not supported" in {
    val msgV3 = versionedCodecV3.encode(data).fold(throwError, identity)
    versionedCodecV2.decodeValue(msgV3).fold(Left(_), Right(_)) shouldBe Left(Err("Version 3 is not supported"))
    versionedCodecV1.decodeValue(msgV3).fold(Left(_), Right(_)) shouldBe Left(Err("Version 3 is not supported"))
  }
}
