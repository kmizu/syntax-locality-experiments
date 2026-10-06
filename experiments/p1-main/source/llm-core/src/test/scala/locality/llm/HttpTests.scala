package locality.llm

import com.sun.net.httpserver.{HttpServer, HttpExchange}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import locality.llm.json.*

object HttpTests:
  private def withServer(body: HttpExchange => Unit)(test: String => Unit): Unit =
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0)
    val serverFailure = new java.util.concurrent.atomic.AtomicReference[Throwable]()
    server.createContext("/", exchange =>
      try body(exchange)
      catch case e: Throwable =>
        serverFailure.set(e)
        try reply(exchange,500,"test server assertion failed")
        catch case _: Throwable => exchange.close()
    )
    server.start()
    try
      test(s"http://127.0.0.1:${server.getAddress.getPort}/v1")
      if serverFailure.get() != null then throw serverFailure.get()
    finally server.stop(0)
  private def reply(e: HttpExchange, status: Int, body: String): Unit =
    val bytes = body.getBytes(UTF_8)
    e.sendResponseHeaders(status,bytes.length)
    try e.getResponseBody.write(bytes)
    finally e.close()
  private val request = LlmRequest("gpt-5.6-terra", "日本語 \"instructions\"\n", Vector(TextMessage("user","text\\content\t😀")), Some("low"),8192)
  def register(): Unit =
    TestHarness.test("http", "Generation wire bytes equal the persisted canonical request including ordered messages and Unicode") {
      val ordered = request.copy(input=Vector(TextMessage("user","first\n😀"),TextMessage("assistant","quoted \"answer\""),TextMessage("user","last\\日本語")))
      val persisted = Json.canonical(Json.obj(
        "model" -> Str(ordered.model), "instructions" -> Str(ordered.instructions),
        "input" -> Arr(ordered.input.map(m => Json.obj("role" -> Str(m.role),"content" -> Str(m.content)))),
        "max_output_tokens" -> Num(BigDecimal(8192)), "reasoning" -> Json.obj("effort" -> Str("low")),
        "store" -> Bool(false), "stream" -> Bool(false), "truncation" -> Str("disabled")
      )).getBytes(UTF_8)
      withServer { e =>
        val actual = e.getRequestBody.readAllBytes()
        assert(java.util.Arrays.equals(actual,persisted),"Generation HTTP body must be byte-identical to the persisted canonical request")
        reply(e,200,ResponseTests.completed)
      } { base =>
        assert(new OpenAiClient(OpenAiConfig("fixture-secret",base)).generate(ordered).isRight)
      }
    }
    TestHarness.test("http", "Token-count wire bytes equal the persisted canonical count request and omit generation settings") {
      val persisted = Json.canonical(Json.obj(
        "model" -> Str(request.model), "instructions" -> Str(request.instructions),
        "input" -> Arr(request.input.map(m => Json.obj("role" -> Str(m.role),"content" -> Str(m.content))))
      )).getBytes(UTF_8)
      withServer { e =>
        assert(e.getRequestURI.getPath == "/v1/responses/input_tokens")
        val actual = e.getRequestBody.readAllBytes()
        assert(java.util.Arrays.equals(actual,persisted),"Token-count HTTP body must be byte-identical to the persisted canonical count request")
        val fields = Json.parse(new String(actual,UTF_8)).toOption.get.asInstanceOf[Obj].fields.map(_._1).toSet
        assert(fields == Set("model","instructions","input"))
        reply(e,200,"{\"input_tokens\":31}")
      } { base =>
        assert(new OpenAiClient(OpenAiConfig("fixture-secret",base)).countInputTokens(request) == Right(31L))
      }
    }
    TestHarness.test("http", "Generation and token-count use separate exact schemas, optional headers, and one attempt") {
      val attempts = new AtomicInteger()
      withServer { e =>
        attempts.incrementAndGet()
        assert(e.getRequestMethod == "POST")
        assert(e.getRequestHeaders.getFirst("Authorization") == "Bearer test-secret")
        assert(e.getRequestHeaders.getFirst("OpenAI-Project") == "proj-test")
        assert(e.getRequestHeaders.getFirst("OpenAI-Organization") == null)
        val data = Json.parse(new String(e.getRequestBody.readAllBytes(),UTF_8)).toOption.get.asInstanceOf[Obj]
        assert(data.get("model").contains(Str("gpt-5.6-terra")))
        assert(data.get("instructions").contains(Str(request.instructions)))
        val expectedInput = Arr(Vector(Json.obj("role"->Str("user"),"content"->Str(request.input.head.content))))
        assert(data.get("input").exists(v => Json.canonical(v) == Json.canonical(expectedInput)))
        if e.getRequestURI.getPath == "/v1/responses/input_tokens" then
          assert(data.fields.map(_._1).toSet == Set("model","instructions","input"))
          reply(e,200,"{\"input_tokens\":31,\"object\":\"response.input_tokens\"}")
        else
          assert(e.getRequestURI.getPath == "/v1/responses")
          assert(data.get("store").contains(Bool(false)))
          assert(data.get("stream").contains(Bool(false)))
          assert(data.get("truncation").contains(Str("disabled")))
          assert(data.get("max_output_tokens").contains(Num(BigDecimal(8192))))
          e.getResponseHeaders.add("x-request-id","req_server")
          reply(e,200,ResponseTests.completed)
      } { base =>
        val client = new OpenAiClient(OpenAiConfig("test-secret",base,project=Some("proj-test")))
        assert(client.countInputTokens(request) == Right(31L))
        val result = client.generate(request).toOption.get
        assert(result.text == "5916" && result.requestId.contains("req_server"))
        assert(attempts.get == 2)
      }
    }
    TestHarness.test("http", "API failure preserves status and Retry-After while redacting secrets without hidden retry") {
      val attempts = new AtomicInteger()
      withServer { e =>
        attempts.incrementAndGet()
        e.getResponseHeaders.add("retry-after","3")
        e.getResponseHeaders.add("x-request-id","req_429")
        reply(e,429,"{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"test-secret exceeded\"}}")
      } { base =>
        val result = new OpenAiClient(OpenAiConfig("test-secret",base)).generate(request).swap.toOption.get.asInstanceOf[ApiError]
        assert(result.httpStatus == 429 && result.code.contains("rate_limit_exceeded"))
        assert(result.retryAfter.contains("3") && result.requestId.contains("req_429"))
        assert(!result.toString.contains("test-secret") && result.rawBody.contains("[REDACTED]"))
        assert(attempts.get == 1)
      }
    }
    TestHarness.test("http", "Redirect is never followed and credentials do not reach another endpoint") {
      val attempts = new AtomicInteger()
      withServer { e =>
        attempts.incrementAndGet()
        e.getResponseHeaders.add("Location", "/other")
        reply(e,302,"redirect")
      } { base =>
        assert(new OpenAiClient(OpenAiConfig("secret",base)).generate(request).isLeft)
        assert(attempts.get == 1)
      }
    }
    TestHarness.test("http", "Response limit is enforced while streaming and timeout yields ambiguous error") {
      withServer(e => reply(e,200,"x" * 4096)) { base =>
        val result = new OpenAiClient(OpenAiConfig("secret",base,maxBodyBytes=256)).generate(request)
        assert(result.left.toOption.get.isInstanceOf[TransportError])
      }
      withServer { e =>
        Thread.sleep(250)
        try reply(e,200,ResponseTests.completed)
        catch case _: java.io.IOException => e.close()
      } { base =>
        val result = new OpenAiClient(OpenAiConfig("secret",base,requestTimeout=Duration.ofMillis(30))).generate(request)
        assert(result.left.toOption.get.asInstanceOf[TransportError].ambiguousOutcome)
      }
    }
    TestHarness.test("http", "Reject unsafe endpoint and bad roles before HTTP; exceptions redact known secrets") {
      val badClient = new OpenAiClient(OpenAiConfig("test-secret","http://example.com/v1"))
      assert(badClient.generate(request).isLeft)
      val calls = new AtomicInteger()
      val fake = new HttpTransport:
        def post(url:String, headers:Map[String,String], body:String): Either[LlmError,HttpResult] =
          calls.incrementAndGet()
          throw new java.io.IOException("test-secret proj-secret")
      val config = OpenAiConfig("test-secret",project=Some("proj-secret"))
      val client = new OpenAiClient(config,fake)
      assert(client.generate(request.copy(input=Vector(TextMessage("tool","x")))).isLeft)
      assert(client.generate(request.copy(instructions=0xd800.toChar.toString)).left.toOption.get.isInstanceOf[DecodeError])
      assert(calls.get == 0)
      val error = client.generate(request).swap.toOption.get
      assert(!error.toString.contains("test-secret") && !error.toString.contains("proj-secret"))
      assert(!config.toString.contains("test-secret"))
    }
    TestHarness.test("http", "Token count rejects fractional, negative and overflowing tokens") {
      Vector("1.5","-1","9223372036854775808").foreach { value =>
        withServer(e => reply(e,200,s"{\"input_tokens\":$value}")) { base =>
          assert(new OpenAiClient(OpenAiConfig("secret",base)).countInputTokens(request).isLeft)
        }
      }
    }
    TestHarness.test("http", "JSON-escaped known secrets are masked in raw and decoded success and error fields") {
      val encoded = "test" + "\\u002d" + "secret"
      Vector(429 -> s"{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"$encoded exceeded\"}}",
        200 -> ResponseTests.completed.replace("\"59\"",s"\"$encoded\"")).foreach { (status, raw) =>
        val transport = new HttpTransport:
          def post(url:String, headers:Map[String,String], body:String): Either[LlmError,HttpResult] =
            Right(HttpResult(status,Map.empty,raw))
        val result = new OpenAiClient(OpenAiConfig("test-secret"),transport).generate(request)
        val sanitized = result.fold(_.asInstanceOf[ApiError].rawBody,_.rawBody)
        assert(!result.toString.contains("test-secret"))
        assert(!Json.render(Json.parse(sanitized).toOption.get).contains("test-secret"))
        assert(sanitized.contains("[REDACTED]"))
        assert(sanitized == raw.replace(encoded,"[REDACTED]"))
      }
    }
    TestHarness.test("http", "Terra max reasoning effort is accepted and serialized exactly") {
      val transport = new HttpTransport:
        def post(url:String, headers:Map[String,String], body:String): Either[LlmError,HttpResult] =
          assert(Json.parse(body).toOption.get.asInstanceOf[Obj].get("reasoning").contains(Json.obj("effort" -> Str("max"))))
          Right(HttpResult(200,Map.empty,ResponseTests.completed))
      val result = new OpenAiClient(OpenAiConfig("test-secret"),transport).generate(request.copy(reasoningEffort=Some("max")))
      assert(result.toOption.exists(_.text == "5916"))
    }
    TestHarness.test("http", "Count diagnostics preserves redacted raw success and response headers, clears on take and before a new count") {
      val raw = "{ \"input_tokens\": 31, \"unknown\": \"test" + "\\u002d" + "secret\" }"
      withServer { e =>
        e.getResponseHeaders.add("X-Request-Id","req-test-secret")
        e.getResponseHeaders.add("Authorization","Bearer test-secret")
        if e.getRequestURI.getPath.endsWith("input_tokens") then reply(e,200,raw)
        else reply(e,200,ResponseTests.completed)
      } { base =>
        val client = new OpenAiClient(OpenAiConfig("test-secret",base))
        assert(client.takeCountHttpResult().isEmpty)
        assert(client.countInputTokens(request) == Right(31L))
        val response = client.takeCountHttpResult().get
        assert(response.status == 200 && response.body == "{ \"input_tokens\": 31, \"unknown\": \"[REDACTED]\" }")
        assert(response.header("x-request-id").contains("req-[REDACTED]"))
        assert(!response.headers.keys.exists(_.equalsIgnoreCase("authorization")))
        assert(!response.toString.contains("test-secret"))
        assert(client.takeCountHttpResult().isEmpty)
        assert(client.countInputTokens(request) == Right(31L))
        assert(client.generate(request).isRight)
        assert(client.takeCountHttpResult().get.body.contains("input_tokens"), "Generate must not replace count diagnostics")
        assert(client.countInputTokens(request) == Right(31L))
        assert(client.countInputTokens(request.copy(input=Vector(TextMessage("tool","bad")))).isLeft)
        assert(client.takeCountHttpResult().isEmpty, "A failed validation must not expose an earlier response")
      }
    }
    TestHarness.test("http", "Count diagnostics preserves API failures and invalid schemas before error mapping") {
      val cases = Vector(
        (429,"{\"error\":{\"code\":\"rate_limit_exceeded\",\"message\":\"test-secret exceeded\"}}"),
        (200,"{\"input_tokens\":1.5,\"extra\":\"test-secret\"}"),
        (200,"not JSON test-secret")
      )
      cases.foreach { (status,raw) =>
        withServer { e =>
          e.getResponseHeaders.add("X-Request-Id","req_count")
          e.getResponseHeaders.add("Retry-After","7")
          reply(e,status,raw)
        } { base =>
          val client = new OpenAiClient(OpenAiConfig("test-secret",base))
          assert(client.countInputTokens(request).isLeft)
          val response = client.takeCountHttpResult().get
          assert(response.status == status && response.body == raw.replace("test-secret","[REDACTED]"))
          assert(response.header("x-request-id").contains("req_count"))
          assert(response.header("retry-after").contains("7"))
          assert(client.takeCountHttpResult().isEmpty)
        }
      }
    }
    TestHarness.test("http", "Count diagnostic slots are isolated between concurrent workers on a shared client") {
      withServer { e =>
        val data = Json.parse(new String(e.getRequestBody.readAllBytes(),UTF_8)).toOption.get.asInstanceOf[Obj]
        val index = data.get("instructions").get.asInstanceOf[Str].value.stripPrefix("worker-").toInt
        e.getResponseHeaders.add("X-Request-Id",s"req_worker_$index")
        reply(e,200,s"{\"input_tokens\":${100 + index}}")
      } { base =>
        val client = new OpenAiClient(OpenAiConfig("test-secret",base))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        val allCounted = new java.util.concurrent.CountDownLatch(4)
        try
          val futures = (0 until 4).map { index =>
            pool.submit(new java.util.concurrent.Callable[Unit]:
              def call(): Unit =
                val counted = client.countInputTokens(request.copy(instructions=s"worker-$index"))
                allCounted.countDown()
                assert(allCounted.await(5,java.util.concurrent.TimeUnit.SECONDS))
                assert(counted == Right(100L + index))
                val response = client.takeCountHttpResult().get
                assert(response.body == s"{\"input_tokens\":${100 + index}}")
                assert(response.header("x-request-id").contains(s"req_worker_$index"))
                assert(client.takeCountHttpResult().isEmpty)
            )
          }
          futures.foreach(_.get(8,java.util.concurrent.TimeUnit.SECONDS))
          assert(client.takeCountHttpResult().isEmpty, "The coordinator must not consume a worker's response")
        finally pool.shutdownNow()
      }
    }
