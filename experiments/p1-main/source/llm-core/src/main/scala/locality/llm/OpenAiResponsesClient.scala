package locality.llm

import java.time.Duration
import java.net.URI
import locality.llm.json.*
import scala.util.control.NonFatal

final case class OpenAiConfig(
  apiKey: String,
  baseUrl: String = "https://api.openai.com/v1",
  project: Option[String] = None,
  organization: Option[String] = None,
  connectTimeout: Duration = Duration.ofSeconds(15),
  requestTimeout: Duration = Duration.ofSeconds(180),
  maxBodyBytes: Int = 16 * 1024 * 1024
):
  override def toString: String = "OpenAiConfig([REDACTED])"

class OpenAiClient(config: OpenAiConfig, transport: HttpTransport) extends LlmClient with CountDiagnostics:
  def this(config: OpenAiConfig) = this(config, new JdkHttpTransport(config.connectTimeout, config.requestTimeout, config.maxBodyBytes, Vector(config.apiKey) ++ config.project ++ config.organization))
  private val redact = new Redactor(Vector(config.apiKey) ++ config.project ++ config.organization)
  private val countHttpResult = new ThreadLocal[HttpResult]()
  def takeCountHttpResult(): Option[HttpResult] =
    val result = Option(countHttpResult.get())
    countHttpResult.remove()
    result
  def generate(request: LlmRequest): Either[LlmError, LlmResponse] =
    send(request,count=false).flatMap(result => ResponseDecoder.decode(result.body,result.header("x-request-id")))
  def countInputTokens(request: LlmRequest): Either[LlmError, Long] =
    countHttpResult.remove()
    send(request,count=true).flatMap { result =>
      Json.parse(result.body) match
        case Right(obj: Obj) => obj.get("input_tokens") match
          case Some(num: Num) => num.toLongExact match
            case Right(n) if n >= 0 => Right(n)
            case _ => Left(DecodeError("input_tokens must be a nonnegative Long integer",result.body))
          case _ => Left(DecodeError("input_tokens must be a number",result.body))
        case _ => Left(DecodeError("Token-count response is not a JSON object",result.body))
    }
  private def safeError(error: LlmError): LlmError = error match
    case e: ApiError => e.copy(code=e.code.map(redact.apply),message=redact(e.message),retryAfter=e.retryAfter.map(redact.apply),requestId=e.requestId.map(redact.apply),rawBody=redact(e.rawBody))
    case e: DecodeError => e.copy(message=redact(e.message),rawBody=redact(e.rawBody))
    case e: TransportError => e.copy(message=redact(e.message))
  private def validate(request: LlmRequest): Either[LlmError, Unit] =
    try
      val uri = URI.create(config.baseUrl)
      val host = Option(uri.getHost).getOrElse("").toLowerCase(java.util.Locale.ROOT)
      val loopback = host == "localhost" || host == "127.0.0.1" || host == "[::1]" || host == "::1" || host.matches("127\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}")
      val safeEndpoint = uri.getScheme == "https" || uri.getScheme == "http" && loopback
      if !safeEndpoint || host.isEmpty || uri.getUserInfo != null || uri.getQuery != null || uri.getFragment != null then
        Left(TransportError("Base URL requires HTTPS or loopback HTTP, a host, and no userinfo/query/fragment",false))
      else if config.apiKey.isEmpty || (Vector(config.apiKey) ++ config.project ++ config.organization).exists(s => s.contains('\n') || s.contains('\r')) then
        Left(TransportError("Invalid authentication header configuration",false))
      else if request.model.isEmpty || request.maxOutputTokens <= 0 then Left(DecodeError("Model must be nonempty and maxOutputTokens positive",""))
      else if request.input.exists(m => !Set("user","assistant","developer","system").contains(m.role)) then
        Left(DecodeError("Unsupported message role",""))
      else if request.reasoningEffort.exists(e => !Set("none","low","medium","high","xhigh","max").contains(e)) then
        Left(DecodeError("Unsupported reasoning effort",""))
      else Right(())
    catch case NonFatal(e) => Left(TransportError(redact(e.toString),false))

  private def send(request: LlmRequest, count: Boolean): Either[LlmError, HttpResult] =
    validate(request).flatMap { _ =>
      try
        val body = if count then ResponsesRequestJson.countInputTokensBody(request) else ResponsesRequestJson.generationBody(request)
        val headers = Map("Authorization" -> s"Bearer ${config.apiKey}", "Content-Type" -> "application/json", "Accept" -> "application/json") ++
          config.project.filter(_.nonEmpty).map("OpenAI-Project" -> _) ++ config.organization.filter(_.nonEmpty).map("OpenAI-Organization" -> _)
        val suffix = if count then "/responses/input_tokens" else "/responses"
        transport.post(config.baseUrl.stripSuffix("/") + suffix,headers,body).left.map(safeError).flatMap { unsafe =>
          val result = unsafe.copy(body=redact(unsafe.body),headers=unsafe.headers.filterNot { (name,_) => name.equalsIgnoreCase("authorization") }.map { (name,values) => redact(name) -> values.map(redact.apply) })
          if count then countHttpResult.set(result)
          if result.status >= 200 && result.status < 300 then Right(result)
          else
            val error = Json.parse(result.body).toOption.collect { case obj: Obj => obj }.flatMap(_.get("error")).collect { case obj: Obj => obj }
            def errorString(key: String): Option[String] = error.flatMap(_.get(key)).collect { case Str(value) => value }
            Left(ApiError(result.status,errorString("code").orElse(errorString("type")),errorString("message").getOrElse(s"HTTP ${result.status}"),result.header("retry-after"),result.header("x-request-id"),result.body))
        }
      catch
        case e: IllegalArgumentException => Left(DecodeError(redact(e.toString),""))
        case NonFatal(e) => Left(TransportError(redact(e.toString),true))
    }

