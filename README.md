# kafka-versioned-scodec

[![Build Status](https://travis-ci.org/dborisenko/kafka-versioned-scodec.svg?branch=master)](https://travis-ci.org/dborisenko/kafka-versioned-scodec)
[![Maven Central](https://img.shields.io/maven-central/v/com.dbrsn/kafka-versioned-scodec_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.dbrsn/kafka-versioned-scodec_2.12)
[![License](https://img.shields.io/badge/license-MIT-brightgreen.svg)](LICENSE)

You can find blog post about this approach [here](http://dbrsn.com/2018-05-06-versioned-kafka-binary-protocol-with-scodec/).

As always, Scala (more precisely, Typelevel stack) can help us to simplify this huge mess with data schemas for Kafka in traditionally concise, generic and clean way. Typelevel stack contains pretty old but still nice library [scodec](https://github.com/scodec/scodec). Scodec is scodec is a combinator library for working with binary data. It focuses on contract-first and pure functional encoding and decoding of binary data and provides integration into shapeless. It does not support versioning from the box and it's really pretty simple. But it's simplicity is the main advantage. Together with composability.

Let's start with the example of usage. Let's define simple data structure `User` (of version v1):

```scala
/**
 * User data structure v1.
 */
final case class User(
  email: String,
  name: Option[String],
  activated: Boolean
)
```

Let's create a simple codec for this data type using scodec.

```scala
import scodec.codecs._
import scodec.Codec
import shapeless.{ ::, HNil }

val userCodecV1: Codec[User] = (utf8_32 :: optional(bool, utf8_32) :: bool).xmap(
  { case email :: name :: activated :: HNil => User(email, name, activated) },
  u => u.email :: u.name :: u.activated :: HNil
)
```

We started thinking about future evolution of data structure in advance. We did not want to involve any complex evolution scenarious or any schema generations. We just want to keep our codec as much as possible in code. And we can start using a [versioned codec wrapper](https://github.com/dborisenko/kafka-versioned-scodec/blob/676ac520525c54f57f784d4a85c44ef7ca303e14/src/main/scala/com/dbrsn/versioned/VersionedCodec.scala) which allows us to do a simple versioning of all our possible formats. The example of code can be found [here](https://github.com/dborisenko/kafka-versioned-scodec/blob/676ac520525c54f57f784d4a85c44ef7ca303e14/src/test/scala/com/dbrsn/versioned/VersionedCodecSpec.scala).

After some time we might come to the idea that we need to add additional field to this data type. Let's say, we want to store total number of posts. Our data type might become:

```scala
/**
 * User data structure v2.
 */
final case class User(
  email: String,
  name: Option[String],
  activated: Boolean,
  numberOfPosts: Long
)

import scodec.codecs._
import scodec.Codec
import shapeless.{ ::, HNil }

// Here we make sure that old data produce correct new data type. Let's assume our statrtup number of posts is 0L.
val userCodecV1: Codec[User] = (utf8_32 :: optional(bool, utf8_32) :: bool).xmap(
  { case email :: name :: activated :: HNil => User(email, name, activated, 0L) },  // This is our migration schema because it produces new correct data type User.
  u => u.email :: u.name :: u.activated :: HNil
)

// But the new message codec already knows how to encode the new data type
val userCodecV2: Codec[User] = (utf8_32 :: optional(bool, utf8_32) :: bool :: int64).xmap(
  { case email :: name :: activated :: numberOfPosts :: HNil => User(email, name, activated, numberOfPosts) },
  u => u.email :: u.name :: u.activated :: u.numberOfPosts :: HNil
)
```

It's easy to see that here in one-liner we provide new version of codec with the newest field included and in another one-liner we provide the migration schema for to build new data type from the old stored binary. It is pretty easy, isn't it? And let's bundlem both of that versions together:

```scala
import com.dbrsn.versioned.VersionedCodec

val versionedCodec: Codec[User] = new VersionedCodec[User](
  entityIdentity = "User",  // Used as prefix in the binary file. Easy way to make sure that file format belongs to the given entity.
  currentVersion = 2,
  codecByVersion = {
    case 1 => userCodecV1
    case 2 => userCodecV2
  }
)
```

And this is how we can use it:

```scala
import scodec.Err
import scodec.bits.BitVector

val inUser: User = User("email@email.com", Some("Denis"), true, 123L)

val binUser: Either[Err, BitVector] = versionedCodec.encode(inUser).toEither
val outUser: Either[Err, User] = versionedCodec.decodeValue(binUser.right.get).toEither
```

This format is binary only. So, it does not have human readable representation. This can be counted as disadvantage of this approach.

This is how our user from the example looks like in HEX representation: 

```text
000000045573657200020000000f656d61696c40656d61696c2e636f6d80000002a232b734b9c00000000000001ec
```

And this is how it looks like in binary view:

```text
00000000: 0000 0004 5573 6572 0002 0000 000f 656d  ....User......em
00000010: 6169 6c40 656d 6169 6c2e 636f 6d80 0000  ail@email.com...
00000020: 02a2 32b7 34b9 c000 0000 0000 001e c0    ..2.4..........
```
