package com.dbrsn.versioned

import java.util

import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.serialization.{ Deserializer, Serde, Serializer }
import org.slf4j.{ Logger, LoggerFactory }
import scodec.bits.BitVector
import scodec.{ Codec, Err }

class SerializerError(error: Err) extends SerializationException(error.message)

class DeserializerError(error: Err) extends SerializationException(error.message)

class CodecSerializer[T](codec: Codec[T]) extends Serializer[T] {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  @throws[SerializerError]("if serialization failed. Stupid Java API unrecoverable explode in runtime in case of error")
  override def serialize(topic: String, data: T): Array[Byte] =
    codec.encode(data).fold(err => {
      logger.error(s"Serializer error: ${err.messageWithContext}")
      throw new SerializerError(err)
    }, _.toByteArray)

  override def close(): Unit = {}
}

class CodecDeserializer[T](codec: Codec[T]) extends Deserializer[T] {
  private val logger: Logger = LoggerFactory.getLogger(getClass)

  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  @SuppressWarnings(Array("org.wartremover.warts.Throw"))
  @throws[DeserializerError]("if deserialization failed. Stupid Java API unrecoverable explode in runtime in case of error")
  override def deserialize(topic: String, data: Array[Byte]): T =
    codec.decode(BitVector(data)).fold(err => {
      logger.error(s"Deserializer error: ${err.messageWithContext}")
      throw new DeserializerError(err)
    }, _.value)

  override def close(): Unit = {}
}

class CodecKafkaSerde[T](codec: Codec[T]) extends Serde[T] {
  override def configure(configs: util.Map[String, _], isKey: Boolean): Unit = {}

  override def close(): Unit = {}

  override lazy val serializer: Serializer[T] = new CodecSerializer(codec)

  override lazy val deserializer: Deserializer[T] = new CodecDeserializer(codec)
}