final class OpenAiResponsesClient(config: OpenAiConfig, transport: HttpTransport) extends OpenAiClient(config, transport):
  def this(config: OpenAiConfig) = this(config, new JdkHttpTransport(config.connectTimeout, config.requestTimeout, config.maxBodyBytes, Vector(config.apiKey) ++ config.project ++ config.organization))

object ResponseDecoder:
  private final case class Invalid(message: String) extends RuntimeException
  private final case class Message(index: Int, role: String, phase: Option[String], texts: Vector[SelectedTextPart], refusals: Vector[String])
  private def obj(value: JsonValue, path: String): Obj = value match
    case v: Obj => v
    case _ => throw Invalid(s"$path must be an object")
  private def field(value: Obj, key: String): JsonValue = value.get(key).getOrElse(throw Invalid(s"Missing required field $key"))
  private def string(value: JsonValue, path: String): String = value match
    case Str(v) => v
    case _ => throw Invalid(s"$path must be a string")
  private def array(value: JsonValue, path: String): Vector[JsonValue] = value match
    case Arr(v) => v
    case _ => throw Invalid(s"$path must be an array")
  private def optional(value: Obj, key: String): Option[JsonValue] = value.get(key).filter(_ != Null)
  private def optionalString(value: Obj, key: String): Option[String] = optional(value,key).map(v => string(v,key))
  private def number(value: JsonValue, path: String): Long = value match
    case num: Num => num.toLongExact match
      case Right(n) if n >= 0 => n
      case _ => throw Invalid(s"$path must be a nonnegative Long integer")
    case _ => throw Invalid(s"$path must be a number")
  private def root(raw: String): Obj = Json.parse(raw) match
    case Right(value) => obj(value,"response")
    case Left(error) => throw Invalid(s"Invalid JSON at ${error.offset}: ${error.reason}")
  private def messages(value: Obj): Vector[Message] =
    array(field(value,"output"),"output").zipWithIndex.flatMap { (item,index) =>
      val output = obj(item,"output item")
      string(field(output,"type"),"output item type") match
        case "message" =>
          val role = string(field(output,"role"),"message role")
          val phase = optionalString(output,"phase")
          val texts = Vector.newBuilder[SelectedTextPart]
          val refusals = Vector.newBuilder[String]
          array(field(output,"content"),"message content").zipWithIndex.foreach { (part,partIndex) =>
            val content = obj(part,"content part")
            string(field(content,"type"),"content type") match
              case "output_text" => texts += SelectedTextPart(index,partIndex,phase,string(field(content,"text"),"text"))
              case "refusal" => refusals += string(field(content,"refusal"),"refusal")
              case _ => ()
          }
          Some(Message(index,role,phase,texts.result(),refusals.result()))
        case _ => None
    }
  private def selected(value: Vector[Message]): Vector[SelectedTextPart] =
    val assistants = value.filter(_.role == "assistant")
    val phased = assistants.exists(_.phase.nonEmpty)
    assistants.filter(m => !phased || m.phase.exists(p => p == "final" || p == "final_answer")).flatMap(_.texts)
  private def usage(value: Obj): Option[TokenUsage] = optional(value,"usage").map { raw =>
    val v = obj(raw,"usage")
    val inputDetails = optional(v,"input_tokens_details").map(obj(_,"input_tokens_details"))
    val outputDetails = optional(v,"output_tokens_details").map(obj(_,"output_tokens_details"))
    def detail(details: Option[Obj], key: String): Option[Long] = details.flatMap(d => optional(d,key)).map(number(_,key))
    TokenUsage(number(field(v,"input_tokens"),"input_tokens"),number(field(v,"output_tokens"),"output_tokens"),number(field(v,"total_tokens"),"total_tokens"),detail(inputDetails,"cached_tokens"),detail(inputDetails,"cache_write_tokens"),detail(outputDetails,"reasoning_tokens"))
  }
  def decode(rawBody: String, requestId: Option[String] = None): Either[LlmError, LlmResponse] =
    try
      val v = root(rawBody)
      val all = messages(v)
      val reason = optional(v,"incomplete_details").map(obj(_,"incomplete_details")).flatMap(optionalString(_,"reason"))
      Right(LlmResponse(string(field(v,"id"),"id"),string(field(v,"model"),"model"),string(field(v,"status"),"status"),selected(all).map(_.text).mkString,all.filter(_.role == "assistant").flatMap(_.refusals),reason,usage(v),requestId,rawBody))
    catch case Invalid(message) => Left(DecodeError(message,rawBody))
  def selectedTextParts(rawBody: String): Either[DecodeError, Vector[SelectedTextPart]] =
    try Right(selected(messages(root(rawBody))))
    catch case Invalid(message) => Left(DecodeError(message,rawBody))

final case class SelectedTextPart(messageIndex: Int, partIndex: Int, phase: Option[String], text: String)
