// Jenkinsfile for Workflow plugin at https://ci.jenkins-ci.org/view/All/job/Reporting/job/infra_unused-code-detector/

node('java') {
   /* timeout 60 minutes */
   timeout(60) {
      // Get the maven tool.
      // ** NOTE: This 'mvn' maven tool must be configured
      // **       in the global configuration.           
      def mvnHome = tool 'mvn'

      // Get the jdk tool.
      // ** NOTE: This 'jdk8' jdk tool must be configured
      // **       in the global configuration.           
      env.JAVA_HOME = tool 'jdk8'

      // Mark the checkout 'stage'....
      stage 'Checkout'
      git branch: 'master', url: 'https://github.com/jenkins-infra/unused-code-detector.git'

      // Mark the code build 'stage'....
      stage 'Build'
      // Run the maven build
      sh "${mvnHome}/bin/mvn clean package exec:java"

      // Mark the archive 'stage'....
      stage 'Archive'
      archive 'target/*.html'
   }
}
