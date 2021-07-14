FROM adoptopenjdk:11-jre-hotspot
RUN mkdir /opt/app
COPY out/artifacts/AddressSearch_jar/AddressSearch.jar /opt/app
COPY KEN_ALL.CSV /opt/app
CMD ["java", "-jar", "/opt/app/AddressSearch.jar", "/opt/app/KEN_ALL.CSV"]
