package locality.llm

import java.time.Duration
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse, HttpTimeoutException, HttpConnectTimeoutException}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.charset.CodingErrorAction
import java.util.concurrent.{CompletableFuture, CompletionStage, Flow, TimeUnit, TimeoutException, ExecutionException}
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

final case class HttpResult(status: Int, headers: Map[String, Vector[String]], body: String):
  def header(name: String): Option[String] = headers.find(_._1.equalsIgnoreCase(name)).flatMap(_._2.headOption)
trait HttpTransport:
  def post(url: String, headers: Map[String, String], body: String): Either[LlmError, HttpResult]

final class JdkHttpTransport(
  connectTimeout: Duration = Duration.ofSeconds(15),
  requestTimeout: Duration = Duration.ofSeconds(180),
  maxBodyBytes: Int = 16 * 1024 * 1024,
  secrets: Vector[String] = Vector.empty
) extends HttpTransport:
  require(!connectTimeout.isNegative && !connectTimeout.isZero, "Connect timeout must be positive")
  require(!requestTimeout.isNegative && !requestTimeout.isZero, "Request timeout must be positive")
  require(maxBodyBytes > 0, "Body limit must be positive")
  private val redact = new Redactor(secrets)
  private val client = HttpClient.newBuilder()
    .connectTimeout(connectTimeout)
    .followRedirects(HttpClient.Redirect.NEVER)
    .build()
  def post(url: String, headers: Map[String, String], body: String): Either[LlmError, HttpResult] =
    try
      val requestBuilder = HttpRequest.newBuilder(URI.create(url))
        .timeout(requestTimeout)
        .POST(HttpRequest.BodyPublishers.ofString(body, UTF_8))
      headers.foreach { (name, value) => requestBuilder.header(name, value) }
      val handler = new HttpResponse.BodyHandler[String]:
        def apply(info: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber[String] = new LimitedBody(maxBodyBytes)
      val future = client.sendAsync(requestBuilder.build(),handler)
      try
        val response = future.get(requestTimeout.toNanos, TimeUnit.NANOSECONDS)
        val safeHeaders = response.headers().map().asScala.toVector
          .filterNot(_._1.equalsIgnoreCase("authorization"))
          .map { (name, values) => name -> values.asScala.toVector.map(redact.apply) }.toMap
        Right(HttpResult(response.statusCode(),safeHeaders,redact(response.body())))
      catch
        case e: TimeoutException =>
          future.cancel(true)
          Left(TransportError(redact(s"HTTP request timed out after ${requestTimeout.toMillis}ms"),true))
        case e: InterruptedException =>
          future.cancel(true)
          Thread.currentThread().interrupt()
          Left(TransportError("HTTP request interrupted",true))
        case e: ExecutionException =>
          val cause = Option(e.getCause).getOrElse(e)
          cause match
            case _: java.nio.charset.CharacterCodingException => Left(DecodeError("HTTP body is not valid UTF-8", ""))
            case _: HttpConnectTimeoutException => Left(TransportError(redact(cause.toString),false))
            case _: java.net.ConnectException => Left(TransportError(redact(cause.toString),false))
            case _ => Left(TransportError(redact(cause.toString),true))
    catch
      case NonFatal(e) => Left(TransportError(redact(e.toString),false))

  private final class LimitedBody(limit: Int) extends HttpResponse.BodySubscriber[String]:
    private val promise = new CompletableFuture[String]()
    private val bytes = new java.io.ByteArrayOutputStream(math.min(limit,8192))
    private var subscription: Flow.Subscription = null
    def getBody(): CompletionStage[String] = promise
    def onSubscribe(value: Flow.Subscription): Unit =
      if subscription != null then value.cancel()
      else
        subscription = value
        value.request(1)
    def onNext(chunks: java.util.List[ByteBuffer]): Unit =
      try
        val size = chunks.asScala.foldLeft(0L)((total, buffer) => total + buffer.remaining())
        if size > limit.toLong - bytes.size() then
          subscription.cancel()
          promise.completeExceptionally(new java.io.IOException(s"HTTP body exceeds $limit bytes"))
        else
          chunks.asScala.foreach { buffer =>
            val chunk = new Array[Byte](buffer.remaining())
            buffer.get(chunk)
            bytes.write(chunk)
          }
          subscription.request(1)
      catch
        case NonFatal(e) =>
          subscription.cancel()
          promise.completeExceptionally(e)
    def onError(error: Throwable): Unit = promise.completeExceptionally(error)
    def onComplete(): Unit =
      try
        val decoder = UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        promise.complete(decoder.decode(ByteBuffer.wrap(bytes.toByteArray)).toString)
      catch case NonFatal(e) => promise.completeExceptionally(e)

final class Redactor(secrets: Vector[String]):
  private val variants = secrets.filter(_.nonEmpty).flatMap { value =>
    val escaped = locality.llm.json.Json.render(locality.llm.json.Str(value))
    Vector(value,escaped.substring(1,escaped.length-1))
  }.distinct.sortBy(s => -s.length)
  private def plain(value: String): String = variants.foldLeft(value)((text, secret) => text.replace(secret,"[REDACTED]"))
  def apply(value: String): String =
    val direct = plain(value)
    if variants.isEmpty || !direct.contains('"') then direct
    else
      val out = new java.lang.StringBuilder
      var cursor = 0
      while cursor < direct.length do
        if direct.charAt(cursor) != '"' then
          out.append(direct.charAt(cursor))
          cursor += 1
        else
          val start = cursor
          cursor += 1
          var closed = false
          while cursor < direct.length && !closed do
            direct.charAt(cursor) match
              case '\\' => cursor = math.min(cursor + 2,direct.length)
              case '"' =>
                cursor += 1
                closed = true
              case _ => cursor += 1
          val token = direct.substring(start,cursor)
          locality.llm.json.Json.parse(token) match
            case Right(locality.llm.json.Str(decoded)) =>
              val masked = plain(decoded)
              if masked == decoded then out.append(token)
              else out.append(locality.llm.json.Json.render(locality.llm.json.Str(masked)))
            case _ => out.append(token)
      out.toString
