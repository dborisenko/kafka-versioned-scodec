package com.dbrsn.versioned

import java.time.LocalDate

import scodec.codecs.utf8_32
import scodec.{ Attempt, Codec, Err }

import scala.util.Try

object Codecs {
  final val LocalDateCodec: Codec[LocalDate] = utf8_32.exmap(
    v => Try(LocalDate.parse(v)).fold(e => Attempt.failure[LocalDate](Err(e.getMessage)), Attempt.successful),
    v => Attempt.successful(v.toString)
  )
}
