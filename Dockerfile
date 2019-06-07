FROM openjdk
COPY target/*.jar .
ENV postgres=172.17.0.1
CMD ["java","-jar","catalogue-1.0.0.jar","--spring.cloud.consul.host=172.17.0.1"]
