package locality.bench.data

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

object Seeds:
  def derive(masterSeed: Long, namespace: String, index: Long): Long =
    val bytes = namespace.getBytes(UTF_8)
    val encoded = ByteBuffer.allocate(8 + 4 + bytes.length + 8).putLong(masterSeed).putInt(bytes.length).put(bytes).putLong(index).array()
    ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(encoded)).getLong
  def hash(text: String): String = MessageDigest.getInstance("SHA-256").digest(text.getBytes(UTF_8)).map(b => f"${b & 0xff}%02x").mkString
