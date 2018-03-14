import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object FrontendBuild extends Build with MicroService {

  val appName = "agent-subscription-frontend"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test() ++ test("it")

  val compile = Seq(
    ws,
    "uk.gov.hmrc" %% "frontend-bootstrap" % "8.19.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % "5.2.0",
    "uk.gov.hmrc" %% "play-partials" % "6.1.0",
    "uk.gov.hmrc" %% "http-caching-client" % "7.1.0",
    "uk.gov.hmrc" %% "passcode-verification" % "5.0.0",
    "uk.gov.hmrc" %% "domain" % "5.1.0",
    "uk.gov.hmrc" %% "agent-kenshoo-monitoring" % "2.4.0",
    "uk.gov.hmrc" %% "agent-mtd-identifiers" % "0.5.0",
    "de.threedimensions" %% "metrics-play" % "2.5.13",
    "org.typelevel" %% "cats" % "0.9.0"
  )

  def test(scope: String = "test") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
    "org.scalatest" %% "scalatest" % "3.0.4" % scope,
    "org.mockito" % "mockito-core" % "2.11.0" % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.0" % scope,
    "com.github.tomakehurst" % "wiremock" % "2.10.1" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "org.jsoup" % "jsoup" % "1.8.1" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "2.0.0" % scope
  )

}
