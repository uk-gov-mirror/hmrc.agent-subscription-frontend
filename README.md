# agent-subscription-frontend

[![Build Status](https://travis-ci.org/hmrc/agent-subscription-frontend.svg)](https://travis-ci.org/hmrc/agent-subscription-frontend) [ ![Download](https://api.bintray.com/packages/hmrc/releases/agent-subscription-frontend/images/download.svg) ](https://bintray.com/hmrc/releases/agent-subscription-frontend/_latestVersion)

This is a web frontend service whose domain is Subscriptions to Agent Services 
following the ROSM (Register Once Subscribe Many) pattern.


### Running the tests

    sbt test it:test


### Running the app locally

    ./run-local
    sm --start AGENT_MTD -f
    
## Continue URL

Agent Subscription journey can be integrated as part of external journey using `continue` url
parameter:
```
http://www.tax.service.gov.uk/agent-subscription/start?continue=/your-service/path?paramA=valueA
```
After successful subscription user will be redirected to Agent Services Account page and presented with `Continue with your journey` button.


### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html")
