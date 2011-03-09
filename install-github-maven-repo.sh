mvn_install_plugin=org.apache.maven.plugins:maven-install-plugin:2.3.1
mvn ${mvn_install_plugin}:install-file \
-Dfile=meronym-utils-1.0.0-SNAPSHOT.jar \
-DgroupId=meronym-utils \
-DartifactId=meronym-utils \
-Dversion=1.0.0-SNAPSHOT \
-Dpackaging=jar \
-DpomFile=pom.xml \
-DcreateChecksum=true \
-DlocalRepositoryPath=./maven-repository

