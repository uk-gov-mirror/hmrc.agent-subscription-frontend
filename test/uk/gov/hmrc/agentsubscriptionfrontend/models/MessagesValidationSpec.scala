package uk.gov.hmrc.agentsubscriptionfrontend.models

import uk.gov.hmrc.agentsubscriptionfrontend.models.Address._
import org.scalatestplus.play.{OneAppPerSuite, PlaySpec}
import play.api.data.validation.ValidationError

class MessagesValidationSpec extends PlaySpec with OneAppPerSuite {

  "renderErrors function" should {
    "concatenate invalid and blacklist error messages" in {
      val validationErrors = Set(ValidationError("error.postcode.invalid"), ValidationError("error.postcode.blacklisted"))
      val result: String = renderErrors(validationErrors)

      result mustEqual " You have entered an invalid postcode, You can't use the postcode you've entered"
    }

    "concatenate invalid and maxLength error messages" in {
      val addressLine = "IpwichoIpwichoIpwichoIpwicho"
      val maxLength = 40
      val validationErrors = Set(ValidationError("error.postcode.invalid"),
        ValidationError("error.address.maxLength", maxLength, addressLine))
      val result: String = renderErrors(validationErrors)

      result mustEqual s" You have entered an invalid postcode, Length of line $addressLine must be up to $maxLength"
    }

    "concatenate invalid and maxLength error messages for 2 lines" in {
      val addressLine1 = "IpwichoIpwichoIpwichoIpwicho"
      val addressLine2 = addressLine1 + "Ipwich"
      val maxLength = 40

      val validationErrors = Set(ValidationError("error.postcode.invalid"),
        ValidationError("error.address.maxLength", maxLength, addressLine1),
        ValidationError("error.address.maxLength", maxLength, addressLine2))
      val result: String = renderErrors(validationErrors)

      result mustEqual s" You have entered an invalid postcode, " +
        s"Length of line $addressLine1 must be up to $maxLength, " +
        s"Length of line $addressLine2 must be up to $maxLength"
    }
  }
}
