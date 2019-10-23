package uk.gov.hmrc.agentsubscriptionfrontend.support

/** scenarios for testing different live session situations
  * The test session store does not use the SessionCache, so if we want to reproduce live behaviour we
  * need to simulate the features of it
  *
  *  */

sealed trait SessionTest

case object NormalSession extends SessionTest
case object SessionLost extends SessionTest

