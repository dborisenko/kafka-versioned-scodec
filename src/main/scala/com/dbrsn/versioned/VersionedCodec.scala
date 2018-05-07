package com.dbrsn.versioned

import com.dbrsn.versioned.VersionedCodec._
import scodec.bits.BitVector
import scodec.codecs._
import scodec.{ Attempt, Codec, DecodeResult, Err, SizeBound }
import shapeless.{ ::, HNil }

object VersionedCodec {
  type Version = Int
  type EntityId = String

  private final val VersionCodec: Codec[Version] = uint16
  private final val EntityIdCodec: Codec[EntityId] = utf8_32

  private def versionNotSupportedFailure(version: Version): Attempt[Nothing] =
    Attempt.failure(Err(s"Version $version is not supported"))

  private def entityIdentityDoNotMatchFailure(real: EntityId, expected: EntityId): Attempt[Nothing] =
    Attempt.failure(Err(s"Entity Identity $real should be $expected"))
}

class VersionedCodec[A](
  entityIdentity: EntityId,
  currentVersion: Version,
  codecByVersion: PartialFunction[Version, Codec[A]]
) extends Codec[A] {

  private def versionNotFoundFailedCodec(version: Version): Codec[A] = Codec[A](
    (_: A) => versionNotSupportedFailure(version),
    (_: BitVector) => versionNotSupportedFailure(version)
  )

  private val internalVersionedCodec: Codec[EntityId :: Version :: BitVector :: HNil] = EntityIdCodec :: VersionCodec :: bits

  override def sizeBound: SizeBound = SizeBound.unknown

  private def codecForVersion(version: Version): Codec[A] =
    codecByVersion.applyOrElse(version, versionNotFoundFailedCodec)

  override def decode(bits: BitVector): Attempt[DecodeResult[A]] = internalVersionedCodec.decode(bits).flatMap(_.value match {
    case `entityIdentity` :: version :: result :: HNil =>
      codecForVersion(version).decode(result)
    case otherEntity :: _ =>
      entityIdentityDoNotMatchFailure(otherEntity, entityIdentity)
  })

  override def encode(value: A): Attempt[BitVector] = for {
    bits <- codecForVersion(currentVersion).encode(value)
    result <- internalVersionedCodec.encode(entityIdentity :: currentVersion :: bits :: HNil)
  } yield result
}
