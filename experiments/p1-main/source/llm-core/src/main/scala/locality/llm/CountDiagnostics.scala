package locality.llm

/** The most recent count HTTP response on the calling worker, cleared when read.
  * A new count call clears any previous value; validation or transport failure has no response.
  * Only redacted response data is exposed. The LlmClient interface remains unchanged.
  */
trait CountDiagnostics:
  def takeCountHttpResult(): Option[HttpResult]
