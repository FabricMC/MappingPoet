node {
   stage 'Checkout'
   checkout scm

   stage 'Build'
   sh "chmod +x gradlew"
   sh "./gradlew clean build publish --stacktrace"
}