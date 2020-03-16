# A trading bot implementation using [BUX](https://getbux.com/) platform

The application uses [Netty](https://netty.io/) and [AsyncHttpClient](https://github.com/AsyncHttpClient/async-http-client) 
under the hood. Application's configuration and pre-defined trades are loaded on startup. 
Then app subscribes and listens quotes updates from WebSocket channel and
sends BUY/SELL requests by executing HTTP requests. For simplicity, pre-defined trades and all 
open positions are stored in memory. I think it's fine for that example, because in 
production-ready application it's possible to initially load all open positions (and pre-defined trades) from the server.

It's possible to put multiple pre-defined trades for different product ids. But only a single definition for one 
specific id. If there are multiple trades defined for one product, only the last one will be considered.

### Build and Run
Application is packed into Docker image. It's possible to run it through docker-compose or just docker.


Build a project with gradle:
```
./gradlew clean build buildDockerImage
```

Put your trades into config (local or dev environment):
```
{PROJECT_DIR}/docker/application_dev.conf
```


Start and see logs docker-compose from `{PROJECT_DIR}/docker` directory ((local or dev environment)):
```
docker-compose -f docker-compose_dev.yml up -d
docker-compose -f docker-compose_dev.yml logs -f app
```
