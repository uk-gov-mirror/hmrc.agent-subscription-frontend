# agent-subscription-frontend

[![Build Status](https://travis-ci.org/hmrc/agent-subscription-frontend.svg)](https://travis-ci.org/hmrc/agent-subscription-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-subscription-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/agent-subscription-frontend/_latestVersion)

This is a web frontend service for agent-subscription. After signing in using government-gateway, agents can go through the steps to
register for Agent Services and obtain an HMRC-AS-AGENT enrolment, giving them access a range of functions available for interacting
with their clients. The domain is Subscriptions to Agent Services 
following the ROSM (Register Once Subscribe Many) pattern.


### Running the tests

    sbt test it:test
    
### Running the tests with coverage

    sbt clean coverageOn test it:test coverageReport    

### Running the app locally

    sm --start AGENT_ONBOARDING -r
    sm --stop AGENT_SUBSCRIPTION_FRONTEND
    sbt run
    
It should then be listening on port 9437

    browse http://localhost:9437/agent-subscription/start    
    
## Continue URL

Agent Subscription journey can be integrated as part of external journey using `continue` url
parameter:
```
http://www.tax.service.gov.uk/agent-subscription/start?continue=/your-service/path?paramA=valueA
```
After successful subscription user will be redirected to Agent Services Account page and presented with `Continue with your journey` button.


### License 

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
