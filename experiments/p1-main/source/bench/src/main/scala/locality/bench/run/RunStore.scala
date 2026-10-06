package locality.bench.run

import java.nio.file.{Path, Files, StandardCopyOption, StandardOpenOption}
import java.nio.channels.{FileChannel, OverlappingFileLockException}
import java.nio.charset.StandardCharsets.UTF_8
import locality.llm.json.*

final class RunStore(val dir: Path) extends AutoCloseable:
  Files.createDirectories(dir)
  private val channel = FileChannel.open(dir.resolve(".writer.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
  private val lock = try
    Option(channel.tryLock()).getOrElse(throw new IllegalStateException("Run directory already has a writer"))
  catch
    case _: OverlappingFileLockException => channel.close(); throw new IllegalStateException("Run directory already has a writer")
    case e: Throwable => channel.close(); throw e
  def path(name: String): Path =
    val result = dir.toAbsolutePath.normalize.resolve(name).normalize
    require(result.startsWith(dir.toAbsolutePath.normalize), "Path escapes run directory")
    result
  def atomic(name: String, body: String): Unit = synchronized {
    val dest = path(name)
    Files.createDirectories(dest.getParent)
    val tmp = Files.createTempFile(dest.getParent, dest.getFileName.toString, ".tmp")
    try
      Files.writeString(tmp, body, UTF_8)
      val file = FileChannel.open(tmp, StandardOpenOption.WRITE)
      try file.force(true) finally file.close()
      Files.move(tmp, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    finally Files.deleteIfExists(tmp)
  }
  def read(name: String): String = Files.readString(path(name), UTF_8)
  def exists(name: String): Boolean = Files.exists(path(name))
  def terminal(id: String): Option[JsonValue] =
    val name = s"trials/$id/terminal.json"
    if !exists(name) then None
    else
      import JsonSupport.*
      val value = parse(read(name))
      arr(field(value, "artifacts")).foreach { reference =>
        val file = str(reference, "path")
        require(exists(file) && sha(read(file)) == str(reference, "sha256"), s"Terminal backing artifact missing or changed: $file")
      }
      Some(value)
  def append(name: String, body: String): Unit = synchronized {
    require(!body.contains('\n'), "JSONL record must fit one physical line")
    val dest = path(name)
    Files.createDirectories(dest.getParent)
    val file = FileChannel.open(dest, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)
    try
      val bytes = UTF_8.encode(body + "\n")
      while bytes.hasRemaining do file.write(bytes)
      file.force(true)
    finally file.close()
  }
  def jsonLines(name: String): Vector[JsonValue] = synchronized {
    if !exists(name) then Vector.empty
    else
      val text = read(name)
      val lines = text.split("\n", -1).toVector
      var complete = lines.dropRight(1)
      if !text.endsWith("\n") && text.nonEmpty then
        Json.parse(lines.last) match
          case Right(_) =>
            complete = lines
            atomic(name, text + "\n")
          case Left(error) if error.offset >= lines.last.length =>
            atomic(name + ".quarantine", lines.last)
            atomic(name, complete.mkString("", "\n", if complete.nonEmpty then "\n" else ""))
          case Left(error) => throw new IllegalArgumentException(s"Corrupt $name final record: $error")
      complete.zipWithIndex.map { (line, index) =>
        Json.parse(line).fold(e => throw new IllegalArgumentException(s"Corrupt $name record ${index + 1}: $e"), identity)
      }
  }
  def close(): Unit =
    lock.release()
    channel.close()
